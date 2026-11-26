package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class VaniAccessibilityService : AccessibilityService() {

    companion object {
        var instance: VaniAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("VaniAccessibility", "VaniAccessibilityService connected successfully")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can capture screen events or text for reading
    }

    override fun onInterrupt() {
        Log.d("VaniAccessibility", "VaniAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun clickByText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return findAndClickNodeByText(rootNode, text)
    }

    private fun findAndClickNodeByText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.text != null && node.text.toString().contains(text, ignoreCase = true)) {
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) {
                        return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    parent = parent.parent
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickNodeByText(child, text)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    fun getScreenTextSummary(): String {
        val rootNode = rootInActiveWindow ?: return "Screen content unavailable"
        val sb = StringBuilder()
        extractText(rootNode, sb)
        return if (sb.length > 500) sb.substring(0, 500) + "..." else sb.toString()
    }

    private fun extractText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        if (!node.text.isNullOrBlank()) {
            sb.append(node.text).append(" ")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            extractText(child, sb)
            child.recycle()
        }
    }
}
