package com.care.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.care.data.CarePreferences
import com.care.data.ServiceLocator
import com.care.util.PermissionState
import com.care.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CareUiState(
    val monitoringEnabled: Boolean = false,
    val deviceId: String = "",
    val deviceName: String = "",
    val permissionState: PermissionState? = null,
    val pendingSyncCount: Int = 0,
    val initialPermissionFlowCompleted: Boolean = false,
    val statusMessage: String? = null
)

class CareViewModel(
    private val appContext: Context,
    private val prefs: CarePreferences = ServiceLocator.prefs
) : ViewModel() {
    private val transientState = MutableStateFlow(CareUiState())
    private val pendingSyncCount = ServiceLocator.eventRepository.pendingSyncCount()

    val uiState: StateFlow<CareUiState> = combine(transientState, pendingSyncCount) { state, pending ->
        state.copy(
            monitoringEnabled = prefs.monitoringEnabled,
            deviceId = prefs.deviceId,
            deviceName = prefs.deviceName,
            permissionState = PermissionUtils.state(appContext),
            pendingSyncCount = pending,
            initialPermissionFlowCompleted = prefs.initialPermissionFlowCompleted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareUiState())

    init {
        publishPermissionState(PermissionUtils.state(appContext))
    }

    fun refreshPermissions() {
        val permissionState = PermissionUtils.state(appContext)
        transientState.value = transientState.value.copy(permissionState = permissionState)
        publishPermissionState(permissionState)
    }

    fun completeInitialPermissionFlow() {
        prefs.initialPermissionFlowCompleted = true
        refreshPermissions()
    }

    fun ensureMonitoringActive() {
        if (!PermissionUtils.state(appContext).canStartMonitoring) return
        ServiceLocator.monitorController.start()
        transientState.value = transientState.value.copy(
            monitoringEnabled = true,
            statusMessage = "Monitoring is active and uploads are automatic."
        )
    }

    private fun publishPermissionState(permissionState: PermissionState) {
        viewModelScope.launch {
            val result = ServiceLocator.firebaseSyncRepository.reportPermissionState(permissionState)
            result.exceptionOrNull()?.let { error ->
                transientState.value = transientState.value.copy(
                    statusMessage = error.message ?: "Cloud upload could not be initialized."
                )
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CareViewModel(context.applicationContext) as T
        }
    }
}
