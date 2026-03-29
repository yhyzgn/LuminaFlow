package com.lumina.flow.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lumina.flow.automation.AutomationScheduler

class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutomationScheduler.ACTION_RUN_AUTOMATION) return
        val id = intent.getLongExtra(AutomationScheduler.AUTOMATION_ID_KEY, -1L)
        Log.d("LuminaFlowAlarm", "onReceive id=$id action=${intent.action}")
        if (id <= 0L) return
        AutomationExecutionService.start(context, id, manual = false)
    }
}
