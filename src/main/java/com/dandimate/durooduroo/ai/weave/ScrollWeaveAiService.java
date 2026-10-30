/** 두루마리 엮기 계약 (명세 8장). 사진 1장당 문단 1개, 문단마다 실제 원문 인용 1개 이상. */
package com.dandimate.durooduroo.ai.weave;

public interface ScrollWeaveAiService {
    ScrollWeaveResult weave(ScrollWeaveCommand command);
}
