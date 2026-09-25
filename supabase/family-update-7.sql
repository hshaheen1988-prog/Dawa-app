-- =====================================================================
-- Dawa: family sharing — update 7 (run once in Supabase → SQL Editor → Run)
-- Safe to run on top of family.sql. Same function signatures, so the
-- existing permissions stay as they are and older app versions keep working.
--  • fam_pull sends the medicine schedule only when it changed (≈10× less data)
--  • limits: registrations per minute, followers per patient, upload size
--  • old data cleanup: devices unused for 180 days
-- =====================================================================

-- ---------- device: throttle + occasional cleanup ----------
create or replace function public.fam_register(p_name text, p_phone text) returns json
language plpgsql security definer set search_path = public as $fn$
declare v_id uuid; v_secret text := replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');
begin
  if (select count(*) from fam_devices where created_at > now() - interval '1 minute') >= 30 then
    raise exception 'busy, try again' using errcode = '53400';
  end if;
  if random() < 0.02 then
    delete from fam_devices where last_seen < now() - interval '180 days';
  end if;
  insert into fam_devices (secret, name, phone) values (v_secret, left(p_name, 80), left(p_phone, 30)) returning id into v_id;
  return json_build_object('id', v_id, 'secret', v_secret);
end $fn$;

-- ---------- patient upload: size limits ----------
create or replace function public.fam_push(p_id uuid, p_secret text, p_snapshot jsonb, p_marks jsonb) returns json
language plpgsql security definer set search_path = public as $fn$
declare v_code text; v_name text;
begin
  v_name := fam_check(p_id, p_secret);
  select code into v_code from fam_links where patient_device = p_id;
  if v_code is null then raise exception 'not sharing' using errcode = '42501'; end if;
  if length(p_snapshot::text) > 300000 then raise exception 'snapshot too large'; end if;
  if p_marks is not null and length(p_marks::text) > 300000 then raise exception 'marks too large'; end if;
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

-- ---------- follower join: at most 10 followers per patient ----------
create or replace function public.fam_join(p_id uuid, p_secret text, p_code text) returns json
language plpgsql security definer set search_path = public as $fn$
declare v_code text := upper(trim(p_code)); v_link fam_links;
begin
  perform fam_check(p_id, p_secret);
  select * into v_link from fam_links where code = v_code;
  if not found then raise exception 'invalid code' using errcode = 'P0002'; end if;
  if v_link.patient_device = p_id then raise exception 'own code' using errcode = 'P0001'; end if;
  if not exists (select 1 from fam_members where code = v_code and caregiver_device = p_id)
     and (select count(*) from fam_members where code = v_code) >= 10 then
    raise exception 'too many followers' using errcode = 'P0003';
  end if;
  insert into fam_members (code, caregiver_device) values (v_code, p_id) on conflict do nothing;
  return json_build_object('code', v_code, 'patient_name', v_link.patient_name);
end $fn$;

-- ---------- pull: schedule only when it changed ----------
-- A 2-minute overlap covers uploads that were still saving during the last pull.
create or replace function public.fam_pull(p_id uuid, p_secret text, p_code text, p_since timestamptz) returns json
language plpgsql security definer set search_path = public as $fn$
declare v_code text; v_link fam_links; v_from timestamptz := p_since - interval '2 minutes';
begin
  perform fam_check(p_id, p_secret);
  v_code := fam_access(p_id, p_code);
  select * into v_link from fam_links where code = v_code;
  return json_build_object(
    'code', v_code,
    'patient_name', v_link.patient_name,
    'snapshot', case
      when v_link.patient_device = p_id then null
      when p_since is null or v_link.snapshot_at is null or v_link.snapshot_at > v_from then v_link.snapshot
      else null end,
    'snapshot_at', v_link.snapshot_at,
    'now', now(),
    'marks', coalesce((
      select json_agg(json_build_object('k', key, 's', status, 'at', at, 'by', by_name, 'u', updated_at) order by updated_at)
      from fam_marks where code = v_code and (p_since is null or updated_at > v_from)), '[]'::json));
end $fn$;

select 'Dawa family update 7 is ready ✓' as result;
