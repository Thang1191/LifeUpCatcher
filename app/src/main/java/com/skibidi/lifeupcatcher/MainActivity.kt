package com.skibidi.lifeupcatcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.skibidi.lifeupcatcher.ui.theme.LifeUpCatcherTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appPickerViewModel: AppPickerViewModel by viewModels()
    private val launcherViewModel: LauncherViewModel by viewModels()
    private val dnsViewModel: DnsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LifeUpCatcherTheme {
                MainScreen(appPickerViewModel, launcherViewModel, dnsViewModel)
            }
        }
    }
}

@Composable
fun MainScreen(
    appPickerViewModel: AppPickerViewModel,
    launcherViewModel: LauncherViewModel,
    dnsViewModel: DnsViewModel
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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