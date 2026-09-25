-- =====================================================================
-- Dawa: family sharing (run once in Supabase → SQL Editor → Run)
-- Tables are private; the app talks only through the fam_* functions,
-- and every call is authenticated with the device's own secret.
-- =====================================================================

create table if not exists public.fam_devices (
  id uuid primary key default gen_random_uuid(),
  secret text not null,
  name text,
  phone text,
  created_at timestamptz not null default now(),
  last_seen timestamptz not null default now()
);

create table if not exists public.fam_links (
  code text primary key,
  patient_device uuid not null unique references public.fam_devices(id) on delete cascade,
  patient_name text,
  snapshot jsonb not null default '{}'::jsonb,
  snapshot_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.fam_members (
  code text not null references public.fam_links(code) on delete cascade,
  caregiver_device uuid not null references public.fam_devices(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (code, caregiver_device)
);

create table if not exists public.fam_marks (
  code text not null references public.fam_links(code) on delete cascade,
  key text not null,
  status text not null check (status in ('taken', 'skipped', 'none')),
  at timestamptz not null,
  by_name text,
  updated_at timestamptz not null default now(),
  primary key (code, key)
);
create index if not exists fam_marks_updated on public.fam_marks (code, updated_at);

alter table public.fam_devices enable row level security;
alter table public.fam_links enable row level security;
alter table public.fam_members enable row level security;
alter table public.fam_marks enable row level security;
revoke all on public.fam_devices, public.fam_links, public.fam_members, public.fam_marks from public, anon, authenticated;

-- ---------- helpers ----------
create or replace function public.fam_check(p_id uuid, p_secret text) returns text
language plpgsql security definer set search_path = public as $fn$
declare v_name text;
begin
  update fam_devices set last_seen = now() where id = p_id and secret = p_secret returning name into v_name;
  if not found then raise exception 'invalid device' using errcode = '28000'; end if;
  return coalesce(v_name, '');
end $fn$;

-- The code this device may read/write: its own link, or one it follows.
create or replace function public.fam_access(p_id uuid, p_code text) returns text
language plpgsql security definer set search_path = public as $fn$
declare v_code text;
begin
  if p_code is null or p_code = '' then
    select code into v_code from fam_links where patient_device = p_id;
  else
    select l.code into v_code from fam_links l
      where l.code = upper(trim(p_code))
        and (l.patient_device = p_id or exists (select 1 from fam_members m where m.code = l.code and m.caregiver_device = p_id));
  end if;
  if v_code is null then raise exception 'no access' using errcode = '42501'; end if;
  return v_code;
end $fn$;

-- ---------- device ----------
create or replace function public.fam_register(p_name text, p_phone text) returns json
language plpgsql security definer set search_path = public as $fn$
declare v_id uuid; v_secret text := replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');
begin
  insert into fam_devices (secret, name, phone) values (v_secret, left(p_name, 80), left(p_phone, 30)) returning id into v_id;
  return json_build_object('id', v_id, 'secret', v_secret);
end $fn$;

create or replace function public.fam_update_me(p_id uuid, p_secret text, p_name text, p_phone text) returns void
language plpgsql security definer set search_path = public as $fn$
begin
  perform fam_check(p_id, p_secret);
  update fam_devices set name = left(p_name, 80), phone = left(p_phone, 30) where id = p_id;
  update fam_links set patient_name = left(p_name, 80) where patient_device = p_id;
end $fn$;

-- ---------- patient side ----------
create or replace function public.fam_share(p_id uuid, p_secret text, p_patient_name text) returns text
language plpgsql security definer set search_path = public as $fn$
declare v_code text; v_chars text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; i int;
begin
  perform fam_check(p_id, p_secret);
  select code into v_code from fam_links where patient_device = p_id;
  if v_code is not null then
    update fam_links set patient_name = left(p_patient_name, 80) where code = v_code;
    return v_code;
  end if;
  loop
    v_code := '';
    for i in 1..6 loop
      v_code := v_code || substr(v_chars, 1 + floor(random() * length(v_chars))::int, 1);
    end loop;
    exit when not exists (select 1 from fam_links where code = v_code);
  end loop;
  insert into fam_links (code, patient_device, patient_name) values (v_code, p_id, left(p_patient_name, 80));
  return v_code;
end $fn$;

create or replace function public.fam_stop_sharing(p_id uuid, p_secret text) returns void
language plpgsql security definer set search_path = public as $fn$
begin
  perform fam_check(p_id, p_secret);
  delete from fam_links where patient_device = p_id;
end $fn$;

create or replace function public.fam_members_of(p_id uuid, p_secret text) returns json
language plpgsql security definer set search_path = public as $fn$
begin
  perform fam_check(p_id, p_secret);
  return coalesce((
    select json_agg(json_build_object('id', d.id, 'name', d.name, 'phone', d.phone, 'since', m.created_at, 'seen', d.last_seen) order by m.created_at)
    from fam_links l join fam_members m on m.code = l.code join fam_devices d on d.id = m.caregiver_device
    where l.patient_device = p_id), '[]'::json);
end $fn$;

create or replace function public.fam_remove_member(p_id uuid, p_secret text, p_member uuid) returns void
language plpgsql security definer set search_path = public as $fn$
begin
  perform fam_check(p_id, p_secret);
  delete from fam_members m using fam_links l
   where m.code = l.code and l.patient_device = p_id and m.caregiver_device = p_member;
end $fn$;

-- Patient uploads the shared medicines/schedule and their own dose marks.
create or replace function public.fam_push(p_id uuid, p_secret text, p_snapshot jsonb, p_marks jsonb) returns json
language plpgsql security definer set search_path = public as $fn$
declare v_code text; v_name text;
begin
  v_name := fam_check(p_id, p_secret);
  select code into v_code from fam_links where patient_device = p_id;
  if v_code is null then raise exception 'not sharing' using errcode = '42501'; end if;
  if length(p_snapshot::text) > 300000 then raise exception 'snapshot too large'; end if;
  update fam_links set snapshot = p_snapshot, snapshot_at = now() where code = v_code;
  if p_marks is not null and jsonb_typeof(p_marks) = 'array' then
    insert into fam_marks (code, key, status, at, by_name, updated_at)
      select v_code, x.k, x.s, x.at, v_name, now()
      from jsonb_to_recordset(p_marks) as x(k text, s text, at timestamptz)
      where x.k is not null and x.s in ('taken', 'skipped', 'none') and x.at is not null
    on conflict (code, key) do update
      set status = excluded.status, at = excluded.at, by_name = excluded.by_name, updated_at = now()
      where fam_marks.at < excluded.at;
  end if;
  delete from fam_marks where code = v_code and at < now() - interval '60 days';
  return json_build_object('code', v_code, 'now', now());
end $fn$;

-- ---------- follower side ----------
create or replace function public.fam_join(p_id uuid, p_secret text, p_code text) returns json
language plpgsql security definer set search_path = public as $fn$
declare v_code text := upper(trim(p_code)); v_link fam_links;
begin
  perform fam_check(p_id, p_secret);
  select * into v_link from fam_links where code = v_code;
  if not found then raise exception 'invalid code' using errcode = 'P0002'; end if;
  if v_link.patient_device = p_id then raise exception 'own code' using errcode = 'P0001'; end if;
  insert into fam_members (code, caregiver_device) values (v_code, p_id) on conflict do nothing;
  return json_build_object('code', v_code, 'patient_name', v_link.patient_name);
end $fn$;

create or replace function public.fam_leave(p_id uuid, p_secret text, p_code text) returns void
language plpgsql security definer set search_path = public as $fn$
begin
  perform fam_check(p_id, p_secret);
  delete from fam_members where code = upper(trim(p_code)) and caregiver_device = p_id;
end $fn$;

-- Both sides: latest snapshot + marks changed since p_since.
create or replace function public.fam_pull(p_id uuid, p_secret text, p_code text, p_since timestamptz) returns json
language plpgsql security definer set search_path = public as $fn$
declare v_code text; v_link fam_links;
begin
  perform fam_check(p_id, p_secret);
  v_code := fam_access(p_id, p_code);
  select * into v_link from fam_links where code = v_code;
  return json_build_object(
    'code', v_code,
    'patient_name', v_link.patient_name,
    'snapshot', case when v_link.patient_device = p_id then null else v_link.snapshot end,
    'snapshot_at', v_link.snapshot_at,
    'now', now(),
    'marks', coalesce((
      select json_agg(json_build_object('k', key, 's', status, 'at', at, 'by', by_name, 'u', updated_at) order by updated_at)
      from fam_marks where code = v_code and (p_since is null or updated_at > p_since)), '[]'::json));
end $fn$;

-- Either side marks a dose (e.g. the follower gave the medicine).
create or replace function public.fam_mark(p_id uuid, p_secret text, p_code text, p_key text, p_status text, p_at timestamptz) returns void
language plpgsql security definer set search_path = public as $fn$
declare v_code text; v_name text;
begin
  v_name := fam_check(p_id, p_secret);
  v_code := fam_access(p_id, p_code);
  if p_status not in ('taken', 'skipped', 'none') then raise exception 'bad status'; end if;
  insert into fam_marks (code, key, status, at, by_name, updated_at)
    values (v_code, p_key, p_status, coalesce(p_at, now()), v_name, now())
  on conflict (code, key) do update
    set status = excluded.status, at = excluded.at, by_name = excluded.by_name, updated_at = now()
    where fam_marks.at <= excluded.at;
end $fn$;

-- Several doses at once (one alarm can cover more than one medicine).
create or replace function public.fam_mark_many(p_id uuid, p_secret text, p_code text, p_keys text[], p_status text, p_at timestamptz) returns void
language plpgsql security definer set search_path = public as $fn$
declare v_code text; v_name text;
begin
  v_name := fam_check(p_id, p_secret);
  v_code := fam_access(p_id, p_code);
  if p_status not in ('taken', 'skipped', 'none') then raise exception 'bad status'; end if;
  insert into fam_marks (code, key, status, at, by_name, updated_at)
    select v_code, k, p_status, coalesce(p_at, now()), v_name, now() from unnest(p_keys) as k
  on conflict (code, key) do update
    set status = excluded.status, at = excluded.at, by_name = excluded.by_name, updated_at = now()
    where fam_marks.at <= excluded.at;
end $fn$;

-- Follower's phone asks right after a dose time: were these doses taken?
create or replace function public.fam_status(p_id uuid, p_secret text, p_code text, p_keys text[]) returns json
language plpgsql security definer set search_path = public as $fn$
declare v_code text;
begin
  perform fam_check(p_id, p_secret);
  v_code := fam_access(p_id, p_code);
  return coalesce((select json_object_agg(key, status) from fam_marks where code = v_code and key = any(p_keys)), '{}'::json);
end $fn$;

-- ---------- permissions ----------
revoke execute on function public.fam_check(uuid, text), public.fam_access(uuid, text) from public, anon, authenticated;
grant execute on function
  public.fam_register(text, text),
  public.fam_update_me(uuid, text, text, text),
  public.fam_share(uuid, text, text),
  public.fam_stop_sharing(uuid, text),
  public.fam_members_of(uuid, text),
  public.fam_remove_member(uuid, text, uuid),
  public.fam_push(uuid, text, jsonb, jsonb),
  public.fam_join(uuid, text, text),
  public.fam_leave(uuid, text, text),
  public.fam_pull(uuid, text, text, timestamptz),
  public.fam_mark(uuid, text, text, text, text, timestamptz),
  public.fam_mark_many(uuid, text, text, text[], text, timestamptz),
  public.fam_status(uuid, text, text, text[])
to anon, authenticated;

select 'Dawa family sharing is ready ✓' as result;
