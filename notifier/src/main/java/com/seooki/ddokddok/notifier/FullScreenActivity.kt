package com.seooki.ddokddok.notifier

import android.app.Activity
import android.os.Bundle

/** 전체 화면 알림이 띄우는 화면. 시스템이 화면을 켜는지만 보면 되므로 바로 닫는다. */
class FullScreenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
