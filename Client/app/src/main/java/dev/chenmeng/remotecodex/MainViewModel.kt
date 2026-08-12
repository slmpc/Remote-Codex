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
    val selectedTaskId: String? = null,
    val detail: TaskDetail? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val hideIdleTasks: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("connection", 0)
    private val api = RemoteCodexApi()
    private val _state = MutableStateFlow(
        MainUiState(
            endpoint = preferences.getString("endpoint", "") ?: "",
            token = preferences.getString("token", "") ?: "",
            hideIdleTasks = preferences.getBoolean("hide_idle_tasks", false),
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

    fun setHideIdleTasks(value: Boolean) {
        preferences.edit().putBoolean("hide_idle_tasks", value).apply()
        _state.value = _state.value.copy(hideIdleTasks = value)
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

    fun openTask(taskId: String) {
        _state.value = _state.value.copy(
            selectedTaskId = taskId,
            detail = null,
            detailLoading = true,
            detailError = null,
        )
        viewModelScope.launch { refreshDetail(taskId) }
    }

    fun closeTask() {
        _state.value = _state.value.copy(
            selectedTaskId = null,
            detail = null,
            detailLoading = false,
            detailError = null,
        )
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
            _state.value.selectedTaskId?.let { refreshDetail(it) }
        }.onFailure { error ->
            _state.value = _state.value.copy(
                connecting = false,
                connected = false,
                error = error.message ?: "无法连接服务端",
            )
        }
    }

    private suspend fun refreshDetail(taskId: String) {
        val current = _state.value
        if (current.endpoint.isBlank() || current.selectedTaskId != taskId) return
        _state.value = current.copy(detailLoading = current.detail == null, detailError = null)
        runCatching {
            withContext(Dispatchers.IO) { api.loadDetail(current.endpoint, current.token, taskId) }
        }.onSuccess { detail ->
            if (_state.value.selectedTaskId == taskId) {
                _state.value = _state.value.copy(detail = detail, detailLoading = false, detailError = null)
            }
        }.onFailure { error ->
            if (_state.value.selectedTaskId == taskId) {
                _state.value = _state.value.copy(
                    detailLoading = false,
                    detailError = error.message ?: "无法获取 Task 详情",
                )
            }
        }
    }
}
