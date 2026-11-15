package com.example.onetapcleaner

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CacheCleanerService : AccessibilityService() {

    companion object {
        var instance: CacheCleanerService? = null
        var isCleaning = false
        var packageQueue = mutableListOf<String>()
        var currentPackage: String? = null

        fun startCleaning(packages: List<String>) {
            packageQueue.clear()
            packageQueue.addAll(packages)
            isCleaning = true
            instance?.processNextApp()
        }

        fun stopCleaning() {
            isCleaning = false
            packageQueue.clear()
            currentPackage = null
            instance?.performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private var state = State.IDLE
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var isActionPending = false

    enum class State {
        IDLE, WAITING_FOR_APP_INFO, WAITING_FOR_STORAGE
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    fun processNextApp() {
        if (!isCleaning) return
        
        if (packageQueue.isNotEmpty()) {
            currentPackage = packageQueue.removeAt(0)
            state = State.WAITING_FOR_APP_INFO
            isActionPending = false
            openAppInfo(currentPackage!!)
        } else {
            isCleaning = false
            state = State.IDLE
            Log.d("CacheCleaner", "Cleaning finished!")
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isCleaning || isActionPending) return

        val rootNode = rootInActiveWindow ?: return

        when (state) {
            State.WAITING_FOR_APP_INFO -> {
                val storageNode = findNodeByText(rootNode, "storage")
                if (storageNode != null && clickNode(storageNode)) {
                    state = State.WAITING_FOR_STORAGE
                }
            }
            State.WAITING_FOR_STORAGE -> {
                val clearCacheNode = findNodeByText(rootNode, "clear cache")
                if (clearCacheNode != null) {
                    if (clearCacheNode.isEnabled) {
                        if (clickNode(clearCacheNode)) {
                            isActionPending = true
                            serviceScope.launch {
                                delay(500) // Wait half a second for cache to clear
                                processNextApp() // Teleport to the next app instantly
                            }
                        }
                    } else {
                        // Cache is already empty/disabled, so just skip to the next app instantly!
                        isActionPending = true
                        serviceScope.launch {
                            processNextApp()
                        }
                    }
                }
            }
            else -> {}
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val list = node.findAccessibilityNodeInfosByText(text)
        for (n in list) {
            val nodeText = n.text?.toString()?.lowercase() ?: n.contentDescription?.toString()?.lowercase() ?: ""
            if (nodeText.contains(text.lowercase())) {
                return n
            }
        }
        return null
    }

    private fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            // This guarantees we instantly teleport to the new App Info screen without piling up history
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        isCleaning = false
        state = State.IDLE
    }
}
