
package com.example.thanhhoa_offlinetask.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val PERIODIC_SYNC = "periodic_task_sync"
    private const val ONE_TIME_SYNC = "one_time_task_sync"

    private fun networkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    fun schedule(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<TaskSyncWorker>(
                15,
                TimeUnit.MINUTES
            )
                .setConstraints(networkConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    fun syncWhenConnected(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<TaskSyncWorker>()
                .setConstraints(networkConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                ONE_TIME_SYNC,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
    }
}
