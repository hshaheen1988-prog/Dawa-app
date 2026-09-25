#!/bin/bash
# Local PostgreSQL that mimics Supabase for the family-sharing tests (port 5499, socket /var/tmp).
# Usage: sudo bash tests/setup_pg.sh   (needs postgresql installed; runs as the postgres user)
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PGDIR=${PGDIR:-/var/tmp/pgdawa}
BIN=$(ls -d /usr/lib/postgresql/*/bin | sort -V | tail -1)
if [ ! -f "$PGDIR/PG_VERSION" ]; then
  mkdir -p "$PGDIR" && chown postgres "$PGDIR"
  su postgres -c "$BIN/initdb -D $PGDIR -A trust -U postgres" >/dev/null
fi
su postgres -c "$BIN/pg_ctl -D $PGDIR -o '-p 5499 -k /var/tmp' -l $PGDIR.log status" >/dev/null 2>&1 || \
  su postgres -c "$BIN/pg_ctl -D $PGDIR -o '-p 5499 -k /var/tmp' -l $PGDIR.log -w start" >/dev/null
P="psql -h /var/tmp -p 5499 -U postgres -v ON_ERROR_STOP=1 -q"
$P -d postgres -Atc "select 1 from pg_database where datname='dawa'" | grep -q 1 || $P -d postgres -c "create database dawa"
$P -d dawa -c "do \$\$ begin
  if not exists (select 1 from pg_roles where rolname='anon') then create role anon nologin; end if;
  if not exists (select 1 from pg_roles where rolname='authenticated') then create role authenticated nologin; end if;
end \$\$; grant usage on schema public to anon, authenticated;"
$P -d dawa -f "$ROOT/supabase/family.sql" >/dev/null
$P -d dawa -f "$ROOT/supabase/family-update-7.sql" >/dev/null
echo "Postgres ready on /var/tmp:5499 (db dawa) with family.sql + family-update-7.sql"
