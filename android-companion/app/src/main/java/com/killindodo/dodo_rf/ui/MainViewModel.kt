package com.killindodo.dodo_rf.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.killindodo.dodo_rf.api.DodoApiClient
import com.killindodo.dodo_rf.model.CapturedSignal
import com.killindodo.dodo_rf.model.StoredSignal
import com.killindodo.dodo_rf.network.DodoWifiBinder
import com.killindodo.dodo_rf.network.WifiBindState
import com.killindodo.dodo_rf.util.FeedbackManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class ShowSnackbar(val message: String) : UiEvent()
    data class PromptSave(val code: String, val freq: Double, val bits: Int, val protocol: Int) : UiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val wifiBinder = DodoWifiBinder(application)
    val apiClient = DodoApiClient()
    val feedbackManager = FeedbackManager(application)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _espState = MutableStateFlow("Offline")
    val espState: StateFlow<String> = _espState

    private val _currentFreq = MutableStateFlow(433.92)
    val currentFreq: StateFlow<Double> = _currentFreq

    private val _latencyMs = MutableStateFlow<Long?>(null)
    val latencyMs: StateFlow<Long?> = _latencyMs

    private val _capturedSignals = MutableStateFlow<List<CapturedSignal>>(emptyList())
    val capturedSignals: StateFlow<List<CapturedSignal>> = _capturedSignals

    private val _storedSignals = MutableStateFlow<List<StoredSignal>>(emptyList())
    val storedSignals: StateFlow<List<StoredSignal>> = _storedSignals

    private val _isSnifferActive = MutableStateFlow(true)
    val isSnifferActive: StateFlow<Boolean> = _isSnifferActive

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents

    private var pollerJob: Job? = null
    var targetHost: String = "192.168.4.1"
        private set

    init {
        startPolling()
    }

    fun setTargetHost(ip: String) {
        targetHost = ip
        apiClient.updateHost(ip)
        refreshStoredSignals()
    }

    fun toggleSniffer(active: Boolean) {
        _isSnifferActive.value = active
    }

    fun clearCapturedSignals() {
        _capturedSignals.value = emptyList()
    }

    fun startPolling() {
        pollerJob?.cancel()
        pollerJob = viewModelScope.launch {
            var consecutiveFails = 0
            while (isActive) {
                if (_isSnifferActive.value && !_isTransmitting.value) {
                    val startT = System.currentTimeMillis()
                    val result = apiClient.getStatus()
                    val duration = System.currentTimeMillis() - startT

                    if (result.isSuccess) {
                        consecutiveFails = 0
                        val status = result.getOrNull()
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        _latencyMs.value = duration
                        status?.let {
                            _espState.value = it.state
                            _currentFreq.value = it.freq
                            if (!it.newSignals.isNullOrEmpty()) {
                                feedbackManager.triggerRxFeedback()
                                val currentList = _capturedSignals.value.toMutableList()
                                currentList.addAll(0, it.newSignals)
                                // Keep buffer bounded
                                if (currentList.size > 200) {
                                    _capturedSignals.value = currentList.subList(0, 200)
                                } else {
                                    _capturedSignals.value = currentList
                                }
                            }
                        }
                    } else {
                        consecutiveFails++
                        if (consecutiveFails >= 3) {
                            _connectionStatus.value = ConnectionStatus.DISCONNECTED
                            _espState.value = "Offline"
                            _latencyMs.value = null
                        }
                    }
                }
                delay(700)
            }
        }
    }

    fun refreshStoredSignals() {
        viewModelScope.launch {
            val result = apiClient.getSignals()
            if (result.isSuccess) {
                _storedSignals.value = result.getOrNull() ?: emptyList()
            }
        }
    }

    fun tuneFrequency(mhz: Double) {
        viewModelScope.launch {
            feedbackManager.triggerClick()
            val result = apiClient.setFrequency(mhz)
            if (result.isSuccess) {
                _currentFreq.value = mhz
                _uiEvents.emit(UiEvent.ShowToast("CC1101 Tuned to ${String.format("%.2f", mhz)} MHz"))
            } else {
                feedbackManager.triggerError()
                _uiEvents.emit(UiEvent.ShowToast("Frequency tune failed: Check connection"))
            }
        }
    }

    fun replaySignal(signal: StoredSignal) {
        viewModelScope.launch {
            _isTransmitting.value = true
            feedbackManager.triggerTxFeedback()
            val result = apiClient.replaySignal(signal.id)
            delay(350)
            _isTransmitting.value = false
            if (result.isSuccess) {
                _uiEvents.emit(UiEvent.ShowToast("Transmitted [${signal.name}] @ ${String.format("%.2f", signal.freq)} MHz"))
            } else {
                feedbackManager.triggerError()
                _uiEvents.emit(UiEvent.ShowToast("TX failed: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun deleteStoredSignal(id: Int) {
        viewModelScope.launch {
            feedbackManager.triggerClick()
            val result = apiClient.deleteSignal(id)
            if (result.isSuccess) {
                _uiEvents.emit(UiEvent.ShowToast("Signal deleted"))
                refreshStoredSignals()
            } else {
                _uiEvents.emit(UiEvent.ShowToast("Delete failed"))
            }
        }
    }

    fun saveSignal(name: String, code: String, freq: Double) {
        viewModelScope.launch {
            val result = apiClient.saveSignal(name, code, freq)
            if (result.isSuccess) {
                feedbackManager.triggerClick()
                _uiEvents.emit(UiEvent.ShowToast("Signal saved as \"$name\""))
                refreshStoredSignals()
            } else {
                feedbackManager.triggerError()
                _uiEvents.emit(UiEvent.ShowToast("Save failed"))
            }
        }
    }

    fun exportSignalsJson(): String {
        return gson.toJson(_storedSignals.value)
    }

    fun importSignalsJson(jsonStr: String) {
        viewModelScope.launch {
            try {
                val result = apiClient.importSignals(jsonStr)
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    _uiEvents.emit(UiEvent.ShowToast("Imported $count signals!"))
                    refreshStoredSignals()
                } else {
                    _uiEvents.emit(UiEvent.ShowToast("Import failed: ${result.exceptionOrNull()?.message}"))
                }
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowToast("Invalid JSON payload"))
            }
        }
    }

    fun triggerAutoConnect() {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        wifiBinder.connectAndBindSoftAp()
    }

    override fun onCleared() {
        super.onCleared()
        pollerJob?.cancel()
        wifiBinder.releaseBinding()
        feedbackManager.release()
    }
}
