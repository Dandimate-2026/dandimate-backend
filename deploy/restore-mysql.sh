#!/usr/bin/env bash
# S3 백업 하나로 운영 MySQL을 되돌린다. 지금 DB 내용은 백업 시점으로 덮어써진다.
#   ./restore-mysql.sh durooduroo-20261101-040000.sql.gz
# 되돌리는 동안 백엔드를 멈추고, 끝나면 다시 켠다.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
NAME="${1:?복원할 백업 파일 이름을 주세요. 목록: aws s3 ls s3://<버킷>/backups/mysql/}"

env_value() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- || true; }
BUCKET="$(env_value BACKUP_S3_BUCKET)"
[ -n "$BUCKET" ] || BUCKET="$(env_value AWS_S3_BUCKET)"

read -r -p "s3://$BUCKET/backups/mysql/$NAME 로 DB를 덮어씁니다. 계속할까요? (yes) " answer
[ "$answer" = "yes" ] || { echo "취소했습니다."; exit 1; }

FILE="$(mktemp /tmp/durooduroo-restore-XXXXXX.sql.gz)"
trap 'rm -f "$FILE"' EXIT
aws s3 cp "s3://$BUCKET/backups/mysql/$NAME" "$FILE" --only-show-errors

docker compose -f docker-compose.prod.yml stop backend
gunzip -c "$FILE" | docker compose -f docker-compose.prod.yml exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
docker compose -f docker-compose.prod.yml start backend
echo "복원 완료: $NAME"
