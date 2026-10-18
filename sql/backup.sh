#!/usr/bin/env bash
# 校园二手交易系统 · 每日 MySQL 逻辑备份（PRD §7）
# 用法：
#   1) Linux/cron：crontab 加 `30 4 * * * /path/to/backup.sh >> /path/to/backup.log 2>&1`
#   2) Windows：Git Bash 执行，或任务计划程序起程序 `bash.exe` 参数 `-lc "/d/毕设/.../sql/backup.sh"`
# 凭据安全（不入库不入 Git）：
#   方式 A：export MYSQL_HOST/PORT/USER + MYSQL_PWD（环境变量，随调度器注入）
#   方式 B：~/.my.cnf 配 [client] user=xxx password=xxx（chmod 600），脚本自动使用
# 可配环境变量：DB_NAME（默认 campus_secondhand）、BACKUP_DIR（默认 ./backups）、RETAIN_DAYS（默认 14）
set -euo pipefail

DB_NAME="${DB_NAME:-campus_secondhand}"
BACKUP_DIR="${BACKUP_DIR:-$(cd "$(dirname "$0")" && pwd)/backups}"
RETAIN_DAYS="${RETAIN_DAYS:-14}"

if [ -z "${MYSQL_PWD:-}" ] && [ ! -f "$HOME/.my.cnf" ]; then
  echo "[backup] 错误：未提供凭据（设置 MYSQL_PWD 或配置 ~/.my.cnf）" >&2
  exit 1
fi

MYSQL_BIN="${MYSQL_BIN:-mysql}"       # Windows 本机可 export MYSQL_BIN=/d/mysql/bin
DUMP_BIN="${DUMP_BIN:-mysqldump}"

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d_%H%M%S)"
TARGET="$BACKUP_DIR/${DB_NAME}_${STAMP}.sql.gz"

# --single-transaction：InnoDB 一致性快照不锁表；--routines/--triggers：全对象；
# --default-character-set=utf8mb4：中文与 emoji 安全
"$DUMP_BIN" ${MYSQL_HOST:+--host="$MYSQL_HOST"} ${MYSQL_PORT:+--port="$MYSQL_PORT"} \
  ${MYSQL_USER:+--user="$MYSQL_USER"} \
  --single-transaction --routines --triggers --events \
  --default-character-set=utf8mb4 "$DB_NAME" | gzip > "$TARGET"

SIZE="$(du -h "$TARGET" | cut -f1)"
echo "[backup] 完成：$TARGET ($SIZE)"

# 清理过期备份（只删本库前缀，防误伤同目录其他文件）
DELETED=$(find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +"$RETAIN_DAYS" -print -delete | wc -l)
echo "[backup] 清理 ${RETAIN_DAYS} 天前备份：${DELETED} 个"

# 恢复方法：gunzip < xxx.sql.gz | mysql -u root -p "$DB_NAME"（先建库）
