package com.example.thanhhoa_offlinetask.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey
    val id: String,

    val title: String,

    val completed: Boolean = false,

    // Thời điểm dữ liệu được cập nhật
    val updatedAt: Long = System.currentTimeMillis(),

    // PENDING / SYNCING / SYNCED / FAILED
    val syncStatus: String = "PENDING",

    // Xóa logic để có thể đồng bộ việc xóa lên server
    val isDeleted: Boolean = false
)