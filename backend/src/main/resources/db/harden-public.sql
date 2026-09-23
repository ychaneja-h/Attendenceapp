-- Attendance uses authenticated Ktor routes and a direct PostgreSQL connection.
-- Supabase's public Data API roles must not access these application tables.
-- This block is transactional, repeatable, and does not delete application data.
DO $attendance_security$
DECLARE
    target record;
    api_role text;
    column_list text;
    can_bypass boolean;
BEGIN
    SELECT rolsuper OR rolbypassrls INTO can_bypass FROM pg_roles WHERE rolname = current_user;
    FOR target IN
        SELECT c.oid, n.nspname, c.relname, pg_get_userbyid(c.relowner) AS owner_name, c.relforcerowsecurity
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND c.relname = ANY (ARRAY[
            'users', 'devices', 'otp_records', 'departments', 'academic_years', 'classes',
            'class_members', 'class_join_requests', 'face_templates', 'face_reenrollment_requests',
            'attendance_sessions', 'attendance_checks', 'attendance_records', 'attendance_check_results',
            'review_actions', 'security_events', 'audit_logs', 'notifications', 'sync_queue',
            'approval_requests', 'plan_a_events', 'plan_a_time_anchors'
        ])
    LOOP
        -- Fail before changing access if this connection would lose its own backend access.
        IF NOT COALESCE(can_bypass, false) AND
           (NOT pg_has_role(current_user, target.owner_name, 'USAGE') OR target.relforcerowsecurity) THEN
            RAISE EXCEPTION 'Attendance hardening requires table owner or BYPASSRLS backend access: %', target.relname;
        END IF;
        EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', target.nspname, target.relname);
        EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE %I.%I FROM PUBLIC', target.nspname, target.relname);
        SELECT string_agg(quote_ident(attname), ', ' ORDER BY attnum) INTO column_list
            FROM pg_attribute WHERE attrelid = target.oid AND attnum > 0 AND NOT attisdropped;
        IF column_list IS NOT NULL THEN
            EXECUTE format('REVOKE ALL PRIVILEGES (%s) ON TABLE %I.%I FROM PUBLIC', column_list, target.nspname, target.relname);
        END IF;
        FOREACH api_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
            IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = api_role) THEN
                EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE %I.%I FROM %I', target.nspname, target.relname, api_role);
                IF column_list IS NOT NULL THEN
                    EXECUTE format('REVOKE ALL PRIVILEGES (%s) ON TABLE %I.%I FROM %I', column_list, target.nspname, target.relname, api_role);
                END IF;
            END IF;
        END LOOP;
    END LOOP;
END
$attendance_security$;

-- Future tables created by this backend role must also start without Data API grants.
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL PRIVILEGES ON TABLES FROM PUBLIC;
DO $attendance_defaults$
DECLARE api_role text;
BEGIN
    FOREACH api_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = api_role) THEN
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL PRIVILEGES ON TABLES FROM %I', api_role);
        END IF;
    END LOOP;
END
$attendance_defaults$;
