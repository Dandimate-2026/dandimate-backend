/**
 * 참여 중인 방의 열람 가능한 기록으로 답하는 챗봇 기능.
 * controller는 HTTP 입출력, service는 권한 확인과 검색·답변 흐름,
 * dto는 공개 응답 조립을 담당한다. 컨트롤러에서 DB나 AI를 직접 호출하지 않는다.
 * 사진·글·참가자는 기존 도메인에서 조회하며 챗봇용 복제 엔티티를 만들지 않는다.
 */
package com.dandimate.durooduroo.chat;
