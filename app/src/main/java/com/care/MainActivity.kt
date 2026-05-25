package com.care

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.care.ui.CareUiState
import com.care.ui.CareViewModel
import com.care.util.PermissionState
import com.care.util.PermissionUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CareTheme {
                val viewModel: CareViewModel = viewModel(factory = CareViewModel.factory(this))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                CareApp(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun CareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF2F6F73),
            secondary = Color(0xFF6C7A61),
            background = Color(0xFFF7F8FA),
            surface = Color.White,
            surfaceVariant = Color(0xFFE9EEF0)
        ),
        content = content
    )
}

private enum class Screen { Status, Access }

@Composable
private fun CareApp(state: CareUiState, viewModel: CareViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var initialPromptStarted by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.permissionState?.canStartMonitoring) {
        if (state.permissionState?.canStartMonitoring == true) viewModel.ensureMonitoringActive()
    }

    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.completeInitialPermissionFlow()
    }
    val accessibilityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshPermissions()
        if (!PermissionUtils.state(context).batteryOptimizationIgnored) {
            batteryLauncher.launch(PermissionUtils.batteryOptimizationIntent(context))
        } else {
            viewModel.completeInitialPermissionFlow()
        }
    }
    val usageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshPermissions()
        val permissions = PermissionUtils.state(context)
        when {
            !permissions.accessibilityGranted -> accessibilityLauncher.launch(PermissionUtils.accessibilityIntent())
            !permissions.batteryOptimizationIgnored -> batteryLauncher.launch(PermissionUtils.batteryOptimizationIntent(context))
            else -> viewModel.completeInitialPermissionFlow()
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermissions()
        val permissions = PermissionUtils.state(context)
        when {
            !permissions.usageAccessGranted -> usageLauncher.launch(PermissionUtils.usageAccessIntent())
            !permissions.accessibilityGranted -> accessibilityLauncher.launch(PermissionUtils.accessibilityIntent())
            !permissions.batteryOptimizationIgnored -> batteryLauncher.launch(PermissionUtils.batteryOptimizationIntent(context))
            else -> viewModel.completeInitialPermissionFlow()
        }
    }

    LaunchedEffect(state.initialPermissionFlowCompleted) {
        if (!state.initialPermissionFlowCompleted && !initialPromptStarted) {
            initialPromptStarted = true
            val permissions = PermissionUtils.state(context)
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissions.notificationGranted ->
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                !permissions.usageAccessGranted -> usageLauncher.launch(PermissionUtils.usageAccessIntent())
                !permissions.accessibilityGranted -> accessibilityLauncher.launch(PermissionUtils.accessibilityIntent())
                !permissions.batteryOptimizationIgnored -> batteryLauncher.launch(PermissionUtils.batteryOptimizationIntent(context))
                else -> viewModel.completeInitialPermissionFlow()
            }
        }
    }

    val openNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(PermissionUtils.notificationSettingsIntent(context))
        }
    }

    if (state.permissionState?.canStartMonitoring != true) {
        PermissionOnboarding(
            state.permissionState,
            state.statusMessage,
            openUsage = { usageLauncher.launch(PermissionUtils.usageAccessIntent()) },
            openAccessibility = { accessibilityLauncher.launch(PermissionUtils.accessibilityIntent()) },
            openNotifications = openNotifications,
            openBattery = { batteryLauncher.launch(PermissionUtils.batteryOptimizationIntent(context)) }
        )
        return
    }

    var selected by rememberSaveable { mutableStateOf(Screen.Status) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = selected == screen,
                        onClick = { selected = screen },
                        icon = { Icon(screen.icon(), contentDescription = screen.name) },
                        label = { Text(screen.name) }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Header(state) }
            state.statusMessage?.let { message -> item { StatusCard(message) } }
            when (selected) {
                Screen.Status -> statusItems(state)
                Screen.Access -> permissionItems(
                    state.permissionState,
                    openUsage = { usageLauncher.launch(PermissionUtils.usageAccessIntent()) },
                    openAccessibility = { accessibilityLauncher.launch(PermissionUtils.accessibilityIntent()) },
                    openNotifications = openNotifications,
                    openBattery = { batteryLauncher.launch(PermissionUtils.batteryOptimizationIntent(context)) }
                )
            }
        }
    }
}

@Composable
private fun PermissionOnboarding(
    permissions: PermissionState?,
    statusMessage: String?,
    openUsage: () -> Unit,
    openAccessibility: () -> Unit,
    openNotifications: () -> Unit,
    openBattery: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Care", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Device monitoring setup", style = MaterialTheme.typography.titleLarge)
        }
        item {
            SectionCard("Parent-managed monitoring") {
                Text(
                    "This device records app activity, visited browser addresses, screen activity, " +
                        "unlock events, and permission changes to the configured Care Firebase project."
                )
                Text("Care displays an ongoing notification while monitoring is active.")
            }
        }
        statusMessage?.let { message ->
            item { StatusCard(message) }
        }
        permissionItems(permissions, openUsage, openAccessibility, openNotifications, openBattery)
    }
}

private fun LazyListScope.statusItems(state: CareUiState) {
    item {
        SectionCard("Monitoring status") {
            StatusRow(Icons.Default.Shield, "Device monitoring", if (state.monitoringEnabled) "Active continuously" else "Starting automatically")
            StatusRow(
                Icons.Default.CloudDone,
                "Cloud upload",
                if (state.pendingSyncCount == 0) {
                    "Captured activity is uploaded"
                } else {
                    "${state.pendingSyncCount} captured events queued for the next five-minute batch upload"
                }
            )
        }
    }
    item {
        SectionCard("Registered device") {
            Text("Device ID", color = Color(0xFF5D6B70))
            Text(state.deviceId, fontWeight = FontWeight.SemiBold)
            Text("Captured activity and permission changes upload automatically to Firebase.")
        }
    }
}

private fun LazyListScope.permissionItems(
    permissions: PermissionState?,
    openUsage: () -> Unit,
    openAccessibility: () -> Unit,
    openNotifications: () -> Unit,
    openBattery: () -> Unit
) {
    item {
        SectionCard("Permissions") {
            PermissionRow("Usage access", permissions?.usageAccessGranted == true, required = true, openUsage)
            PermissionRow("Accessibility monitoring", permissions?.accessibilityGranted == true, required = true, openAccessibility)
            PermissionRow("Monitoring notification", permissions?.notificationGranted == true, required = true, openNotifications)
            PermissionRow("Battery optimization exception", permissions?.batteryOptimizationIgnored == true, required = false, openBattery)
        }
    }
}

@Composable
private fun Header(state: CareUiState) {
    Column {
        Text("Care", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "${state.deviceName}  |  ${if (state.monitoringEnabled) "Monitoring active" else "Preparing monitoring"}",
            color = Color(0xFF5D6B70)
        )
    }
}

@Composable
private fun StatusCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F2F0))) {
        Text(message, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Color(0xFF5D6B70))
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, required: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                if (granted) "Granted" else if (required) "Required" else "Recommended",
                color = if (granted) Color(0xFF2F6F73) else Color(0xFF9B3A2F)
            )
        }
        if (!granted) Button(onClick = onOpen) { Text("Grant") }
    }
}

private fun Screen.icon() = when (this) {
    Screen.Status -> Icons.Default.Shield
    Screen.Access -> Icons.Default.Security
}
