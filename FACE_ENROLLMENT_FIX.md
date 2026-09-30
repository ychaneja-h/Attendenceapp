# Face enrollment and replacement approval

New students must finish face enrollment. Restarting or navigating to another student screen returns unfinished enrollment to the camera. The system Back action cannot dismiss mandatory enrollment. Existing enrollment can be recovered from the server before requesting another scan.

Initial enrollment remains automatically accepted. Replacing an existing face creates a teacher approval request and does not activate the new template. The teacher Home screen checks for approval requests every 30 seconds while displayed, with a link to review them. These are in-app alerts; background push notifications are not implemented.

Request load and approval failures are visible, and teachers can refresh. Duplicate taps are disabled while a decision is saving. Upload retries reuse captured samples, and retrying the same server request is idempotent. A different replacement cannot overwrite an unresolved request.

The backend rejects student entry and exit face evidence if enrollment is missing or a replacement is pending. This check also applies when offline events are uploaded: previously captured face events uploaded during a pending replacement must wait until the request is resolved. Teacher manual attendance decisions remain available.

Validation: backend integration tests cover initial enrollment, upload retries, inactive replacement templates, teacher approval, unauthorized approval, resolved decisions, and rejection restoring eligibility. Android unit tests and a debug APK build also run.

Before release, deploy the matching backend and test signup, app restart, camera permission denial, replacement approval/rejection, and offline synchronization on phones. Release 1.4.2 uses Android version code 10 and the existing release certificate.

Suggested next improvements: background push notifications for teacher approvals; an approval audit trail; verifying initial enrollment against a trusted student identity in person; and device integrity checks plus server-verifiable face/liveness evidence. Current face matching occurs on the phone, so these changes do not establish that a modified client actually performed a face scan.
