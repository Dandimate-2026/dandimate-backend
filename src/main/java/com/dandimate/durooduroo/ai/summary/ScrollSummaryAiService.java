/** 지도 바텀시트에 보여 줄 두루마리 한 줄 요약 계약. 80자를 넘거나 비어 있으면 저장하지 않는다. */
package com.dandimate.durooduroo.ai.summary;

public interface ScrollSummaryAiService {
    int MAX_LENGTH = 80;

    String summarize(ScrollSummaryCommand command);
}
