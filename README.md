# Attendance App (Android / Kotlin / Jetpack Compose)

A production-grade mobile attendance management client built with **Kotlin**, **Jetpack Compose**, and **Material 3**, designed for students and faculty members of Gautam Buddha University (GBU). The app integrates directly with the [GBU Attendance Management Backend](https://github.com/akshit-singhh/Attendance-Management-Backend).

---

## Feature Overview

### Faculty / Teacher Portal
- **Dashboard & Progress Tracking**:
  - Live **Daily Teaching Progress** tracker (percentage of daily classes conducted).
  - Summary metrics: Conducted classes count, expected students today, and pending session counter.
  - Quick action to trigger **Extra / Make-up Classes** with custom date and subject selection.
- **Interactive Roll Call**:
  - Full-screen roster marking interface with quick actions (*Mark All Present*, *Mark All Absent*).
  - Per-student status toggle (*Present* / *Absent*) with real-time tally.
  - Search and filter students by roll number or name.
- **Attendance Inspection & In-Place Editing (No Admin Bottleneck)**:
  - Submitted classes feature a **View Details** action that reopens the roll call with historical marks pre-populated.
  - Teachers can correct accidental mis-markings and resubmit updates in-place directly via `/mobile/attendance/sync-offline`, avoiding cumbersome admin correction queues.
- **Extra & Make-up Class Sessions**:
  - Conduct unscheduled extra or make-up classes on demand for any allotted course offering.
- **Weekly Faculty Timetable**:
  - Interactive day-by-day timetable schedule with lecture/lab tags, class timings, and lecture hall locations.
- **Teaching Records & Analytics**:
  - Class-by-class historical attendance performance and student turnout rates.

---

### Student Portal
- **Attendance Dashboard & Safe Bunk Analytics**:
  - Real-time overall percentage gauge with 75% statutory target threshold.
  - **Safe Bunk Buffer**: Dynamically calculates the exact number of consecutive classes a student can safely miss while staying above 75%, or the classes needed to recover from a shortfall.
  - Today's class schedule feed showing room allocations, instructors, and period timings.
- **Subject-wise Breakdown**:
  - Individual subject cards displaying attended vs. total periods, current percentage, and syllabus category (Core, Elective, Lab, Theory).
  - Subject detail drill-down showing historical period logs and dates.
- **Attendance Planner ("What If I Skip?")**:
  - Predictive simulation tool allowing students to model the percentage impact of attending or skipping future classes before making decisions.
- **Monthly Attendance Calendar**:
  - Color-coded calendar visualization displaying daily attendance health (green/red/amber indicators).
- **Weekly Academic Timetable**:
  - Complete schedule mapping day, time slots, subject codes, and assigned classrooms.
- **Leave Applications & Tracking**:
  - Apply for medical or duty leaves directly from mobile.
  - Real-time status tracking (*Pending*, *Approved*, *Rejected*).
- **In-App Notifications**:
  - Instant alerts for newly marked attendance sessions, leave request approvals, and low attendance warnings.

---

### Shared & System Features
- **User Profile Management**:
  - Dedicated **Profile Screen** accessible directly by tapping the user avatar on the home bar.
  - Displays user identity (Full Name, Roll Number / Faculty ID, Email, Role, Academic School/Department).
  - Secure session termination with logout confirmation dialog.
- **Multi-Role Switching**:
  - Built-in role switcher for users holding multiple roles (e.g., Faculty + Coordinator).
- **First-Time Login & Password Creation**:
  - Automatic routing to password creation screen upon first-time login for newly onboarded accounts.
- **Cross-Device Responsiveness (Android 11 to Android 16)**:
  - Extensively verified across display densities, from older 720p devices (Android 11) up to modern 1080p+ AMOLED screens (Android 16).
  - Flexible layout containers, scroll-safe cards, and dynamic status chip wrapping prevent text clipping or button squishing.
  - Fixed edge-to-edge navigation bar dividers across light and dark system surfaces.

---

##  Architecture & Technology Stack

| Layer | Technologies |
|---|---|
| **Language** | Kotlin 1.9+ |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM (Model-View-ViewModel) + Repository Pattern |
| **Reactive State** | Kotlin Coroutines, `StateFlow`, `SharedFlow` |
| **Networking** | Retrofit 2 + OkHttp 3 + Moshi JSON Converter |
| **Security & Auth** | JWT Bearer Token stored in encrypted `TokenManager` (SharedPreferences) |
| **Navigation** | AndroidX Navigation Compose with type-safe `Routes` |
| **Image & Icons** | Material Symbols / Compose Icons with SVG vector support |

---

## Project Structure

```
app/src/main/java/com/akshit/attendance/
├── AttendanceApp.kt            # Application entry point, global dependencies
├── MainActivity.kt             # Single-activity Compose host
├── data/
│   ├── model/                  # Data Transfer Objects (DTOs) matching backend schemas
│   │   └── Models.kt           # User, Class, AttendanceRecord, Leave, Stats models
│   ├── network/                # REST API Client & Interceptors
│   │   ├── ApiConfig.kt        # Base URL configuration (emulator vs physical IP)
│   │   ├── ApiService.kt       # Retrofit endpoint interfaces
│   │   ├── RetrofitClient.kt   # OkHttp client builder with AuthInterceptor
│   │   └── TokenManager.kt     # Secure JWT token persistence
│   └── repository/             # Domain repositories
│       ├── AuthRepository.kt   # Login, password creation, role switching
│       ├── StudentRepository.kt# Student dashboard, timetable, planner, leaves
│       └── TeacherRepository.kt# Classes, roll call submission, records, edits
├── navigation/
│   ├── NavGraph.kt             # Central navigation graph
│   ├── Routes.kt               # Type-safe destination routes & arguments
│   └── ViewModelFactory.kt     # Factory for instantiating ViewModels
├── notification/
│   ├── AttendanceNotificationManager.kt # Notification dispatcher
│   ├── NotificationHelper.kt            # Android system notification channels
│   └── NotificationItem.kt              # Local notification data model
└── ui/
    ├── auth/                   # Login, RolePicker, CreatePassword screens
    ├── components/             # Reusable UI widgets
    │   ├── AppTopBar.kt        # Standard top bar with avatar and notices
    │   ├── BottomNavBars.kt    # Responsive bottom navigation bar
    │   ├── StatCard.kt         # Metric display tiles
    │   ├── StatusChip.kt       # Colored attendance status pills
    │   └── ShimmerEffects.kt   # Skeleton loading animations
    ├── profile/                # User profile screen & ViewModel
    ├── student/                # Student screens (Dashboard, Timetable, Planner, etc.)
    ├── teacher/                # Teacher screens (Classes, RollCall, Records, etc.)
    └── theme/                  # Design tokens (Color, Shape, Type, Theme)
```

---

## 🔌 API Integration & Configuration

### 1. Configure the Backend Endpoint
Open `app/src/main/java/com/akshit/attendance/data/network/ApiConfig.kt`:

```kotlin
object ApiConfig {
    // For Android Emulator (maps to host localhost):
    const val BASE_URL = "http://10.0.2.2:8000"

    // For Physical Device testing over Wi-Fi (use your development PC's LAN IP):
    // const val BASE_URL = "http://192.168.1.100:8000"
}
```

### 2. Primary Endpoints Used

| Purpose | Method | Endpoint |
|---|---|---|
| **Login** | `POST` | `/mobile/auth/login` |
| **Create Password** | `POST` | `/mobile/auth/create-password` |
| **Teacher Classes** | `GET` | `/mobile/teacher/classes` |
| **Get Class Roster** | `GET` | `/mobile/teacher/classes/{id}/roster?date=YYYY-MM-DD` |
| **Submit / Edit Attendance** | `POST` | `/mobile/attendance/sync-offline` |
| **Teacher Timetable** | `GET` | `/mobile/teacher/timetable` |
| **Student Dashboard** | `GET` | `/mobile/student/dashboard` |
| **Student Timetable** | `GET` | `/mobile/student/timetable` |
| **Student Leaves** | `GET` / `POST` | `/mobile/student/leaves` |
| **User Profile** | `GET` | `/mobile/auth/profile` |

---

## Building & Running

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1+) or newer
- **JDK**: Version 17 or higher
- **Android SDK**: API 34 / 35 (Android 14 / 15)
- **Minimum SDK**: API 26 (Android 8.0 Oreo)

### Build Commands (Terminal)
Navigate to the Android project root (`D:\Attendance app\AttendanceApp\app`):

```bash
# Clean project
./gradlew clean

# Build Debug APK
./gradlew assembleDebug

# Install directly to connected device / emulator
./gradlew installDebug
```

Output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Device Compatibility & Testing Notes
- **Small Screens (720p / Android 11)**: All headings, metrics cards, and roll call rows auto-scale without horizontal scroll or truncated text.
- **High-Density Displays (1080p+ / Android 16)**: Edge-to-edge support with custom insets handling ensures the bottom navigation bar and status bar blend seamlessly into the background without duplicate dividers or black borders.
- **Network Resilience**: Full graceful error handling with shimmer placeholders on network delay and actionable retry prompts.
