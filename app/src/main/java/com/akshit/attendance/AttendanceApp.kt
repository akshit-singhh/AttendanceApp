package com.akshit.attendance

import android.app.Application
import com.akshit.attendance.data.network.RetrofitClient
import com.akshit.attendance.data.network.TokenManager
import com.akshit.attendance.data.repository.AuthRepository
import com.akshit.attendance.data.repository.MockStudentRepository
import com.akshit.attendance.data.repository.MockTeacherRepository
import com.akshit.attendance.data.repository.RealStudentRepository
import com.akshit.attendance.data.repository.RealTeacherRepository
import com.akshit.attendance.data.repository.StudentRepository
import com.akshit.attendance.data.repository.TeacherRepository

/**
 * Manual dependency container. A starter scaffold with ~5 screens doesn't earn Hilt's
 * setup cost — reconsider once the screen count or team size grows enough that
 * constructor wiring here becomes the bottleneck, not before.
 */
class AttendanceApp : Application() {

    lateinit var tokenManager: TokenManager
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var teacherRepository: TeacherRepository
        private set
    lateinit var studentRepository: StudentRepository
        private set

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)
        val api = RetrofitClient.get(tokenManager)
        authRepository = AuthRepository(api, tokenManager)

        // Flip these two lines per-repository if you need to develop UI fully
        // offline — both real repos now call endpoints that exist server-side
        // (though getHistory/submitCorrectionRequest currently 500 — see the
        // bug note in TeacherRepository.kt).
        teacherRepository = RealTeacherRepository(api)
        studentRepository = RealStudentRepository(api)

        // Uncomment to develop UI fully offline against sample data:
        // teacherRepository = MockTeacherRepository()
        // studentRepository = MockStudentRepository()
    }
}
