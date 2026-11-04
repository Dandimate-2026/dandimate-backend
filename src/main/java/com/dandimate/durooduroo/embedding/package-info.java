/**
 * 덧붙임 원문은 그대로 두고 검색용 벡터를 별도 저장한다.
 * domain/repository: 덧붙임별 원문 해시·모델·차원·청크 벡터·처리 상태.
 * service: 같은 트랜잭션에서 작업 등록, 외부 임베딩은 트랜잭션 밖에서 실행.
 * 별도 지속성 큐로 최초 색인·재시도·중단 작업 복구를 처리한다.
 */
package com.dandimate.durooduroo.embedding;
