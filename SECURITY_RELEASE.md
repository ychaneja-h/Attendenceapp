# Security changes for 1.4.0

The app uses its own authenticated Ktor API and a server-side PostgreSQL connection. Android does not use the Supabase public Data API to read attendance tables.

## Supabase verification on 2026-09-23

Project `zqufobolapqtodrgizyd` initially reported 23 critical advisor findings: 19 public tables without RLS and four potentially sensitive-column exposures. The repeatable migration in `backend/src/main/resources/db/harden-public.sql` enables RLS and revokes table and column grants from `PUBLIC`, `anon` and `authenticated`. The backend table owner retains access. No attendance or user records are deleted.

The live audit returned 19 public tables, zero tables without RLS and zero tables exposed to client roles. A role-switching check verified that both client roles receive permission errors on every public table. The project has no public functions or policies, and no public views were present. Supabase Advisor then reported no security, performance or health issues. Default table grants were revoked so future tables created by the backend role start without client access. Startup applies the same protection to the new Plan A tables in the schema transaction.

## Backend and Android changes

- JWT signing requires an explicit secret of at least 32 characters. The known development secret is rejected. Configure `JWT_SECRET` in Render before deploying.
- Every authenticated request checks the account's current role, active status and token version. Password and approved device changes invalidate prior tokens.
- Password changes verify the current password, serialize concurrent updates and sign the client out after success. Duplicate student registration cannot overwrite an account.
- Class management, roster access and student approval actions check the owning teacher. Legacy client-supplied attendance sync is retired in favor of Plan A evidence reconciliation.
- Face requests validate finite embeddings, and the client only activates server-approved face data. Cached class membership retains approval status.
- Photo uploads have size/dimension limits, content validation and image re-encoding to a generated PNG filename. Unsupported formats are rejected.
- Database error details are removed from public route responses. Hardcoded database-password and admin-seeding fallbacks are removed.
- Signing credentials live in ignored local configuration. The previously tracked release keystore is removed from current Git tracking and Docker build context.

## Remaining operational limits

Removing a key or password from the current source does not erase old Git history. The release signing key was previously committed; retain upgrade compatibility while assessing key rotation through the distribution channel. Do not publish the local keystore or signing configuration.

Accounts originally created with published/default passwords still require password changes. Removing automatic default seeding does not change existing account passwords. Database credentials should also be rotated if they were ever disclosed.

These checks cover the identified database exposure and reviewed application paths. They are not a complete penetration test. Physical attendance, camera and BLE behavior still require device and classroom testing.
