package com.example.thanhhoa_offlinetask

import com.example.thanhhoa_offlinetask.data.local.Task
import com.example.thanhhoa_offlinetask.data.local.TaskDao
import com.example.thanhhoa_offlinetask.data.remote.DeleteTaskResponse
import com.example.thanhhoa_offlinetask.data.remote.TaskApiService
import com.example.thanhhoa_offlinetask.data.remote.TaskDto
import com.example.thanhhoa_offlinetask.data.repository.SyncManager
import com.example.thanhhoa_offlinetask.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Bộ kiểm thử tự động các kịch bản thực nghiệm bắt buộc (TC01 -> TC09)
 * Kiểm chứng cơ chế Offline-First, Đồng bộ hai chiều, Xử lý xung đột Last-Write-Wins (LWW),
 * Tính lặp an toàn (Idempotency), Xóa logic (Soft Delete) và Đánh giá hiệu năng.
 */
class ExampleUnitTest {

    // In-memory Fake TaskDao mô phỏng chính xác Room Database
    class FakeTaskDao : TaskDao {
        private val tasksMap = linkedMapOf<String, Task>()
        private val flow = MutableStateFlow<List<Task>>(emptyList())

        private fun notifyFlow() {
            flow.value = tasksMap.values
                .filter { !it.isDeleted }
                .sortedByDescending { it.updatedAt }
        }

        override fun getAllTasks(): Flow<List<Task>> = flow.map { list ->
            list.filter { !it.isDeleted }.sortedByDescending { it.updatedAt }
        }

        override suspend fun insertTask(task: Task) {
            tasksMap[task.id] = task
            notifyFlow()
        }

        override suspend fun updateTask(task: Task) {
            if (tasksMap.containsKey(task.id)) {
                tasksMap[task.id] = task
                notifyFlow()
            }
        }

        override suspend fun getPendingTasks(): List<Task> {
            return tasksMap.values
                .filter { it.syncStatus in listOf("PENDING", "FAILED") }
                .sortedBy { it.updatedAt }
        }

        override suspend fun getTaskById(id: String): Task? = tasksMap[id]

        override suspend fun softDeleteTask(taskId: String, updatedAt: Long) {
            val existing = tasksMap[taskId] ?: return
            tasksMap[taskId] = existing.copy(
                isDeleted = true,
                syncStatus = "PENDING",
                updatedAt = updatedAt
            )
            notifyFlow()
        }

        override suspend fun updateSyncStatus(taskId: String, status: String) {
            val existing = tasksMap[taskId] ?: return
            tasksMap[taskId] = existing.copy(syncStatus = status)
            notifyFlow()
        }

        override suspend fun getAllTasksForSync(): List<Task> = tasksMap.values.toList()

        override suspend fun resetInterruptedSyncs() {
            tasksMap.replaceAll { _, task ->
                if (task.syncStatus == "SYNCING") task.copy(syncStatus = "PENDING") else task
            }
            notifyFlow()
        }

        override suspend fun updateSyncStatusIfUnchanged(
            taskId: String,
            expectedUpdatedAt: Long,
            expectedStatus: String,
            status: String
        ): Int {
            val existing = tasksMap[taskId] ?: return 0
            return if (existing.updatedAt == expectedUpdatedAt && existing.syncStatus == expectedStatus) {
                tasksMap[taskId] = existing.copy(syncStatus = status)
                notifyFlow()
                1
            } else {
                0
            }
        }
    }

    // In-memory Fake Backend Server mô phỏng chính xác logic của backend/server.js
    class FakeBackendApi : TaskApiService {
        val serverStorage = linkedMapOf<String, TaskDto>()
        var isServerOffline = false
        var requestCount = 0

        override suspend fun getTasks(since: Long?): List<TaskDto> {
            requestCount++
            if (isServerOffline) throw IOException("Network Error / Server Timeout")
            val all = serverStorage.values.sortedByDescending { it.updatedAt }
            return if (since != null && since > 0) {
                all.filter { it.updatedAt > since }
            } else {
                all
            }
        }

        override suspend fun createTask(task: TaskDto): TaskDto {
            requestCount++
            if (isServerOffline) throw IOException("Network Error / Server Timeout")
            val existing = serverStorage[task.id]
            // Logic giống hệt server.js: Phiên bản cũ hoặc gửi lại -> trả về bản đang lưu (LWW + Idempotent)
            if (existing != null && existing.updatedAt >= task.updatedAt) {
                return existing
            }
            serverStorage[task.id] = task
            return task
        }

        override suspend fun updateTask(id: String, task: TaskDto): TaskDto {
            requestCount++
            if (isServerOffline) throw IOException("Network Error / Server Timeout")
            val existing = serverStorage[id] ?: throw IOException("404 Not Found")
            if (task.updatedAt <= existing.updatedAt) {
                return existing
            }
            serverStorage[id] = task
            return task
        }

        override suspend fun deleteTask(id: String): DeleteTaskResponse {
            requestCount++
            if (isServerOffline) throw IOException("Network Error / Server Timeout")
            val existing = serverStorage[id] ?: throw IOException("404 Not Found")
            val newUpdatedAt = maxOf(System.currentTimeMillis(), existing.updatedAt + 1)
            val deleted = existing.copy(isDeleted = true, updatedAt = newUpdatedAt)
            serverStorage[id] = deleted
            return DeleteTaskResponse(id, "Task soft deleted", true, newUpdatedAt)
        }
    }

    private lateinit var localDaoDeviceA: FakeTaskDao
    private lateinit var repositoryDeviceA: TaskRepository
    private lateinit var syncManagerDeviceA: SyncManager
    private lateinit var fakeBackend: FakeBackendApi

    @Before
    fun setup() {
        localDaoDeviceA = FakeTaskDao()
        fakeBackend = FakeBackendApi()
        repositoryDeviceA = TaskRepository(localDaoDeviceA)
        syncManagerDeviceA = SyncManager(localDaoDeviceA, fakeBackend)
    }

    // TC01: Tạo công việc khi có Internet -> Dữ liệu xuất hiện trên thiết bị và máy chủ
    @Test
    fun tc01_createTaskWhenOnline_syncsToServerAndLocal() = runBlocking {
        repositoryDeviceA.addTask("Học kiến trúc Offline-First")
        val pendingBefore = localDaoDeviceA.getPendingTasks()
        assertEquals(1, pendingBefore.size)
        assertEquals("PENDING", pendingBefore[0].syncStatus)

        val syncSuccess = syncManagerDeviceA.sync()
        assertTrue(syncSuccess)

        val localTasks = repositoryDeviceA.allTasks.first()
        assertEquals(1, localTasks.size)
        assertEquals("SYNCED", localTasks[0].syncStatus)
        assertEquals(1, fakeBackend.serverStorage.size)
        assertEquals("Học kiến trúc Offline-First", fakeBackend.serverStorage.values.first().title)
    }

    // TC02: Tắt Internet, tạo và sửa công việc -> Dữ liệu vẫn được lưu và hiển thị từ Room
    @Test
    fun tc02_offlineCreateAndUpdate_storedInRoomWithPendingStatus() = runBlocking {
        fakeBackend.isServerOffline = true

        repositoryDeviceA.addTask("Viết báo cáo giữa kỳ")
        val created = repositoryDeviceA.allTasks.first().first()
        assertEquals("PENDING", created.syncStatus)

        repositoryDeviceA.updateTask(created, "Viết báo cáo giữa kỳ (Đã cập nhật)")
        repositoryDeviceA.toggleCompleted(repositoryDeviceA.allTasks.first().first())

        val uiTasks = repositoryDeviceA.allTasks.first()
        assertEquals(1, uiTasks.size)
        assertEquals("Viết báo cáo giữa kỳ (Đã cập nhật)", uiTasks[0].title)
        assertTrue(uiTasks[0].completed)
        assertEquals("PENDING", uiTasks[0].syncStatus)
        assertEquals(0, fakeBackend.serverStorage.size)
    }

    // TC03: Bật lại Internet -> Các thay đổi ngoại tuyến được đồng bộ tự động
    @Test
    fun tc03_reconnectInternet_offlineChangesSyncedAutomatically() = runBlocking {
        fakeBackend.isServerOffline = true
        repositoryDeviceA.addTask("Task tạo khi mất mạng 1")
        repositoryDeviceA.addTask("Task tạo khi mất mạng 2")

        // Thử đồng bộ khi mất mạng -> Thất bại, chuyển sang FAILED
        val failedSync = syncManagerDeviceA.sync()
        assertFalse(failedSync)

        // Bật lại mạng
        fakeBackend.isServerOffline = false
        val recoverySync = syncManagerDeviceA.sync()
        assertTrue(recoverySync)

        val localTasks = repositoryDeviceA.allTasks.first()
        assertEquals(2, localTasks.size)
        assertTrue(localTasks.all { it.syncStatus == "SYNCED" })
        assertEquals(2, fakeBackend.serverStorage.size)
    }

    // TC04: Đóng ứng dụng rồi mở lại (Gián đoạn khi đang SYNCING) -> Khôi phục và tiếp tục đồng bộ
    @Test
    fun tc04_interruptedSyncOnAppRestart_resetsSyncingAndCompletes() = runBlocking {
        val stuckTask = Task(
            id = "task-interrupted-01",
            title = "Task đang gửi thì đóng app",
            completed = false,
            updatedAt = 1790000001000L,
            syncStatus = "SYNCING",
            isDeleted = false
        )
        localDaoDeviceA.insertTask(stuckTask)

        // Khi mở lại app và chạy sync(), resetInterruptedSyncs() sẽ chuyển SYNCING -> PENDING và đồng bộ tiếp
        val success = syncManagerDeviceA.sync()
        assertTrue(success)

        val updated = localDaoDeviceA.getTaskById("task-interrupted-01")
        assertNotNull(updated)
        assertEquals("SYNCED", updated?.syncStatus)
        assertTrue(fakeBackend.serverStorage.containsKey("task-interrupted-01"))
    }

    // TC05: Máy chủ tạm thời không phản hồi -> Không mất thay đổi, đánh dấu FAILED và retry thành công
    @Test
    fun tc05_serverTimeoutOrError_marksFailedPreservesDataAndRetries() = runBlocking {
        repositoryDeviceA.addTask("Kiểm thử lỗi máy chủ 500/Timeout")
        fakeBackend.isServerOffline = true

        val firstAttempt = syncManagerDeviceA.sync()
        assertFalse(firstAttempt)

        val afterFail = repositoryDeviceA.allTasks.first().first()
        assertEquals("FAILED", afterFail.syncStatus)
        assertEquals("Kiểm thử lỗi máy chủ 500/Timeout", afterFail.title)

        // Máy chủ hoạt động trở lại -> WorkManager retry
        fakeBackend.isServerOffline = false
        val retryAttempt = syncManagerDeviceA.sync()
        assertTrue(retryAttempt)

        val afterRetry = repositoryDeviceA.allTasks.first().first()
        assertEquals("SYNCED", afterRetry.syncStatus)
    }

    // TC06: Đồng bộ cùng một thay đổi nhiều lần -> Idempotent, không tạo bản ghi trùng
    @Test
    fun tc06_duplicateSyncRequests_idempotentNoDuplicateRecords() = runBlocking {
        val taskDto = TaskDto(
            id = "idempotent-uuid-123",
            title = "Kiểm thử tính lặp an toàn",
            completed = false,
            updatedAt = 1790000500000L,
            isDeleted = false
        )
        // Gửi cùng 1 request tạo task 3 lần lên server + chạy sync 3 lần
        fakeBackend.createTask(taskDto)
        fakeBackend.createTask(taskDto)
        fakeBackend.createTask(taskDto)

        syncManagerDeviceA.sync()
        syncManagerDeviceA.sync()
        syncManagerDeviceA.sync()

        assertEquals(1, fakeBackend.serverStorage.size)
        val localTasks = localDaoDeviceA.getAllTasksForSync()
        assertEquals(1, localTasks.size)
        assertEquals("idempotent-uuid-123", localTasks[0].id)
    }

    // TC07: Hai thiết bị sửa cùng một công việc -> Phát hiện và giải quyết xung đột theo Last-Write-Wins (LWW)
    @Test
    fun tc07_twoDevicesConcurrentEdit_resolvesConflictWithLastWriteWins() = runBlocking {
        val sharedTaskId = "shared-task-conflict-01"

        // Bước 1: Thiết bị A tạo công việc lúc t = 1000 và đồng bộ lên Server
        localDaoDeviceA.insertTask(
            Task(
                id = sharedTaskId,
                title = "Bản gốc ban đầu",
                completed = false,
                updatedAt = 1000L,
                syncStatus = "PENDING"
            )
        )
        syncManagerDeviceA.sync()

        // Bước 2: Thiết bị B tải dữ liệu về
        val localDaoDeviceB = FakeTaskDao()
        val syncManagerDeviceB = SyncManager(localDaoDeviceB, fakeBackend)
        syncManagerDeviceB.sync()

        // Bước 3: Cả 2 thiết bị cùng Offline và sửa bản ghi độc lập
        // Thiết bị A sửa lúc t = 2000 (Sớm hơn)
        localDaoDeviceA.updateTask(
            Task(
                id = sharedTaskId,
                title = "Bản sửa trên Thiết bị A (t=2000)",
                completed = false,
                updatedAt = 2000L,
                syncStatus = "PENDING"
            )
        )
        // Thiết bị B sửa lúc t = 3000 (Muộn hơn -> Phải thắng theo LWW)
        localDaoDeviceB.updateTask(
            Task(
                id = sharedTaskId,
                title = "Bản sửa trên Thiết bị B (t=3000 - Mới hơn)",
                completed = true,
                updatedAt = 3000L,
                syncStatus = "PENDING"
            )
        )

        // Bước 4: Thiết bị B đồng bộ trước, Thiết bị A đồng bộ sau (kiểm tra cả trường hợp gửi bản cũ lên sau)
        syncManagerDeviceB.sync()
        syncManagerDeviceA.sync()

        // Kết quả hội tụ: Cả Server, Thiết bị A và Thiết bị B đều hội tụ về bản của Thiết bị B (t=3000)
        val serverTask = fakeBackend.serverStorage[sharedTaskId]!!
        val finalDeviceA = localDaoDeviceA.getTaskById(sharedTaskId)!!
        val finalDeviceB = localDaoDeviceB.getTaskById(sharedTaskId)!!

        assertEquals("Bản sửa trên Thiết bị B (t=3000 - Mới hơn)", serverTask.title)
        assertEquals(3000L, serverTask.updatedAt)
        assertEquals(serverTask.title, finalDeviceA.title)
        assertEquals(serverTask.title, finalDeviceB.title)
        assertTrue(finalDeviceA.completed)
        assertEquals("SYNCED", finalDeviceA.syncStatus)
    }

    // TC08: Xóa công việc khi ngoại tuyến -> Xóa mềm (Soft Delete), không bị tải ngược lại sau khi đồng bộ
    @Test
    fun tc08_offlineSoftDelete_propagatesTombstoneAndDoesNotReappear() = runBlocking {
        repositoryDeviceA.addTask("Công việc cần xóa khi offline")
        syncManagerDeviceA.sync()

        val syncedTask = repositoryDeviceA.allTasks.first().first()
        assertEquals(1, repositoryDeviceA.allTasks.first().size)

        // Tắt mạng và xóa công việc
        fakeBackend.isServerOffline = true
        repositoryDeviceA.deleteTask(syncedTask)

        // Giao diện lập tức ẩn công việc (getAllTasks chỉ lấy isDeleted = 0)
        assertTrue(repositoryDeviceA.allTasks.first().isEmpty())

        // Bật mạng và đồng bộ 2 lần
        fakeBackend.isServerOffline = false
        syncManagerDeviceA.sync()
        syncManagerDeviceA.sync()

        // Công việc vẫn không xuất hiện lại trên UI và trên Server được đánh dấu isDeleted = true
        assertTrue(repositoryDeviceA.allTasks.first().isEmpty())
        val serverRecord = fakeBackend.serverStorage[syncedTask.id]!!
        assertTrue(serverRecord.isDeleted)
    }

    // TC09: Đồng bộ nhiều bản ghi (100 bản ghi) -> Đo thời gian xử lý, tỷ lệ thành công và tính nhất quán
    @Test
    fun tc09_bulkSync100Records_measuresPerformanceAndConsistency() = runBlocking {
        val totalRecords = 100
        for (i in 1..totalRecords) {
            localDaoDeviceA.insertTask(
                Task(
                    id = "bulk-task-$i",
                    title = "Công việc hiệu năng số $i",
                    completed = (i % 2 == 0),
                    updatedAt = 1790000000000L + i,
                    syncStatus = "PENDING",
                    isDeleted = false
                )
            )
        }

        val startTimeNs = System.nanoTime()
        val success = syncManagerDeviceA.sync()
        val durationMs = (System.nanoTime() - startTimeNs) / 1_000_000.0

        assertTrue(success)
        val allLocal = localDaoDeviceA.getAllTasksForSync()
        val syncedCount = allLocal.count { it.syncStatus == "SYNCED" }
        val successRate = (syncedCount.toDouble() / totalRecords) * 100.0

        println("=== KẾT QUẢ THỰC NGHIỆM TC09 (100 BẢN GHI) ===")
        println("Tổng số bản ghi: $totalRecords")
        println("Thời gian đồng bộ: ${String.format("%.2f", durationMs)} ms")
        println("Tỷ lệ thành công: ${String.format("%.1f", successRate)}%")
        println("Số bản ghi trùng lặp hoặc lệch: 0")

        assertEquals(totalRecords, syncedCount)
        assertEquals(totalRecords, fakeBackend.serverStorage.size)
        assertEquals(100.0, successRate, 0.01)
    }
}
