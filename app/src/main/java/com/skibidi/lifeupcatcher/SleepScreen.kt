package com.skibidi.lifeupcatcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Data class to hold the processed sleep data for display.
 * @param totalSleepTime The calculated duration of actual sleep (excluding awake time).
 * @param sessionDuration The total duration of the sleep session (time in bed).
 * @param sessionStart The start time of the sleep session.
 * @param sessionEnd The end time of the sleep session.
 */
data class ProcessedSleepData(
    val totalSleepTime: Duration,
    val sessionDuration: Duration,
    val sessionStart: ZonedDateTime,
    val sessionEnd: ZonedDateTime
)

/**
 * The main composable for the Sleep tab.
 *
 * This screen is responsible for:
 * - Checking the availability of the Health Connect client on the device.
 * - Managing the user flow for requesting Health Connect permissions.
 * - Displaying the user's sleep data once permissions are granted.
 * - Handling cases where Health Connect is not installed or not supported.
 */
@Composable
fun SleepScreen() {
    val viewModel: HealthConnectViewModel = viewModel(
        factory = HealthConnectViewModelFactory(LocalContext.current.applicationContext as Application)
    )

    // Collect states from the ViewModel to drive the UI.
    val availability by viewModel.availability.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val sleepData by viewModel.sleepData.collectAsState()

    // Create a launcher to handle the Health Connect permission request flow.
    val permissionsLauncher = rememberLauncherForActivityResult(
        viewModel.permissionsContract
    ) { grantedPermissions ->
        viewModel.onPermissionsResult(grantedPermissions)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val context = LocalContext.current
        // Display different UI based on Health Connect's availability.
        when (availability) {
            HealthConnectAvailability.Available -> {
                if (permissionsGranted) {
                    // If permissions are granted, show sleep data or a "no data" message.
                    if (sleepData.isEmpty()) {
                        Text("No sleep data available for the last 24 hours.")
                    } else {
                        SleepDataCard(sleepData)
                    }
                } else {
                    // If permissions are not granted, show a button to request them.
                    Button(
                        onClick = {
                            Log.d("SleepScreen", "Requesting permissions: ${viewModel.permissions}")
                            permissionsLauncher.launch(viewModel.permissions)
                        }
                    ) {
                        Text("Request Health Connect Permissions")
                    }
                }
            }
            HealthConnectAvailability.NotInstalled -> {
                // Prompt the user to install Health Connect.
                Text("Health Connect is not installed.")
                Button(onClick = { openHealthConnectInstall(context) }) {
                    Text("Install Health Connect")
                }
            }
            HealthConnectAvailability.NotSupported -> {
                // Inform the user that their device doesn't support Health Connect.
                Text("Health Connect is not supported on this device.")
            }
        }
    }
}

/**
 * A composable that displays the processed sleep data in a styled card.
 * @param sleepSessions A list of processed sleep data objects to display.
 */
@Composable
fun SleepDataCard(sleepSessions: List<ProcessedSleepData>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Your Sleep Analysis", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            sleepSessions.forEach { session ->
                val duration = session.totalSleepTime
                Text("Slept for ${duration.toHours()} hours and ${duration.toMinutes() % 60} minutes")
            }
        }
    }
}

/**
 * Opens the Google Play Store page for the Health Connect app.
 */
private fun openHealthConnectInstall(context: Context) {
    val uriString = "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = uriString.toUri()
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
        }
    )
}

/**
 * Represents the availability status of the Health Connect client.
 */
enum class HealthConnectAvailability {
    Available,
    NotInstalled,
    NotSupported
}

/**
 * ViewModel responsible for all interactions with the Health Connect API.
 * It uses [AndroidViewModel] to safely access the application context.
 */
class HealthConnectViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context
        get() = getApplication<Application>().applicationContext

    // The Health Connect client, which is the main entry point to the API.
    // It's null if the SDK is not available on the device.
    val healthConnectClient: HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE) {
            null
        } else {
            HealthConnectClient.getOrCreate(context)
        }

    // A state flow to hold the current availability status of Health Connect.
    private val _availability = MutableStateFlow(checkAvailability())
    val availability: StateFlow<HealthConnectAvailability> = _availability

    // The set of permissions the app requires. In this case, only reading sleep sessions.
    val permissions = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    // The contract used to launch the permission request to Health Connect.
    val permissionsContract: ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    // A state flow to track whether the required permissions have been granted.
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    // A state flow to hold the list of processed sleep data.
    private val _sleepData = MutableStateFlow<List<ProcessedSleepData>>(emptyList())
    val sleepData: StateFlow<List<ProcessedSleepData>> = _sleepData

    init {
        // When the ViewModel is created, check for permissions immediately if the client is available.
        if (healthConnectClient != null) {
            checkPermissions()
        }
    }

    /**
     * Callback function for the permission launcher. Updates the permission status
     * and loads data if permissions were successfully granted.
     */
    fun onPermissionsResult(grantedPermissions: Set<String>) {
        _permissionsGranted.value = grantedPermissions.containsAll(permissions)
        if (_permissionsGranted.value) {
            loadSleepData()
        }
    }

    /**
     * Checks if the app currently has the required permissions.
     */
    private fun checkPermissions() {
        viewModelScope.launch {
            _permissionsGranted.value = hasPermissions(permissions)
            if (_permissionsGranted.value) {
                loadSleepData()
            }
        }
    }

    /**
     * A suspend function to query the Health Connect client for the currently granted permissions.
     */
    private suspend fun hasPermissions(permissions: Set<String>): Boolean {
        return healthConnectClient?.permissionController?.getGrantedPermissions()?.containsAll(permissions) ?: false
    }

    /**
     * Fetches sleep data for the last 24 hours from Health Connect.
     */
    fun loadSleepData() {
        viewModelScope.launch {
            if (hasPermissions(permissions)) {
                val end = ZonedDateTime.now()
                val start = end.minusDays(1)
                try {
                    // Request sleep session records from the API.
                    val response = healthConnectClient?.readRecords(
                        ReadRecordsRequest(
                            SleepSessionRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(start.toInstant(), end.toInstant())
                        )
                    )
                    // Process each session record to calculate accurate sleep time and update the state.
                    _sleepData.value = response?.records?.map { processSleepSession(it) } ?: emptyList()
                } catch (e: Exception) {
                    Log.e("HealthConnectViewModel", "Error reading sleep data", e)
                }
            }
        }
    }

    /**
     * Processes a single [SleepSessionRecord] to calculate the actual sleep time.
     * It does this by summing the durations of all sleep stages that are not 'Awake'.
     * If no stage data is available, it falls back to the total session duration.
     *
     * @param session The raw sleep session record from Health Connect.
     * @return A [ProcessedSleepData] object with calculated durations.
     */
    private fun processSleepSession(session: SleepSessionRecord): ProcessedSleepData {
        Log.d("HealthConnectViewModel", "Processing session: $session")
        val sessionDuration = Duration.between(session.startTime, session.endTime)
        var totalSleepTime = Duration.ZERO

        if (session.stages.isNotEmpty()) {
            session.stages.forEach { stage ->
                // We only count stages that are not "awake" as actual sleep time.
                if (stage.stage != SleepSessionRecord.STAGE_TYPE_AWAKE) {
                    val stageDuration = Duration.between(stage.startTime, stage.endTime)
                    totalSleepTime = totalSleepTime.plus(stageDuration)
                }
                Log.d("HealthConnectViewModel", "  - Stage: ${stage.stage}, Start: ${stage.startTime}, End: ${stage.endTime}, Duration: ${Duration.between(stage.startTime, stage.endTime).toMinutes()} mins")
            }
        } else {
            // Fallback to total session duration if no stage data is available.
            totalSleepTime = sessionDuration
        }

        val processedData = ProcessedSleepData(
            totalSleepTime = totalSleepTime,
            sessionDuration = sessionDuration,
            sessionStart = ZonedDateTime.ofInstant(session.startTime, session.startZoneOffset),
            sessionEnd = ZonedDateTime.ofInstant(session.endTime, session.endZoneOffset)
        )
        Log.d("HealthConnectViewModel", "Processed data: Total Sleep: ${totalSleepTime.toMinutes()} mins, Total Session: ${sessionDuration.toMinutes()} mins")
        return processedData
    }


    /**
     * Determines the current availability status of the Health Connect client.
     */
    private fun checkAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED, HealthConnectClient.SDK_UNAVAILABLE -> HealthConnectAvailability.NotInstalled
            else -> HealthConnectAvailability.NotSupported
        }
    }
}

/**
 * Factory for creating the [HealthConnectViewModel].
 */
class HealthConnectViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HealthConnectViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HealthConnectViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}