package dev.chenmeng.remotecodex

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val endpoint: String = "",
    val token: String = "",
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
    val snapshot: RemoteSnapshot? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("connection", 0)
    private val api = RemoteCodexApi()
    private val _state = MutableStateFlow(
        MainUiState(
            endpoint = preferences.getString("endpoint", "") ?: "",
            token = preferences.getString("token", "") ?: "",
        ),
    )
    val state = _state.asStateFlow()
    private var pollingJob: Job? = null

    init {
        if (_state.value.endpoint.isNotBlank()) connect()
    }

    fun updateEndpoint(value: String) {
        _state.value = _state.value.copy(endpoint = value)
    }

    fun updateToken(value: String) {
        _state.value = _state.value.copy(token = value)
    }

    fun connect() {
        val endpoint = _state.value.endpoint.trim()
        if (endpoint.isBlank()) {
            _state.value = _state.value.copy(error = "请输入电脑的 IP 地址")
            return
        }
        preferences.edit()
            .putString("endpoint", endpoint)
            .putString("token", _state.value.token)
            .apply()
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(2_500)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val current = _state.value
        if (current.endpoint.isBlank()) return
        _state.value = current.copy(connecting = current.snapshot == null, error = null)
        runCatching {
            withContext(Dispatchers.IO) { api.load(current.endpoint, current.token) }
        }.onSuccess { snapshot ->
            _state.value = _state.value.copy(
                connecting = false,
                connected = true,
                error = null,
                snapshot = snapshot,
            )
        }.onFailure { error ->
            _state.value = _state.value.copy(
                connecting = false,
                connected = false,
                error = error.message ?: "无法连接服务端",
            )
        }
    }
}
