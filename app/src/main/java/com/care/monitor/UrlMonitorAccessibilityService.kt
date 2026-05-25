package com.care.monitor

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.care.data.EventType
import com.care.data.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UrlMonitorAccessibilityService : AccessibilityService() {
    private var lastUrl: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val root = rootInActiveWindow ?: return
        val url = URL_BAR_IDS.asSequence()
            .flatMap { id -> root.findAccessibilityNodeInfosByViewId(id).asSequence() }
            .mapNotNull { it.text?.toString()?.trim() }
            .firstOrNull { it.looksLikeUrl() }
            ?: findUrlText(root)
            ?: return

        if (url == lastUrl) return
        lastUrl = url
        CoroutineScope(Dispatchers.IO).launch {
            ServiceLocator.eventRepository.record(
                eventType = EventType.URL,
                packageName = packageName,
                details = url
            )
        }
    }

    override fun onInterrupt() = Unit

    private fun findUrlText(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()?.trim()
        if (text.looksLikeUrl()) return text
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let { findUrlText(it) }
            if (found != null) return found
        }
        return null
    }

    private fun String?.looksLikeUrl(): Boolean {
        if (this.isNullOrBlank()) return false
        return contains(".") && !contains(" ") || startsWith("http://") || startsWith("https://")
    }

    companion object {
        private val URL_BAR_IDS = listOf(
            "com.android.chrome:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.microsoft.emmx:id/url_bar",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text"
        )
    }
}
