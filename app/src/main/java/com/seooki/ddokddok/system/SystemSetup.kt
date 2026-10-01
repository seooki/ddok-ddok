package com.seooki.ddokddok.system

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.seooki.ddokddok.core.WakeMethod
import com.seooki.ddokddok.service.AccessibilityBridge
import com.seooki.ddokddok.service.ScreenWakeAccessibilityService
import com.seooki.ddokddok.service.WakeNotificationListener

data class SetupStatus(
    val notificationAccess: Boolean,
    val accessibilityEnabled: Boolean,
    val accessibilityConnected: Boolean,
    val batteryUnrestricted: Boolean,
    val notificationsAllowed: Boolean,
    val menuKeySupported: Boolean,
) {
    /** 설정에서는 켜져 있는데 실제로 연결되지 않은 상태. 앱이 비정상 종료되면 생기고, 껐다 켜야 풀린다. */
    val accessibilityStuck: Boolean get() = accessibilityEnabled && !accessibilityConnected

    /** 전원 버튼 방식을 쓸 때만 접근성이 꼭 필요하다. 기본 방식에서는 권장이다(삼성은 화면 켜기에 필요할 수 있다). */
    fun accessibilityRequired(method: WakeMethod): Boolean = method == WakeMethod.MENU_KEY && menuKeySupported

    fun missingEssentials(method: WakeMethod): Int =
        listOf(notificationAccess, !accessibilityRequired(method) || accessibilityEnabled).count { !it }

    fun essentialsDone(method: WakeMethod): Boolean = missingEssentials(method) == 0
}

/** 권한 상태를 읽고, 사용자가 켜야 하는 시스템 설정 화면을 연다. */
object SystemSetup {

    fun status(context: Context): SetupStatus {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        return SetupStatus(
            notificationAccess = notificationManager.isNotificationListenerAccessGranted(listenerComponent(context)),
            accessibilityEnabled = isAccessibilityEnabled(context),
            accessibilityConnected = AccessibilityBridge.service.value != null,
            batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
            notificationsAllowed = notificationManager.areNotificationsEnabled(),
            menuKeySupported = Build.VERSION.SDK_INT >= 36,
        )
    }

    fun openNotificationAccess(context: Context) {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent(context).flattenToString())
        start(context, detail, fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /** 우리 서비스 설정 화면으로 바로 가는 공개 API가 없어서 먼저 시도해 보고, 안 되면 접근성 설정을 연다. */
    fun openAccessibility(context: Context) {
        val detail = Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
            .putExtra(Intent.EXTRA_COMPONENT_NAME, accessibilityComponent(context).flattenToString())
        start(context, detail, fallback = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openAppDetails(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
    }

    fun openAppNotificationSettings(context: Context) {
        start(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)),
        )
    }

    fun openSecuritySettings(context: Context) {
        start(context, Intent(Settings.ACTION_SECURITY_SETTINGS), fallback = Intent(Settings.ACTION_SETTINGS))
    }

    // 알림 감지가 절전 기능에 끊기면 앱의 핵심 기능이 멈추므로, 설정 화면 대신 시스템 확인 창으로 바로 묻는다.
    @SuppressLint("BatteryLife")
    fun requestBatteryUnrestricted(context: Context) {
        start(
            context,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)),
            fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val ours = accessibilityComponent(context)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    private fun start(context: Context, intent: Intent, fallback: Intent? = null) {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            fallback?.let { start(context, it) }
        } catch (e: SecurityException) {
            fallback?.let { start(context, it) }
        }
    }

    private fun packageUri(context: Context) = Uri.fromParts("package", context.packageName, null)

    private fun listenerComponent(context: Context) =
        ComponentName(context, WakeNotificationListener::class.java)

    private fun accessibilityComponent(context: Context) =
        ComponentName(context, ScreenWakeAccessibilityService::class.java)

    private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
}
