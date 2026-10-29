
package com.example.thanhhoa_offlinetask.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thanhhoa_offlinetask.data.local.Task
import com.example.thanhhoa_offlinetask.data.remote.NetworkUtils
import com.example.thanhhoa_offlinetask.data.repository.SyncManager
import com.example.thanhhoa_offlinetask.data.repository.TaskRepository
import com.example.thanhhoa_offlinetask.worker.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(
    private val repository: TaskRepository,
    private val syncManager: SyncManager,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext

    val tasks: StateFlow<List<Task>> =
        repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow("")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    private fun requestBackgroundSync() {
        SyncScheduler.syncWhenConnected(appContext)
    }

    fun addTask(title: String) {
        if (title.isBlank()) return

        viewModelScope.launch {
            repository.addTask(title.trim())
            requestBackgroundSync()
        }
    }

    fun updateTask(task: Task, newTitle: String) {
        if (newTitle.isBlank()) return

        viewModelScope.launch {
            repository.updateTask(task, newTitle.trim())
            requestBackgroundSync()
        }
    }

    fun toggleCompleted(task: Task) {
        viewModelScope.launch {
            repository.toggleCompleted(task)
            requestBackgroundSync()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            requestBackgroundSync()
        }
    }

    fun syncTasks() {
        if (_isSyncing.value) return

        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Đang đồng bộ..."

            try {
                val isOnline = NetworkUtils.isNetworkAvailable(appContext)
                val success = syncManager.sync()

                _syncMessage.value = when {
                    success -> "Đồng bộ thành công"
                    !isOnline -> "Không có kết nối mạng (Wi-Fi/4G đang tắt). Đồng bộ thất bại."
                    else -> "Đồng bộ thất bại. Hãy kiểm tra kết nối máy chủ."
                }
            } catch (e: Exception) {
                _syncMessage.value =
                    "Lỗi đồng bộ: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
