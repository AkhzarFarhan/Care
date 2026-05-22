package com.care.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.care.data.ActivityEvent
import com.care.data.CarePreferences
import com.care.data.EventType
import com.care.data.ServiceLocator
import com.care.firebase.SyncResult
import com.care.util.PermissionState
import com.care.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class DashboardStats(
    val todayEvents: Int = 0,
    val urlCount: Int = 0,
    val unlockCount: Int = 0,
    val appUsageSeconds: Long = 0,
    val topApps: List<Pair<String, Long>> = emptyList()
)

data class CareUiState(
    val monitoringEnabled: Boolean = false,
    val firebaseSyncEnabled: Boolean = true,
    val browserTrackingEnabled: Boolean = true,
    val pollingIntervalSeconds: Long = 10,
    val deviceName: String = "",
    val parentUserId: String? = null,
    val permissionState: PermissionState? = null,
    val recentEvents: List<ActivityEvent> = emptyList(),
    val dashboardStats: DashboardStats = DashboardStats(),
    val pendingSyncCount: Int = 0,
    val selectedEventType: EventType? = null,
    val statusMessage: String? = null,
    val busy: Boolean = false
)

class CareViewModel(
    private val appContext: Context,
    private val prefs: CarePreferences = ServiceLocator.prefs
) : ViewModel() {
    private val transientState = MutableStateFlow(CareUiState())
    private val todayEvents = ServiceLocator.eventRepository.todayEvents()
    private val recentEvents = ServiceLocator.eventRepository.recentEvents()
    private val pendingSyncCount = ServiceLocator.eventRepository.pendingSyncCount()

    val uiState: StateFlow<CareUiState> = combine(
        transientState,
        recentEvents,
        todayEvents,
        pendingSyncCount
    ) { state, recent, today, pending ->
        state.copy(
            monitoringEnabled = prefs.monitoringEnabled,
            firebaseSyncEnabled = prefs.firebaseSyncEnabled,
            browserTrackingEnabled = prefs.browserTrackingEnabled,
            pollingIntervalSeconds = prefs.pollingIntervalSeconds,
            deviceName = prefs.deviceName,
            parentUserId = ServiceLocator.firebaseSyncRepository.currentUserId ?: prefs.parentUserId,
            permissionState = PermissionUtils.state(appContext),
            recentEvents = recent.filter { state.selectedEventType == null || it.eventType == state.selectedEventType },
            dashboardStats = today.toDashboardStats(),
            pendingSyncCount = pending
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareUiState())

    fun refreshPermissions() {
        transientState.value = transientState.value.copy(permissionState = PermissionUtils.state(appContext))
    }

    fun startMonitoring() {
        ServiceLocator.monitorController.start()
        transientState.value = transientState.value.copy(statusMessage = "Monitoring started.")
    }

    fun stopMonitoring() {
        ServiceLocator.monitorController.stop()
        transientState.value = transientState.value.copy(statusMessage = "Monitoring stopped.")
    }

    fun setFirebaseSyncEnabled(enabled: Boolean) {
        prefs.firebaseSyncEnabled = enabled
        transientState.value = transientState.value.copy(firebaseSyncEnabled = enabled)
    }

    fun setBrowserTrackingEnabled(enabled: Boolean) {
        prefs.browserTrackingEnabled = enabled
        transientState.value = transientState.value.copy(browserTrackingEnabled = enabled)
    }

    fun setPollingInterval(seconds: Long) {
        prefs.pollingIntervalSeconds = seconds
        transientState.value = transientState.value.copy(pollingIntervalSeconds = prefs.pollingIntervalSeconds)
    }

    fun setDeviceName(name: String) {
        prefs.deviceName = name
        transientState.value = transientState.value.copy(deviceName = prefs.deviceName)
    }

    fun filterEventType(type: EventType?) {
        transientState.value = transientState.value.copy(selectedEventType = type)
    }

    fun signIn(email: String, password: String, createAccount: Boolean) {
        viewModelScope.launch {
            transientState.value = transientState.value.copy(busy = true, statusMessage = null)
            val result = if (createAccount) {
                ServiceLocator.firebaseSyncRepository.createAccount(email, password)
            } else {
                ServiceLocator.firebaseSyncRepository.signIn(email, password)
            }
            transientState.value = transientState.value.copy(
                busy = false,
                statusMessage = result.fold(
                    onSuccess = { "Signed in and registered this device." },
                    onFailure = { it.message ?: "Firebase sign-in failed." }
                )
            )
        }
    }

    fun signOut() {
        ServiceLocator.firebaseSyncRepository.signOut()
        transientState.value = transientState.value.copy(statusMessage = "Signed out.")
    }

    fun syncNow() {
        viewModelScope.launch {
            transientState.value = transientState.value.copy(busy = true)
            val result = runCatching { ServiceLocator.firebaseSyncRepository.syncPending(limit = 500) }
                .getOrElse { SyncResult(message = it.message ?: "Sync failed.") }
            transientState.value = transientState.value.copy(busy = false, statusMessage = result.message)
        }
    }

    fun deleteLocalData() {
        viewModelScope.launch {
            ServiceLocator.eventRepository.deleteAll()
            transientState.value = transientState.value.copy(statusMessage = "Local activity data deleted.")
        }
    }

    fun deleteCloudData() {
        viewModelScope.launch {
            transientState.value = transientState.value.copy(busy = true)
            val message = ServiceLocator.firebaseSyncRepository.deleteCloudDataForDevice().fold(
                onSuccess = { "Cloud data for this device deleted." },
                onFailure = { it.message ?: "Could not delete cloud data." }
            )
            transientState.value = transientState.value.copy(busy = false, statusMessage = message)
        }
    }

    fun export(context: Context, uri: Uri, json: Boolean) {
        viewModelScope.launch {
            val from = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis
            val to = System.currentTimeMillis()
            val content = if (json) {
                ServiceLocator.eventRepository.exportJson(from, to)
            } else {
                ServiceLocator.eventRepository.exportCsv(from, to)
            }
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray())
            }
            transientState.value = transientState.value.copy(statusMessage = "Export completed.")
        }
    }

    private fun List<ActivityEvent>.toDashboardStats(): DashboardStats {
        val appDurations = filter { it.eventType == EventType.APP_USAGE }
            .groupBy { it.packageName ?: "Unknown" }
            .mapValues { entry ->
                entry.value.sumOf { ((it.endTimestamp ?: it.timestamp) - (it.startTimestamp ?: it.timestamp)).coerceAtLeast(0) / 1000 }
            }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        return DashboardStats(
            todayEvents = size,
            urlCount = count { it.eventType == EventType.URL },
            unlockCount = count { it.eventType == EventType.UNLOCK },
            appUsageSeconds = appDurations.sumOf { it.second },
            topApps = appDurations
        )
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CareViewModel(context.applicationContext) as T
        }
    }
}
