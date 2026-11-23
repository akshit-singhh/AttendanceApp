package com.akshit.attendance.util

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Centralized Date & Schedule Provider for the Attendance App.
 *
 * Production Architecture:
 * - In live production, uses dynamic system clock via `LocalDate.now()`.
 * - In demo/testing mode (`isDemoMode = true`), defaults to the active sample schedule date
 *   (12 Nov 2026, Thursday) so teachers and evaluators can interact with sample semester
 *   classes without empty-screen roadblocks on weekends or holidays.
 */
object AppDateProvider {
    /**
     * Set to true while testing with demo semester dataset.
     * Set to false for live institutional deployment.
     */
    var isDemoMode: Boolean = false

    /** Active fallback date for demo semester classes */
    val DemoDate: LocalDate = LocalDate.of(2026, 11, 19)

    val today: LocalDate
        get() = if (isDemoMode) DemoDate else LocalDate.now()

    val todayString: String
        get() = today.toString()

    val todayDayOfWeek: String
        get() = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
}
