package com.seooki.ddokddok.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.seooki.ddokddok.appGraph

/**
 * 화면을 전원 버튼처럼 켜고 끄기 위한 접근성 서비스.
 * 화면 내용은 읽지 않고(canRetrieveWindowContent=false), 메뉴 키 신호와 화면 잠금 동작만 쓴다.
 */
class ScreenWakeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        AccessibilityBridge.attach(this)
        applicationContext.appGraph.statusNotifier.clearAccessibilityProblem()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityBridge.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityBridge.detach(this)
        super.onDestroy()
    }

    /**
     * 화면이 꺼져 있으면 안드로이드는 메뉴 키를 '화면을 켜는 키'로 보고, 키 입력은 앱에 넘기지 않은 채 화면만 켠다.
     * 화면이 켜져 있으면 사용 중인 앱에 메뉴 키가 그대로 전달되므로 보내지 않는다.
     */
    @RequiresApi(36)
    fun pressMenuKey(): Boolean {
        if (getSystemService(PowerManager::class.java).isInteractive) return false
        return performGlobalAction(GLOBAL_ACTION_MENU)
    }

    /** 전원 버튼을 눌러 끈 것처럼 화면을 끈다. 기기 관리자 방식과 달리 다음 잠금 해제 때 지문·얼굴 인식이 그대로 된다. */
    fun lockScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
}
