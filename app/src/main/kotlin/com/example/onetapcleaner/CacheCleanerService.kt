package com.example.onetapcleaner

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CacheCleanerService : AccessibilityService() {

    companion object {
        var isCleaning = false
        var packageQueue = mutableListOf<String>()
        var currentPackage: String? = null

        fun startCleaning(packages: List<String>) {
            packageQueue.clear()
            packageQueue.addAll(packages)
            isCleaning = true
        }

        fun stopCleaning() {
            isCleaning = false
            packageQueue.clear()
            currentPackage = null
        }
    }

    private var state = State.IDLE

    enum class State {
        IDLE, WAITING_FOR_APP_INFO, WAITING_FOR_STORAGE, WAITING_FOR_CLEAR_CACHE_CONFIRMATION
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isCleaning) return

        val rootNode = rootInActiveWindow ?: return

        when (state) {
            State.IDLE -> {
                if (packageQueue.isNotEmpty()) {
                    currentPackage = packageQueue.removeAt(0)
                    openAppInfo(currentPackage!!)
                    state = State.WAITING_FOR_APP_INFO
                } else {
                    isCleaning = false
                    Log.d("CacheCleaner", "Cleaning finished!")
                }
            }
            State.WAITING_FOR_APP_INFO -> {
                // Find "Storage & cache" or "Storage"
                val storageNode = findNodeByText(rootNode, "Storage & cache") ?: findNodeByText(rootNode, "Storage")
                if (storageNode != null) {
                    if (storageNode.parent?.isClickable == true) {
                        storageNode.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } else if (storageNode.isClickable) {
                        storageNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    state = State.WAITING_FOR_STORAGE
                }
            }
            State.WAITING_FOR_STORAGE -> {
                // Find "Clear cache"
                val clearCacheNode = findNodeByText(rootNode, "Clear cache")
                if (clearCacheNode != null) {
                    if (clearCacheNode.isEnabled) {
                         if (clearCacheNode.parent?.isClickable == true) {
                            clearCacheNode.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                         } else if (clearCacheNode.isClickable) {
                            clearCacheNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                         }
                    }
                    // Wait a bit, then go back
                    Thread.sleep(500)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Thread.sleep(200)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    state = State.IDLE
                }
            }
            else -> {}
        }
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val list = node.findAccessibilityNodeInfosByText(text)
        for (n in list) {
            if (n.text?.toString()?.equals(text, ignoreCase = true) == true) {
                return n
            }
        }
        return null
    }

    private fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        isCleaning = false
    }
}
