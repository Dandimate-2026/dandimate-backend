package com.dandimate.durooduroo.chat.quota;
import com.dandimate.durooduroo.chat.dto.ChatMessageResponse;
import com.dandimate.durooduroo.common.auth.Tokens;
import com.dandimate.durooduroo.common.exception.*;
import com.dandimate.durooduroo.user.domain.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Short account-locked transactions; never hold a database lock during AI calls. */
@Service
public class ChatQuotaService {
 @org.springframework.beans.factory.annotation.Autowired private com.dandimate.durooduroo.reviewerdemo.service.DemoScopeService scopes;
 public static final int FREE_LIMIT=4;
 private static final ZoneId KST=ZoneId.of("Asia/Seoul");
 @PersistenceContext private EntityManager em;
 private final TransactionTemplate tx;
 private final ObjectMapper json;
 @org.springframework.beans.factory.annotation.Autowired private com.dandimate.durooduroo.chat.service.ChatRecordAccess access;
 public ChatQuotaService(PlatformTransactionManager manager,ObjectMapper json) { tx=new TransactionTemplate(manager);this.json=json; }
 public record Quota(int freeLimit,int freeRemaining,long credits,OffsetDateTime resetsAt) {}
 private ChatWallet wallet(String user) {
  if(em.find(UserAccount.class,user,LockModeType.PESSIMISTIC_WRITE)==null)
   throw new BusinessException(ErrorCode.INVALID_MEMBER_TOKEN,"로그인이 필요합니다.");
  ChatWallet wallet=em.find(ChatWallet.class,user);
  if(wallet==null) { wallet=new ChatWallet(user);em.persist(wallet); }
  wallet.reset(LocalDate.now(KST));
  for(var usage:em.createQuery("select u from ChatUsage u where u.userId=:user and u.status='PENDING' and u.expiresAt<:now",ChatUsage.class)
    .setParameter("user",user).setParameter("now",Instant.now()).getResultList()) release(wallet,usage);
  return wallet;
 }
 public Quota get(String user) {return tx.execute(s->{var w=wallet(user);return new Quota(FREE_LIMIT,Math.max(0,FREE_LIMIT-w.freeUsed),w.credits,
   w.freeDate.plusDays(1).atStartOfDay(KST).toOffsetDateTime());});}
 private record Ticket(String id,String attemptId,ChatMessageResponse replay) {}
 public ChatMessageResponse execute(String user,String key,String fingerprint,Supplier<ChatMessageResponse> answer) {
  if(key!=null&&(key.length()<16||key.length()>128)) throw new BusinessException(ErrorCode.VALIDATION_ERROR,"Idempotency-Key는 16~128자입니다.");
  String scope=scopes.current(user);
  String id=Tokens.hash(user+":"+scope+":"+(key==null?UUID.randomUUID():key));
  String hash=Tokens.hash(fingerprint);
  Ticket ticket=tx.execute(s->{
   var w=wallet(user);scopes.requireCurrent(user,scope);var previous=em.find(ChatUsage.class,id);
   if(previous!=null) {
    if(!hash.equals(previous.requestHash))throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,"다른 질문에 사용된 요청 키입니다.");
    if(previous.response!=null) return new Ticket(id,null,read(previous.response));
    if(previous.status.equals("PENDING"))throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,"질문을 처리 중입니다.");
    em.remove(previous);em.flush();
   }
   if(w.freeUsed>=FREE_LIMIT && w.credits<=0)throw new BusinessException(ErrorCode.CHAT_LIMIT_EXCEEDED,"무료 질문과 크레딧을 모두 사용했어요.");
   var u=new ChatUsage();u.setDemoScope(scope);u.id=id;u.userId=user;u.requestHash=hash;u.status="PENDING";u.usageDate=w.freeDate;
   u.attemptId=UUID.randomUUID().toString();u.free=w.freeUsed<FREE_LIMIT;u.expiresAt=Instant.now().plusSeconds(600);
   if(u.free)w.freeUsed++;else w.credits--;
   em.persist(u);return new Ticket(id,u.attemptId,null);
  });
  if(ticket.replay()!=null) {
   var current=access.load(user,false);
   for(var source:ticket.replay().sources()) {
    var visible=new ArrayList<com.dandimate.durooduroo.chat.dto.ChatSourceView.EntryInfo>(source.entries());
    if(source.firstEntry()!=null)visible.add(source.firstEntry());
    for(var entry:visible) {
     var now=current.get(entry.entryId());
     if(now==null||!now.entry().equals(entry))throw new BusinessException(ErrorCode.VERSION_CONFLICT,"기록이나 권한이 바뀌었어요. 새 요청으로 질문해 주세요.");
    }
   }
   return ticket.replay();
  }
  try {
   var response=answer.get();
   tx.executeWithoutResult(s->{
    var w=wallet(user);scopes.requireCurrent(user,scope);var u=em.find(ChatUsage.class,id);
    if(!u.status.equals("PENDING")||!ticket.attemptId().equals(u.attemptId))throw new BusinessException(ErrorCode.AI_TEMPORARILY_UNAVAILABLE,"질문 처리 시간이 초과되었습니다. 다시 시도해 주세요.");
    if(!Set.of("ANSWER","CONFLICT").contains(response.outcome())) release(w,u);
    else u.status="CHARGED";
    if(key!=null)u.response=write(response);
   });
   return response;
  } catch(RuntimeException failure) {
   tx.executeWithoutResult(s->{var w=wallet(user);var u=em.find(ChatUsage.class,id);if(u!=null&&u.status.equals("PENDING")&&ticket.attemptId().equals(u.attemptId))release(w,u);});
   throw failure;
  }
 }
 @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="PT1H")
 public void cleanup() { tx.executeWithoutResult(s->em.createQuery("delete from ChatUsage u where u.status<>'PENDING' and u.expiresAt<:before")
   .setParameter("before",Instant.now().minusSeconds(86400)).executeUpdate()); }
 private void release(ChatWallet wallet,ChatUsage usage) {
  if(usage.free) { if(usage.usageDate.equals(wallet.freeDate))wallet.freeUsed--; }
  else wallet.credits++;
  usage.status="RELEASED";
 }
 /** Called in the same transaction as the locked payment order's DONE transition. */
 @org.springframework.transaction.annotation.Transactional
 public void grant(String user,int credits) {if(credits<=0)throw new IllegalArgumentException();wallet(user).credits+=credits;}
 private String write(ChatMessageResponse value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 private ChatMessageResponse read(String value) {try{return json.readValue(value,ChatMessageResponse.class);}catch(Exception e){throw new IllegalStateException(e);}}
}
