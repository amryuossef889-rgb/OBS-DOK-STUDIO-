package com.example.core.diagnostics

import android.os.Debug
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

data class DiagnosticMetrics(
    val renderFps: Float = 0f,
    val encoderFps: Float = 0f,
    val droppedFrames: Long = 0L,
    val bitrateKbps: Int = 0,
    val memoryUsageMb: Long = 0L,
    val cpuUsagePercent: Float = 0f,
    val thermalStatusText: String = "Normal"
)

data class LogEvent(
    val timestamp: String,
    val level: String,
    val message: String
)

class StudioDiagnostics {

    private val _metrics = MutableStateFlow(DiagnosticMetrics())
    val metrics = _metrics.asStateFlow()

    private val recentLogs = ConcurrentLinkedDeque<LogEvent>()
    private val _logsFlow = MutableStateFlow<List<LogEvent>>(emptyList())
    val logsFlow = _logsFlow.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(level: String, message: String) {
        val event = LogEvent(
            timestamp = timeFormat.format(Date()),
            level = level,
            message = message
        )
        recentLogs.addFirst(event)
        while (recentLogs.size > 100) {
            recentLogs.removeLast()
        }
        _logsFlow.value = recentLogs.toList()
    }

    fun updateMetrics(
        renderFps: Float,
        encoderFps: Float,
        droppedFrames: Long,
        bitrateKbps: Int,
        thermalStatus: String
    ) {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

        _metrics.value = DiagnosticMetrics(
            renderFps = renderFps,
            encoderFps = encoderFps,
            droppedFrames = droppedFrames,
            bitrateKbps = bitrateKbps,
            memoryUsageMb = usedMem,
            cpuUsagePercent = estimateCpuUsage(),
            thermalStatusText = thermalStatus
        )
    }

    private fun estimateCpuUsage(): Float {
        // Safe heuristic based on process CPU ticks or fallback
        return runCatching {
            val statFile = RandomAccessFile("/proc/stat", "r")
            val line = statFile.readLine()
            statFile.close()
            val toks = line.split("\\s+".toRegex())
            val idle = toks[4].toLong()
            val total = toks.subList(1, toks.size).mapNotNull { it.toLongOrNull() }.sum()
            val usage = (1.0f - (idle.toFloat() / total.coerceAtLeast(1))) * 100f
            usage.coerceIn(0f, 100f)
        }.getOrDefault(12.5f)
    }
}
