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
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
    val snapshot: RemoteSnapshot? = null,
    val selectedTaskId: String? = null,
    val detail: TaskDetail? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val hideIdleTasks: Boolean = false,
    val newestOutputsFirst: Boolean = false,
    val promptDraft: String = "",
    val promptSubmitting: Boolean = false,
    val promptMessage: String? = null,
    val promptError: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("connection", 0)
    private val api = RemoteCodexApi()
    private val _state = MutableStateFlow(
        MainUiState(
            endpoint = preferences.getString("endpoint", "") ?: "",
            hideIdleTasks = preferences.getBoolean("hide_idle_tasks", false),
            newestOutputsFirst = preferences.getBoolean("newest_outputs_first", false),
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

    fun setHideIdleTasks(value: Boolean) {
        preferences.edit().putBoolean("hide_idle_tasks", value).apply()
        _state.value = _state.value.copy(hideIdleTasks = value)
    }

    fun setNewestOutputsFirst(value: Boolean) {
        preferences.edit().putBoolean("newest_outputs_first", value).apply()
        _state.value = _state.value.copy(newestOutputsFirst = value)
    }

    fun connect() {
        val endpoint = _state.value.endpoint.trim()
        if (endpoint.isBlank()) {
            _state.value = _state.value.copy(error = "请输入电脑的 IP 地址")
            return
        }
        preferences.edit()
            .putString("endpoint", endpoint)
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
            promptDraft = "",
            promptMessage = null,
            promptError = null,
        )
        viewModelScope.launch { refreshDetail(taskId) }
    }

    fun closeTask() {
        _state.value = _state.value.copy(
            selectedTaskId = null,
            detail = null,
            detailLoading = false,
            detailError = null,
            promptDraft = "",
            promptSubmitting = false,
            promptMessage = null,
            promptError = null,
        )
    }

    fun updatePromptDraft(value: String) {
        _state.value = _state.value.copy(promptDraft = value, promptMessage = null, promptError = null)
    }

    fun consumePromptMessage() {
        _state.value = _state.value.copy(promptMessage = null)
    }

    fun submitPrompt(mode: String) {
        val current = _state.value
        val taskId = current.selectedTaskId ?: return
        val text = current.promptDraft.trim()
        if (text.isBlank() || current.promptSubmitting) return
        runPromptAction(taskId, if (mode == "intervene") "Prompt 已立即发送" else "已加入 Prompt 队列") {
            api.submitPrompt(current.endpoint, taskId, text, mode)
        }
    }

    fun deleteQueuedPrompt(promptId: String) {
        val current = _state.value
        val taskId = current.selectedTaskId ?: return
        runPromptAction(taskId, "已从队列删除", clearDraft = false) {
            api.deleteQueuedPrompt(current.endpoint, taskId, promptId)
        }
    }

    fun interveneQueuedPrompt(promptId: String) {
        val current = _state.value
        val taskId = current.selectedTaskId ?: return
        runPromptAction(taskId, "队列 Prompt 已立即发送", clearDraft = false) {
            api.interveneQueuedPrompt(current.endpoint, taskId, promptId)
        }
    }

    private fun runPromptAction(
        taskId: String,
        successMessage: String,
        clearDraft: Boolean = true,
        action: () -> Unit,
    ) {
        _state.value = _state.value.copy(promptSubmitting = true, promptMessage = null, promptError = null)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onSuccess {
                    if (_state.value.selectedTaskId == taskId) {
                        _state.value = _state.value.copy(
                            promptDraft = if (clearDraft) "" else _state.value.promptDraft,
                            promptSubmitting = false,
                            promptMessage = successMessage,
                            promptError = null,
                        )
                        refreshDetail(taskId)
                    }
                }
                .onFailure { error ->
                    if (_state.value.selectedTaskId == taskId) {
                        _state.value = _state.value.copy(
                            promptSubmitting = false,
                            promptMessage = null,
                            promptError = error.message ?: "Prompt 操作失败",
                        )
                    }
                }
        }
    }

    private suspend fun refresh() {
        val current = _state.value
        if (current.endpoint.isBlank()) return
        _state.value = current.copy(connecting = current.snapshot == null, error = null)
        runCatching {
            withContext(Dispatchers.IO) { api.load(current.endpoint) }
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
            withContext(Dispatchers.IO) { api.loadDetail(current.endpoint, taskId) }
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
