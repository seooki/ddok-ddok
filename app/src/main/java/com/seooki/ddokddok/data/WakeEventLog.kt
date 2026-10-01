package com.seooki.ddokddok.data

import android.util.AtomicFile
import androidx.core.util.readText
import androidx.core.util.writeText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * 최근 [MAX_EVENTS]건의 화면 켜기 기록. 백업되지 않는 앱 전용 폴더에 저장한다.
 * 추가는 메인 스레드에서 하고, 파일 읽기·쓰기는 한 번에 하나씩 IO 스레드에서 한다.
 */
class WakeEventLog(
    file: File,
    private val scope: CoroutineScope,
    io: CoroutineDispatcher,
) {
    private val atomicFile = AtomicFile(file)
    private val fileAccess = io.limitedParallelism(1)
    private val _events = MutableStateFlow<List<WakeEvent>>(emptyList())

    /** 오래된 것부터 최신 순. */
    val events: StateFlow<List<WakeEvent>> = _events.asStateFlow()

    init {
        scope.launch(fileAccess) {
            val stored = read()
            // 읽는 동안 새로 들어온 기록은 뒤에 붙인다.
            _events.update { (stored + it).takeLast(MAX_EVENTS) }
        }
    }

    fun add(event: WakeEvent) {
        _events.update { (it + event).takeLast(MAX_EVENTS) }
        persist()
    }

    fun clear() {
        _events.value = emptyList()
        persist()
    }

    // 저장할 내용은 파일 작업 차례가 왔을 때 읽는다. 처음 읽기가 끝나기 전에 쓰면 옛 기록을 덮어쓰기 때문이다.
    private fun persist() {
        scope.launch(fileAccess) { write(_events.value) }
    }

    private fun read(): List<WakeEvent> = try {
        atomicFile.readText().lineSequence().mapNotNull(WakeEventCodec::decode).toList()
    } catch (e: IOException) {
        emptyList()
    }

    private fun write(events: List<WakeEvent>) {
        try {
            atomicFile.writeText(events.joinToString("\n", transform = WakeEventCodec::encode))
        } catch (e: IOException) {
            // 기록을 못 남겨도 화면 켜기에는 영향이 없다. 다음 기록 때 다시 저장한다.
        }
    }

    companion object {
        const val MAX_EVENTS = 100
    }
}
