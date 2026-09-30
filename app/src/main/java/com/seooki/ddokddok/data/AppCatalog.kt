package com.seooki.ddokddok.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class AppEntry(val packageName: String, val label: String)

/** 설치된 앱의 이름과 아이콘. 한 번 읽은 값은 메모리에 둔다. */
class AppCatalog(private val context: Context) {
    private val packageManager = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = LruCache<String, ImageBitmap>(ICON_CACHE_SIZE)

    /** 홈 화면에 나타나는 앱 목록. 이름순으로 정렬한다. */
    suspend fun launcherApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val collator = Collator.getInstance(Locale.getDefault())
        queryLauncherActivities().asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { AppEntry(it.packageName, labelOf(it)) }
            .sortedWith(compareBy(collator) { it.label })
            .toList()
    }

    /** 앱 이름. 지워졌거나 볼 수 없는 앱이면 패키지 이름을 돌려준다. */
    suspend fun label(packageName: String): String = labels[packageName] ?: withContext(Dispatchers.IO) {
        applicationInfo(packageName)?.let(::labelOf) ?: packageName
    }

    suspend fun icon(packageName: String, sizePx: Int): ImageBitmap? {
        icons.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                packageManager.getApplicationIcon(packageName)
                    .toBitmap(sizePx, sizePx)
                    .asImageBitmap()
                    .also { icons.put(packageName, it) }
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    private fun labelOf(info: ApplicationInfo): String =
        packageManager.getApplicationLabel(info).toString().also { labels[info.packageName] = it }

    private fun queryLauncherActivities(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    private fun applicationInfo(packageName: String): ApplicationInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private companion object {
        const val ICON_CACHE_SIZE = 200
    }
}
