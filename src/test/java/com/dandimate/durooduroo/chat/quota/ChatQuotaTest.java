package com.dandimate.durooduroo.chat.quota;
import com.dandimate.durooduroo.chat.dto.ChatMessageResponse;
import com.dandimate.durooduroo.user.service.KakaoAccountService;
import com.dandimate.durooduroo.common.exception.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:chat-quota;MODE=MySQL;DB_CLOSE_DELAY=-1","app.jobs.poll-interval-ms=3600000","app.embedding.initial-delay-ms=3600000"})
class ChatQuotaTest {
 @Autowired ChatQuotaService quota;
 @Autowired KakaoAccountService accounts;
 @Autowired TransactionTemplate tx;
 @PersistenceContext EntityManager em;
 String user;
 @BeforeEach void setup(){user=accounts.findOrCreate(UUID.randomUUID().toString());}
 ChatMessageResponse response(String outcome){return new ChatMessageResponse("응답",List.of()).withOutcome(outcome);}
 @Test void freeThenCreditsAndIndependentAccount() {
  quota.grant(user,2);
  for(int i=0;i<4;i++)quota.execute(user,null,"질문",()->response("ANSWER"));
  assertThat(quota.get(user).credits()).isEqualTo(2);
  for(int i=0;i<2;i++)quota.execute(user,null,"질문",()->response("ANSWER"));
  assertThatThrownBy(()->quota.execute(user,null,"질문",()->response("ANSWER"))).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_LIMIT_EXCEEDED));
  assertThat(quota.get(accounts.findOrCreate(UUID.randomUUID().toString())).freeRemaining()).isEqualTo(4);
 }
 @Test void failuresAndNonAnswersRefund() {
  for(String outcome:List.of("INSUFFICIENT","INVALID","SMALL_TALK","OUT_OF_SCOPE"))quota.execute(user,null,"질문",()->response(outcome));
  assertThatThrownBy(()->quota.execute(user,null,"질문",()->{throw new IllegalStateException();})).isInstanceOf(IllegalStateException.class);
  assertThat(quota.get(user).freeRemaining()).isEqualTo(4);
 }
 @Test void concurrentRequestsCannotOverspend() throws Exception {
  try(var pool=Executors.newFixedThreadPool(10)) {
   var start=new CountDownLatch(1);var successes=new AtomicInteger();var calls=new ArrayList<Future<?>>();
   for(int i=0;i<10;i++)calls.add(pool.submit(()->{try{start.await();quota.execute(user,null,"질문",()->response("ANSWER"));successes.incrementAndGet();}
     catch(BusinessException e){assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_LIMIT_EXCEEDED);}catch(InterruptedException e){throw new RuntimeException(e);}}));
   start.countDown();for(var call:calls)call.get(20,TimeUnit.SECONDS);
   assertThat(successes.get()).isEqualTo(4);assertThat(quota.get(user).freeRemaining()).isZero();
  }
 }
 @Test void repeatedKeyChargesOnceAndRejectsDifferentQuestion() {
  String key=UUID.randomUUID().toString();var calls=new AtomicInteger();
  for(int i=0;i<2;i++)quota.execute(user,key,"질문",()->{calls.incrementAndGet();return response("ANSWER");});
  assertThat(calls.get()).isEqualTo(1);assertThat(quota.get(user).freeRemaining()).isEqualTo(3);
  assertThatThrownBy(()->quota.execute(user,key,"다른 질문",()->response("ANSWER"))).isInstanceOf(BusinessException.class);
 }
 @Test void kstDailyResetKeepsPurchasedCredits() {
  quota.grant(user,10);quota.execute(user,null,"질문",()->response("ANSWER"));
  tx.executeWithoutResult(s->em.find(ChatWallet.class,user).freeDate=LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1));
  var balance=quota.get(user);assertThat(balance.freeRemaining()).isEqualTo(4);assertThat(balance.credits()).isEqualTo(10);
  assertThat(balance.resetsAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));assertThat(balance.resetsAt().getHour()).isZero();
 }
 @Test void staleAttemptCannotSettleOrRefundNewAttempt() {
  String key=UUID.randomUUID().toString();
  assertThatThrownBy(()->quota.execute(user,key,"질문",()->{
    tx.executeWithoutResult(s->em.createQuery("update ChatUsage u set u.expiresAt=:time where u.userId=:user")
      .setParameter("time",Instant.now().minusSeconds(1)).setParameter("user",user).executeUpdate());
    quota.execute(user,key,"질문",()->response("ANSWER"));
    return response("ANSWER");
  })).isInstanceOf(BusinessException.class);
  assertThat(quota.get(user).freeRemaining()).isEqualTo(3);
 }
 @Test void expiredReservationReleasesBudget() {
  quota.get(user);
  tx.executeWithoutResult(s->{var w=em.find(ChatWallet.class,user);w.freeUsed=1;var u=new ChatUsage();u.id="expired";u.userId=user;u.requestHash="hash";u.status="PENDING";u.free=true;u.usageDate=w.freeDate;u.expiresAt=Instant.now().minusSeconds(1);em.persist(u);});
  assertThat(quota.get(user).freeRemaining()).isEqualTo(4);assertThat(quota.get(user).freeRemaining()).isEqualTo(4);
 }
}
