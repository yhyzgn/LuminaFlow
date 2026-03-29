package com.lumina.flow

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lumina.flow.automation.AutomationPlanner
import com.lumina.flow.automation.AutomationScheduler
import com.lumina.flow.data.AutomationDao
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LuminaFlowApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var dao: AutomationDao

    @Inject
    lateinit var scheduler: AutomationScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createChannels()
        applicationScope.launch {
            dao.getEnabled().forEach { entity ->
                val nextRunAt = AutomationPlanner.computeNextRun(entity)
                if (nextRunAt != null) {
                    val updated = entity.copy(nextRunAt = nextRunAt)
                    dao.upsert(updated)
                    scheduler.schedule(updated)
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    AutomationScheduler.ACTION_CHANNEL_ID,
                    "自动化动作",
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                NotificationChannel(
                    AutomationScheduler.STATUS_CHANNEL_ID,
                    "自动化状态",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        )
    }
}
