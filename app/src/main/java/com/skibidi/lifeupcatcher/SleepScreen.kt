package com.skibidi.lifeupcatcher

import android.Manifest
import android.app.Application
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.skibidi.lifeupcatcher.data.repository.SettingsRepository
import com.skibidi.lifeupcatcher.data.repository.SleepSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ProcessedSleepData(
    val totalSleepTime: Duration,
    val sessionDuration: Duration,
    val sessionStart: ZonedDateTime,
    val sessionEnd: ZonedDateTime
)

@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    val healthConnectClient: HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE) null
        else HealthConnectClient.getOrCreate(context)

    private val _availability = MutableStateFlow(checkAvailability())
    val availability: StateFlow<HealthConnectAvailability> = _availability

    val permissions = setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))
    val permissionsContract: ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    private val _sleepData = MutableStateFlow<List<ProcessedSleepData>>(emptyList())
    val sleepData: StateFlow<List<ProcessedSleepData>> = _sleepData

    val settings: StateFlow<SleepSettings> = settingsRepository.sleepSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SleepSettings())

    init {
        if (healthConnectClient != null) {
            checkPermissions()
        }
    }

    fun onPermissionsResult(grantedPermissions: Set<String>) {
        _permissionsGranted.value = grantedPermissions.containsAll(permissions)
        if (_permissionsGranted.value) {
            loadSleepData()
        }
    }

    private fun checkPermissions() {
        viewModelScope.launch {
            _permissionsGranted.value = hasPermissions(permissions)
            if (_permissionsGranted.value) {
                loadSleepData()
            }
        }
    }

    private suspend fun hasPermissions(permissions: Set<String>): Boolean {
        return healthConnectClient?.permissionController?.getGrantedPermissions()?.containsAll(permissions) ?: false
    }

    fun loadSleepData() {
        viewModelScope.launch {
            if (hasPermissions(permissions)) {
                try {
                    val end = ZonedDateTime.now()
                    val start = end.minusDays(1)
                    val response = healthConnectClient?.readRecords(
                        ReadRecordsRequest(
                            SleepSessionRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(start.toInstant(), end.toInstant())
                        )
                    )
                    _sleepData.value = response?.records?.map { processSleepSession(it) } ?: emptyList()
                } catch (e: Exception) {
                    Log.e("HealthConnectViewModel", "Error reading sleep data", e)
                }
            }
        }
    }

    fun updateSettings(newSettings: SleepSettings) {
        viewModelScope.launch {
            settingsRepository.updateSleepSettings(newSettings)
            if (newSettings.isServiceEnabled) {
                scheduleSleepCheckWorker(newSettings)
            } else {
                cancelSleepCheckWorker()
            }
        }
    }

    fun setServiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            val newSettings = current.copy(isServiceEnabled = enabled)
            settingsRepository.updateSleepSettings(newSettings)
            if (enabled) {
                scheduleSleepCheckWorker(newSettings)
            } else {
                cancelSleepCheckWorker()
            }
        }
    }

    private fun scheduleSleepCheckWorker(settings: SleepSettings) {
        val workManager = WorkManager.getInstance(context)
        val workRequestTag = "sleep-check-worker"

        val now = LocalDateTime.now()
        var nextCheck = now
            .withHour(settings.checkHour)
            .withMinute(settings.checkMinute)
            .withSecond(0)

        if (nextCheck.isBefore(now)) {
            nextCheck = nextCheck.plusDays(1)
        }

        val initialDelay = Duration.between(now, nextCheck).toMillis()

        Log.d("HealthConnectViewModel", "Scheduling worker. Next check: $nextCheck, Initial delay: $initialDelay ms")

        val sleepCheckWorkRequest = OneTimeWorkRequestBuilder<SleepCheckWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(workRequestTag)
            .build()

        workManager.enqueueUniqueWork(
            workRequestTag,
            ExistingWorkPolicy.REPLACE,
            sleepCheckWorkRequest
        )
    }

    private fun cancelSleepCheckWorker() {
        WorkManager.getInstance(context).cancelUniqueWork("sleep-check-worker")
    }

    private fun processSleepSession(session: SleepSessionRecord): ProcessedSleepData {
        val sessionDuration = Duration.between(session.startTime, session.endTime)
        var totalSleepTime = Duration.ZERO

        if (session.stages.isNotEmpty()) {
            session.stages.forEach { stage ->
                if (stage.stage != SleepSessionRecord.STAGE_TYPE_AWAKE) {
                    totalSleepTime = totalSleepTime.plus(Duration.between(stage.startTime, stage.endTime))
                }
            }
        } else {
            totalSleepTime = sessionDuration
        }

        return ProcessedSleepData(
            totalSleepTime = totalSleepTime,
            sessionDuration = sessionDuration,
            sessionStart = ZonedDateTime.ofInstant(session.startTime, session.startZoneOffset),
            sessionEnd = ZonedDateTime.ofInstant(session.endTime, session.endZoneOffset)
        )
    }

    private fun checkAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED, HealthConnectClient.SDK_UNAVAILABLE -> HealthConnectAvailability.NotInstalled
            else -> HealthConnectAvailability.NotSupported
        }
    }
}

@Composable
fun SleepScreen(
    viewModel: HealthConnectViewModel = hiltViewModel()
) {
    val availability by viewModel.availability.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val sleepData by viewModel.sleepData.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val permissionsLauncher = rememberLauncherForActivityResult(
        viewModel.permissionsContract
    ) { grantedPermissions ->
        viewModel.onPermissionsResult(grantedPermissions)
    }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (availability) {
            HealthConnectAvailability.Available -> {
                if (permissionsGranted) {
                    NotificationPermissionRequest()
                    Spacer(modifier = Modifier.height(16.dp))
                    SleepDataCard(sleepData)
                    Spacer(modifier = Modifier.height(16.dp))
                    SleepRewardSettings(
                        settings = settings,
                        onUpdate = viewModel::updateSettings,
                        onToggleService = viewModel::setServiceEnabled
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(onClick = { permissionsLauncher.launch(viewModel.permissions) }) {
                            Text("Request Health Connect Permissions")
                        }
                    }
                }
            }
            HealthConnectAvailability.NotInstalled -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Health Connect is not installed.")
                        Button(onClick = { openHealthConnectInstall(context) }) {
                            Text("Install Health Connect")
                        }
                    }
                }
            }
            HealthConnectAvailability.NotSupported -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Health Connect is not supported on this device.")
                }
            }
        }
    }
}

@Composable
fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val context = LocalContext.current
        var hasNotificationPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                hasNotificationPermission = isGranted
            }
        )

        if (!hasNotificationPermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Enable notifications to get alerts for your sleep rewards and punishments.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Enable Notifications")
                    }
                }
            }
        }
    }
}

@Composable
fun SleepRewardSettings(
    settings: SleepSettings,
    onUpdate: (SleepSettings) -> Unit,
    onToggleService: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hour: Int, minute: Int ->
                onUpdate(settings.copy(checkHour = hour, checkMinute = minute))
            },
            settings.checkHour,
            settings.checkMinute,
            true
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Sleep Reward Settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Sleep Service", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.isServiceEnabled,
                    onCheckedChange = onToggleService
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Check Time: ${formatTime(settings.checkHour, settings.checkMinute)}", modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        timePickerDialog.updateTime(settings.checkHour, settings.checkMinute)
                        timePickerDialog.show()
                    },
                    enabled = settings.isServiceEnabled
                ) {
                    Text("Change")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = settings.thresholdHours.toString(),
                    onValueChange = { 
                        val valStr = it.filter { char -> char.isDigit() }
                        onUpdate(settings.copy(thresholdHours = valStr.toIntOrNull() ?: 0))
                    },
                    label = { Text("Threshold Hours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = settings.isServiceEnabled
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = settings.thresholdMinutes.toString(),
                    onValueChange = { 
                        val valStr = it.filter { char -> char.isDigit() }
                        onUpdate(settings.copy(thresholdMinutes = valStr.toIntOrNull() ?: 0))
                    },
                    label = { Text("Threshold Minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = settings.isServiceEnabled
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = settings.rewardAmount.toString(),
                    onValueChange = { 
                        val valStr = it.filter { char -> char.isDigit() }
                        onUpdate(settings.copy(rewardAmount = valStr.toIntOrNull() ?: 0))
                    },
                    label = { Text("Reward") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = settings.isServiceEnabled
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = settings.punishmentAmount.toString(),
                    onValueChange = { 
                        val valStr = it.filter { char -> char.isDigit() }
                        onUpdate(settings.copy(punishmentAmount = valStr.toIntOrNull() ?: 0))
                    },
                    label = { Text("Punishment") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = settings.isServiceEnabled
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = settings.successTitle,
                onValueChange = { onUpdate(settings.copy(successTitle = it)) },
                label = { Text("Success Title") },
                modifier = Modifier.fillMaxWidth(),
                enabled = settings.isServiceEnabled
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = settings.successMessage,
                onValueChange = { onUpdate(settings.copy(successMessage = it)) },
                label = { Text("Success Message") },
                modifier = Modifier.fillMaxWidth(),
                enabled = settings.isServiceEnabled
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = settings.failureTitle,
                onValueChange = { onUpdate(settings.copy(failureTitle = it)) },
                label = { Text("Failure Title") },
                modifier = Modifier.fillMaxWidth(),
                enabled = settings.isServiceEnabled
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = settings.failureMessage,
                onValueChange = { onUpdate(settings.copy(failureMessage = it)) },
                label = { Text("Failure Message") },
                modifier = Modifier.fillMaxWidth(),
                enabled = settings.isServiceEnabled
            )
        }
    }
}

@Composable
fun SleepDataCard(sleepSessions: List<ProcessedSleepData>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Your Sleep Analysis", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            if (sleepSessions.isEmpty()) {
                Text("No sleep data available for the last 24 hours.")
            } else {
                sleepSessions.forEach { session ->
                    val duration = session.totalSleepTime
                    Text("Slept for ${duration.toHours()} hours and ${duration.toMinutes() % 60} minutes")
                }
            }
        }
    }
}

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

enum class HealthConnectAvailability {
    Available, NotInstalled, NotSupported
}
