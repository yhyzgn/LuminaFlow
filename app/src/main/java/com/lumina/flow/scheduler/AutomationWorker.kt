package com.lumina.flow.scheduler

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AutomationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : Worker(context, params) {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun doWork(): Result {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = android.app.Notification.Builder(applicationContext, "lumina_flow")
            .setContentTitle("LuminaFlow")
            .setContentText("自动化任务已触发")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        nm.notify(1, notification)
        return Result.success()
    }
}