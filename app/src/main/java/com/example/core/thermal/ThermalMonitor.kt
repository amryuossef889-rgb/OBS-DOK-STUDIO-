package com.example.core.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor

enum class ThermalStatus {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN
}

sealed class ThermalAction {
    data class Warning(val status: ThermalStatus) : ThermalAction()
    data class DowngradeQuality(val status: ThermalStatus) : ThermalAction()
    data class ForceStop(val status: ThermalStatus) : ThermalAction()
}

class ThermalMonitor(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val _thermalStatus = MutableStateFlow(ThermalStatus.NONE)
    val thermalStatus = _thermalStatus.asStateFlow()

    private val _actions = MutableSharedFlow<ThermalAction>(extraBufferCapacity = 16)
    val actions = _actions.asSharedFlow()

    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    fun start(executor: Executor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            val l = PowerManager.OnThermalStatusChangedListener { status ->
                handleThermalStatus(status)
            }
            listener = l
            runCatching {
                powerManager.addThermalStatusListener(executor, l)
            }
        }
    }

    private fun handleThermalStatus(status: Int) {
        val mapped = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
            else -> ThermalStatus.NONE
        }

        _thermalStatus.value = mapped
        Log.i("ThermalMonitor", "Thermal status changed to $mapped ($status)")

        when (mapped) {
            ThermalStatus.MODERATE -> {
                _actions.tryEmit(ThermalAction.Warning(mapped))
            }
            ThermalStatus.SEVERE -> {
                _actions.tryEmit(ThermalAction.DowngradeQuality(mapped))
            }
            ThermalStatus.CRITICAL, ThermalStatus.EMERGENCY, ThermalStatus.SHUTDOWN -> {
                _actions.tryEmit(ThermalAction.ForceStop(mapped))
            }
            else -> {}
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && listener != null && powerManager != null) {
            runCatching {
                powerManager.removeThermalStatusListener(listener!!)
            }
            listener = null
        }
    }
}
