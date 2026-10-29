package com.example.thanhhoa_offlinetask.data.repository

import com.example.thanhhoa_offlinetask.data.local.Task
import com.example.thanhhoa_offlinetask.data.local.TaskDao
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TaskRepository(
    private val taskDao: TaskDao
) {

    // Lấy toàn bộ task từ Room
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()

    // Thêm task mới
    suspend fun addTask(title: String) {
        val task = Task(
            id = UUID.randomUUID().toString(),
            title = title,
            completed = false,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING",
            isDeleted = false
        )

        taskDao.insertTask(task)
    }

    // Sửa tên task
    suspend fun updateTask(
        task: Task,
        newTitle: String
    ) {
        val nextUpdatedAt = maxOf(System.currentTimeMillis(), task.updatedAt + 1)
        val updatedTask = task.copy(
            title = newTitle,
            updatedAt = nextUpdatedAt,
            syncStatus = "PENDING"
        )

        taskDao.updateTask(updatedTask)
    }

    // Hoàn thành / bỏ hoàn thành
    suspend fun toggleCompleted(task: Task) {
        val nextUpdatedAt = maxOf(System.currentTimeMillis(), task.updatedAt + 1)
        val updatedTask = task.copy(
            completed = !task.completed,
            updatedAt = nextUpdatedAt,
            syncStatus = "PENDING"
        )

        taskDao.updateTask(updatedTask)
    }

    // Xóa logic
    suspend fun deleteTask(task: Task) {
        val nextUpdatedAt = maxOf(System.currentTimeMillis(), task.updatedAt + 1)
        taskDao.softDeleteTask(
            taskId = task.id,
            updatedAt = nextUpdatedAt
        )
    }

    // Lấy các task đang chờ đồng bộ
    suspend fun getPendingTasks(): List<Task> {
        return taskDao.getPendingTasks()
    }
}