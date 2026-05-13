package com.skibidi.lifeupcatcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.skibidi.lifeupcatcher.data.local.entity.MonitoredItemEntity
import rikka.shizuku.Shizuku

@Composable
fun ShopItemsScreen(
    viewModel: ShopItemsViewModel = hiltViewModel(),
    isAccessibilityPermissionGranted: Boolean
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsState()
    val isMonitoringEnabled by viewModel.isMonitoringEnabled.collectAsState()
    val isShizukuEnabled by viewModel.isShizukuEnabled.collectAsState()
    val shizukuState by viewModel.shizukuState.collectAsState()

    val appPickerViewModel: AppPickerViewModel = hiltViewModel()
    val groups by appPickerViewModel.groups.collectAsState()

    val showAddDialog = remember { mutableStateOf(false) }
    val editingItem = remember { mutableStateOf<MonitoredItemEntity?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.setMonitoringEnabled(true)
        requestBackgroundPermission(context)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog.value = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (!isAccessibilityPermissionGranted) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Accessibility Permission Required",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Tap here to enable it for app blocking.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Monitoring Service",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val shizukuActive = isShizukuEnabled && shizukuState.isAvailable && shizukuState.isPermissionGranted
                        val accessibilityActive = isAccessibilityPermissionGranted

                        val statusText = when {
                            !isMonitoringEnabled -> "Stopped"
                            shizukuActive && accessibilityActive -> "Running (Full Protection)"
                            shizukuActive -> "Running (Shizuku blocks only)"
                            accessibilityActive -> "Running (Accessibility blocks only)"
                            else -> "Permission Required"
                        }
                        val statusColor = when {
                            !isMonitoringEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                            statusText == "Permission Required" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                    }
                    Switch(
                        checked = isMonitoringEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.setMonitoringEnabled(true)
                                        requestBackgroundPermission(context)
                                    } else {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    viewModel.setMonitoringEnabled(true)
                                    requestBackgroundPermission(context)
                                }
                            } else {
                                viewModel.setMonitoringEnabled(false)
                            }
                        }
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shizuku Integration",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val shizukuStatusText = when {
                            !isShizukuEnabled -> "Disabled"
                            !shizukuState.isAvailable -> "Shizuku Not Running"
                            !shizukuState.isPermissionGranted -> "Permission Required"
                            else -> "Enabled"
                        }
                        val shizukuStatusColor = when {
                            !isShizukuEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                            shizukuStatusText == "Enabled" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                        Text(
                            text = shizukuStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = shizukuStatusColor
                        )
                    }
                    Switch(
                        checked = isShizukuEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                try {
                                    if (shizukuState.isAvailable) {
                                        if (shizukuState.isPermissionGranted) {
                                            viewModel.setShizukuEnabled(true)
                                        } else {
                                            Shizuku.requestPermission(1001)
                                        }
                                    } else {
                                        Toast.makeText(context, "Shizuku is not running", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Shizuku error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                viewModel.setShizukuEnabled(false)
                            }
                        }
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    ShopItemRow(
                        item = item,
                        groups = groups,
                        onEdit = { editingItem.value = item },
                        onDelete = { viewModel.removeItem(item.name) }
                    )
                }
            }
        }
    }

    if (showAddDialog.value) {
        ItemDialog(
            groups = groups,
            isShizukuEnabled = isShizukuEnabled,
            onDismiss = { showAddDialog.value = false },
            onConfirm = { name, groupId, startMsg, stopMsg, quitMsg, technique, weekdayLimit ->
                if (name.isNotBlank()) {
                    viewModel.addItem(name.trim(), groupId, startMsg, stopMsg, quitMsg, technique, weekdayLimit)
                }
                showAddDialog.value = false
            }
        )
    }

    if (editingItem.value != null) {
        ItemDialog(
            groups = groups,
            item = editingItem.value,
            isShizukuEnabled = isShizukuEnabled,
            onDismiss = { editingItem.value = null },
            onConfirm = { name, groupId, startMsg, stopMsg, quitMsg, technique, weekdayLimit ->
                viewModel.updateItem(name, groupId, startMsg, stopMsg, quitMsg, technique, weekdayLimit)
                editingItem.value = null
            }
        )
    }
}

@Composable
fun ItemDialog(
    groups: List<AppGroup>,
    item: MonitoredItemEntity? = null,
    isShizukuEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, groupId: String?, startMsg: String?, stopMsg: String?, quitMsg: String?, blockingTechnique: String, weekdayLimit: Set<String>) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var selectedGroup by remember { mutableStateOf(groups.find { it.id == item?.linkedGroupId }) }
    var startMsg by remember { mutableStateOf(item?.startMessage ?: "") }
    var stopMsg by remember { mutableStateOf(item?.stopMessage ?: "") }
    var quitMsg by remember { mutableStateOf(item?.forceQuitMessage ?: "") }
    var blockingTechnique by remember { mutableStateOf(item?.blockingTechnique ?: "HOME") }
    var weekdayLimit by remember { mutableStateOf(item?.weekdayLimit ?: setOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")) }

    var groupExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (blockingTechnique == "WORK_PROFILE") {
        selectedGroup = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add Shop Item" else "Edit Shop Item") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (item == null) name = it },
                    label = { Text("Item Name") },
                    readOnly = item != null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                val isGroupSelectionEnabled = blockingTechnique != "WORK_PROFILE"

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (isGroupSelectionEnabled) selectedGroup?.name ?: "None" else "(Not applicable)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Group") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Group") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isGroupSelectionEnabled
                    )
                    if (isGroupSelectionEnabled) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { groupExpanded = true }
                        )
                    }
                    DropdownMenu(
                        expanded = groupExpanded,
                        onDismissRequest = { groupExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                selectedGroup = null
                                groupExpanded = false
                            }
                        )
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = {
                                    selectedGroup = group
                                    groupExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Blocking Technique", style = MaterialTheme.typography.titleSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { blockingTechnique = "HOME" }
                ) {
                    RadioButton(
                        selected = blockingTechnique == "HOME",
                        onClick = { blockingTechnique = "HOME" }
                    )
                    Text(
                        text = "Global Action Home",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isShizukuEnabled) {
                            if (isShizukuEnabled) {
                                blockingTechnique = "DISABLE"
                            } else {
                                Toast.makeText(context, "Enable Shizuku first", Toast.LENGTH_SHORT).show()
                            }
                        }
                ) {
                    RadioButton(
                        selected = blockingTechnique == "DISABLE",
                        onClick = {
                            if (isShizukuEnabled) {
                                blockingTechnique = "DISABLE"
                            } else {
                                Toast.makeText(context, "Enable Shizuku first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = isShizukuEnabled
                    )
                    Column {
                        Text(
                            text = "Disable Apps",
                            modifier = Modifier.padding(start = 8.dp),
                            color = if (isShizukuEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            text = "(Requires Shizuku)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isShizukuEnabled) {
                            if (isShizukuEnabled) {
                                blockingTechnique = "WORK_PROFILE"
                            } else {
                                Toast.makeText(context, "Enable Shizuku first", Toast.LENGTH_SHORT).show()
                            }
                        }
                ) {
                    RadioButton(
                        selected = blockingTechnique == "WORK_PROFILE",
                        onClick = {
                            if (isShizukuEnabled) {
                                blockingTechnique = "WORK_PROFILE"
                            } else {
                                Toast.makeText(context, "Enable Shizuku first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = isShizukuEnabled
                    )
                    Column {
                        Text(
                            text = "Toggle Work Profile",
                            modifier = Modifier.padding(start = 8.dp),
                            color = if (isShizukuEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            text = "(Requires Shizuku)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Toast Messages (Optional)", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = startMsg,
                    onValueChange = { startMsg = it },
                    label = { Text("On Start") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = stopMsg,
                    onValueChange = { stopMsg = it },
                    label = { Text("On Stop") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = quitMsg,
                    onValueChange = { quitMsg = it },
                    label = { Text("On Force Quit") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Weekday Limit", style = MaterialTheme.typography.titleSmall)

                val days = listOf("M", "Tu", "W", "Th", "F", "Sa", "Su")
                val fullDayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (i in 0..3) {
                            val day = days[i]
                            val fullDayName = fullDayNames[i]
                            val isSelected = weekdayLimit.contains(fullDayName)
                            TextButton(
                                onClick = {
                                    weekdayLimit = if (isSelected) {
                                        weekdayLimit - fullDayName
                                    } else {
                                        weekdayLimit + fullDayName
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Text(day)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (i in 4..6) {
                            val day = days[i]
                            val fullDayName = fullDayNames[i]
                            val isSelected = weekdayLimit.contains(fullDayName)
                            TextButton(
                                onClick = {
                                    weekdayLimit = if (isSelected) {
                                        weekdayLimit - fullDayName
                                    } else {
                                        weekdayLimit + fullDayName
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Text(day)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name,
                        selectedGroup?.id.takeIf { blockingTechnique != "WORK_PROFILE" },
                        startMsg.takeIf { it.isNotBlank() },
                        stopMsg.takeIf { it.isNotBlank() },
                        quitMsg.takeIf { it.isNotBlank() },
                        blockingTechnique,
                        weekdayLimit
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Composable
fun ShopItemRow(item: MonitoredItemEntity, groups: List<AppGroup>, onEdit: () -> Unit, onDelete: () -> Unit) {
    val selectedGroup = groups.find { it.id == item.linkedGroupId }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (item.isActive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                shape = MaterialTheme.shapes.medium
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black
            )
            Text(
                text = if (item.isActive) "Running" else "Stopped",
                style = MaterialTheme.typography.bodySmall,
                color = if (item.isActive) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (item.blockingTechnique != "WORK_PROFILE") {
                Text(
                    text = "Group: ${selectedGroup?.name ?: "None"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black
                )
            }
            Text(
                text = "Type: ${item.blockingTechnique.replace("_", " ")}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Black)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Black)
        }
    }
}

@SuppressLint("BatteryLife")
private fun requestBackgroundPermission(context: Context) {
    val intent = Intent()
    val packageName = context.packageName
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        intent.data = "package:$packageName".toUri()
        context.startActivity(intent)
    }
}
