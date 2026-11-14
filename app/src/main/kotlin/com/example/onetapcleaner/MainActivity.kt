package com.example.onetapcleaner

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.onetapcleaner.ui.theme._1TapCleanerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            _1TapCleanerTheme {
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

@Composable
fun CleanerScreen(context: Context) {
    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibility(context)) }
    var appsToClean by remember { mutableStateOf(emptyList<String>()) }
    var isLoadingApps by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Periodically re-check accessibility in case user just granted it
    LaunchedEffect(Unit) {
        while (true) {
            val enabled = checkAccessibility(context)
            if (isAccessibilityEnabled != enabled) {
                isAccessibilityEnabled = enabled
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isAccessibilityEnabled) {
            Text("Accessibility Service is disabled.", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }) {
                Text("Enable Accessibility")
            }
        } else {
            Text("Ready to clean caches!", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoadingApps) {
                CircularProgressIndicator()
                Text("Loading apps...")
            } else {
                Button(onClick = {
                    isLoadingApps = true
                    coroutineScope.launch {
                        val packages = withContext(Dispatchers.IO) {
                            val pm = context.packageManager
                            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
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

fun checkAccessibility(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    for (service in enabledServices) {
        if (service.id.contains(context.packageName)) {
            return true
        }
    }
    return false
}
