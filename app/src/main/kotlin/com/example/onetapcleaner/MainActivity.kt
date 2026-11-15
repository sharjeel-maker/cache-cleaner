package com.example.onetapcleaner

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CleanerScreen(this)
                }
            }
        }
    }
}

fun checkAccessibility(context: Context): Boolean {
    var accessibilityEnabled = 0
    try {
        accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED
        )
    } catch (e: Settings.SettingNotFoundException) {
        e.printStackTrace()
    }
    
    if (accessibilityEnabled == 1) {
        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (settingValue != null) {
            return settingValue.contains(context.packageName)
        }
    }
    return false
}

@Composable
fun CleanerScreen(context: Context) {
    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibility(context)) }
    var appsToClean by remember { mutableStateOf(emptyList<String>()) }
    var isLoadingApps by remember { mutableStateOf(false) }
    var isCleaning by remember { mutableStateOf(CacheCleanerService.isCleaning) }
    var remainingApps by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // Periodically re-check accessibility in case user just granted it
    LaunchedEffect(Unit) {
        while (true) {
            val enabled = checkAccessibility(context)
            if (isAccessibilityEnabled != enabled) {
                isAccessibilityEnabled = enabled
            }
            if (isCleaning != CacheCleanerService.isCleaning) {
                isCleaning = CacheCleanerService.isCleaning
            }
            if (isCleaning) {
                remainingApps = CacheCleanerService.packageQueue.size
            }
            kotlinx.coroutines.delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "1-Tap Cache Cleaner",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        if (!isAccessibilityEnabled) {
            Text(
                text = "Please enable the Accessibility Service for this app to allow automatic cache clearing.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) {
                Text("Enable Service")
            }
        } else {
            Text(
                text = "Service is Enabled and Ready!",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoadingApps) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading apps...")
            } else if (isCleaning) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Cleaning in progress...", style = MaterialTheme.typography.titleMedium)
                Text("Remaining: $remainingApps apps", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { CacheCleanerService.stopCleaning() }) {
                    Text("Stop Cleaning")
                }
            } else {
                Button(onClick = {
                    isLoadingApps = true
                    coroutineScope.launch {
                        val packages = withContext(Dispatchers.IO) {
                            val pm = context.packageManager
                            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                            // Filter out system apps to avoid issues
                            apps.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                                .map { it.packageName }
                        }
                        appsToClean = packages
                        isLoadingApps = false
                        if (packages.isNotEmpty()) {
                            CacheCleanerService.startCleaning(packages)
                        }
                    }
                }) {
                    Text("Start Cleaning")
                }
            }
        }
    }
}
