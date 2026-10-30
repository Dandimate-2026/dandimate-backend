/**
 * 사진 한 장의 원문으로 되묻기 gapType과 질문을 정하는 규칙 기반 서비스입니다.
 * AskbackAiService(DEV-C)를 붙이기 전까지 데모 실행(askbacks/run)에서 쓴다.
 */
package com.dandimate.durooduroo.askback.service;
import com.dandimate.durooduroo.memory.domain.EntryKind; import com.dandimate.durooduroo.memory.domain.MemoryEntry; import java.util.List; import org.springframework.stereotype.Service;
@Service public class AskbackRuleService {
    /** NO_ADDON: 덧붙임 0개이고 첫 한마디 20자 미만. 원문이 하나뿐이면 MISSING_REASON, 그 외 MISSING_CONTEXT. */
    public RuleResult decide(List<MemoryEntry> photoEntries){
        boolean noAddon=photoEntries.stream().noneMatch(e->e.getKind()==EntryKind.ADDON);
        boolean shortFirst=photoEntries.stream().filter(e->e.getKind()==EntryKind.FIRST).allMatch(e->e.getTranscript().strip().length()<20);
        if(noAddon&&shortFirst)return new RuleResult("NO_ADDON","이 사진에서 꼭 남기고 싶은 기억은 무엇인가요?");
        if(photoEntries.size()==1)return new RuleResult("MISSING_REASON","그 순간이 특별했던 이유는 무엇인가요?");
        return new RuleResult("MISSING_CONTEXT","사진 속 상황의 앞뒤 이야기를 조금 더 알려줄래요?");
    }
    public record RuleResult(String gapType,String question){}
}
