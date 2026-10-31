# 운영 배포: AWS 무료 등급 (EC2 한 대 + S3)

EC2 한 대에 **백엔드와 MySQL을 Docker로 같이** 올리고, 사진·음성과 DB 백업은 **S3**에 둡니다.
RDS를 쓰지 않아 DB 비용이 따로 들지 않는 대신, 백업은 `backup-mysql.sh`가 매일 S3에 올립니다.

```text
인터넷 ──▶ nginx(80/443) ──▶ backend(127.0.0.1:8080) ──▶ mysql(컨테이너 안, 포트 비공개)
                                   │
                                   └──▶ S3: 사진·음성 / backups/mysql/ (매일 DB 백업)
```

| 파일 | 서버 위치 | 역할 |
|---|---|---|
| `docker-compose.prod.yml` | `/home/ubuntu/durooduroo/` | 백엔드 + MySQL 8.4 (메모리 1GB용) |
| `mysql/low-memory.cnf` | `/home/ubuntu/durooduroo/mysql/` | MySQL 메모리 줄이기, utf8mb4, 서울 시간 |
| `.env.production.example` | → `/home/ubuntu/durooduroo/.env` | 비밀값·설정 (Git에 올리지 않음) |
| `backup-mysql.sh` · `restore-mysql.sh` | `/home/ubuntu/durooduroo/` | DB 백업·복원 |

CD(`.github/workflows/cd.yml`)가 main에 병합될 때마다 위 파일(`.env` 제외)을 서버에 복사하고 새 이미지로 교체합니다.
테이블은 Flyway(`src/main/resources/db/migration`)가 만듭니다.

> 무료 등급 조건(기간, 크레딧, 인스턴스 종류, 공인 IPv4 요금)은 바뀝니다. 만들기 전에 AWS 콘솔의 Free Tier 페이지에서 확인하고,
> **1단계 요금 알림을 가장 먼저** 켜세요.

## 1. 요금 알림 (가장 먼저)

Billing → Budgets → 예산 만들기 → "Zero spend budget"(1센트라도 나가면 메일) 또는 월 1~5달러 예산.

## 2. S3 버킷

1. S3 → 버킷 만들기: 리전 **아시아 태평양(서울) ap-northeast-2**, 이름 예: `durooduroo-prod-<팀이름>`
2. **퍼블릭 액세스 차단: 모두 켬** (사진은 서버가 만든 서명 URL로만 열립니다)
3. 권한 → CORS: 브라우저가 사진을 S3에 바로 올립니다. 프론트 주소로 바꿔 넣으세요.

```json
[
  {
    "AllowedOrigins": ["https://프론트-도메인"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3000
  }
]
```

4. 관리 → 수명 주기 규칙: 접두사 `backups/mysql/`, **현재 버전 만료 14일** (오래된 백업 자동 삭제)

## 3. EC2용 IAM 역할

IAM → 역할 만들기 → 신뢰할 엔터티 "AWS 서비스 / EC2" → 아래 정책을 인라인으로 추가 → 이름 `durooduroo-ec2`.
서버에 접근 키를 두지 않아도 됩니다(`.env`의 `AWS_ACCESS_KEY_ID`는 비워 둡니다).

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": ["s3:ListBucket"], "Resource": "arn:aws:s3:::버킷이름" },
    { "Effect": "Allow", "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"], "Resource": "arn:aws:s3:::버킷이름/*" }
  ]
}
```

## 4. EC2 인스턴스

1. EC2 → 인스턴스 시작: **Ubuntu Server 24.04 LTS**, 인스턴스 유형은 **"프리 티어 사용 가능"** 표시가 있는 것(메모리 1GB급)
2. 키 페어 만들기(.pem은 잃어버리면 다시 못 받습니다. 이 키가 GitHub Secret `SERVER_KEY`가 됩니다)
3. 스토리지: gp3 30GB (무료 등급 범위 안에서)
4. 보안 그룹:

| 포트 | 출처 | 용도 |
|---|---|---|
| 22 | **내 IP** | SSH (전체 공개 금지) |
| 80, 443 | 0.0.0.0/0 | nginx |
| 3306, 8080 | **열지 않음** | DB·백엔드는 서버 안에서만 |

5. 고급 세부 정보 → IAM 인스턴스 프로파일: `durooduroo-ec2`
6. (선택) 탄력적 IP를 연결하면 서버를 껐다 켜도 주소가 같습니다. 공인 IPv4 요금은 콘솔에서 확인하세요.

## 5. 서버 준비 (SSH로 한 번)

```bash
ssh -i 키.pem ubuntu@서버주소
```

스왑 2GB (메모리 1GB에서 백엔드 + MySQL을 돌리려면 필요합니다):

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

Docker와 AWS CLI:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu   # 다시 로그인하면 sudo 없이 docker 사용
sudo snap install aws-cli --classic
aws s3 ls s3://버킷이름          # IAM 역할이 붙었는지 확인 (에러 없으면 OK)
```

설정 파일:

```bash
mkdir -p /home/ubuntu/durooduroo && cd /home/ubuntu/durooduroo
nano .env            # deploy/.env.production.example 내용을 붙여 넣고 값 채우기
chmod 600 .env
openssl rand -hex 24 # MYSQL_PASSWORD, MYSQL_ROOT_PASSWORD 에 쓸 무작위 값 (두 번 실행해 각각)
```

## 6. GitHub Secrets (레포 Settings → Secrets and variables → Actions)

| 이름 | 값 |
|---|---|
| `SERVER_HOST` | EC2 공인 IP 또는 도메인 |
| `SERVER_USERNAME` | `ubuntu` |
| `SERVER_SSH_PORT` | `22` |
| `SERVER_KEY` | 4단계 .pem 파일 내용 전체 |
| `GHCR_READ_TOKEN` | GitHub 개인 토큰(`read:packages`) — 서버가 이미지를 받을 때 씀 |

GitHub Actions가 SSH로 들어오므로, 22번 포트를 "내 IP"로만 열었다면 배포가 막힙니다.
처음엔 배포할 때만 잠깐 열거나, 팀에서 방식(예: SSH 대신 SSM)을 정하세요.

## 7. 첫 배포와 확인

develop → main 병합(또는 main 푸시)이면 CD가 자동으로 배포합니다. 서버에서 확인:

```bash
cd /home/ubuntu/durooduroo
docker compose -f docker-compose.prod.yml ps                 # 둘 다 running / healthy
docker compose -f docker-compose.prod.yml logs --tail 50 backend   # "Successfully applied 1 migration", [운영 설정 점검] 경고 확인
curl -s http://127.0.0.1:8080/api/v1/health
free -h                                                     # 메모리·스왑 사용량
```

DB 직접 보기: `docker compose -f docker-compose.prod.yml exec mysql mysql -uroot -p durooduroo` (비밀번호는 `.env`의 `MYSQL_ROOT_PASSWORD`)

## 8. 백업과 복원

매일 새벽 4시 백업(cron):

```bash
crontab -e
# 아래 한 줄 추가
0 4 * * * /home/ubuntu/durooduroo/backup-mysql.sh >> /home/ubuntu/durooduroo/backup.log 2>&1
```

처음 한 번은 직접 돌려서 S3에 파일이 생기는지 확인하세요: `./backup-mysql.sh && aws s3 ls s3://버킷이름/backups/mysql/`

복원(지금 DB를 백업 시점으로 덮어씀, 확인 질문이 나옵니다):

```bash
./restore-mysql.sh durooduroo-20261101-040000.sql.gz
```

## 9. 테이블을 바꿀 때

- 엔티티를 바꾸면 `src/main/resources/db/migration/V2__무엇을_바꾸는지.sql`처럼 **새 파일**을 추가합니다. 이미 배포된 `V1__init.sql`은 고치지 않습니다.
- CI의 `mysql-schema` 작업이 MySQL 8.4에서 마이그레이션을 적용하고 엔티티와 맞는지(`validate`) 검사합니다. 파일을 빠뜨리면 여기서 실패합니다.
- 로컬(H2)은 Flyway를 쓰지 않고 엔티티로 테이블을 만듭니다.

## 10. 나중에 RDS로 옮길 때

1. RDS MySQL 8.4를 만들고 보안 그룹에서 EC2만 3306 허용
2. `./backup-mysql.sh`로 받은 덤프를 RDS에 복원
3. `docker-compose.prod.yml`에서 `mysql` 서비스와 `depends_on`을 지우고, `MYSQL_URL`을 RDS 주소(`sslMode=REQUIRED`)로 바꾸기

코드는 바꾸지 않습니다.
