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
        val notifyId = inputData.getInt("notify_id", 1001)
        NotificationHelper.showNotification(applicationContext, notifyId, title, text)
        return Result.success()
    }

    companion object {
        fun buildInput(title: String, text: String, notifyId: Int = 1001): Data {
            return Data.Builder()
                .putString("title", title)
                .putString("text", text)
                .putInt("notify_id", notifyId)
                .build()
        }
    }
}
