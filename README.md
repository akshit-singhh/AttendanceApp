# Attendance App (Android / Kotlin / Jetpack Compose)

Starter scaffold matching the DESIGN.md tokens from your Stitch mockups, wired
against your actual backend (github.com/akshit-singhh/Attendance-Management-Backend,
commit 6a1891a, 2026-09-26).

## Before you open this in Android Studio

1. Set `ApiConfig.BASE_URL` (data/network/ApiConfig.kt) to your running backend.
   `10.0.2.2` is the emulator's alias for host localhost — fine for local dev.
2. Run the backend. You'll need at least one seeded user for each role to test login.

## What's real vs mocked

Every network call in `data/network/ApiService.kt` matches an endpoint that exists
in your backend today. `data/repository/` splits each domain into a `Real*` and
`Mock*` implementation behind the same interface — `AttendanceApp.kt` picks which
one each screen gets. Swap Mock->Real per call as gaps close; screens never change.

**Known backend bug — read before testing Records/History:**
`AttendanceRecord` (app/models/attendance.py) has no `course_offering_id`,
`marked_by_id`, or `date` field. Four endpoints assume at least one of these exist
and will 500 until the model is fixed:
- `GET /mobile/attendance/history`
- `POST /mobile/attendance/corrections`
- `POST /web/corrections/{id}/resolve`
- `POST /web/leaves/{id}/approve` (also assigns `AttendanceStatus.LEAVE`, which
  isn't in the enum — only PRESENT/ABSENT — so this one has two bugs)

Fix once, at the model, rather than patching each endpoint — they all read from
the same missing fields. The Android app's calls to the first two are already
correct against the *intended* contract; nothing to change on the app side.

## Not built yet (intentionally)

- Offline queue for roll-call submission (SRS Section 12) — a failed submit is
  lost today, not queued. Needs Room + WorkManager; flagged with a `ponytail:`
  comment at the exact spot in TeacherViewModel.submitAttendance().
- Analytics tab — no backend endpoint exists for this at all, not even a shape.
  Placeholder screen only.
- Duty/Special leave types — `LeaveRequest` has no `leave_type` column, so the
  app only offers Medical.
- Token refresh-on-401 interceptor — needs the offline-auth rules (max offline
  window) decided first; see comment in RetrofitClient.kt.

## Structure

```
data/model/       DTOs matching backend schemas exactly
data/network/      Retrofit + token storage
data/repository/   Real*/Mock* pairs behind one interface per domain
ui/theme/          Colors/type/shapes from DESIGN.md
ui/components/     Shared: status chip, top bar, bottom nav, stat card
ui/auth/           Login, Create Password (first-login), Role Picker
ui/student/        Dashboard (real), History (mock), Leaves (real)
ui/teacher/        Classes (real), Roll Call (real), Records (real, currently
                    500s — see bug above), Analytics (placeholder)
navigation/        Routes, manual ViewModelFactory (no Hilt), NavGraph
```
