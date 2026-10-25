# 두루두루 Backend

2026 단디톤 두루두루 서비스의 Spring Boot 백엔드 저장소입니다.

## 저장소 구조

```text
.
├── docs/
│   ├── api/API_SPEC.md
│   └── swagger/
│       ├── index.html
│       └── openapi.yaml
└── README.md
```

## API 문서

- 사람이 읽는 API·기능 명세: `docs/api/API_SPEC.md`
- OpenAPI 원본: `docs/swagger/openapi.yaml`
- Swagger UI: `docs/swagger/index.html`

Swagger UI를 로컬에서 확인하려면 저장소 루트에서 다음 명령을 실행합니다.

```bash
python3 -m http.server 8080
```

이후 브라우저에서 <http://localhost:8080/docs/swagger/>를 엽니다.

## 개발 원칙

- 외부 공개 API 계약은 `docs/swagger/openapi.yaml`을 기준으로 합니다.
- API 변경 시 구현보다 먼저 OpenAPI와 `API_SPEC.md`를 함께 수정합니다.
- OpenAI API 키와 기타 비밀값은 커밋하지 않고 환경 변수로 관리합니다.
