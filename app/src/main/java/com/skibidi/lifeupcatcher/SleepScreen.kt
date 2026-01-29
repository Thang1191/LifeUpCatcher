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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

data class ProcessedSleepData(
    val totalSleepTime: Duration,
    val sessionDuration: Duration,
    val sessionStart: ZonedDateTime,
    val sessionEnd: ZonedDateTime
)

@Composable
fun SleepScreen() {
    val viewModel: HealthConnectViewModel = viewModel(
        factory = HealthConnectViewModelFactory(LocalContext.current.applicationContext as Application)
    )

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
            .verticalScroll(rememberScrollState()), // Make screen scrollable
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (availability) {
            HealthConnectAvailability.Available -> {
                if (permissionsGranted) {
                    NotificationPermissionRequest()
                    Spacer(modifier = Modifier.height(16.dp))
                    SleepDataCard(sleepData)
                    Spacer(modifier = Modifier.height(16.dp))
                    SleepRewardSettings(settings, onSave = viewModel::saveSettings)
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
    onSave: (SleepSettings) -> Unit
) {
    var isEnabled by remember(settings) { mutableStateOf(settings.isServiceEnabled) }
    var checkHour by remember(settings) { mutableStateOf(settings.checkHour) }
    var checkMinute by remember(settings) { mutableStateOf(settings.checkMinute) }
    var thresholdHours by remember(settings) { mutableStateOf(settings.thresholdHours.toString()) }
    var thresholdMinutes by remember(settings) { mutableStateOf(settings.thresholdMinutes.toString()) }
    var rewardAmount by remember(settings) { mutableStateOf(settings.rewardAmount.toString()) }
    var punishmentAmount by remember(settings) { mutableStateOf(settings.punishmentAmount.toString()) }
    var successTitle by remember(settings) { mutableStateOf(settings.successTitle) }
    var successMessage by remember(settings) { mutableStateOf(settings.successMessage) }
    var failureTitle by remember(settings) { mutableStateOf(settings.failureTitle) }
    var failureMessage by remember(settings) { mutableStateOf(settings.failureMessage) }

    val context = LocalContext.current
    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hour: Int, minute: Int ->
                checkHour = hour
                checkMinute = minute
            },
            checkHour,
            checkMinute,
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
                    checked = isEnabled,
                    onCheckedChange = { isEnabled = it }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Check Time: ${formatTime(checkHour, checkMinute)}", modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        timePickerDialog.updateTime(checkHour, checkMinute)
                        timePickerDialog.show()
                    },
                    enabled = isEnabled
                ) {
                    Text("Change")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = thresholdHours,
                    onValueChange = { thresholdHours = it.filter { char -> char.isDigit() } },
                    label = { Text("Threshold Hours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = isEnabled
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = thresholdMinutes,
                    onValueChange = { thresholdMinutes = it.filter { char -> char.isDigit() } },
                    label = { Text("Threshold Minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = isEnabled
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = rewardAmount,
                    onValueChange = { rewardAmount = it.filter { char -> char.isDigit() } },
                    label = { Text("Reward") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = isEnabled
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = punishmentAmount,
                    onValueChange = { punishmentAmount = it.filter { char -> char.isDigit() } },
                    label = { Text("Punishment") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = isEnabled
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = successTitle,
                onValueChange = { successTitle = it },
                label = { Text("Success Title") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isEnabled
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = successMessage,
                onValueChange = { successMessage = it },
                label = { Text("Success Message") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isEnabled
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = failureTitle,
                onValueChange = { failureTitle = it },
                label = { Text("Failure Title") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isEnabled
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = failureMessage,
                onValueChange = { failureMessage = it },
                label = { Text("Failure Message") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isEnabled
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                val newSettings = SleepSettings(
                    isServiceEnabled = isEnabled,
                    checkHour = checkHour,
                    checkMinute = checkMinute,
                    thresholdHours = thresholdHours.toIntOrNull() ?: 8,
                    thresholdMinutes = thresholdMinutes.toIntOrNull() ?: 0,
                    rewardAmount = rewardAmount.toIntOrNull() ?: 10,
                    punishmentAmount = punishmentAmount.toIntOrNull() ?: 5,
                    successTitle = successTitle,
                    successMessage = successMessage,
                    failureTitle = failureTitle,
                    failureMessage = failureMessage
                )
                Log.d("SleepRewardSettings", "Save button clicked with settings: $newSettings")
                onSave(newSettings)
            }) {
                Text("Save Settings")
            }
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

class HealthConnectViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val settingsRepository = SleepSettingsRepository(context)

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

    fun saveSettings(newSettings: SleepSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(newSettings)
            if (newSettings.isServiceEnabled) {
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
        Log.d("HealthConnectViewModel", "Work request enqueued with tag: $workRequestTag")
    }

    private fun cancelSleepCheckWorker() {
        val workManager = WorkManager.getInstance(context)
        val workRequestTag = "sleep-check-worker"
        workManager.cancelUniqueWork(workRequestTag)
        Log.d("HealthConnectViewModel", "Cancelled work request with tag: $workRequestTag")
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

class HealthConnectViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HealthConnectViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HealthConnectViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
