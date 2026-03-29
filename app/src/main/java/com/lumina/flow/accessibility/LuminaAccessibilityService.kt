package com.lumina.flow.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class LuminaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        AccessibilityAutomationBridge.attach(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityAutomationBridge.detach(this)
        return super.onUnbind(intent)
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    suspend fun performHomeAction(): Boolean =
        withContext(Dispatchers.Main) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

    suspend fun forceStopPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false

        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)

        repeat(20) {
            delay(300L)
            val root = rootInActiveWindow ?: return@repeat
            val forceStopNode = findForceStopNode(root)
            if (forceStopNode != null) {
                if (!forceStopNode.isEnabled) return false
                clickNode(forceStopNode)
                delay(500L)
                rootInActiveWindow?.let { confirmRoot ->
                    findConfirmNode(confirmRoot)?.let { clickNode(it) }
                }
                delay(500L)
                performGlobalAction(GLOBAL_ACTION_BACK)
                return true
            }
        }
        return false
    }

    private fun findForceStopNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val texts = listOf("强行停止", "结束运行", "Force stop", "FORCE STOP")
        return texts.firstNotNullOfOrNull { text ->
            root.findAccessibilityNodeInfosByText(text).firstOrNull()
        }
    }

    private fun findConfirmNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val texts = listOf("确定", "强行停止", "OK", "Force stop")
        return texts.firstNotNullOfOrNull { text ->
            root.findAccessibilityNodeInfosByText(text).firstOrNull { it.isClickable || it.isEnabled }
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
            current = current.parent
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val path = Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
            dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                    .build(),
                null,
                null
            )
        }
    }
}

object AccessibilityAutomationBridge {
    @Volatile
    private var service: LuminaAccessibilityService? = null

    fun attach(service: LuminaAccessibilityService) {
        this.service = service
    }

    fun detach(service: LuminaAccessibilityService) {
        if (this.service === service) this.service = null
    }

    fun isEnabled(): Boolean = service != null

    suspend fun goHome(): Boolean = service?.performHomeAction() == true

    suspend fun forceStopPackage(packageName: String): Boolean =
        service?.forceStopPackage(packageName) == true
}
