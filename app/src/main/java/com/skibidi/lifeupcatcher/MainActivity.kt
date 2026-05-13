package com.skibidi.lifeupcatcher

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.skibidi.lifeupcatcher.data.repository.DnsRepository
import com.skibidi.lifeupcatcher.data.repository.SettingsRepository
import com.skibidi.lifeupcatcher.ui.theme.LifeUpCatcherTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appPickerViewModel: AppPickerViewModel by viewModels()
    private val launcherViewModel: LauncherViewModel by viewModels()
    private val dnsViewModel: DnsViewModel by viewModels()
    private val lockViewModel: LockViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var dnsRepository: DnsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            combine(
                settingsRepository.isMonitoringEnabled,
                dnsRepository.isDnsLockingEnabled
            ) { monitoring, dns -> monitoring || dns }.collect { shouldRun ->
                val intent = Intent(this@MainActivity, MainService::class.java)
                if (shouldRun) {
                    startForegroundService(intent)
                } else {
                    stopService(intent)
                }
            }
        }

        lifecycleScope.launch {
            lockViewModel.isLocked.collect { locked ->
                if (locked) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            LifeUpCatcherTheme {
                LockOverlay(viewModel = lockViewModel) {
                    MainScreen(appPickerViewModel, launcherViewModel, dnsViewModel, lockViewModel)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // If focus is lost during a challenge, reset it to prevent Lens/OCR copy-paste
        if (!hasFocus && lockViewModel.isChallengeActive.value) {
            lockViewModel.resetChallenge()
        }
    }

    override fun onStop() {
        super.onStop()
        lockViewModel.onAppBackgrounded()
    }
}

@Composable
fun MainScreen(
    appPickerViewModel: AppPickerViewModel,
    launcherViewModel: LauncherViewModel,
    dnsViewModel: DnsViewModel,
    lockViewModel: LockViewModel
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var showLockConfig by rememberSaveable { mutableStateOf(false) }
    val lockSettings by lockViewModel.lockSettings.collectAsState()

    if (showLockConfig) {
        var charCountText by rememberSaveable { mutableStateOf(lockSettings.charCount.toString()) }
        AlertDialog(
            onDismissRequest = { showLockConfig = false },
            title = { Text("Random Lock Challenge") },
            text = {
                Column {
                    Text("Enter the number of random characters for the challenge:")
                    OutlinedTextField(
                        value = charCountText,
                        onValueChange = { charCountText = it.filter { c -> c.isDigit() } },
                        label = { Text("Characters count") },
                        modifier = Modifier.padding(top = 8.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val count = charCountText.toIntOrNull() ?: 10
                        lockViewModel.enableLock(count)
                        showLockConfig = false
                    }
                ) {
                    Text("Enable & Lock")
                }
            },
            dismissButton = {
                if (lockSettings.isEnabled) {
                    TextButton(
                        onClick = {
                            lockViewModel.disableLock()
                            showLockConfig = false
                        }
                    ) {
                        Text("Disable")
                    }
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showLockConfig = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Configure Lock")
            }
        },
        floatingActionButtonPosition = FabPosition.Start,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedIndex == 0,
                    onClick = { selectedIndex = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Apps") },
                    label = { Text("Apps") }
                )
                NavigationBarItem(
                    selected = selectedIndex == 1,
                    onClick = { selectedIndex = 1 },
                    icon = { Icon(Icons.Filled.Info, contentDescription = "Activity") },
                    label = { Text("Activity") }
                )
                NavigationBarItem(
                    selected = selectedIndex == 2,
                    onClick = { selectedIndex = 2 },
                    icon = { Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = "Launcher") },
                    label = { Text("Launcher") }
                )
                NavigationBarItem(
                    selected = selectedIndex == 3,
                    onClick = { selectedIndex = 3 },
                    icon = { Icon(Icons.Filled.Dns, contentDescription = "DNS") },
                    label = { Text("DNS") }
                )
                NavigationBarItem(
                    selected = selectedIndex == 4,
                    onClick = { selectedIndex = 4 },
                    icon = { Icon(Icons.Filled.Bedtime, contentDescription = "Sleep") },
                    label = { Text("Sleep") }
                )
                NavigationBarItem(
                    selected = selectedIndex == 5,
                    onClick = { selectedIndex = 5 },
                    icon = { Icon(Icons.Filled.Build, contentDescription = "Debug") },
                    label = { Text("Debug") }
                )
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            when (selectedIndex) {
                0 -> AppPickerScreen(viewModel = appPickerViewModel)
                1 -> ActivityScreen()
                2 -> LauncherScreen(viewModel = launcherViewModel)
                3 -> DnsScreen(viewModel = dnsViewModel)
                4 -> SleepScreen()
                5 -> DebugScreen()
            }
        }
    }
}
