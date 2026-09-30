package com.seooki.ddokddok

import android.app.Application
import android.os.Build
import com.seooki.ddokddok.core.WakeMethod
import com.seooki.ddokddok.data.AppCatalog
import com.seooki.ddokddok.data.SettingsRepository
import com.seooki.ddokddok.data.WakeEventLog
import com.seooki.ddokddok.wake.PostureSampler
import com.seooki.ddokddok.wake.ScreenWaker
import com.seooki.ddokddok.wake.WakeController
import com.seooki.ddokddok.wake.WakeTestRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** 앱에 하나씩만 있는 객체들. 규모가 작아서 의존성 주입 라이브러리 없이 여기서 직접 만든다. */
class AppGraph(app: Application) {
    /** 앱이 살아 있는 동안 도는 작업. 메인 스레드에서 실행된다. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val posture = PostureSampler(app)
    private val waker = ScreenWaker(app)

    val settings = SettingsRepository(
        app,
        defaultMethod = if (Build.VERSION.SDK_INT >= 36) WakeMethod.MENU_KEY else WakeMethod.WAKE_LOCK,
    )
    val events = WakeEventLog(File(app.noBackupFilesDir, "wake_events.tsv"), scope, Dispatchers.IO)
    val apps = AppCatalog(app)
    val controller = WakeController(app, settings, events, waker, posture, scope)
    val wakeTest = WakeTestRunner(app, waker, settings, scope)

    val hasProximitySensor: Boolean get() = posture.hasProximitySensor
}
