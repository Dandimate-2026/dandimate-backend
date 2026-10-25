# 두루두루 Spring 단일 서버 개발 역할·기능 명세

<aside>
💡

이 문서는 역할별로 노션 페이지를 나누기 위한 복붙용 문서입니다.

- `#` 제목 하나를 노션 페이지 하나로 사용합니다.
- `DEV-A`, `DEV-B`, `DEV-C`, `DEV-D`, `FE`는 실제 담당자 이름으로 교체합니다.
- 공개 API와 OpenAI 호출을 모두 하나의 Spring Boot 애플리케이션에서 구현합니다.
- 프론트 계약의 단일 출처는 `openapi/public-api.yaml`입니다. 이 문서의 JSON 예시는 이해를 돕는 용도이며, 필드가 다르면 yaml이 우선합니다.
- 동작·제한값·화면 규칙의 기준은 기능명세서(FR-*)입니다. 기능명세서 11장 API 요약표는 이 문서와 yaml로 대체합니다.
- AI 기능 사이의 계약은 Java interface와 record DTO로 관리합니다.

</aside>

# 0. 공통 개발 규칙

## 배경

두루두루는 친구들이 올린 사진과 음성·글을 모아 AI가 한 편의 두루마리로 엮는 모바일 웹 서비스다. 가입 없이 초대 링크와 닉네임만으로 참여하므로, 계정 기반 서비스와 다른 권한·복구 규칙이 필요하다.

AI는 결과를 생성하지만 방 상태·권한·한도·원문·버전의 최종 판단은 도메인 서비스가 한다. `ai` 패키지는 OpenAI 호출과 결과 파싱만 담당하며 Repository를 직접 호출하지 않는다.

## 목표

1. 프론트는 Spring 공개 API만 호출한다.
2. 하나의 Spring 애플리케이션만 MySQL, Object Storage, OpenAI에 접근한다.
3. `ai` 패키지는 Java 입력 DTO를 받아 결과 DTO를 반환하며 DB에 직접 접근하지 않는다.
4. AI가 만든 모든 인용은 실제 원문에 존재해야 한다.
5. 모든 생성 요청은 실패·재시도·중복 요청을 견딘다.

## 전체 구조

```text
[Mobile Web]
    │  /api/v1 + memberAccessToken
    ▼
[Spring Boot]
    ├── domain       방·사진·원문·되묻기·두루마리
    ├── ai           OpenAI Client·Prompt·DTO·Validator
    ├── jobs         비동기 AI 작업
    ├── MySQL
    ├── Object Storage
    └── OpenAI API
```

### 권장 패키지 구조

```text
com.duruduru
├── room
├── member
├── media
├── entry
├── circulation
├── askback
├── scroll
├── reply
├── notification
├── job
└── ai
    ├── OpenAiConfig
    ├── client
    ├── transcription
    ├── askback
    ├── scene
    ├── weave
    ├── reply
    ├── prompt
    └── validation
```

`ai` 패키지는 Repository를 주입받지 않고 Java command/result DTO와 OpenAI SDK만 사용한다. 도메인 서비스가 DB에서 필요한 데이터를 읽어 command를 만들고, 반환된 result를 검증·저장한다.

## 담당

| 역할 | 주담당 영역 |
|---|---|
| **DEV-A** | 방·초대·참여자 인증, 사진·음성 파일, 첫 한마디·덧붙임 CRUD |
| **DEV-B** | 돌려보기, 마감 상태, AI Job, 되묻기 발송·답변, 두루마리·답가·알림·버전 관리 |
| **DEV-C** | Spring `ai` 패키지 공통 클라이언트, 음성 받아쓰기, AI 되묻기 |
| **DEV-D** | Spring `ai` 패키지 장면 카드, 두루마리 엮기, 답가 판별·평가 |
| **FE** | 모바일 웹, OpenAPI 클라이언트, 폴링·SSE |

## 협업 규칙

1. 공개 API 계약 변경은 해당 기능 담당자와 FE 담당자 승인이 필요하다.
2. Java AI DTO·interface 변경은 도메인 담당자와 AI 기능 담당자 승인이 필요하다.
3. `ai` 패키지는 Repository와 EntityManager를 직접 사용하지 않는다.
4. 브라우저는 OpenAI를 직접 호출하지 않는다.
5. 모든 AI 결과 DTO에 `schemaVersion`, `promptVersion`, `model`, `openaiRequestId`를 포함한다.
6. AI 요청·응답 원문과 사진·음성 URL은 로그에 남기지 않는다.
7. API·DB enum은 영문 대문자를 쓰고 한국어는 화면에서만 사용한다.

## 공통 인증

| 토큰 | 용도 | 저장 |
|---|---|---|
| `inviteToken` | 방 미리보기와 신규 입장 | DB에는 SHA-256 해시만 저장 |
| `memberAccessToken` | 입장 후 참여자 권한 | DB에는 SHA-256 해시만 저장 |

```http
Authorization: Bearer {memberAccessToken}
```

`/api/v1/rooms` 생성, `/api/v1/invites/**`를 제외한 모든 API는 위 헤더가 필수다.

> 닉네임만 입력해 기존 참여자 권한을 복구하면 다른 사람이 사진과 음성을 삭제할 수 있다. 브라우저 토큰을 잃은 사용자는 MVP에서는 새 참여자로 입장시킨다.

## 공통 응답

### 성공

```json
{
  "data": {},
  "meta": {
    "requestId": "01J..."
  }
}
```

### 실패

```json
{
  "error": {
    "code": "NICKNAME_TAKEN",
    "message": "이미 사용 중인 이름이에요.",
    "details": {
      "suggestions": ["서연2", "서연3"]
    },
    "requestId": "01J..."
  }
}
```

## 공통 enum

| 의미 | enum |
|---|---|
| 방 상태: 모으는 중 / 돌려보는 중 / 엮는 중 / 완성·자라는 중 | `COLLECTING` / `CIRCULATING` / `WEAVING` / `GROWING` |
| 방장/참여자 | `HOST` / `MEMBER` |
| 원문 종류: 첫 한마디/덧붙임/되묻기 답 | `FIRST` / `ADDON` / `ASKBACK_ANSWER` |
| 입력 방식: 음성/글 | `VOICE` / `TEXT` |
| 사진 상태 | `UPLOADING` / `PROCESSING` / `READY` / `FAILED` |
| 마감 방식 | `SCHEDULED` / `EARLY` / `DEMO` |
| 작업 상태: 대기/실행/성공/실패 | `QUEUED` / `RUNNING` / `SUCCEEDED` / `FAILED` |
| 작업 유형 | `ASKBACK_ANALYZE` / `WEAVE_SCROLL` / `REPLY_REWEAVE` / `MEDIA_DELETE` |
| 받아쓰기 컨텍스트 | `PHOTO` / `ASKBACK` / `PARAGRAPH` |
| 되묻기 유형 | `CONFLICT` / `MISSING_REASON` / `NO_ADDON` / `MISSING_CONTEXT` |
| 되묻기 상태 | `PENDING` / `ANSWERED` / `FORGOT` / `EXPIRED` / `CANCELLED` |
| 답가 분류 | `NEW_FACT` / `CORRECTION` / `REACTION_ONLY` / `UNSAFE` |
| 답가 상태 | `PENDING` / `APPLIED` / `REACTION` / `REJECTED` / `DELETED` |
| 알림 유형 | `ASKBACK_RECEIVED` / `SCROLL_READY` / `REPLY_APPLIED` |
| 꽃 색 (입장 순서대로 배정) | `AZALEA`(진달래) / `FORSYTHIA`(개나리) / `ROYAL_AZALEA`(철쭉) / `CANOLA`(유채) / `LILAC`(라일락) / `FORGET_ME_NOT`(물망초) / `TEAL`(청록) / `PURPLE`(자주) |

## 공통 오류 코드

| HTTP | 코드 | 의미 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 필드 형식·길이 오류 |
| 400 | `CONSENT_REQUIRED` | 필수 동의 누락 |
| 401 | `INVALID_MEMBER_TOKEN` | 참여자 토큰 없음·오류 |
| 403 | `FORBIDDEN` | 방장·작성자·참여자 권한 없음 |
| 404 | `ROOM_NOT_FOUND` | 존재하지 않는 방 또는 초대 링크 |
| 404 | `RESOURCE_NOT_FOUND` | 사진·원문·문단·되묻기 없음, 또는 볼 권한 없음 |
| 409 | `ROOM_FULL` | 방 정원 8명 초과 |
| 409 | `NICKNAME_TAKEN` | 닉네임 중복 |
| 409 | `ROOM_WEAVING` | 엮는 중 쓰기 요청 |
| 409 | `INVALID_ROOM_STATE` | 현재 상태에서 불가능한 요청 |
| 409 | `LOCKED` | 내 응답 전 타인 이야기 요청 |
| 409 | `VERSION_CONFLICT` | 문단·방 버전 충돌 |
| 409 | `ASKBACK_CLOSED` | 이미 종료된 되묻기에 답변 요청 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 키로 다른 내용 요청 |
| 413 | `FILE_TOO_LARGE` | 파일 크기 제한 초과 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 지원하지 않는 파일 형식 |
| 422 | `MIN_DURATION_NOT_MET` | 음성 최소 길이 미달 |
| 422 | `MAX_DURATION_EXCEEDED` | 음성 최대 길이 초과 |
| 422 | `UPLOAD_VERIFICATION_FAILED` | 업로드 완료 시 Object Storage 검증 실패 |
| 422 | `LIMIT_REACHED` | 사진·덧붙임·재생성 한도 |
| 502 | `STT_FAILED` | 받아쓰기 실패 |
| 502 | `AI_INVALID_OUTPUT` | AI 결과 형식·근거 검증 실패 |
| 503 | `AI_TEMPORARILY_UNAVAILABLE` | 일시적인 AI 장애 |
| 504 | `AI_TIMEOUT` | AI 처리 시간 초과 |

## 상태별 쓰기 허용

| 요청 | COLLECTING | CIRCULATING | WEAVING | GROWING |
|---|:-:|:-:|:-:|:-:|
| 신규 입장 | O | O | O | O |
| 사진 업로드·첫 한마디 | O | O | X | 2차 |
| 덧붙임·패스 | – | O | X | 2차 |
| 되묻기 답변 | – | O | X | X (마감 시 만료) |
| 원문 수정 | O | O | X | X |
| 원문·사진 삭제 | O | O | X | O |
| 답가 | X | X | X | O |

X는 `WEAVING`이면 `409 ROOM_WEAVING`, 그 외에는 `409 INVALID_ROOM_STATE`를 반환한다.

"2차"는 기능명세서 FR-G5(마감 후 추가 반영)를 1차에서 미룬 항목이다. 1차에서는 `409 INVALID_ROOM_STATE`를 반환하고, 2차에서 O로 바꾼다. 마감 후 합류자는 두루마리 열람과 답가만 할 수 있다.

## 멱등성

아래 API는 `Idempotency-Key`를 필수로 받는다.

- 방 생성·입장
- 사진 업로드 URL 발급·업로드 완료
- 첫 한마디·덧붙임·되묻기 답변·답가 확정
- 되묻기 기억 안 나요
- 조기 마감·다시 엮기

같은 사용자·경로·키로 24시간 안에 다시 요청하면 최초 응답을 그대로 반환한다. 인증 전 요청(방 생성·입장)은 사용자 대신 요청 본문 해시로 구분한다. 같은 키에 다른 본문이 오면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.

## 완료 조건

- [ ] 공개 API가 Swagger UI에서 열린다.
- [ ] OpenAI 호출부를 mock으로 교체해 전체 흐름을 테스트할 수 있다.
- [ ] 공통 enum과 Java DTO가 테스트로 고정된다.
- [ ] AI JSON Schema와 Java DTO가 달라지면 테스트가 실패한다.
- [ ] API 키가 프론트 번들과 Git 이력에 존재하지 않는다.

# 1. DEV-A · 방·초대·참여자

## 배경

사용자는 회원가입 없이 초대 링크와 닉네임만으로 방에 들어온다. 링크를 아는 것과 방의 참여자 권한을 갖는 것은 다르므로 초대 토큰과 참여자 토큰을 분리해야 한다.

## 목표

> 방 생성자가 자동으로 방장이 되고, 초대받은 사용자가 동의와 닉네임만 입력하면 3초 안에 방 홈으로 이동한다.

## 담당

- 주담당: **DEV-A**
- 연동: FE, DEV-B
- 테이블: `rooms`, `members`, `idempotency_keys`

## 기능적 요구사항

- 방 이름은 1~20자, 닉네임은 1~10자다.
- 방 생성 시 방장과 첫 번째 꽃 색을 같은 트랜잭션에서 만든다.
- 마감 시각 기본값은 방 생성 시각 + 48시간이다. 방장은 `WEAVING` 이전에 한 번, 생성 시각 기준 24~72시간 사이로 바꿀 수 있다 (FR-G4).
- 방 정원은 8명이다.
- 방 안에서 닉네임과 꽃 색이 중복되면 안 된다.
- 첫 입장 때 약관·미디어 처리·외부 AI 전송에 동의해야 한다.
- 초대 토큰을 재발급하면 기존 토큰은 즉시 무효가 된다.
- 초대 링크 조회 API는 참여자 닉네임과 콘텐츠를 노출하지 않는다. 꽃 색 목록(`flowers`)은 노출해도 된다.
- 마감 후에도 입장할 수 있다 (FR-A5). 입장 화면은 `status`로 분기한다.

## API 명세

### 1. 방 생성

```http
POST /api/v1/rooms
Idempotency-Key: {uuid}
```

```json
{
  "name": "졸업 전 마지막 경주",
  "meetingDate": "2026-10-24",
  "hostNickname": "서연",
  "consent": {
    "version": "2026-10-01",
    "terms": true,
    "mediaProcessing": true,
    "externalAiProcessing": true
  }
}
```

```json
{
  "data": {
    "room": {
      "id": "room_01",
      "name": "졸업 전 마지막 경주",
      "status": "COLLECTING",
      "deadlineAt": "2026-10-26T12:30:00Z"
    },
    "member": {
      "id": "member_01",
      "nickname": "서연",
      "role": "HOST",
      "flowerCode": "AZALEA"
    },
    "inviteToken": "raw-invite-token",
    "memberAccessToken": "raw-member-token"
  }
}
```

### 2. 초대 링크 확인

```http
GET /api/v1/invites/{inviteToken}
```

```json
{
  "data": {
    "roomName": "졸업 전 마지막 경주",
    "meetingDate": "2026-10-24",
    "status": "CIRCULATING",
    "memberCount": 4,
    "capacity": 8,
    "flowers": ["AZALEA", "FORSYTHIA", "ROYAL_AZALEA", "CANOLA"],
    "canJoin": true
  }
}
```

`canJoin`은 정원이 남았을 때 `true`다.

### 3. 닉네임 입장

```http
POST /api/v1/invites/{inviteToken}/join
Idempotency-Key: {uuid}
```

```json
{
  "nickname": "지수",
  "consent": {
    "version": "2026-10-01",
    "terms": true,
    "mediaProcessing": true,
    "externalAiProcessing": true
  }
}
```

응답은 방 생성 응답에서 `inviteToken`을 뺀 형태다.

### 4. 방 홈

```http
GET /api/v1/rooms/{roomId}
Authorization: Bearer {memberAccessToken}
```

응답에는 `room`, `me`, `members`, `todayFlowers`, `myTodo`, `unreadCount`, `allCompleted`를 포함한다.

```json
{
  "members": [
    {
      "id": "member_01",
      "nickname": "서연",
      "flowerCode": "AZALEA",
      "photoCount": 2,
      "storyCount": 3,
      "responseRate": 0.75
    }
  ],
  "todayFlowers": [
    {"memberId": "member_01", "flowerCode": "AZALEA", "entryKind": "FIRST"}
  ],
  "myTodo": {
    "needsFirstEntry": false,
    "pendingCirculationCount": 3,
    "topAskback": {"id": "askback_01", "question": "..."}
  }
}
```

- `todayFlowers`: 이야기(`FIRST`·`ADDON`) 하나당 꽃 하나, 확정 순서대로 (FR-A9)
- `responseRate`: 그 참여자가 올린 사진에 다른 참여자가 응답한 비율 (FR-A10)
- `topAskback`: 미응답 되묻기 중 하나만 (FR-D5). 없으면 `null`

### 5. 방 관리

| Method | Endpoint | 권한 | 기능 |
|---|---|---|---|
| `PATCH` | `/api/v1/rooms/{roomId}` | 방장 | 이름·날짜·마감 수정 |
| `POST` | `/api/v1/rooms/{roomId}/invite-token/rotate` | 방장 | 초대 링크 재발급 |
| `DELETE` | `/api/v1/rooms/{roomId}` | 방장 | 방과 모든 데이터 삭제 요청 |

## DB 규칙

```text
rooms
- id
- name
- meeting_date
- status
- deadline_at
- invite_token_hash
- host_member_id
- regeneration_count
- version

members
- id
- room_id
- nickname
- role
- flower_code
- access_token_hash
- consent_version
- consented_at
- joined_at
```

- `UNIQUE(room_id, nickname)`
- `UNIQUE(room_id, flower_code)`
- 토큰 원문은 응답에서 한 번만 보내고 DB에는 해시만 저장한다.

## 예외

| 상황 | 처리 |
|---|---|
| 닉네임 중복 | `409 NICKNAME_TAKEN` + 숫자를 붙인 추천 이름 |
| 9번째 입장 | `409 ROOM_FULL` |
| 무효·재발급 전 토큰 | `404 ROOM_NOT_FOUND` |
| 동의 일부 누락 | `400 CONSENT_REQUIRED` |
| 토큰을 잃고 같은 닉네임 입력 | 기존 사용자 복구 금지, 새 닉네임 제안 |

## 완료 조건

- [ ] 방과 방장이 하나의 트랜잭션에서 생성된다.
- [ ] 동시에 같은 닉네임으로 입장해도 한 명만 성공한다.
- [ ] 8명이 동시에 입장해도 꽃 색이 중복되지 않는다.
- [ ] 초대 링크 재발급 직후 기존 링크가 실패한다.
- [ ] 참여자 토큰으로 다른 방을 조회할 수 없다.
- [ ] 방 삭제 시 `MEDIA_DELETE` 작업이 함께 생성된다.

# 2. DEV-A · 사진·음성·첫 한마디

## 배경

모바일 사진과 음성은 네트워크 실패와 형식 차이가 크다. 사진은 Object Storage로 직접 업로드하고, 음성은 받아쓰기 결과를 사용자가 확인한 뒤 공개해야 한다.

## 목표

> 사용자가 사진 1~3장을 올리고 음성 또는 글로 첫 한마디를 확정할 수 있으며, 잘못된 받아쓰기는 공개 전에 수정할 수 있다.

## 담당

- 주담당: **DEV-A**
- 연동: DEV-C, FE
- 테이블: `media_assets`, `transcription_drafts`, `photos`, `entries`

## 기능적 요구사항

- 1인당 활성 사진은 1~3장이다.
- 클라이언트 압축 후 사진은 장당 2MB 이하다.
- JPEG, PNG, HEIC/HEIF를 허용한다.
- HEIC는 서버에서 JPEG 또는 WEBP 파생본을 만든다.
- 음성 파일은 10MB 이하이며, 길이는 서버가 직접 측정한다(ffprobe 등). 클라이언트가 보낸 길이는 믿지 않는다.
- 음성은 첫 한마디 5~60초, 덧붙임·되묻기 답변·답가는 1~30초다.
- 음성 전사문은 확정 전 다른 참여자에게 공개하지 않는다.
- 글은 첫 한마디 5~300자, 덧붙임·되묻기 답변·답가는 1~200자다.
- 사진은 업로더가 첫 한마디를 확정하거나 "나중에"를 누르면 돌려보기에 노출된다. "나중에"를 누른 사진은 "한마디 없음"으로 표시한다 (FR-B3). 업로드만 하고 아무것도 누르지 않은 사진은 노출하지 않는다.

## 사진 업로드 API

### 1. 업로드 URL 발급

```http
POST /api/v1/rooms/{roomId}/photos/upload-requests
Authorization: Bearer {token}
Idempotency-Key: {uuid}
```

```json
{
  "files": [
    {
      "clientFileId": "local-1",
      "fileName": "IMG_1234.HEIC",
      "contentType": "image/heic",
      "sizeBytes": 1840000,
      "displayOrder": 1
    }
  ]
}
```

```json
{
  "data": {
    "uploads": [
      {
        "clientFileId": "local-1",
        "photoId": "photo_01",
        "uploadUrl": "https://storage/...signed...",
        "expiresAt": "2026-10-24T12:40:00Z",
        "requiredHeaders": {"Content-Type": "image/heic"}
      }
    ]
  }
}
```

Object Storage key는 서버가 생성하며, 사진은 `UPLOADING` 상태로 만든다.

### 2. 업로드 완료

```http
POST /api/v1/photos/{photoId}/complete
Authorization: Bearer {token}
Idempotency-Key: {uuid}
```

Spring은 Object Storage HEAD 요청으로 크기·MIME을 검증한다. HEIC면 `PROCESSING`, 그 외에는 `READY`로 바꾼다. 검증에 실패하면 `422 UPLOAD_VERIFICATION_FAILED`를 반환한다.

## 받아쓰기·확정 API

### 1. 음성 받아쓰기 초안

```http
POST /api/v1/photos/{photoId}/transcriptions
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `file` | binary | O | mp4, m4a, webm, wav 등 |
| `kind` | enum | O | `FIRST`, `ADDON` |

```json
{
  "data": {
    "transcriptionId": "transcription_01",
    "audioAssetId": "audio_01",
    "transcript": "이때 우리가 막차 놓쳐서 뛰어갔잖아.",
    "detectedLanguages": ["ko"],
    "expiresAt": "2026-10-25T12:30:00Z"
  }
}
```

### 2. 원문 확정

```http
POST /api/v1/photos/{photoId}/entries
Authorization: Bearer {token}
Idempotency-Key: {uuid}
```

음성 입력:

```json
{
  "kind": "FIRST",
  "inputMode": "VOICE",
  "transcriptionId": "transcription_01",
  "transcript": "이때 우리가 막차 놓쳐서 뛰어갔잖아."
}
```

글 입력:

```json
{
  "kind": "FIRST",
  "inputMode": "TEXT",
  "transcript": "이때 우리가 막차 놓쳐서 뛰어갔잖아."
}
```

이 API의 `kind`는 `FIRST`·`ADDON`만 받는다. 되묻기 답변은 6-1장 API를 쓴다.

### 3. 첫 한마디 나중에

```http
POST /api/v1/photos/{photoId}/first-entry/skip
Idempotency-Key: {uuid}
```

사진을 "한마디 없음" 상태로 돌려보기에 노출한다. 나중에 첫 한마디를 확정하면 일반 사진이 된다.

### 4. 수정·삭제

| Method | Endpoint | 기능 |
|---|---|---|
| `PATCH` | `/api/v1/entries/{entryId}` | 본인 확정 원문 수정 |
| `DELETE` | `/api/v1/entries/{entryId}` | 본인 원문 삭제 |
| `DELETE` | `/api/v1/photos/{photoId}` | 본인 사진과 종속 콘텐츠 삭제 |

## 처리 흐름

```text
브라우저 녹음
→ Spring에 업로드
→ 길이·크기·형식 검증
→ Object Storage 저장
→ `TranscriptionAiService` 호출
→ 전사 초안 반환
→ 사용자가 수정
→ entries 확정
→ source_revision 증가
→ 되묻기 AI Job 생성 (6-1장)
```

전사 초안은 `transcription_drafts`에 저장한다. `context_type`(`PHOTO`/`ASKBACK`/`PARAGRAPH`)과 `context_id`로 어디에서 만든 초안인지 묶고, 24시간 뒤 만료한다. 확정 시 현재 참여자의 초안인지, 해당 리소스용인지, 아직 소비되지 않았는지 확인한 뒤 `consumed_at`을 기록한다.

## 예외

| 상황 | 처리 |
|---|---|
| 최소 길이 미달 음성 | `422 MIN_DURATION_NOT_MET` |
| 최대 길이 초과 음성 | `422 MAX_DURATION_EXCEEDED` |
| STT 실패 | 초안 생성 실패, 음성은 임시 보존, 글 입력 안내 |
| 업로드 3회 실패 | FE가 `UPLOAD_FAILED` 화면 상태로 다시 시도 제공, 서버 사진은 `FAILED` |
| 4번째 사진 | `422 LIMIT_REACHED` |
| HEIC 변환 실패 | 원본 삭제하지 않고 `FAILED` 상태·재시도 |
| 덧붙임이 달린 사진 삭제 | 확인 후 사진·종속 원문·관련 되묻기 삭제, 문단 재생성 |

## 완료 조건

- [ ] iOS `m4a/mp4`, Android `webm/opus` 샘플이 모두 전사된다.
- [ ] 전사 초안은 확정 전 돌려보기에 나타나지 않는다.
- [ ] 클라이언트가 조작한 Object Storage key를 등록할 수 없다.
- [ ] HEIC 파생본에서 EXIF 위치정보가 제거된다.
- [ ] 같은 확정 요청을 재전송해도 entry가 하나만 생긴다.
- [ ] 수정·삭제할 때 해당 사진의 `source_revision`이 증가한다.

# 3. DEV-B · 돌려보기·패스·참여 현황

## 배경

사진은 한 명씩 순차 전달하지 않고 확정 즉시 전원에게 노출한다. 사용자가 자기 기억을 남기기 전에는 다른 사람의 덧붙임을 숨겨 기억 오염을 줄인다.

## 목표

> 사용자가 모든 타인 사진에 덧붙임 또는 패스를 남기고, 서버가 각 카드의 잠금과 전원 완료를 정확히 판정한다.

## 담당

- 주담당: **DEV-B**
- 연동: DEV-A, FE
- 테이블: `entries`, `photo_passes`, `event_logs`

## API 명세

### 돌려보기 목록

```http
GET /api/v1/rooms/{roomId}/circulation?cursor=&size=20
```

```json
{
  "data": {
    "cards": [
      {
        "photo": {
          "id": "photo_01",
          "imageUrl": "https://...",
          "uploader": {"nickname": "서연", "flowerCode": "AZALEA"}
        },
        "firstEntry": {
          "id": "entry_01",
          "transcript": "이때 막차 놓쳤잖아.",
          "audioUrl": "https://..."
        },
        "myResponse": {"status": "NONE"},
        "locked": true,
        "lockReason": "RESPOND_FIRST",
        "otherEntries": []
      }
    ],
    "nextCursor": null
  }
}
```

- `myResponse.status`: `NONE` / `ADDON` / `PASSED`. 되묻기 답변은 일반 돌려보기 응답 상태를 대체하지 않는다.
- `otherEntries`에는 `ADDON`과 `ASKBACK_ANSWER`를 모두 포함한다.
- 잠긴 카드에서는 타인의 덧붙임·되묻기 답을 응답 JSON에 넣지 않는다. 프론트에서 CSS로만 숨기면 안 된다.

### 패스

```http
PUT /api/v1/photos/{photoId}/passes/me
DELETE /api/v1/photos/{photoId}/passes/me
```

### 덧붙임

DEV-A의 원문 API를 재사용한다.

```http
POST /api/v1/photos/{photoId}/entries
```

```json
{
  "kind": "ADDON",
  "inputMode": "TEXT",
  "transcript": "사진 찍고 바로 역까지 뛰었지."
}
```

## 핵심 판정

- 내 사진: 처음부터 모든 이야기를 볼 수 있다.
- 타인 사진 + 내 응답 없음: 첫 한마디만 볼 수 있다.
- 타인 사진 + 덧붙임/패스 완료: 모든 이야기를 볼 수 있다.
- 사진당 참여자 덧붙임은 최대 2개다. 되묻기 답은 이 한도에 포함하지 않는다.
- 모든 참여자가 모든 타인 사진에 덧붙임 또는 패스를 남기면 `allCompleted=true`다. 되묻기 답변은 완료 판정에 포함하지 않는다.

## 이벤트 로그

| 이벤트 | 시점 |
|---|---|
| `circulation_viewed` | 카드 상세 열람 |
| `addon_submitted` | 덧붙임 확정 |
| `circulation_passed` | 패스 |
| `all_members_completed` | 전원 완료 최초 달성 |

## 완료 조건

- [ ] 잠긴 카드 응답에 다른 사람 원문이 포함되지 않는다.
- [ ] 패스 후 덧붙임으로 바꾸면 패스가 제거된다.
- [ ] 3번째 덧붙임 요청은 실패한다.
- [ ] 새 사진 확정 후 폴링 기준 5초 안에 카드가 보인다.
- [ ] 본인 사진은 전원 완료 계산 대상에서 제외된다.

# 4. DEV-B · 마감·AI Job·상태 전이

## 배경

마감은 방을 닫는 시각이 아니라 첫 두루마리를 생성하는 시각이다. 서버가 재시작되거나 AI가 실패해도 마감 작업이 사라지면 안 된다.

## 목표

> 48시간·조기 마감·데모 마감이 같은 작업 흐름을 사용하며, AI 호출이 실패해도 재시도와 수동 복구가 가능하다.

## 담당

- 주담당: **DEV-B**
- 연동: DEV-C, DEV-D
- 테이블: `rooms`, `ai_jobs`, `scene_cards`, `scrolls`

## 상태 전이

```text
COLLECTING --첫 한마디 확정--> CIRCULATING
CIRCULATING --마감 시각 도달/조기 마감--> WEAVING
WEAVING --생성 성공--> GROWING
WEAVING --생성 실패--> WEAVING
GROWING --답가/삭제--> GROWING + 문단 버전 증가
```

마감 시각 도달은 스케줄러가 감지해 `mode=SCHEDULED`로 같은 마감 로직을 호출한다. `COLLECTING`에서 마감 시각이 지났고 공개할 원문이 0개면 상태를 유지하고 `close_at`을 24시간 연장한 뒤 방장에게 앱 내 알림을 보낸다. 자동 연장은 방당 1회만 허용한다. 연장된 시각에도 원문이 없으면 방을 `GROWING`으로 전환하되 빈 두루마리 안내 화면을 제공하고 AI Job은 만들지 않는다.

## API 명세

### 조기·데모 마감

```http
POST /api/v1/rooms/{roomId}/close
Idempotency-Key: {uuid}
```

```json
{
  "mode": "EARLY"
}
```

`mode`는 `EARLY` 또는 `DEMO`다. 방장만 호출한다.

```json
{
  "data": {
    "roomStatus": "WEAVING",
    "job": {
      "id": "job_01",
      "type": "WEAVE_SCROLL",
      "status": "QUEUED"
    }
  }
}
```

### 작업 상태

```http
GET /api/v1/jobs/{jobId}
```

```json
{
  "data": {
    "id": "job_01",
    "type": "WEAVE_SCROLL",
    "status": "RUNNING",
    "progress": {"completed": 5, "total": 12},
    "errorCode": null,
    "createdAt": "...",
    "updatedAt": "..."
  }
}
```

같은 방 참여자만 조회할 수 있다. `ASKBACK_ANALYZE` 작업은 공개 API로 조회하지 않는다.

### 수동 다시 엮기

```http
POST /api/v1/rooms/{roomId}/weave-jobs
Idempotency-Key: {uuid}
```

방장만 호출하며 방당 재생성 한도(10회, 기능명세서 초기값)에 포함한다. 답가 재엮기도 같은 한도에 포함한다. 한도를 넘으면 `422 LIMIT_REACHED`.

## Job 처리 규칙

1. 방 상태 변경과 `ai_jobs(QUEUED)` 생성은 같은 트랜잭션이다.
2. Worker는 `SELECT ... FOR UPDATE SKIP LOCKED`로 작업을 선점한다.
3. Spring 내부 AI 서비스 호출 전 `RUNNING`으로 바꾼다.
4. 성공 결과와 `SUCCEEDED` 변경을 한 트랜잭션으로 저장한다.
5. 실패하면 즉시 1회, 5초 후 1회, 총 2회 재시도한다 (FR-E7). Job 경로에서는 SDK 자동 재시도를 끄고 Job 재시도로 일원화한다.
6. `RUNNING` 상태로 일정 시간(예: 5분) 이상 갱신이 없으면 Worker가 죽은 것으로 보고 `QUEUED`로 되돌린다.
7. `WEAVE_SCROLL`이 최종 실패하면 `WEAVING`에 머물고 방장에게 다시 엮기를 제공한다.

## 원문 revision

사진의 확정 원문이 추가·수정·삭제되면 `source_revision`을 증가시킨다. AI 작업이 끝났을 때 시작 revision과 현재 revision이 다르면 오래된 결과를 저장하지 않고 새 작업을 만든다.

## 완료 조건

- [ ] 서버를 마감 직후 재시작해도 작업이 실행된다.
- [ ] 마감 API를 중복 호출해도 작업이 하나만 생긴다.
- [ ] Worker 여러 개가 같은 작업을 처리하지 않는다.
- [ ] 오래된 AI 결과가 최신 원문을 덮어쓰지 않는다.
- [ ] AI 최종 실패 후 방장이 다시 엮을 수 있다.
- [ ] 운영 환경에서 `DEMO` 마감이 거부된다.

# 5. DEV-C · OpenAI 공통 클라이언트·음성 받아쓰기

## 배경

브라우저 녹음은 iOS와 Android의 파일 형식이 다르고 배경 소음과 친구 이름 때문에 한국어 인식 품질이 흔들린다. 모델 호출은 Spring `ai` 패키지에서 관리해야 비용·재시도·모델 교체가 가능하다.

## 목표

> 60초 이하의 완료된 음성을 10초 안에 전사하고, 실패해도 사용자가 글 입력으로 이어갈 수 있는 결과를 반환한다.

## 담당

- 주담당: **DEV-C**
- 연동: DEV-A
- OpenAI: Audio Transcriptions
- 시작 모델: 환경변수 `OPENAI_TRANSCRIBE_MODEL=gpt-transcribe`
- 모델은 환경변수로 교체할 수 있지만, 변경 시 대표 음성 30건 회귀 평가를 먼저 통과해야 한다.

## SDK 설정

Spring Boot 전용 starter 대신 공식 `openai-java` 라이브러리를 직접 Bean으로 등록한다.

```gradle
implementation("com.openai:openai-java:${openAiJavaVersion}")
```

```java
@Configuration
public class OpenAiConfig {
    @Bean
    OpenAIClient openAIClient() {
        return OpenAIOkHttpClient.builder()
            .fromEnv()
            .timeout(Duration.ofSeconds(60))
            .maxRetries(0) // 재시도는 호출 경로별로 서비스에서 관리
            .build();
    }
}
```

- 동기 STT 호출: 요청 단위 timeout 15초, 재시도 1회. 사용자가 기다리는 경로이므로 목표 10초를 넘기지 않게 짧게 잡는다.
- Job 경로(되묻기·장면 카드·엮기·답가): SDK 재시도 없이 4장 Job 재시도 규칙을 따른다.

SDK 버전은 구현 시작일에 공식 문서에서 확인하고 버전 카탈로그에 고정한다.

## Spring 내부 서비스 계약

```java
public interface TranscriptionAiService {
    TranscriptionResult transcribe(TranscriptionCommand command);
}
```

```java
public record TranscriptionCommand(
    String audioAssetId,
    Resource audio,
    String fileName,
    String mimeType,
    List<String> languages,
    List<String> keywords
) {}
```

```json
{
  "schemaVersion": "1.0",
  "text": "이때 우리가 막차 놓쳐서 뛰어갔잖아.",
  "languages": ["ko"],
  "model": "...",
  "promptVersion": "transcribe-v1",
  "openaiRequestId": "req_..."
}
```

## 구현 규칙

- OpenAI API 키는 환경변수 또는 Secret Manager에서만 읽는다.
- `MediaStorage`가 Object Storage에서 읽은 `Resource`만 전달한다.
- 파일 크기·MIME·녹음 시간을 검증한 뒤 OpenAI SDK에 `InputStream`으로 전달한다.
- 친구 닉네임을 `keywords`로 전달하되 실제 발화에 없는 단어를 억지로 삽입하지 않는지 평가한다.
- OpenAI 요청 ID, 모델, latency, 사용량만 로그로 남기고 음성·전사문은 로그에 남기지 않는다.
- 공급자 timeout과 rate limit은 재시도 가능 오류로 구분한다.

## 오류

| 내부 코드 | retryable | Spring 변환 |
|---|---:|---|
| `INVALID_INPUT` | X | `422` |
| `DOWNLOAD_FAILED` | O | `502 STT_FAILED` |
| `OPENAI_RATE_LIMITED` | O | `503 AI_TEMPORARILY_UNAVAILABLE` |
| `OPENAI_TIMEOUT` | O | `504 AI_TIMEOUT` |
| `OPENAI_FAILED` | 조건부 | `502 STT_FAILED` |

## 평가셋

- 조용한 실내 한국어 5건
- 카페·야외 소음 5건
- 대구·경북 억양 5건
- 친구 이름·장소명이 포함된 음성 5건
- 5초 근처와 60초 근처 음성 5건
- 중간에 끊긴 음성 5건

## 완료 조건

- [ ] mp4/m4a/webm/wav 샘플을 처리한다.
- [ ] 대표 음성 30건의 빈 결과가 0건이다.
- [ ] P95 처리 시간이 10초 이내다.
- [ ] 임의 URL을 입력으로 받지 않고 서버가 관리하는 미디어만 처리한다.
- [ ] OpenAI 장애 시 표준 오류 형식을 반환한다.

# 6. DEV-C · AI 되묻기

## 배경

모든 사람에게 질문을 뿌리면 피로도가 높다. 사진과 모든 원문에서 이야기의 빈 곳을 찾고, 답할 가능성이 있는 한 사람에게만 근거 있는 질문을 보내야 한다.

이 장은 AI가 질문을 만드는 부분이다. 질문 저장·발송·답변은 6-1장에서 다룬다.

## 목표

> 새로운 원문이 생겼을 때 20초 안에 구멍을 판정하고, 신뢰도 0.6 이상일 때만 60자 이내 질문 하나를 반환한다.

## 담당

- 주담당: **DEV-C**
- 연동: DEV-B
- 관련 기능: FR-D1~D9

## Spring 내부 서비스 계약

```java
public interface AskbackAiService {
    AskbackResult analyze(AskbackCommand command);
}
```

`AskbackCommand` 예시:

```json
{
  "photo": {
    "id": "photo_01",
    "uploaderMemberId": "member_01",
    "imageUrl": "https://..."
  },
  "members": [
    {"id": "member_01", "nickname": "서연", "aliases": []},
    {"id": "member_02", "nickname": "지수", "aliases": ["지숭"]}
  ],
  "entries": [
    {
      "id": "entry_01",
      "memberId": "member_01",
      "nickname": "서연",
      "kind": "FIRST",
      "transcript": "다들 갑자기 웃었던 사진이야."
    }
  ],
  "previousAskbacks": [
    {
      "gapType": "MISSING_REASON",
      "targetMemberId": "member_02",
      "question": "지수 님, 이때 뭐 하고 있었어요?",
      "status": "FORGOT"
    }
  ],
  "limits": {
    "eligibleMemberIds": ["member_01"],
    "allowedGapTypes": ["CONFLICT", "MISSING_REASON", "NO_ADDON", "MISSING_CONTEXT"]
  }
}
```

- `previousAskbacks`: 이 사진에 이미 보낸 질문. AI는 같은 취지의 질문을 반복하지 않는다.
- `eligibleMemberIds`: Spring이 `FORGOT` 응답자, 한도 도달자, 이름·별칭이 겹치는 참여자를 미리 제외한 목록.

```json
{
  "schemaVersion": "1.0",
  "shouldAsk": true,
  "gapType": "MISSING_REASON",
  "confidence": 0.82,
  "targetMemberId": "member_01",
  "question": "서연 님, 이때 다 같이 웃은 이유가 뭐였어요?",
  "evidenceEntryIds": ["entry_01"],
  "reason": "웃었다는 사실은 있으나 원인이 없음",
  "model": "...",
  "promptVersion": "askback-v1",
  "openaiRequestId": "req_..."
}
```

## AI가 하는 일

- 4개 구멍 유형 판정
- 후보 중 질문 대상 한 명 제안
- 60자 이내 호기심 톤 질문 생성
- 근거가 된 원문 ID 반환

## Spring이 다시 검증하는 일

- 신뢰도 0.6 이상
- 대상자가 실제 참여자이고 `eligibleMemberIds`에 존재
- 발송 한도 (6-1장)
- 근거 ID가 실제 해당 사진의 확정 원문
- 질문 길이와 금칙 표현
- 마감 24시간 전 이전인지 여부
- `source_revision`이 Job 시작 시점과 같은지 여부

## 질문 대상 후보 (FR-D2)

Spring이 `eligibleMemberIds`를 만들 때 아래 참여자만 넣는다.

- 사진을 올린 사람
- 그 사진에 이미 원문을 남긴 사람
- 원문에 이름·별칭이 나왔지만 그 사진에 원문이 없는 사람

## 구멍 유형별 규칙

| 유형 | 판정 주체 | 조건 | 기본 대상 |
|---|---|---|---|
| `CONFLICT` 기억 엇갈림 | AI | 두 명 이상의 서술이 사실에서 충돌 | 이름이 나왔지만 말이 없는 사람 |
| `MISSING_REASON` 이유 빠짐 | AI | 보이는 행동·분위기의 이유가 없음 | 사진을 올린 사람 |
| `NO_ADDON` 아무도 안 고른 사진 | Spring 규칙 | 덧붙임 0개이고 첫 한마디 20자 미만 | 사진을 올린 사람 |
| `MISSING_CONTEXT` 사진 밖 장면 | AI | 앞뒤 맥락이 전혀 없음 | 사진을 올린 사람 |

- 여러 유형이 동시에 해당하면 위 표 순서가 우선이다.
- `NO_ADDON`은 조건이 기계적으로 판정되므로 Spring이 `allowedGapTypes`에 넣을지 결정한다. AI는 질문 문장만 만든다.

## 하지 않는 것

- 얼굴 인식
- 방에 없는 이름을 참여자로 추정
- 근거 없는 질문 생성
- “왜 기억 못 해요?” 같은 추궁
- `FORGOT` 응답자에게 재질문

## 평가 기준

| 지표 | 목표 |
|---|---:|
| 존재하지 않는 대상자 선택 | 0건 |
| 근거 ID 오류 | 0건 |
| 60자 초과 | 0건 |
| 사람이 보기에 답할 가치가 있는 질문 | 80% 이상 |
| 불쾌·추궁 어투 | 0건 |

## 완료 조건

- [ ] `shouldAsk=false`도 정상 결과로 처리한다.
- [ ] 같은 입력의 질문 대상이 허용 목록 밖으로 나가지 않는다.
- [ ] 이름이 같은 참여자가 둘이면 질문하지 않는다.
- [ ] 기억 엇갈림 사례에서 서로의 주장을 사실로 단정하지 않는다.
- [ ] `previousAskbacks`와 같은 취지의 질문을 다시 만들지 않는다.
- [ ] 모든 응답이 Java record와 JSON Schema를 통과한다.

# 6-1. DEV-B · 되묻기 발송·답변

## 배경

6장에서 AI가 만든 질문을 저장하고, 대상자에게만 보여 주고, 답을 원문으로 받아들이고, 한도와 만료를 관리하는 도메인 흐름이다.

## 목표

> 검증을 통과한 질문이 대상자 한 명에게만 5초 안에 보이고, 대상자는 말하기·글로 답하거나 "기억 안 나요"·"나중에"를 누를 수 있다 (FR-D6). 답은 해당 사진의 원문이 되어 두루마리 재료로 쓰인다.

## 담당

- 주담당: **DEV-B**
- 연동: DEV-C (`AskbackAiService`), DEV-A (원문·받아쓰기 재사용), FE
- 테이블: `askbacks`, `entries`, `transcription_drafts`, `notifications`, `event_logs`

## 기능적 요구사항

- `FIRST`·`ADDON`·`ASKBACK_ANSWER` 원문이 확정·수정·삭제되면 `ASKBACK_ANALYZE` Job을 만든다. 같은 사진에 `QUEUED` Job이 있으면 새로 만들지 않고, Worker가 실행 시점의 최신 원문을 읽는다.
- 질문은 대상자 본인에게만 보인다. 다른 참여자에게는 질문 존재 여부도 노출하지 않는다.
- 답은 `kind=ASKBACK_ANSWER` 원문으로 저장하며 3장의 잠금·전원 완료 판정에 반영된다.
- "기억 안 나요"(`FORGOT`)를 누른 참여자는 같은 사진에서 다시 질문 대상이 되지 않는다.
- "나중에"는 상태를 `PENDING`으로 두고 `postponed_at`만 기록한다. 홈에는 미룬 적 없는 질문을 먼저 띄운다.
- 홈에는 질문을 한 번에 하나만 노출한다 (FR-D5).
- 마감 24시간 전부터는 새 질문을 만들지 않는다. 방이 `WEAVING`이 되면 미응답 질문은 `EXPIRED`가 된다.
- AI 호출이 최종 실패하면 질문 없이 종료한다. 되묻기는 부가 기능이므로 사용자에게 알리지 않는다.

## 발송 한도 (FR-D3)

| 기준 | 한도 |
|---|---:|
| 참여자당 방 전체 누적 수신 | 2개 |
| 사진당 | 1회 |
| 사진당 예외 | 이전과 다른 구멍 유형이 생기면 1회 추가 |

한도에 걸린 결과는 저장하지 않고 `askback_skipped` 이벤트에 사유를 남긴다.

## 처리 흐름

```text
원문 확정·수정·삭제
→ source_revision 증가 + ai_jobs(ASKBACK_ANALYZE) 생성
→ Worker가 AskbackCommand 구성 (eligibleMemberIds 사전 필터링)
→ AskbackAiService.analyze()
→ shouldAsk=false면 종료
→ Spring 검증 (6장 목록 + 한도)
→ askbacks(PENDING) + 알림 저장 (같은 트랜잭션)
→ 대상자에게 SSE askback.created
→ 답변 → ASKBACK_ANSWER 원문 확정 → ANSWERED → 재분석
```

## API 명세

| Method | Endpoint | 기능 |
|---|---|---|
| `GET` | `/api/v1/rooms/{roomId}/askbacks/me?status=PENDING` | 내가 받은 질문 목록 |
| `GET` | `/api/v1/askbacks/{askbackId}` | 질문 상세 (대상자만) |
| `POST` | `/api/v1/askbacks/{askbackId}/transcriptions` | 답변 음성 받아쓰기 |
| `POST` | `/api/v1/askbacks/{askbackId}/answer` | 답변 확정 |
| `POST` | `/api/v1/askbacks/{askbackId}/forgot` | 기억 안 나요 |
| `POST` | `/api/v1/askbacks/{askbackId}/postpone` | 나중에 |
| `POST` | `/api/v1/rooms/{roomId}/askbacks/run` | 데모: 되묻기 즉시 실행 (FR-H9, 방장·운영 비활성) |

### 질문 목록 응답

```json
{
  "data": {
    "askbacks": [
      {
        "id": "askback_01",
        "photo": {
          "id": "photo_01",
          "imageUrl": "https://...",
          "uploader": {"nickname": "서연", "flowerCode": "AZALEA"}
        },
        "gapType": "MISSING_REASON",
        "question": "서연 님, 이때 다 같이 웃은 이유가 뭐였어요?",
        "evidenceNicknames": ["서연", "지수"],
        "status": "PENDING",
        "createdAt": "2026-10-24T13:00:00Z",
        "expiresAt": "2026-10-25T12:30:00Z"
      }
    ]
  }
}
```

- `evidenceNicknames`: 근거 원문 작성자의 닉네임. 화면에 "서연 님과 지수 님의 이야기를 읽고 물어봤어요"로 표시한다 (FR-D4).
- `confidence`, `reason`, 근거 원문 본문은 내보내지 않는다. 상세 응답에는 해당 사진의 첫 한마디를 함께 넣고, 잠금 규칙은 돌려보기와 같다.

### 답변 확정

```http
POST /api/v1/askbacks/{askbackId}/answer
Idempotency-Key: {uuid}
```

```json
{
  "inputMode": "VOICE",
  "transcriptionId": "transcription_05",
  "transcript": "지수가 사진 찍다가 뒤로 넘어졌거든."
}
```

```json
{
  "data": {
    "askback": {"id": "askback_01", "status": "ANSWERED"},
    "entry": {"id": "entry_09", "kind": "ASKBACK_ANSWER", "photoId": "photo_01"}
  }
}
```

- 받아쓰기 초안은 `context_type=ASKBACK`, `context_id=askbackId`로 저장한다.
- `forgot`도 `Idempotency-Key`를 받으며, 화면 문구는 부담 없는 톤으로 쓴다. 예: "괜찮아요, 다른 친구가 기억할지도 몰라요."

## 상태 전이

```text
PENDING --답변--> ANSWERED
PENDING --기억 안 나요--> FORGOT
PENDING --WEAVING 진입/만료--> EXPIRED
PENDING --근거 원문·사진 삭제--> CANCELLED
```

- 종료 상태에 답변·기억 안 나요를 요청하면 `409 ASKBACK_CLOSED`를 반환한다.
- 답변으로 만든 원문을 작성자가 삭제해도 askback은 `ANSWERED`로 유지한다. 그래야 같은 질문이 다시 가지 않는다.

## DB 규칙

```text
askbacks
- id
- room_id
- photo_id
- target_member_id
- gap_type
- question
- confidence
- reason
- evidence_entry_ids_json
- source_revision
- status
- answer_entry_id
- ai_job_id
- model
- prompt_version
- openai_request_id
- created_at
- resolved_at
- expires_at
- postponed_at
- active_key   -- 아래 식으로 계산하는 STORED 생성 컬럼
```

```sql
active_key VARCHAR(100)
GENERATED ALWAYS AS (
    CASE
        WHEN status = 'PENDING'
        THEN CONCAT(photo_id, ':', target_member_id)
        ELSE NULL
    END
) STORED
```

- `UNIQUE KEY uk_askback_active (active_key)`로 사진·대상자당 미응답 질문 1개를 DB에서 보장한다. MySQL에는 부분 인덱스가 없어 생성 컬럼을 쓴다.
- `INDEX(target_member_id, status)`
- `entries`에 `askback_id` nullable 컬럼을 추가한다.

## 이벤트 로그

이벤트 이름과 값은 기능명세서 12장을 따른다.

| 이벤트 | 시점 | 값 |
|---|---|---|
| `askback_sent` | 질문 저장 | `type`, `target` |
| `askback_responded` | 답변·기억 안 나요·나중에 | `choice`: `answered` / `forgot` / `later` |
| `askback_skipped` | 검증·한도 탈락 (개발용 추가) | `reason` |
| `askback_expired` | 마감 만료 (개발용 추가) | – |

되묻기 응답률(`answered / askback_sent`)은 6장 "답할 가치가 있는 질문 80%"의 실사용 지표로도 쓴다.

## 완료 조건

- [ ] 질문이 대상자 외 참여자의 API 응답에 포함되지 않는다.
- [ ] 같은 사진·대상자에게 미응답 질문이 동시에 2개 생기지 않는다.
- [ ] `FORGOT`을 누른 참여자는 같은 사진에서 다시 질문받지 않는다.
- [ ] 답변이 `ASKBACK_ANSWER` 원문으로 저장되고 장면 카드 입력에 포함된다.
- [ ] 마감 24시간 전 이후에는 질문이 생성되지 않는다.
- [ ] `WEAVING` 진입 시 미응답 질문이 모두 `EXPIRED`가 된다.
- [ ] 같은 답변 요청을 재전송해도 원문이 하나만 생긴다.
- [ ] 원문 수정이 연속으로 일어나도 오래된 revision의 질문이 저장되지 않는다.

# 7. DEV-D · 장면 카드 추출

## 배경

사진과 모든 원문을 한 번에 두루마리로 생성하면 평평한 여행 후기가 되거나 근거 없는 내용을 만들 가능성이 커진다. 먼저 사진 한 장마다 사실과 인용을 구조화한다.

## 목표

> 사진과 해당 사진의 원문에서만 사실을 뽑아, 다음 단계가 안전하게 사용할 장면 카드를 만든다.

## 담당

- 주담당: **DEV-D**
- 연동: DEV-B
- OpenAI: Responses API 이미지+텍스트, Structured Outputs

## Spring 내부 서비스 계약

```java
public interface SceneCardAiService {
    SceneCardResult extract(SceneCardCommand command);
}
```

`SceneCardCommand` 예시:

```json
{
  "photo": {
    "id": "photo_01",
    "uploaderMemberId": "member_01",
    "imageUrl": "https://..."
  },
  "entries": [
    {
      "id": "entry_01",
      "memberId": "member_01",
      "nickname": "서연",
      "kind": "FIRST",
      "transcript": "막차 놓쳐서 역까지 뛰었잖아."
    },
    {
      "id": "entry_02",
      "memberId": "member_02",
      "nickname": "지수",
      "kind": "ADDON",
      "transcript": "다리가 풀리는 줄 알았어."
    }
  ]
}
```

`entries`에는 `FIRST`·`ADDON`·`ASKBACK_ANSWER` 확정 원문을 모두 넣는다.

```json
{
  "schemaVersion": "1.0",
  "photoId": "photo_01",
  "what": "친구들이 막차를 놓친 뒤 역까지 뛰었던 장면",
  "whoMemberIds": ["member_01", "member_02"],
  "emotion": ["당황", "즐거움"],
  "voices": [
    {
      "entryId": "entry_02",
      "memberId": "member_02",
      "exactQuote": "다리가 풀리는 줄 알았어",
      "toneNote": "과장 섞인 웃음"
    }
  ],
  "conflict": null,
  "gap": "사진만으로 막차 여부는 확인할 수 없음",
  "mustQuoteEntryId": "entry_02",
  "safety": {"action": "ALLOW", "categories": []},
  "model": "...",
  "promptVersion": "scene-card-v1",
  "openaiRequestId": "req_..."
}
```

충돌이 있으면 `conflict`는 아래 형태다.

```json
{
  "summary": "누가 먼저 뛰자고 했는지 기억이 다름",
  "claims": [
    {"entryId": "entry_01", "memberId": "member_01", "claim": "지수가 먼저 뛰자고 함"},
    {"entryId": "entry_03", "memberId": "member_03", "claim": "서연이 먼저 뛰자고 함"}
  ]
}
```

`safety.action`은 `ALLOW` / `SOFTEN` / `OMIT` 중 하나다.

## 검증 규칙

- `exactQuote`는 원문 transcript의 정확한 부분 문자열이어야 한다.
- `whoMemberIds`는 요청으로 받은 참여자만 가능하다.
- 사진만 보고 장소·관계·행동의 이유를 단정하지 않는다.
- 충돌하는 기억은 하나를 선택하지 않고 `conflict`에 병기한다.
- 욕설·놀림은 `safety.action`으로 표시한다.

## 완료 조건

- [ ] 모든 인용이 원문 문자열 검사에 통과한다.
- [ ] 원문에 없는 인물 ID가 생성되지 않는다.
- [ ] 사진만 보고 지역·행사명을 지어내지 않는다.
- [ ] 원문이 하나뿐인 사진도 카드가 생성된다.
- [ ] 사진 12장을 병렬 처리할 수 있다.

# 8. DEV-D · 두루마리 생성

## 배경

장면 카드는 사실 중심의 중간 데이터다. 이를 사진 순서에 맞춰 읽을 수 있는 문단으로 바꾸되 친구들의 말투와 정확한 인용을 보존해야 한다.

## 목표

> 사진 1장당 문단 1개를 만들고, 문단마다 실제 원문 인용을 최소 1개 포함하며, 근거 없는 사실을 0건으로 유지한다.

## 담당

- 주담당: **DEV-D**
- 저장·검증: **DEV-B**
- 관련 기능: FR-E1~E12

## Spring 내부 서비스 계약

```java
public interface ScrollWeaveAiService {
    ScrollWeaveResult weave(ScrollWeaveCommand command);
}
```

`ScrollWeaveCommand` 예시:

```json
{
  "room": {
    "id": "room_01",
    "name": "졸업 전 마지막 경주"
  },
  "sceneCards": [
    {
      "photoId": "photo_01",
      "displayOrder": 1,
      "what": "친구들이 역까지 뛰었던 장면",
      "mustQuoteEntryId": "entry_02"
    }
  ],
  "sourceEntries": {
    "entry_02": {
      "photoId": "photo_01",
      "memberId": "member_02",
      "nickname": "지수",
      "transcript": "다리가 풀리는 줄 알았어"
    }
  }
}
```

`sceneCards`에는 7장 결과 전체를 넣는다. 위 예시는 일부 필드만 표시했다.

```json
{
  "schemaVersion": "1.0",
  "paragraphs": [
    {
      "photoId": "photo_01",
      "body": "막차를 놓친 친구들은 역까지 뛰었다. 지수는 ‘다리가 풀리는 줄 알았어’라고 기억했다.",
      "citations": [
        {
          "entryId": "entry_02",
          "memberId": "member_02",
          "exactQuote": "다리가 풀리는 줄 알았어"
        }
      ],
      "safety": {"action": "ALLOW", "categories": []}
    }
  ],
  "model": "...",
  "promptVersion": "weave-v1",
  "openaiRequestId": "req_..."
}
```

## 생성 규칙

1. 돌려보기에 노출된 사진 1장당 문단 1개다. 한마디 없는 사진은 AI를 거치지 않고 사진과 올린 사람의 기본 문구로 처리한다 (FR-E5).
2. 장면 카드에 없는 사실을 추가하지 않는다.
3. 문단마다 원문 표현을 1개 이상 인용한다.
4. 한 문단 본문에는 최대 3명의 관점을 담는다.
5. 지문은 담백한 평어체, 인용은 원문 그대로 쓴다.
6. 기억 충돌은 한쪽을 정답으로 만들지 않는다.
7. 욕설·성적 표현·특정인 놀림은 안전 정책에 따라 생략 또는 간접 서술한다.

## DEV-B의 결과 검증

```text
citations.entryId가 실제 존재하는가
→ entry가 같은 사진에 속하는가
→ memberId가 entry 작성자와 일치하는가
→ exactQuote가 transcript 부분 문자열인가
→ 문단과 사진 수가 일치하는가
```

검증 실패 시 해당 사진 문단만 최대 2회 재생성한다. 계속 실패하면 Spring이 원문 나열형 기본 문단을 만든다.

## 완료 조건

- [ ] 사진 12장 기준 30초 안에 생성된다. 60초를 넘으면 실패로 처리한다 (FR-E1).
- [ ] 인용 문자열 정확도 100%다.
- [ ] 근거 없는 인물·장소·행동이 0건이다.
- [ ] 일부 참여만 있어도 결과가 만들어진다.
- [ ] AI 출력 실패 시 기본 문단으로 대체된다.
- [ ] “내 말 같다” 내부 평가 평균 4.0/5 이상이다.

# 9. DEV-B + DEV-D · 답가와 문단 버전

## 배경

두루마리 생성 후 친구가 빠진 사실을 덧붙일 수 있다. 단순 반응은 본문에 넣지 않고, 새 사실이나 정정만 기존 문단을 해치지 않는 방식으로 반영한다.

## 목표

> 같은 문단에 답가가 동시에 들어와도 기존 인용을 잃지 않고 순서대로 반영하며, 삭제하면 유효한 원문 집합으로 복구한다.

## 담당

| 영역 | 담당 |
|---|---|
| 답가 API·DB·Job·문단 잠금·버전 | **DEV-B** |
| 답가 분류·부분 재엮기 | **DEV-D** |

## 공개 API

### 답가 음성 받아쓰기

```http
POST /api/v1/paragraphs/{paragraphId}/transcriptions
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

### 답가 확정

```http
POST /api/v1/paragraphs/{paragraphId}/replies
Idempotency-Key: {uuid}
```

```json
{
  "inputMode": "TEXT",
  "transcript": "사실 역에 도착하고 나서 지수가 표를 잃어버린 걸 알았어."
}
```

```json
{
  "data": {
    "reply": {"id": "reply_01", "status": "PENDING"},
    "job": {"id": "job_02", "type": "REPLY_REWEAVE", "status": "QUEUED"}
  }
}
```

작업이 끝나면 답가 상태는 `APPLIED`(본문 반영), `REACTION`(반응으로 저장), `REJECTED`(`UNSAFE`) 중 하나가 된다. 작성자가 지우면 `DELETED`다.

분류 신뢰도가 낮으면 `REACTION_ONLY`로 버리지 않고 본문에 엮는다. 작성자가 나중에 지울 수 있다 (FR-F3).

### 기타

| Method | Endpoint | 기능 |
|---|---|---|
| `DELETE` | `/api/v1/replies/{replyId}` | 본인 답가 삭제·문단 복구 작업 |
| `PUT` | `/api/v1/paragraphs/{paragraphId}/fidelity-rating` | 말투 보존 1~5점. 본인 원문이 인용된 문단만 (FR-F5) |
| `PUT` | `/api/v1/paragraphs/{paragraphId}/reactions/ah-matda` | 아 맞다 반응 추가 |
| `DELETE` | `/api/v1/paragraphs/{paragraphId}/reactions/ah-matda` | 반응 취소 |

## Spring 내부 AI 서비스 계약

### 1. 답가 분류 서비스

```java
public interface ReplyAiService {
    ReplyClassificationResult classify(ReplyClassificationCommand command);
}
```

응답:

```json
{
  "classification": "NEW_FACT",
  "confidence": 0.91,
  "reason": "기존 문단에 없던 표 분실 사건 추가",
  "schemaVersion": "1.0",
  "model": "...",
  "promptVersion": "reply-classify-v1",
  "openaiRequestId": "req_..."
}
```

### 2. 문단 부분 재엮기 서비스

```java
public interface ParagraphReweaveAiService {
    ReweaveResult reweave(ReweaveCommand command);
}
```

요청에는 `paragraphId`, `baseParagraphVersion`, 현재 본문·인용, 모든 유효 원문, 새 답가, 분류값을 포함한다. 응답은 8장 `paragraphs[]` 한 개와 같은 형태다.

## 처리 규칙

- `REACTION_ONLY`: 본문에 넣지 않고 반응으로 저장한다.
- `NEW_FACT`: 기존 문장 순서와 인용을 유지하며 추가한다.
- `CORRECTION`: 기존 사실을 삭제하지 않고 “OO는 ~라고 정정했다”로 병기한다.
- `UNSAFE`: 본문에 넣지 않고 작성자에게 안내한다.
- 결과 문단은 기존 길이의 1.5배를 넘지 않는다.
- 같은 문단의 답가는 생성 순서대로 직렬 처리한다.
- `baseParagraphVersion`이 현재와 다르면 최신 버전으로 다시 실행한다.
- 재엮기 결과도 8장 결과 검증을 그대로 거친다. 정정과 충돌하지 않는 기존 인용은 모두 유지한다. 정정으로 무효화된 인용은 새 답가 인용으로 교체할 수 있으며, 제거·교체된 인용 ID는 문단 버전 이력에 기록한다.

## 버전 테이블

```text
paragraph_versions
- id
- paragraph_id
- version
- body
- sources_json
- reason: INITIAL / REPLY_ADDED / REPLY_DELETED / ENTRY_DELETED
- created_at
```

## 완료 조건

- [ ] “ㅋㅋ”, “맞아”가 본문에 엮이지 않는다.
- [ ] 정정이 기존 기억을 몰래 덮어쓰지 않는다.
- [ ] 동시에 2개 답가를 보내도 둘 다 반영된다.
- [ ] 답가 삭제 후 유효한 원문만 남은 문단으로 돌아간다.
- [ ] 기존 인용이 부분 재엮기로 사라지지 않는다.

# 10. DEV-B · 두루마리 조회·알림·실시간 갱신

## 배경

AI 작업은 수초에서 수십 초 걸리므로 프론트가 진행 상태를 확인해야 한다. 웹 푸시는 MVP에서 제외하고 앱 내 배지와 단톡방 공유로 재방문을 유도한다.

## 목표

> 작업 완료, 새 사진, 새 덧붙임, 되묻기, 새 답가를 5초 안에 화면에 반영한다.

## 담당

- 주담당: **DEV-B**
- 연동: FE
- 테이블: `scrolls`, `paragraphs`, `paragraph_sources`, `notifications`

## API 명세

| Method | Endpoint | 기능 |
|---|---|---|
| `GET` | `/api/v1/rooms/{roomId}/scroll` | 현재 두루마리와 출처 조회 |
| `GET` | `/api/v1/rooms/{roomId}/notifications` | 내 앱 내 알림 |
| `POST` | `/api/v1/notifications/{notificationId}/read` | 읽음 처리 |
| `POST` | `/api/v1/rooms/{roomId}/event-token` | SSE 60초 일회용 토큰 |
| `GET` | `/api/v1/rooms/{roomId}/events?streamToken=...` | SSE 연결 |

### 두루마리 응답 요약

```json
{
  "data": {
    "scroll": {"id": "scroll_01", "version": 3, "status": "READY"},
    "paragraphs": [
      {
        "id": "paragraph_01",
        "photoId": "photo_01",
        "version": 2,
        "body": "...",
        "aiGenerated": true,
        "citations": [
          {"entryId": "entry_02", "nickname": "지수", "exactQuote": "다리가 풀리는 줄 알았어"}
        ],
        "replies": [{"id": "reply_01", "status": "APPLIED"}],
        "ahMatdaCount": 2
      }
    ]
  }
}
```

방이 `WEAVING`이면 `scroll`은 `null`이고 진행 중인 `job`을 함께 반환한다.

브라우저 기본 `EventSource`는 Authorization 헤더 설정이 어려워 일회용 스트림 토큰을 사용한다. 일정이 빠듯하면 SSE를 미루고 5초 폴링만 구현한다.

## SSE 이벤트

| event | data | 수신 대상 |
|---|---|---|
| `photo.uploaded` | `photoId`, `roomId`, `status` | 업로더만 |
| `photo.published` | `photoId`, `roomId` | 첫 한마디 확정 또는 "나중에" 선택 후 방 전체 |
| `entry.created` | `photoId`, `entryId` | 방 전체 |
| `askback.created` | `askbackId` | 대상자만 |
| `job.updated` | `jobId`, `status`, `progress` | 방 전체 |
| `scroll.ready` | `scrollId`, `version` | 방 전체 |
| `paragraph.updated` | `paragraphId`, `version` | 방 전체 |
| `notification.created` | `notificationId`, `type` | 대상자만 |

SSE에는 본문·사진 URL을 넣지 않는다. 이벤트를 받은 프론트가 REST API를 다시 조회하며, 잠금 규칙은 REST에서 적용된다.

## 완료 조건

- [ ] 다른 방 토큰으로 두루마리를 읽을 수 없다.
- [ ] SSE 토큰은 한 번 사용하거나 60초가 지나면 만료된다.
- [ ] SSE가 끊기면 프론트가 5초 폴링으로 전환할 수 있다.
- [ ] 대상자 전용 이벤트가 다른 참여자 스트림에 전송되지 않는다.
- [ ] 알림 읽음 처리는 대상자만 가능하다.
- [ ] AI 생성 문단에 `aiGenerated=true`와 출처가 항상 표시된다.

# 11. Spring AI 기능 공통 평가·프롬프트 관리

## 배경

응답이 JSON으로 나온다는 것만으로 품질이 보장되지는 않는다. 프롬프트를 바꿀 때 되묻기 대상, 사실 충실도, 말투 보존이 함께 무너질 수 있으므로 고정 평가셋이 필요하다.

## 담당

- 평가셋·판정 기준: **DEV-C + DEV-D**
- 독립 검수: PM 또는 AI 구현자가 아닌 팀원
- 비용·작업 로그 저장: **DEV-B**

## 프롬프트 버전

| 기능 | 버전 예시 |
|---|---|
| 받아쓰기 context | `transcribe-v1` |
| 되묻기 | `askback-v1` |
| 장면 카드 | `scene-card-v1` |
| 두루마리 | `weave-v1` |
| 답가 분류 | `reply-classify-v1` |
| 문단 재엮기 | `reweave-v1` |

프롬프트 원문은 코드 또는 별도 버전 파일로 관리하고 DB 결과에는 버전만 저장한다.

## 평가셋 구성

| 유형 | 최소 케이스 |
|---|---:|
| 원문 하나뿐인 사진 | 5 |
| 참여자 기억이 충돌하는 사진 | 5 |
| 아무도 덧붙이지 않은 사진 | 5 |
| 되묻기 이력(`FORGOT` 포함)이 있는 사진 | 5 |
| 욕설·특정인 놀림 포함 | 5 |
| 답가가 단순 반응인 경우 | 5 |
| 답가가 정정인 경우 | 5 |
| 대구·경북 억양·고유명사 음성 | 10 |

## 자동 판정

| 항목 | 기준 |
|---|---|
| 인용 정확도 | 원문 부분 문자열 일치 100% |
| 존재하지 않는 member/entry ID | 0건 |
| JSON Schema 위반 | 0건 |
| 되묻기 60자 초과 | 0건 |
| 기존 인용 삭제 | 0건 |
| 정량 비용 | 작업 종류별 input/output token 집계 |

## 사람 판정

| 항목 | 방법 |
|---|---|
| 말투 보존 | “내 말 같다” 1~5점, 평균 4.0 이상 |
| 질문 가치 | 실제로 답하고 싶은 질문인지 |
| 추궁 여부 | 불쾌·평가·압박 어투인지 |
| 사실 과장 | 원문보다 단정적으로 바뀌었는지 |
| 이야기 품질 | 평평한 여행 후기로 변하지 않았는지 |

## 완료 조건

- [ ] 대표 입력 30~50개가 JSONL fixture로 고정되고 JUnit 테스트로 실행된다.
- [ ] 프롬프트 변경 PR에 회귀 평가 결과가 첨부된다.
- [ ] 구현자가 자기 결과를 단독으로 최종 판정하지 않는다.
- [ ] 모델·프롬프트별 품질과 비용을 비교할 수 있다.

# 12. 통합 일정과 인수 순서

## 1차 · 계약 고정

| 담당 | 작업 |
|---|---|
| DEV-A | 방·참여자·미디어 DTO 확정 |
| DEV-B | Job·되묻기·두루마리·답가 DTO 확정 |
| DEV-C | STT·되묻기 Java interface·record DTO와 mock |
| DEV-D | 장면 카드·엮기·답가 Java interface·record DTO와 mock |

통과 기준:

- [ ] OpenAI 클라이언트를 mock으로 교체한 통합 테스트 성공
- [ ] 각 AI 서비스가 고정 DTO 결과를 반환
- [ ] 공개 OpenAPI를 FE가 import
- [ ] enum과 오류 코드 합의

## 2차 · 90초 데모 P0

```text
방 생성
→ 초대 링크 입장
→ 사진 업로드
→ 음성 STT·수정·확정
→ 덧붙임/패스
→ 되묻기 1건 수신·답변
→ 즉시 마감
→ 장면 카드
→ 두루마리 생성
→ 답가 한 건 재엮기
```

## 3차 · 실패 복구

- AI timeout·rate limit
- 업로드 실패·HEIC 변환 실패
- 서버 재시작 후 Job 복구
- 중복 요청과 동시 답가
- 원문·사진·답가 삭제 후 재엮기
- SSE 실패 후 폴링 전환

## 배포 순서

```text
Spring API → Web
```

Spring 단일 애플리케이션을 먼저 배포하고 Web의 API 주소를 연결한다. 운영 배포에서는 데모 즉시 마감 기능을 비활성화한다.

# 13. 이번 MVP에서 하지 않는 것

| 제외 기능 | 이유 |
|---|---|
| 얼굴 인식 | 개인정보·오탐 위험, 이름 매칭으로 충분 |
| 공개 피드·팔로우 | 친구 모임의 비공개 경험과 충돌 |
| 카카오 자동 메시지 | 비즈니스 채널·템플릿 승인 필요, OS 공유 시트로 대체 |
| 웹 푸시 | iOS·설치 조건과 일정 위험, 앱 내 알림으로 대체 |
| 마감 후 새 사진·첫 한마디·덧붙임 (FR-G5) | 문단 재생성 범위가 커짐, 1차는 입장·열람·답가만 허용하고 2차에서 연다 |
| 실결제·인쇄 배송 | 지불 의사 검증 전 |
| 혼자 쓰는 일기 | 핵심 가설인 친구 간 회람과 무관 |
| 영상 편집·사진 보정 | 핵심 가치와 무관 |
| AI가 원문 자동 수정 | 말투 보존과 사실 충실도 훼손 |
