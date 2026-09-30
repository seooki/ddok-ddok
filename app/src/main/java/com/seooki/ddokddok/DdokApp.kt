package com.seooki.ddokddok

import android.app.Application
import android.content.Context
import android.os.StrictMode

class DdokApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        // 디버그 빌드에서만 메인 스레드 디스크 접근, 닫지 않은 자원, 위험한 인텐트 사용을 로그로 잡는다.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
        super.onCreate()
        graph = AppGraph(this)
    }
}

val Context.appGraph: AppGraph get() = (applicationContext as DdokApp).graph
