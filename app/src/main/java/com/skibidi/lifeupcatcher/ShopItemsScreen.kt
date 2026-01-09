package com.skibidi.lifeupcatcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ShopItemsScreen() {
    val context = LocalContext.current
    val items by ShopItemRepository.items.collectAsState()
    val isMonitoringEnabled by ShopItemRepository.isMonitoringEnabled.collectAsState()

    val appPickerViewModel: AppPickerViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(context.applicationContext as android.app.Application)
    )
    val groups by appPickerViewModel.groups.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ShopItemState?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
             ShopItemRepository.setMonitoringEnabled(true)
             requestBackgroundPermission(context)
        } else {
             ShopItemRepository.setMonitoringEnabled(true)
             requestBackgroundPermission(context)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
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
            // Monitoring Toggle Card
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

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    ShopItemRow(
                        item = item, 
                        groups = groups,
                        onEdit = { editingItem = item }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        ItemDialog(
            groups = groups,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, groupId, startMsg, stopMsg, quitMsg ->
                if (name.isNotBlank()) {
                    ShopItemRepository.addItem(name.trim(), groupId, startMsg, stopMsg, quitMsg)
                }
                showAddDialog = false
            }
        )
    }

    if (editingItem != null) {
        ItemDialog(
            groups = groups,
            item = editingItem,
            onDismiss = { editingItem = null },
            onConfirm = { name, groupId, startMsg, stopMsg, quitMsg ->
                ShopItemRepository.updateItem(name, groupId, startMsg, stopMsg, quitMsg)
                editingItem = null
            }
        )
    }
}

@Composable
fun ItemDialog(
    groups: List<AppGroup>,
    item: ShopItemState? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, groupId: String?, startMsg: String?, stopMsg: String?, quitMsg: String?) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var selectedGroup by remember { mutableStateOf(groups.find { it.id == item?.linkedGroupId }) }
    var startMsg by remember { mutableStateOf(item?.startMessage ?: "") }
    var stopMsg by remember { mutableStateOf(item?.stopMessage ?: "") }
    var quitMsg by remember { mutableStateOf(item?.forceQuitMessage ?: "") }
    
    var groupExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add Shop Item" else "Edit Shop Item") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (item == null) name = it }, // Only editable if adding
                    label = { Text("Item Name") },
                    readOnly = item != null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedGroup?.name ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Group") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Group") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { groupExpanded = true }
                    )
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
                        selectedGroup?.id,
                        startMsg.takeIf { it.isNotBlank() },
                        stopMsg.takeIf { it.isNotBlank() },
                        quitMsg.takeIf { it.isNotBlank() }
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
            Text(text = item.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (item.isActive) "Running" else "Stopped",
                style = MaterialTheme.typography.bodySmall,
                color = if (item.isActive) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Group: ${selectedGroup?.name ?: "None"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = { ShopItemRepository.removeItem(item.name) }) {
            Icon(Icons.Default.Delete, contentDescription = "Remove")
        }
    }
}

private fun requestBackgroundPermission(context: Context) {
    val intent = Intent()
    val packageName = context.packageName
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        intent.data = Uri.parse("package:$packageName")
        context.startActivity(intent)
    }
}
