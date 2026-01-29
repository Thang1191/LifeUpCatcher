package com.skibidi.lifeupcatcher

import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var installedLaunchers by remember { mutableStateOf(emptyList<String>()) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        installedLaunchers = resolveInfoList.map { it.activityInfo.packageName }
    }


    Column(modifier = Modifier.padding(16.dp)) {
        // Enable/Disable Service
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Enable Launcher Service")
            Switch(
                checked = uiState.isServiceEnabled,
                onCheckedChange = { viewModel.setServiceEnabled(it) },
                enabled = uiState.isShizukuAvailable
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Launcher Selection
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Launcher Selection")
                Spacer(modifier = Modifier.height(8.dp))
                LauncherDropdown("Main Launcher", installedLaunchers, uiState.mainLauncher) { viewModel.setMainLauncher(it) }
                Spacer(modifier = Modifier.height(8.dp))
                LauncherDropdown("Focus Launcher", installedLaunchers, uiState.focusLauncher) { viewModel.setFocusLauncher(it) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Time Window
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Time Window")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimePicker(label = "Start Time", selectedTime = uiState.startTime) { viewModel.setStartTime(it) }
                    Text("-")
                    TimePicker(label = "End Time", selectedTime = uiState.endTime) { viewModel.setEndTime(it) }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weekday Selection
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Active Days")
                WeekdaySelector(selectedDays = uiState.weekdays) { viewModel.setWeekdays(it) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherDropdown(
    label: String,
    launchers: List<String>,
    selectedLauncher: String,
    onLauncherSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            value = selectedLauncher,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            launchers.forEach { launcher ->
                DropdownMenuItem(
                    text = { Text(launcher) },
                    onClick = {
                        onLauncherSelected(launcher)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun TimePicker(label: String, selectedTime: String, onTimeSelected: (String) -> Unit) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val (hour, minute) = selectedTime.split(':').map { it.toInt() }

    val timePickerDialog = TimePickerDialog(
        context,
        { _, selectedHour, selectedMinute ->
            onTimeSelected(String.format("%02d:%02d", selectedHour, selectedMinute))
        }, hour, minute, true
    )

    Button(onClick = { timePickerDialog.show() }) {
        Text("$label: $selectedTime")
    }
}

@Composable
fun WeekdaySelector(selectedDays: List<Boolean>, onSelectionChange: (List<Boolean>) -> Unit) {
    val weekdays = listOf("S", "M", "T", "W", "T", "F", "S")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        weekdays.forEachIndexed { index, day ->
            val isSelected = selectedDays[index]
            Button(
                onClick = {
                    val newSelection = selectedDays.toMutableList()
                    newSelection[index] = !newSelection[index]
                    onSelectionChange(newSelection)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                )
            ) {
                Text(
                    day,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}