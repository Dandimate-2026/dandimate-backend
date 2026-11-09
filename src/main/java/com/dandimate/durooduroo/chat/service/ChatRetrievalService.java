package com.dandimate.durooduroo.chat.service;
import com.dandimate.durooduroo.ai.embedding.OpenAiEmbeddingService;
import com.dandimate.durooduroo.embedding.repository.MemoryEmbeddingRepository;
import com.dandimate.durooduroo.embedding.service.EmbeddingText;
import com.dandimate.durooduroo.common.exception.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
/** Rank photos by lexical + semantic reciprocal rank fusion; no universal cosine cutoff. */
@Service
public class ChatRetrievalService {
  private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ChatRetrievalService.class);
 private final MemoryEmbeddingRepository index;private final OpenAiEmbeddingService embeddings;
 public ChatRetrievalService(MemoryEmbeddingRepository index,OpenAiEmbeddingService embeddings){this.index=index;this.embeddings=embeddings;}
 private static final Pattern WORD=Pattern.compile("[가-힣a-z0-9]+");
 private static final Set<String> STOP=Set.of("누가","누구","누구랑","누구였지","누구였어","옛날에","같은데","것","같아","알려줘","어떤","뭐","뭐였지","에서","있어","있었어","이야기","기억","그때","그거","우리","우리가");
 public List<String> retrieve(String question,Map<String,ChatRecordAccess.Record> allowed){
  var rows=new HashMap<String,com.dandimate.durooduroo.embedding.domain.MemoryEmbedding>();
  index.findAllById(allowed.keySet()).forEach(r->rows.put(r.getEntryId(),r));
  for(var r:allowed.values()){
   var v=rows.get(r.entryId());
   if(v==null||!v.ready()||!v.getModel().equals(embeddings.model())||!v.getSourceHash().equals(r.hash()))
    throw new BusinessException(ErrorCode.AI_TEMPORARILY_UNAVAILABLE,"새 기록의 검색을 준비하고 있어요. 잠시 후 다시 질문해 주세요.");
  }
   long embeddingStarted=System.nanoTime();
   var queries=embeddings.embed(EmbeddingText.chunks(question)).vectors();
   log.info("Chat query embedding done model={} chunks={} latencyMs={}",embeddings.model(),queries.size(),(System.nanoTime()-embeddingStarted)/1_000_000);
  Map<String,Double> semantic=new HashMap<>(),lexical=new HashMap<>();
  var words=keywords(question);
  for(var r:allowed.values()){
   String photoId=r.source().photo().photoId();double best=-1;
   var documentVectors=rows.get(r.entryId()).vectors();
   for(var q:queries)for(var v:documentVectors)best=Math.max(best,cosine(q,v));
   semantic.merge(photoId,best,Math::max);
   var photo=r.source().photo();
   String document=(r.source().room().name()+" "+r.source().room().meetingDate()+" "+photo.placeName()+" "+photo.address()+" "+photo.city()+" "+r.entry().author().nickname()+" "+r.entry().text()).toLowerCase(Locale.ROOT);
   double score=0;for(var word:words)if(document.contains(word))score+=word.length();
   lexical.merge(photoId,score,Math::max);
  }
  Map<String,Double> fused=new HashMap<>();rank(semantic,fused,false);rank(lexical,fused,true);
  return fused.keySet().stream().sorted(Comparator.<String>comparingDouble(fused::get).reversed().thenComparing(x->x)).toList();
 }
 private static Set<String> keywords(String query){
  Set<String> words=new LinkedHashSet<>();var m=WORD.matcher(query.toLowerCase(Locale.ROOT));
  while(m.find()){
   String w=m.group();if(STOP.contains(w))continue;
   w=w.replaceFirst("(에서는|에게서|에서|으로|하고|이랑|랑|은|는|이|가|을|를|에)$","");
   if(w.length()>=2||w.matches("[a-z0-9]"))words.add(w);
  }return words;
 }
 private static void rank(Map<String,Double> channel,Map<String,Double> fused,boolean positiveOnly){
  var sorted=channel.keySet().stream().filter(p->!positiveOnly||channel.get(p)>0)
   .sorted(Comparator.<String>comparingDouble(channel::get).reversed().thenComparing(x->x)).toList();
  for(int i=0;i<sorted.size();i++)fused.merge(sorted.get(i),1.0/(60+i+1),Double::sum);
 }
 public static double cosine(List<Double> a,List<Double> b){
  if(a.size()!=b.size())return -1;double dot=0,aa=0,bb=0;
  for(int i=0;i<a.size();i++){dot+=a.get(i)*b.get(i);aa+=a.get(i)*a.get(i);bb+=b.get(i)*b.get(i);}
  return aa==0||bb==0?-1:dot/Math.sqrt(aa*bb);
 }
}
