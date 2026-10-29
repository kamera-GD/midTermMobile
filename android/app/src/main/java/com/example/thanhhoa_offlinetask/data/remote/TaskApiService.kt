
package com.example.thanhhoa_offlinetask.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Query

data class TaskDto(
    val id: String,
    val title: String,
    val completed: Boolean,
    val updatedAt: Long,
    val isDeleted: Boolean
)

interface TaskApiService {

    @GET("tasks")
    suspend fun getTasks(
        @Query("since") since: Long? = null
    ): List<TaskDto>

    @POST("tasks")
    suspend fun createTask(
        @Body task: TaskDto
    ): TaskDto

    @PUT("tasks/{id}")
    suspend fun updateTask(
        @Path("id") id: String,
        @Body task: TaskDto
    ): TaskDto

    @DELETE("tasks/{id}")
    suspend fun deleteTask(
        @Path("id") id: String
    ): DeleteTaskResponse
}

data class DeleteTaskResponse(
    val id: String,
    val message: String,
    val isDeleted: Boolean,
    val updatedAt: Long
)
