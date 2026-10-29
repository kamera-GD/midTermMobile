
package com.example.thanhhoa_offlinetask.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.thanhhoa_offlinetask.data.local.AppDatabase
import com.example.thanhhoa_offlinetask.data.remote.NetworkUtils
import com.example.thanhhoa_offlinetask.data.remote.RetrofitClient
import com.example.thanhhoa_offlinetask.data.repository.SyncManager

class TaskSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val database =
                AppDatabase.getDatabase(applicationContext)

            val syncManager = SyncManager(
                database.taskDao(),
                RetrofitClient.apiService,
                isOnlineProvider = { NetworkUtils.isNetworkAvailable(applicationContext) }
            )

            val success = syncManager.sync()

            if (success) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
