
package com.example.thanhhoa_offlinetask.data.repository

import com.example.thanhhoa_offlinetask.data.local.Task
import com.example.thanhhoa_offlinetask.data.local.TaskDao
import com.example.thanhhoa_offlinetask.data.remote.TaskApiService
import com.example.thanhhoa_offlinetask.data.remote.TaskDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncManager(
    private val taskDao: TaskDao,
    private val api: TaskApiService,
    private val isOnlineProvider: () -> Boolean = { true }
) {

    companion object {
        private val syncMutex = Mutex()
    }

    suspend fun sync(): Boolean = syncMutex.withLock {
        try {
            // Khôi phục Task bị kẹt nếu lần đồng bộ trước gián đoạn
            taskDao.resetInterruptedSyncs()

            // Kiểm tra kết nối mạng thực sự (Wi-Fi / Cellular) trước khi gọi API
            // (Tránh trường hợp tắt Wi-Fi trên thiết bị nhưng request vẫn đi lọt qua cáp USB adb reverse 127.0.0.1)
            if (!isOnlineProvider()) {
                val pendingTasks = taskDao.getPendingTasks()
                for (task in pendingTasks) {
                    taskDao.updateSyncStatusIfUnchanged(
                        taskId = task.id,
                        expectedUpdatedAt = task.updatedAt,
                        expectedStatus = task.syncStatus,
                        status = "FAILED"
                    )
                }
                return@withLock false
            }

            // Đẩy thay đổi local lên server trước
            val pendingTasks = taskDao.getPendingTasks()

            for (task in pendingTasks) {
                val claimed = taskDao.updateSyncStatusIfUnchanged(
                    taskId = task.id,
                    expectedUpdatedAt = task.updatedAt,
                    expectedStatus = task.syncStatus,
                    status = "SYNCING"
                )

                if (claimed == 0) {
                    continue
                }

                try {
                    // Backend hỗ trợ POST upsert theo ID
                    val serverTask = api.createTask(task.toDto())

                    val currentTask = taskDao.getTaskById(task.id)

                    // Chỉ cập nhật nếu Task không bị chỉnh sửa trong lúc gửi
                    if (
                        currentTask != null &&
                        currentTask.updatedAt == task.updatedAt &&
                        currentTask.syncStatus == "SYNCING"
                    ) {
                        val syncedEntity = serverTask.toEntity("SYNCED")
                        taskDao.updateTask(syncedEntity)
                    }
                } catch (e: Exception) {
                    taskDao.updateSyncStatusIfUnchanged(
                        taskId = task.id,
                        expectedUpdatedAt = task.updatedAt,
                        expectedStatus = "SYNCING",
                        status = "FAILED"
                    )

                    throw e
                }
            }

            // Lấy dữ liệu mới nhất từ server
            val remoteTasks = api.getTasks()

            for (remote in remoteTasks) {
                val local = taskDao.getTaskById(remote.id)

                val hasLocalChanges =
                    local?.syncStatus in listOf(
                        "PENDING",
                        "SYNCING",
                        "FAILED"
                    )

                if (hasLocalChanges) {
                    continue
                }

                if (local == null) {
                    taskDao.insertTask(
                        remote.toEntity("SYNCED")
                    )
                } else if (remote.updatedAt >= local.updatedAt) {
                    taskDao.updateTask(
                        remote.toEntity("SYNCED")
                    )
                }
            }

            true
        } catch (e: Exception) {
            System.err.println("SyncManager: Error during sync: ${e.message}")
            false
        }
    }
}

private fun Task.toDto() = TaskDto(
    id = id,
    title = title,
    completed = completed,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

private fun TaskDto.toEntity(status: String) = Task(
    id = id,
    title = title,
    completed = completed,
    updatedAt = updatedAt,
    syncStatus = status,
    isDeleted = isDeleted
)
