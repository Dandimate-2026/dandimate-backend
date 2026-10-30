# 두루두루 Backend

Spring Boot 백엔드의 도메인별 패키지 구조입니다.

```text
src/main/java/com/dandimate/durooduroo/
├── room       # 방 생성·초대 코드·방 상태
├── member     # 참여자 입장·닉네임·memberToken·권한
├── photo      # 사진 업로드·해시 중복 확인·업로더 정보
├── memory     # 사진별 한마디·STT 결과·패스 답변
├── ai         # AI 요청·문단 생성·작업 상태·결과 검증
├── scroll     # 완성된 두루마리 저장·조회
├── storage    # 이미지·음성 파일 저장소 연동
└── common     # 공통 응답·예외·설정·유틸리티
```

현재는 도메인 경계만 정의했으며, 각 도메인 내부에 `controller`, `service`, `repository`, `domain`, `dto`를 필요할 때 추가합니다.

## 도메인별 책임

| 폴더 | 담당 책임 |
|---|---|
| `room` | 방을 생성하고 초대 코드와 방 상태를 관리한다. |
| `member` | 방 참여자, 닉네임, memberToken, 방장·참여자 역할을 관리한다. |
| `photo` | 사진 업로드, 파일 해시, 중복 사진 통합, 업로더 연결을 관리한다. |
| `memory` | 사진에 대한 텍스트·음성 한마디, STT 결과, 답변·패스 상태를 관리한다. |
| `ai` | AI 호출, 프롬프트 버전, 생성 상태, 결과 검증과 실패 처리를 담당한다. |
| `scroll` | AI가 만든 두루마리와 문단을 저장하고 참여자에게 조회한다. |
| `storage` | 이미지와 음성 파일을 로컬 또는 Object Storage에 저장한다. |
| `common` | 모든 도메인이 함께 사용하는 응답 형식, 예외, 설정을 관리한다. |

각 도메인 안에서는 다음 책임을 지킨다.

```text
controller  HTTP 요청·응답
service     비즈니스 규칙
domain      상태·핵심 불변식
repository  DB 저장·조회
dto         API 입력·출력 형식
```

## 실행

```bash
./gradlew bootRun
```

로컬 MySQL과 S3 호환 저장소(MinIO)를 함께 띄우려면:

```bash
cp .env.example .env
set -a; source .env; set +a
docker compose up -d
gradle bootRun --args='--spring.profiles.active=mysql'
```

MinIO를 사용할 때는 `STORAGE_TYPE=s3`, `AWS_S3_ENDPOINT=http://localhost:9000`,
`AWS_S3_BUCKET=durooduroo`로 설정하고 MinIO 콘솔(`http://localhost:9001`)에서
동일한 이름의 버킷을 한 번 생성한다.

헬스체크:

```http
GET http://localhost:8080/api/v1/health
```

## 운영 연결

MySQL 프로필:

```bash
export MYSQL_URL='jdbc:mysql://localhost:3306/durooduroo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
export MYSQL_USERNAME='root'
export MYSQL_PASSWORD='비밀번호'
export OPENAI_API_KEY='sk-...'
./gradlew bootRun --args='--spring.profiles.active=mysql'
```

STT는 OpenAI Audio Transcription API의 multipart 업로드 방식과 `gpt-transcribe` 모델을 사용한다. 키가 없으면 `STT_NOT_CONFIGURED` 오류를 반환한다.

Object Storage는 `STORAGE_TYPE=local`이면 `uploads/`에 저장하고, `STORAGE_TYPE=s3`이면 아래 값을 사용한다.

```bash
export AWS_REGION='ap-northeast-2'
export AWS_S3_BUCKET='durooduroo-assets'
export STORAGE_TYPE='s3'
```
