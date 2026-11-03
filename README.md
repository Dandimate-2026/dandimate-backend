# 두루두루 Backend

친구들이 올린 사진과 음성·글을 모아 AI가 한 편의 두루마리로 엮는 서비스의 Spring Boot 단일 서버입니다.
공개 API와 OpenAI 호출을 모두 이 애플리케이션 하나가 담당합니다.

- API 계약(단일 출처): `Dandimate-2026/dandimate-backend`의 `docs/swagger/openapi.yaml`
  → 이 레포에는 `src/main/resources/static/openapi/public-api.yaml`로 복사해 두고, 실행 중인 서버의 `/swagger-ui.html`에서 볼 수 있습니다.
  계약서를 바꾸면 이 파일도 같이 바꾸세요. `ContractSchemaTest`가 실제 응답을 이 스키마로 검사합니다.
- 기능 명세: 개발 역할·기능 명세(장 번호 0~13)를 기준으로 구현했습니다.

## 빠르게 실행 (OpenAI 키 없이)

```bash
./gradlew bootRun
```

- `local` 프로필(기본): H2 메모리 DB, 로컬 파일 저장소(`uploads/`), **가짜 STT**, **규칙 기반 AI**, 데모 기능 켜짐
- 헬스체크 `GET http://localhost:8080/api/v1/health`, API 문서 `http://localhost:8080/swagger-ui.html`
- 테스트: `./gradlew test`

## 패키지 구조

```text
src/main/java/com/dandimate/durooduroo/
├── room          방·초대·상태 전이(COLLECTING→CIRCULATING→WEAVING→GROWING)·방장 관리·마감 스케줄러
├── member        입장·닉네임·꽃 색·토큰(SHA-256 해시 저장)
├── photo         업로드 URL 발급·업로드 완료 검증·HEIC JPEG 파생본(EXIF 제거)
├── memory        원문(첫 한마디·덧붙임·되묻기 답)·패스·돌려보기 잠금·받아쓰기 초안
├── askback       되묻기 분석·발송·답변
├── scroll        두루마리 생성(장면 카드→엮기→인용 검증)·답가·문단 버전
├── notification  앱 내 알림·SSE
├── job           AI 작업(Job)·Worker(SKIP LOCKED, 재시도, 멈춘 작업 복구)
├── ai            AI 계약(interface + record DTO)과 구현: rule(기본)·openai·fake STT. Repository를 쓰지 않는다.
├── storage       로컬/S3 저장소·서명 URL
├── event         지표용 이벤트 로그
└── common        공통 응답·오류 코드·인증·멱등성(Idempotency-Key)
```

## 프론트엔드 연동 요약

- 인증: `Authorization: Bearer {memberAccessToken}` (방 생성·초대 API 제외). 토큰을 쿼리로 보내면 401.
- 응답: 성공 `{data, meta:{requestId}}`, 실패 `{error:{code, message, details, requestId}}`, 시각은 오프셋 포함 ISO 8601.
- 멱등성: 계약서에 `Idempotency-Key`가 있는 POST 13개는 요청마다 새 UUID를 붙이고, 네트워크 재시도에만 같은 키를 다시 씁니다.
- 사진: `upload-requests` → 응답의 `uploadUrl`에 파일을 `requiredHeaders`와 함께 **PUT** → 본문 없이 `POST /photos/{id}/complete`.
  HEIC는 `PROCESSING`이었다가 파생본이 생기면 `READY`(SSE `photo.uploaded`).
- 마감·다시 엮기·답가는 `202 {data:{job}}`. 완료는 `GET /jobs/{id}` 폴링이나 SSE(`job.updated`, `scroll.ready`).
- SSE: `POST /rooms/{id}/event-token` 응답의 `eventsUrl`을 그대로 `EventSource`에 넣습니다(60초 일회용).
- 초대 토큰 원문은 저장하지 않으므로, 링크를 잃으면 방장이 재발급(`invite-token/rotate`)합니다.

## 로컬 MySQL + MinIO

```bash
cp .env.example .env            # 값 채우기
docker compose up -d            # MySQL 8.4, MinIO
# .env의 MYSQL_URL에 &가 있어 source 대신 env로 넘긴다
env $(grep -v '^#' .env | grep . | xargs) ./gradlew bootRun --args='--spring.profiles.active=mysql'
```

- 테이블은 Flyway가 `src/main/resources/db/migration/V*.sql`로 만들고, Hibernate는 엔티티와 맞는지만 검사합니다(`validate`).
  엔티티를 바꾸면 `V2__설명.sql`처럼 새 파일을 추가하세요. 예전에 `update`로 만든 DB는 처음 켤 때 V1로 표시(baseline)하고 이어서 진행합니다.

- `STORAGE_TYPE=s3`, `AWS_S3_ENDPOINT=http://localhost:9000`, `AWS_S3_BUCKET=durooduroo`로 두고 MinIO 콘솔(`http://localhost:9001`)에서 버킷을 한 번 만듭니다.
- 데모 마감을 쓰려면 `DEMO_ENABLED=true`, 키 없이 음성을 쓰려면 `STT_PROVIDER=fake`.
- 이미 만든 MySQL DB에서 `paragraphs.body`·`scrolls.body`·`paragraph_versions.body`가 `TINYTEXT`면 `MEDIUMTEXT`로 바꾸거나 DB를 다시 만드세요(그대로 두면 `validate`가 실패합니다).

## 운영 배포

**AWS 무료 등급 기준으로 EC2 한 대에 백엔드와 MySQL을 같이 올리고, 사진·음성·DB 백업은 S3에 둡니다.**
AWS 콘솔 설정 순서, 서버 준비, GitHub Secrets, 백업·복원은 [`deploy/README.md`](deploy/README.md)에 있습니다.

- 이미지: `Dockerfile` (JRE 21 + ffprobe + heif-convert, 비루트 사용자)
- `.github/workflows/cd.yml`: main 푸시 → 테스트 → GHCR 이미지 → `deploy/` 파일을 EC2에 복사 → `docker compose -f docker-compose.prod.yml up -d`
- `.github/workflows/ci.yml`: 전체 테스트 + MySQL 8.4에서 Flyway·`validate` 검사
- 서버 `.env`: [`deploy/.env.production.example`](deploy/.env.production.example) (DB 주소는 compose가 정하므로 넣지 않습니다)

`mysql` 프로필로 시작하면 위험한 설정(데모 켜짐, 가짜 STT, 다른 서버 DB에 TLS 없이 연결 등)을 `[운영 설정 점검]` 경고 로그로 알려 줍니다.

nginx(프론트와 같은 서버)에서 `/api`를 백엔드로 넘길 때:

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    client_max_body_size 11m;          # 음성 10MB
}
location ~ ^/api/v1/rooms/[^/]+/events$ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_buffering off;               # SSE
    proxy_read_timeout 1h;
}
```

SSE 허브는 서버 한 대 기준입니다. 여러 대로 늘리면 Redis pub/sub 같은 중계가 필요합니다.

## 실제 OpenAI로 전환 (체크리스트)

1. 서버 `.env`에 `OPENAI_API_KEY`, `OPENAI_TEXT_MODEL`(텍스트 모델), `OPENAI_STT_MODEL`(전사 모델, 기본 `gpt-transcribe`)을 넣는다.
2. `AI_PROVIDER=openai`, `STT_PROVIDER=openai`로 바꾼다. 둘은 따로 켤 수 있다.
3. 평가셋을 OpenAI 구현으로 돌린다: `AI_PROVIDER=openai OPENAI_API_KEY=... OPENAI_TEXT_MODEL=... ./gradlew test --tests '*AiEvalTest'`
   (`src/test/resources/ai-eval/*.jsonl` 35건. 인용 정확도·없는 ID·60자·기존 인용 유지 등 자동 판정)
4. 프롬프트는 `src/main/resources/prompts/reply-classify-v1.md`, `{askback,scene-card,reweave}-v2.md`, `weave-v3.md`. 바꿀 때는 새 버전 파일을 만들고 평가 결과를 PR에 첨부한다.
5. 장면 카드와 되묻기에는 사진도 함께 보낸다(`AI_SEND_PHOTOS=true` 기본, 저장소에서 읽어 data URL·`detail=low`로 전송, HEIC는 JPEG 파생본). 사진에서는 보이는 장면만 쓰고 이름·이유는 원문에서만 가져온다. 끄면 원문만 보낸다.
   사진을 보고 만든 장면은 엮기 입력의 `photoScene`으로만 넘어가 문단 첫머리 장면 묘사에 쓰인다.
6. 품질 채점(LLM judge): `AI_PROVIDER=openai ... ./gradlew test --tests '*AiJudgeEvalTest'` → `build/reports/ai-judge/report.md`.
   기준은 되묻기 "답할 가치가 있는 질문" 80% 이상, 문단 지어낸 사실 0건·종합 평균 3.5 이상. 기본 CI에서는 건너뛴다.
7. 대표 음성 30건 회귀 평가(명세 5장)는 실제 녹음 샘플이 필요해 아직 없다.


## 카카오 로그인과 사용자별 방 관리

### 변경 구조

- `users`: 서비스 전체 계정. `(provider, provider_user_id)`를 고유하게 저장해 같은 카카오 계정의 재로그인 시 기존 계정을 사용한다. 이메일·카카오 닉네임은 계정 식별에 사용하지 않는다.
- `members.user_id → users.id`: 계정과 방별 참가자 연결. `(room_id, user_id)` 중복을 막는다. `members.id`와 사진·원문의 작성자 연결, 방별 닉네임·역할은 유지한다.
- `inviteToken`은 초대 링크용으로 유지한다. `memberAccessToken`은 로그인한 사용자의 방 생성·입장 응답에서 제거한다. 외부 API는 기존 Bearer 토큰을 로그인 수단으로 받지 않는다.
- 인증된 사용자 ID로 해당 방의 참가자를 찾아 기존 방장·작성자 권한을 검사한다. 같은 계정이 같은 초대로 재입장하면 기존 참가 정보를 반환한다.
- 방 생성 등 멱등성 응답도 계정별로 분리한다. 서로 다른 사용자가 같은 요청과 키를 보내도 다른 사용자의 방·응답을 재사용하지 않는다.
- 최초 카카오 인증 시 최소 계정을 자동 생성한다. 방 생성·입장 시 기존 서비스 이용·미디어·외부 AI 처리 동의 검사는 그대로 유지한다. 별도 가입 완료 화면이나 계정 연결/탈퇴 기능은 이번 범위에 포함하지 않는다.

### 인증 경로와 클라이언트 연동

| 경로 | 동작 |
|---|---|
| `GET /oauth2/authorization/kakao` | 브라우저를 카카오 로그인으로 이동 |
| `GET /login/oauth2/code/kakao` | 인가 코드·state 검증, 계정 조회/생성, 세션 발급 후 프론트로 이동 |
| `GET /api/v1/auth/csrf` | 쓰기 요청에 필요한 `headerName`, `token` 반환 |
| `GET /api/v1/users/me` | 내 계정 ID·가입 시각 |
| `GET /api/v1/users/me/rooms` | 참여 방과 각 방의 참가자 ID·닉네임·역할 |
| `POST /api/v1/auth/logout` | 현재 서비스 세션 종료 (카카오 전체 로그아웃/연결 해제 아님) |

1. 프론트에서 로그인 시작 주소로 **페이지 이동**한다. OAuth 경로는 `/api/v1` 밖이므로 프록시도 `/oauth2/`, `/login/`을 백엔드로 전달해야 한다.
2. 로그인 후 브라우저가 `JSESSIONID`를 관리한다. API 요청은 `credentials: 'include'`를 사용한다. 서비스 토큰을 URL이나 로컬 스토리지로 전달하지 않는다.
3. `/api/v1/auth/csrf` 응답의 `token`을 POST/PUT/PATCH/DELETE 요청의 `X-CSRF-TOKEN`에 넣는다. 로그인·로그아웃 후에는 토큰을 다시 조회한다. 기존 `Idempotency-Key` 요구도 유지한다.
4. 초대 링크 미리보기는 공개지만 방 생성·초대 입장은 로그인 후 진행한다. 초대 링크 정보를 프론트가 보관하고 로그인 완료 후 입장 요청을 보내면 된다.
5. 서명된 사진/음성 조회 URL, 일회용 SSE URL은 기존 서명/토큰 검증을 사용한다. 서명된 로컬 사진 업로드 PUT만 CSRF 검사를 제외한다.

### 서버 설정

실제 키는 코드/커밋에 넣지 않고 AWS 서버 환경변수 또는 Secrets Manager에서 주입한다. `.env.example`에는 이름과 예시만 둔다. Spring Boot가 `.env` 파일을 자동으로 읽는 것은 아니므로 실행 환경에서 주입해야 한다.

| 변수 | 내용 |
|---|---|
| `KAKAO_CLIENT_ID` | 카카오 REST API 키 |
| `KAKAO_CLIENT_SECRET` | 카카오 Client Secret |
| `KAKAO_REDIRECT_URI` | 등록한 백엔드 주소 + `/login/oauth2/code/kakao` (완전히 일치해야 함) |
| `APP_FRONTEND_URL` | 로그인 완료 후 이동할 프론트 **origin**, 경로·끝 슬래시 제외. CORS 허용 출처에도 사용 |
| `SESSION_COOKIE_SECURE` | 기본 `true`. HTTPS 사용. 개인 HTTP 개발 환경에서만 `false` |

카카오 로그인 활성화와 Redirect URI 등록 후 서버 설정을 적용한다. 카카오 설정이 빠졌을 때 로그인 시작/콜백은 `503 OAUTH_NOT_CONFIGURED`를 반환하며 보호 API는 계속 인증을 요구한다. 서버 설정 값이나 실제 키를 응답에 노출하지 않는다.

현재 세션은 서버 메모리에 보관하며 비활동 30분 후 만료한다. 단일 백엔드 인스턴스 기준이고, 재시작하면 재로그인이 필요하다. 다중 인스턴스 배포 전에는 공유 세션 저장소를 추가해야 한다. 프론트와 API는 같은 사이트의 HTTPS 도메인(예: `app.example.com`, `api.example.com`)을 사용한다. 서로 다른 사이트를 사용할 경우 쿠키·CSRF 정책 재설계가 필요하다.

### 기존 DB와 검증 범위

- 테이블 변경은 Flyway `V2__kakao_accounts.sql`, `V3__room_plans.sql`(`src/main/resources/db/migration`)로 서버가 켜질 때 자동 적용된다. 손으로 SQL을 실행하지 않는다. 로컬 H2는 JPA가 새 구조를 만든다.
- 기존 익명 참가자의 `user_id`는 NULL로 남긴다. 닉네임만으로 계정을 자동 연결하지 않는다. 기존 데이터가 필요한 운영 환경은 소유권 확인을 포함한 별도 이전 작업이 필요하다. 이번 변경은 기존 행을 삭제하지 않는다.
- 기존 업무 회귀 테스트는 테스트 전용 `LegacyWorkflowSecurity`로 이전 참가자 토큰 시나리오를 유지한다. 이 설정은 운영 빌드에 포함되지 않는다.
- `AccountSecurityTest`는 실제 보안 필터를 사용해 세션/CSRF, 비참여자·방장 권한, 중복 입장, 계정별 멱등성, 사진·음성·두루마리 흐름을 검증한다.
- `OAuthCallbackTest`는 카카오 토큰/사용자 정보 응답을 모의해 콜백부터 계정 생성·세션 교체·내 정보 조회를 검증한다. 실제 카카오 계정으로의 브라우저 로그인 및 AWS 배포 검증은 서버 설정 후 별도 수행해야 한다.
