-- Read-only metadata audit; no student details, passwords or face templates are selected.
SELECT n.nspname AS schema_name, c.relname AS table_name,
       c.relrowsecurity AS rls_enabled, c.relforcerowsecurity AS rls_forced,
       pg_get_userbyid(c.relowner) AS owner_name,
       r.rolname AS api_role,
       has_table_privilege(r.oid, c.oid, 'SELECT') AS can_select_table,
       has_any_column_privilege(r.oid, c.oid, 'SELECT') AS can_select_any_column,
       has_table_privilege(r.oid, c.oid, 'INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER') AS has_write_privilege
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
CROSS JOIN pg_roles r
WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
  AND r.rolname IN ('anon', 'authenticated')
ORDER BY c.relname, r.rolname;
