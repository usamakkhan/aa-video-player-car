# Structured maintenance — 2026-10-12

## Changes

Replaced unstructured note entry with a separate maintenance screen and Room-backed service records. Records contain title/type, optional service date and mileage, optional due date/mileage, exact notes, stable UUID and creation/update timestamps. Supports creation, editing and confirmed deletion. The list shows date-based overdue status and due mileage; it does not infer current odometer readings or schedule reminders.

Dates are calendar-day values (`LocalDate.toEpochDay`) to avoid timezone shifts. Validation rejects impossible/malformed dates, negative/fractional/overflow mileage, missing titles and due values earlier than service values. Unknown historical dates/mileage can remain empty. Lists initially load 50 rows and expose Load more; database operations use the existing background worker.

Draft fields and stable IDs survive activity recreation. Saving the same restored draft updates one record rather than inserting duplicates. Callback generation checks prevent a completed background operation from replacing a different editor that the user has since opened. Back/Cancel leaves the editor without saving.

## Upgrade and data preservation

Room schema version 2 adds `maintenance_records` and `data_imports` through explicit `MIGRATION_1_2`. The original media table is not altered or dropped. Both schema exports are retained for subsequent migration tests. No destructive fallback exists.

The former Activity preference `maintenance` is copied exactly into a single Imported maintenance notes record on first opening the feature. Text is not split or heuristically interpreted. A transactional import marker makes the operation idempotent, including after the imported record is intentionally deleted. Original preferences are retained as source data.

## Verified

Full command: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest`.

- Debug APK build: PASS.
- JVM tests: 8 PASS (3 registry, 5 maintenance validation).
- Pixel 9 Pro Android 15 instrumentation: 12 PASS (9 existing playback/dashboard, 2 database, 1 maintenance screen).
- Lint: 0 errors, 23 warnings; no lint baseline added.
- Version-1 database reconstructed from its exported schema, populated with media/progress, upgraded through real Room migration and reopened successfully. Legacy Unicode/whitespace notes remain identical, import does not duplicate, and deleting an imported note does not resurrect it.
- Record CRUD, ordering/paging and ID preservation tested.
- Editor validation, draft restoration after recreation, saving, editing and no duplicate record tested through app views.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `b362294433cb364c6bf9e857a3644d7996452f62d4891670379bcb78798ef55b`.
Reports: `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/androidTests/connected/debug/`, `app/build/reports/lint-results-debug.html`.

## Limits and continuation

Programmatic UI tests do not establish touch geometry, keyboard visibility or visual quality. Those checks remain outstanding following the earlier Computer Use access rejection. Reminders, unit conversion, multiple vehicles, export/import and large-history performance are not implemented or verified. Do not claim full-app completion or zero defects from this milestone. The Android Auto stub-host limitation is unchanged. See the root handoff and feature matrix for all remaining features.
