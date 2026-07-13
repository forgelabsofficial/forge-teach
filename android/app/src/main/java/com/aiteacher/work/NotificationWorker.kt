package com.aiteacher.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import com.aiteacher.work.NotificationHelper

class NotificationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: "Study session"
        val text = inputData.getString("text") ?: "Time for your study session"
        NotificationHelper.showNotification(applicationContext, 1001, title, text)
        return Result.success()
    }

    companion object {
        fun buildInput(title: String, text: String): Data {
            return Data.Builder().putString("title", title).putString("text", text).build()
        }
    }
}
