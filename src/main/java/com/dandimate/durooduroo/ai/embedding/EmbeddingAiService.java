package com.dandimate.durooduroo.ai.embedding;

import java.util.List;

/** 외부 모델 호출 계약. 저장소·권한·재시도 정책은 embedding 서비스가 담당한다. */
public interface EmbeddingAiService {
    /** 원문과 질문 모두 같은 모델·차원으로 처리하며 결과는 입력 순서를 유지한다. */
    EmbeddingResult embed(List<String> texts);
}
