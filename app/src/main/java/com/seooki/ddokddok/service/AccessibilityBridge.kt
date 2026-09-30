package com.seooki.ddokddok.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 실행 중인 접근성 서비스를 앱의 다른 부분과 이어 준다. 서비스가 꺼져 있으면 null이다. */
object AccessibilityBridge {
    private val _service = MutableStateFlow<ScreenWakeAccessibilityService?>(null)

    val service: StateFlow<ScreenWakeAccessibilityService?> = _service.asStateFlow()

    internal fun attach(service: ScreenWakeAccessibilityService) {
        _service.value = service
    }

    internal fun detach(service: ScreenWakeAccessibilityService) {
        _service.compareAndSet(service, null)
    }
}
