package com.care

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.care.data.ActivityEvent
import com.care.data.EventType
import com.care.ui.CareUiState
import com.care.ui.CareViewModel
import com.care.util.PermissionUtils
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

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

private enum class Screen { Dashboard, Permissions, Device, Logs, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CareApp(state: CareUiState, viewModel: CareViewModel) {
    var selected by remember { mutableStateOf(Screen.Dashboard) }
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermissions()
    }
    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.export(context, it, json = false) }
    }
    val exportJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.export(context, it, json = true) }
    }

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
            item {
                Header(state)
            }
            state.statusMessage?.let { message ->
                item { StatusCard(message) }
            }
            when (selected) {
                Screen.Dashboard -> dashboardItems(state, viewModel)
                Screen.Permissions -> permissionItems(
                    state = state,
                    openUsage = { context.startActivity(PermissionUtils.usageAccessIntent()) },
                    openAccessibility = { context.startActivity(PermissionUtils.accessibilityIntent()) },
                    openNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(PermissionUtils.notificationSettingsIntent(context))
                        }
                    },
                    openBattery = { context.startActivity(PermissionUtils.appDetailsIntent(context)) }
                )
                Screen.Device -> deviceItems(state, viewModel)
                Screen.Logs -> logItems(state, viewModel)
                Screen.Settings -> settingsItems(
                    state = state,
                    viewModel = viewModel,
                    exportCsv = { exportCsvLauncher.launch("care-activity.csv") },
                    exportJson = { exportJsonLauncher.launch("care-activity.json") }
                )
            }
        }
    }
}

private fun LazyListScope.dashboardItems(state: CareUiState, viewModel: CareViewModel) {
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Events", state.dashboardStats.todayEvents.toString(), Modifier.weight(1f))
            StatCard("URLs", state.dashboardStats.urlCount.toString(), Modifier.weight(1f))
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Unlocks", state.dashboardStats.unlockCount.toString(), Modifier.weight(1f))
            StatCard("App time", "${state.dashboardStats.appUsageSeconds / 60}m", Modifier.weight(1f))
        }
    }
    item {
        SectionCard(title = "Monitoring") {
            Text(if (state.monitoringEnabled) "Foreground monitoring is running." else "Monitoring is stopped.")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::startMonitoring, enabled = !state.monitoringEnabled) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start")
                }
                OutlinedButton(onClick = viewModel::stopMonitoring, enabled = state.monitoringEnabled) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop")
                }
            }
        }
    }
    item {
        SectionCard(title = "Top apps today") {
            if (state.dashboardStats.topApps.isEmpty()) {
                Text("No app sessions recorded today.")
            } else {
                state.dashboardStats.topApps.forEach { (name, seconds) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${seconds / 60}m ${seconds % 60}s", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    item { EventList("Recent activity", state.recentEvents.take(8)) }
}

private fun LazyListScope.permissionItems(
    state: CareUiState,
    openUsage: () -> Unit,
    openAccessibility: () -> Unit,
    openNotifications: () -> Unit,
    openBattery: () -> Unit
) {
    val permissions = state.permissionState
    item {
        SectionCard(title = "Required access") {
            PermissionRow("Usage Access", permissions?.usageAccessGranted == true, openUsage)
            PermissionRow("Accessibility URL monitor", permissions?.accessibilityGranted == true, openAccessibility)
            PermissionRow("Notifications", permissions?.notificationGranted == true, openNotifications)
            PermissionRow("Battery optimization", permissions?.batteryOptimizationIgnored == true, openBattery)
        }
    }
}

private fun LazyListScope.deviceItems(state: CareUiState, viewModel: CareViewModel) {
    item {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var deviceName by remember(state.deviceName) { mutableStateOf(state.deviceName) }
        SectionCard(title = "Parent account") {
            Text(state.parentUserId?.let { "Signed in: $it" } ?: "Sign in to sync this device with Firebase.")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                password,
                { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.signIn(email, password, createAccount = false) }, enabled = !state.busy) {
                    Text("Sign in")
                }
                OutlinedButton(onClick = { viewModel.signIn(email, password, createAccount = true) }, enabled = !state.busy) {
                    Text("Create")
                }
                OutlinedButton(onClick = viewModel::signOut) {
                    Text("Sign out")
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(deviceName, { deviceName = it }, label = { Text("Device name") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { viewModel.setDeviceName(deviceName) }) {
                Text("Save device")
            }
        }
    }
}

private fun LazyListScope.logItems(state: CareUiState, viewModel: CareViewModel) {
    item {
        SectionCard(title = "Filters") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(selected = state.selectedEventType == null, onClick = { viewModel.filterEventType(null) }, label = { Text("All") })
                EventType.entries.forEach { type ->
                    FilterChip(selected = state.selectedEventType == type, onClick = { viewModel.filterEventType(type) }, label = { Text(type.name) })
                }
            }
        }
    }
    item { EventList("Activity log", state.recentEvents) }
}

private fun LazyListScope.settingsItems(
    state: CareUiState,
    viewModel: CareViewModel,
    exportCsv: () -> Unit,
    exportJson: () -> Unit
) {
    item {
        SectionCard(title = "Sync") {
            ToggleRow("Firebase sync", state.firebaseSyncEnabled, viewModel::setFirebaseSyncEnabled)
            Text("${state.pendingSyncCount} events waiting to sync.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::syncNow, enabled = !state.busy) {
                Icon(Icons.Default.CloudSync, null)
                Spacer(Modifier.width(6.dp))
                Text("Sync now")
            }
        }
    }
    item {
        SectionCard(title = "Tracking") {
            ToggleRow("Browser URL tracking", state.browserTrackingEnabled, viewModel::setBrowserTrackingEnabled)
            Text("Usage polling: ${state.pollingIntervalSeconds}s")
            Slider(
                value = state.pollingIntervalSeconds.toFloat(),
                onValueChange = { viewModel.setPollingInterval(it.roundToInt().toLong()) },
                valueRange = 5f..60f,
                steps = 10
            )
        }
    }
    item {
        SectionCard(title = "Export and deletion") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = exportCsv) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(6.dp))
                    Text("CSV")
                }
                OutlinedButton(onClick = exportJson) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(6.dp))
                    Text("JSON")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::deleteLocalData) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Local")
                }
                OutlinedButton(onClick = viewModel::deleteCloudData) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Cloud")
                }
            }
        }
    }
}

@Composable
private fun Header(state: CareUiState) {
    Column {
        Text("Care", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "${state.deviceName}  |  ${if (state.monitoringEnabled) "Monitoring on" else "Monitoring off"}",
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
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = Color(0xFF5D6B70))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(if (granted) "Granted" else "Needs attention", color = if (granted) Color(0xFF2F6F73) else Color(0xFF9B3A2F))
        }
        Button(onClick = onOpen) { Text(if (granted) "Open" else "Grant") }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun EventList(title: String, events: List<ActivityEvent>) {
    SectionCard(title = title) {
        if (events.isEmpty()) {
            Text("No events recorded yet.")
        } else {
            events.forEach { event -> EventRow(event) }
        }
    }
}

@Composable
private fun EventRow(event: ActivityEvent) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(event.eventType.name, fontWeight = FontWeight.SemiBold)
            Text(event.packageName ?: event.details.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF5D6B70))
            event.details?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF7E8A8F)) }
        }
        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.timestamp)))
    }
}

private fun Screen.icon() = when (this) {
    Screen.Dashboard -> Icons.Default.Visibility
    Screen.Permissions -> Icons.Default.Security
    Screen.Device -> Icons.Default.Lock
    Screen.Logs -> Icons.Default.History
    Screen.Settings -> Icons.Default.Settings
}
