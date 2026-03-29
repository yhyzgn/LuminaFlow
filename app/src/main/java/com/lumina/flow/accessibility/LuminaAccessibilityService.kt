package com.lumina.flow.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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

        var stopped = false
        try {
            var forceStopTapped = false
            repeat(36) { round ->
                delay(400L)
                if (forceStopTapped) {
                    val confirmNode = findConfirmNode()
                    if (confirmNode != null) {
                        clickNode(confirmNode)
                        delay(900L)
                        stopped = true
                        return@repeat
                    }
                }

                val forceStopNode = findForceStopNode()
                if (!forceStopTapped && forceStopNode != null && forceStopNode.isEnabled) {
                    clickNode(forceStopNode)
                    forceStopTapped = true
                    delay(900L)
                }

                // Some ROMs place the force-stop button below fold, try scrolling occasionally.
                if (!forceStopTapped && round % 4 == 3) {
                    performScrollForward()
                }
            }
        } finally {
            closeDetailsPage(stopped)
        }
        return stopped
    }

    private fun findForceStopNode(): AccessibilityNodeInfo? {
        val keywords = listOf(
            "强行停止",
            "强制停止",
            "结束运行",
            "结束应用",
            "force stop",
            "forcestop"
        )
        return findNodeCandidates(keywords, requireEnabled = true)
            .filter { candidate ->
                // App details action row: keep upper/middle buttons, avoid top-right uninstall style targets.
                candidate.bounds.centerY() in 150..1100
            }
            .sortedWith(
                compareBy<NodeCandidate> { kotlin.math.abs(it.bounds.centerX() - screenCenterX()) }
                    .thenBy { it.bounds.centerY() }
            )
            .firstOrNull()
            ?.node
    }

    private fun findConfirmNode(): AccessibilityNodeInfo? {
        val keywords = listOf(
            "强行停止",
            "强制停止",
            "结束运行",
            "仍要停止",
            "仍然停止",
            "force stop"
        )
        return findNodeCandidates(keywords, requireEnabled = false)
            .filter { candidate ->
                // Confirmation sheet is in the lower half; prefer bottom-right destructive button.
                candidate.bounds.centerY() > screenHeight() / 2
            }
            .sortedWith(
                compareByDescending<NodeCandidate> { it.bounds.centerY() }
                    .thenByDescending { it.bounds.centerX() }
            )
            .firstOrNull()
            ?.node
    }

    private fun findNodeCandidates(
        keywords: List<String>,
        requireEnabled: Boolean
    ): List<NodeCandidate> {
        val roots = collectWindowRoots()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        roots.forEach(queue::add)
        val candidates = mutableListOf<NodeCandidate>()

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = buildString {
                append(node.text?.toString().orEmpty())
                append(' ')
                append(node.contentDescription?.toString().orEmpty())
            }.lowercase().replace("\\s+".toRegex(), "")

            val matched = keywords.any { keyword ->
                text.contains(keyword.lowercase().replace("\\s+".toRegex(), ""))
            }
            if (matched && (!requireEnabled || node.isEnabled)) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                candidates += NodeCandidate(node, bounds)
            }

            repeat(node.childCount) { index ->
                node.getChild(index)?.let(queue::add)
            }
        }
        return candidates
    }

    private fun collectWindowRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots += it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            windows
                ?.asSequence()
                ?.mapNotNull(AccessibilityWindowInfo::getRoot)
                ?.forEach { root -> if (roots.none { it == root }) roots += root }
        }
        return roots
    }

    private fun performScrollForward() {
        collectWindowRoots().forEach { root ->
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node.isScrollable) {
                    if (node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return
                }
                repeat(node.childCount) { index ->
                    node.getChild(index)?.let(queue::add)
                }
            }
        }
    }

    private suspend fun closeDetailsPage(stopped: Boolean) {
        delay(220L)
        if (stopped) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            delay(300L)
            // Some ROMs ignore the first HOME after settings confirmation.
            performGlobalAction(GLOBAL_ACTION_HOME)
            delay(220L)
            return
        }

        performGlobalAction(GLOBAL_ACTION_BACK)
        delay(260L)
        performGlobalAction(GLOBAL_ACTION_BACK)
        delay(260L)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun screenHeight(): Int =
        resources.displayMetrics.heightPixels

    private fun screenCenterX(): Int =
        resources.displayMetrics.widthPixels / 2

    private fun clickNode(node: AccessibilityNodeInfo) {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                val success = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) return
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

    private data class NodeCandidate(
        val node: AccessibilityNodeInfo,
        val bounds: android.graphics.Rect
    )
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
