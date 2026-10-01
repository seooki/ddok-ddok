package com.seooki.ddokddok.service

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.seooki.ddokddok.R
import com.seooki.ddokddok.appGraph
import com.seooki.ddokddok.ui.formatEpochTime

/** 빠른 설정 타일. 누르면 켜고 끈다. 쉬는 중이면 바로 다시 켠다. */
class WakeTileService : TileService() {
    private val settings get() = applicationContext.appGraph.settings

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val now = System.currentTimeMillis()
        settings.update { current ->
            if (current.enabled && !current.isSnoozed(now)) {
                current.copy(enabled = false)
            } else {
                current.copy(enabled = true, snoozeUntilEpochMs = 0)
            }
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val current = settings.current
        val snoozed = current.enabled && current.isSnoozed(System.currentTimeMillis())
        tile.state = if (current.enabled && !snoozed) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        // 타일 부제목은 몇 글자만 보여서 짧게 쓰고, 언제까지 쉬는지는 화면 읽기 프로그램에 알려 준다.
        tile.subtitle = when {
            !current.enabled -> getString(R.string.tile_off)
            snoozed -> getString(R.string.tile_snoozed)
            else -> getString(R.string.tile_on)
        }
        tile.stateDescription = if (snoozed) {
            getString(R.string.tile_snoozed_description, formatEpochTime(current.snoozeUntilEpochMs))
        } else {
            null
        }
        tile.updateTile()
    }

    companion object {
        /** 앱에서 설정을 바꿨을 때 타일이 보이고 있으면 다시 그리게 한다. */
        fun requestRefresh(context: Context) {
            requestListeningState(context, ComponentName(context, WakeTileService::class.java))
        }
    }
}
