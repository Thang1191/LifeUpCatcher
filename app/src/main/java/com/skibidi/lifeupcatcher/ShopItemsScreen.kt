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
import androidx.lifecycle.viewmodel.compose.viewModel
import rikka.shizuku.Shizuku

@Composable
fun ShopItemsScreen() {
    val context = LocalContext.current
    val items by ShopItemRepository.items.collectAsState()
    val isMonitoringEnabled by ShopItemRepository.isMonitoringEnabled.collectAsState()
    val isShizukuEnabled by ShopItemRepository.isShizukuEnabled.collectAsState()

    val appPickerViewModel: AppPickerViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(context.applicationContext as android.app.Application)
    )
    val groups by appPickerViewModel.groups.collectAsState()

    val showAddDialog = remember { mutableStateOf(false) }
    val editingItem = remember { mutableStateOf<ShopItemState?>(null) }
    var shizukuAvailable by remember { mutableStateOf(false) }

    val shizukuPermissionListener = remember {
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001 && grantResult == PackageManager.PERMISSION_GRANTED) {
                ShopItemRepository.setShizukuEnabled(true)
            }
        }
    }

    val shizukuBinderReceivedListener = remember {
        Shizuku.OnBinderReceivedListener {
            shizukuAvailable = true
        }
    }

    val shizukuBinderDeadListener = remember {
        Shizuku.OnBinderDeadListener {
            shizukuAvailable = false
        }
    }

    DisposableEffect(Unit) {
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        
        shizukuAvailable = Shizuku.pingBinder()

        onDispose {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        ShopItemRepository.setMonitoringEnabled(true)
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
                        Text(
                            text = if (isMonitoringEnabled) "Running" else "Stopped",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isMonitoringEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isMonitoringEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                        ShopItemRepository.setMonitoringEnabled(true)
                                        requestBackgroundPermission(context)
                                    } else {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    ShopItemRepository.setMonitoringEnabled(true)
                                    requestBackgroundPermission(context)
                                }
                            } else {
                                ShopItemRepository.setMonitoringEnabled(false)
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
                        Text(
                            text = if (isShizukuEnabled) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isShizukuEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isShizukuEnabled,
                        onCheckedChange = { enabled ->
                             if (enabled) {
                                try {
                                    if (shizukuAvailable || Shizuku.pingBinder()) {
                                        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                                            ShopItemRepository.setShizukuEnabled(true)
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
                                 ShopItemRepository.setShizukuEnabled(false)
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
                        onEdit = { editingItem.value = item }
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
            onConfirm = { name, groupId, startMsg, stopMsg, quitMsg, technique ->
                if (name.isNotBlank()) {
                    ShopItemRepository.addItem(name.trim(), groupId, startMsg, stopMsg, quitMsg, technique)
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
            onConfirm = { name, groupId, startMsg, stopMsg, quitMsg, technique ->
                ShopItemRepository.updateItem(name, groupId, startMsg, stopMsg, quitMsg, technique)
                editingItem.value = null
            }
        )
    }
}

@Composable
fun ItemDialog(
    groups: List<AppGroup>,
    item: ShopItemState? = null,
    isShizukuEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, groupId: String?, startMsg: String?, stopMsg: String?, quitMsg: String?, blockingTechnique: String) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var selectedGroup by remember { mutableStateOf(groups.find { it.id == item?.linkedGroupId }) }
    var startMsg by remember { mutableStateOf(item?.startMessage ?: "") }
    var stopMsg by remember { mutableStateOf(item?.stopMessage ?: "") }
    var quitMsg by remember { mutableStateOf(item?.forceQuitMessage ?: "") }
    var blockingTechnique by remember { mutableStateOf(item?.blockingTechnique ?: "HOME") }
    
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
                            text = "(Requires Shizuku/Root)",
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
                            text = "(Requires Shizuku/Root)",
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
                        blockingTechnique
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
fun ShopItemRow(item: ShopItemState, groups: List<AppGroup>, onEdit: () -> Unit) {
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
        IconButton(onClick = { ShopItemRepository.removeItem(item.name) }) {
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
