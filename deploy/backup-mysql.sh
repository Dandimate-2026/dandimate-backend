#!/usr/bin/env bash
# 운영 MySQL을 덤프해 S3에 올린다. EC2에서 cron으로 하루 한 번 돌린다(deploy/README.md).
#   0 4 * * * /home/ubuntu/durooduroo/backup-mysql.sh >> /home/ubuntu/durooduroo/backup.log 2>&1
# 필요: aws CLI, EC2 IAM 역할의 s3:PutObject 권한. 오래된 백업 삭제는 S3 수명 주기 규칙이 맡는다.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

# .env 값에는 &·? 가 들어 있어 source 하지 않고 필요한 줄만 읽는다.
env_value() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- || true; }
BUCKET="$(env_value BACKUP_S3_BUCKET)"
[ -n "$BUCKET" ] || BUCKET="$(env_value AWS_S3_BUCKET)"
[ -n "$BUCKET" ] || { echo "BACKUP_S3_BUCKET 또는 AWS_S3_BUCKET이 .env에 없습니다." >&2; exit 1; }

STAMP="$(date +%Y%m%d-%H%M%S)"
FILE="$(mktemp /tmp/durooduroo-XXXXXX.sql.gz)"
trap 'rm -f "$FILE"' EXIT

# 비밀번호는 컨테이너 안의 환경변수로만 쓴다(호스트 프로세스 목록에 남지 않게).
docker compose -f docker-compose.prod.yml exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --quick --routines --triggers --set-gtid-purged=OFF --databases durooduroo' \
  | gzip > "$FILE"

# 덤프가 중간에 끊기면 파일 끝에 완료 표시가 없다.
gunzip -c "$FILE" | tail -1 | grep -q 'Dump completed' || { echo "덤프가 완료되지 않았습니다." >&2; exit 1; }

aws s3 cp "$FILE" "s3://$BUCKET/backups/mysql/durooduroo-$STAMP.sql.gz" --only-show-errors
echo "$(date '+%F %T') 백업 완료: s3://$BUCKET/backups/mysql/durooduroo-$STAMP.sql.gz ($(du -h "$FILE" | cut -f1))"
