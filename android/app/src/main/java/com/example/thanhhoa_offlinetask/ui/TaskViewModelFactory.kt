
package com.example.thanhhoa_offlinetask.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.thanhhoa_offlinetask.data.repository.SyncManager
import com.example.thanhhoa_offlinetask.data.repository.TaskRepository

class TaskViewModelFactory(
    private val repository: TaskRepository,
    private val syncManager: SyncManager,
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            return TaskViewModel(
                repository,
                syncManager,
                context
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
