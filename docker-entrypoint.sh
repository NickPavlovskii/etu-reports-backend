#!/bin/sh
set -e
# Neon / Render: postgresql://... → jdbc:postgresql://... (Spring datasource.url)
if [ -n "${DATABASE_URL:-}" ]; then
  u="$DATABASE_URL"
  case "$u" in
    jdbc:*) ;;
    postgres://*) u="jdbc:postgresql://${u#postgres://}" ;;
    postgresql://*) u="jdbc:$u" ;;
  esac
  export DATABASE_URL="$u"
fi
exec java -jar /app/app.jar
