#!/bin/sh
set -e
# Нормализация URL для Spring/Hikari: частая опечатка dbc: → jdbc:, Neon postgresql:// → jdbc:
normalize_db_url() {
  u="$1"
  case "$u" in
    dbc:*) u="j${u}" ;; # dbc:postgresql → jdbc:postgresql
    jdbc:*) ;;
    postgres://*) u="jdbc:postgresql://${u#postgres://}" ;;
    postgresql://*) u="jdbc:$u" ;;
  esac
  printf '%s' "$u"
}

if [ -n "${DATABASE_URL:-}" ]; then
  export DATABASE_URL="$(normalize_db_url "$DATABASE_URL")"
fi
if [ -n "${SPRING_DATASOURCE_URL:-}" ]; then
  export SPRING_DATASOURCE_URL="$(normalize_db_url "$SPRING_DATASOURCE_URL")"
fi
exec java -jar /app/app.jar
