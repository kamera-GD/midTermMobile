
package com.example.thanhhoa_offlinetask.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    // Lấy danh sách công việc chưa bị xóa
    // Sắp xếp theo rowid DESC (thứ tự tạo ban đầu) để khi tích chọn/bỏ tích hoặc sửa tên,
    // công việc vẫn đứng yên tại vị trí cũ mà không bị nhảy lên đầu danh sách.
    @Query("""
        SELECT * FROM tasks
        WHERE isDeleted = 0
        ORDER BY rowid DESC
    """)
    fun getAllTasks(): Flow<List<Task>>

    // Thêm hoặc thay thế Task theo ID
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    // Cập nhật Task
    @Update
    suspend fun updateTask(task: Task)

    // Lấy các Task cần đồng bộ hoặc thử lại
    @Query("""
        SELECT * FROM tasks
        WHERE syncStatus IN ('PENDING', 'FAILED')
        ORDER BY updatedAt ASC
    """)
    suspend fun getPendingTasks(): List<Task>

    // Lấy một Task theo ID, kể cả Task đã xóa mềm
    @Query("""
        SELECT * FROM tasks
        WHERE id = :id
        LIMIT 1
    """)
    suspend fun getTaskById(id: String): Task?

    // Xóa mềm: giữ dữ liệu để đồng bộ lên Server
    @Query("""
        UPDATE tasks
        SET isDeleted = 1,
            syncStatus = 'PENDING',
            updatedAt = :updatedAt
        WHERE id = :taskId
    """)
    suspend fun softDeleteTask(
        taskId: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    // Cập nhật trạng thái đồng bộ
    @Query("""
        UPDATE tasks
        SET syncStatus = :status
        WHERE id = :taskId
    """)
    suspend fun updateSyncStatus(
        taskId: String,
        status: String
    )

    // Lấy toàn bộ Task, bao gồm Task đã xóa mềm
    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksForSync(): List<Task>

    @Query("""
    UPDATE tasks
    SET syncStatus = 'PENDING'
    WHERE syncStatus = 'SYNCING'
""")
    suspend fun resetInterruptedSyncs()

    @Query("""
    UPDATE tasks
    SET syncStatus = :status
    WHERE id = :taskId
      AND updatedAt = :expectedUpdatedAt
      AND syncStatus = :expectedStatus
""")
    suspend fun updateSyncStatusIfUnchanged(
        taskId: String,
        expectedUpdatedAt: Long,
        expectedStatus: String,
        status: String
    ): Int
}
