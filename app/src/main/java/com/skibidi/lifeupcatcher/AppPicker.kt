package com.skibidi.lifeupcatcher

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skibidi.lifeupcatcher.data.local.entity.AppGroupEntity
import com.skibidi.lifeupcatcher.data.repository.AppGroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val isSystemApp: Boolean,
    val isEnabled: Boolean
)

data class AppGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packageNames: Set<String>
)

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    application: Application,
    private val appGroupRepository: AppGroupRepository
) : AndroidViewModel(application) {
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    val groups: StateFlow<List<AppGroup>> = appGroupRepository.allGroups.map { entities ->
        entities.map { AppGroup(it.id, it.name, it.packageNames) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_META_DATA)
            }

            val appList = packages.mapNotNull { packInfo ->
                val appInfo = packInfo.applicationInfo
                if (appInfo != null) {
                    val label = appInfo.loadLabel(pm).toString()
                    val iconDrawable = appInfo.loadIcon(pm)
                    val iconBitmap = drawableToBitmap(iconDrawable)
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    AppInfo(packInfo.packageName, label, iconBitmap, isSystemApp, appInfo.enabled)
                } else {
                    null
                }
            }.sortedBy { it.label }
            _apps.value = appList
        }
    }

    fun saveGroup(name: String, selectedPackages: Set<String>) {
        viewModelScope.launch {
            appGroupRepository.addGroup(AppGroupEntity(name = name, packageNames = selectedPackages))
        }
    }

    fun updateGroup(id: String, name: String, selectedPackages: Set<String>) {
        viewModelScope.launch {
            appGroupRepository.updateGroup(AppGroupEntity(id, name, selectedPackages))
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            val group = appGroupRepository.getGroupById(groupId)
            if (group != null) {
                appGroupRepository.deleteGroup(group)
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}

@Composable
fun AppPickerScreen(viewModel: AppPickerViewModel) {
    val groups by viewModel.groups.collectAsState()
    val apps by viewModel.apps.collectAsState()
    var isCreatingGroup by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<AppGroup?>(null) }

    Scaffold(
        floatingActionButton = {
            if (!isCreatingGroup && editingGroup == null) {
                FloatingActionButton(onClick = { isCreatingGroup = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Group")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (isCreatingGroup) {
                CreateGroupScreen(
                    allApps = apps,
                    onSave = { name, selected ->
                        viewModel.saveGroup(name, selected)
                        isCreatingGroup = false
                    },
                    onCancel = { isCreatingGroup = false }
                )
            } else if (editingGroup != null) {
                CreateGroupScreen(
                    allApps = apps,
                    initialName = editingGroup!!.name,
                    initialSelection = editingGroup!!.packageNames,
                    onSave = { name, selected ->
                        viewModel.updateGroup(editingGroup!!.id, name, selected)
                        editingGroup = null
                    },
                    onCancel = { editingGroup = null }
                )
            } else {
                GroupListScreen(
                    groups = groups,
                    allApps = apps,
                    onDelete = { viewModel.deleteGroup(it) },
                    onEdit = { editingGroup = it }
                )
            }
        }
    }
}

@Composable
fun GroupListScreen(
    groups: List<AppGroup>,
    allApps: List<AppInfo>,
    onDelete: (String) -> Unit,
    onEdit: (AppGroup) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(groups) { group ->
            GroupItem(group, allApps, onDelete, onEdit)
        }
    }
}

@Composable
fun GroupItem(
    group: AppGroup,
    allApps: List<AppInfo>,
    onDelete: (String) -> Unit,
    onEdit: (AppGroup) -> Unit
) {
    val appMap = remember(allApps) { allApps.associateBy { it.packageName } }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onEdit(group) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Group")
                }
                IconButton(onClick = { onDelete(group.id) }) {
                    Icon(Icons.Default.Close, contentDescription = "Delete Group")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0.85f to Color.Black,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                group.packageNames.forEach { pkg ->
                    val app = appMap[pkg]
                    if (app?.icon != null) {
                        Image(
                            bitmap = app.icon.asImageBitmap(),
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(end = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    allApps: List<AppInfo>,
    initialName: String = "",
    initialSelection: Set<String> = emptySet(),
    onSave: (String, Set<String>) -> Unit,
    onCancel: () -> Unit
) {
    var groupName by remember { mutableStateOf(initialName) }
    var selectedPackages by remember { mutableStateOf(initialSelection) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var showDisabledApps by remember { mutableStateOf(false) }

    val filteredApps by remember(searchQuery, showSystemApps, showDisabledApps, allApps) {
        derivedStateOf {
            allApps.filter { app ->
                val matchesSearch = searchQuery.isBlank() || app.label.contains(searchQuery, ignoreCase = true)
                val matchesSystem = showSystemApps || !app.isSystemApp
                val matchesEnabled = showDisabledApps || app.isEnabled
                matchesSearch && matchesSystem && matchesEnabled
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Group", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    if (groupName.isNotBlank() && selectedPackages.isNotEmpty()) {
                        onSave(groupName, selectedPackages)
                    }
                },
                enabled = groupName.isNotBlank() && selectedPackages.isNotEmpty()
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save")
            }
        }

        OutlinedTextField(
            value = groupName,
            onValueChange = { groupName = it },
            label = { Text("Group Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Apps") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = showSystemApps,
                onClick = { showSystemApps = !showSystemApps },
                label = { Text("System Apps") }
            )
            FilterChip(
                selected = showDisabledApps,
                onClick = { showDisabledApps = !showDisabledApps },
                label = { Text("Disabled Apps") }
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredApps) { app ->
                AppItem(
                    app = app,
                    isSelected = selectedPackages.contains(app.packageName),
                    onToggle = {
                        val newSet = selectedPackages.toMutableSet()
                        if (newSet.contains(app.packageName)) {
                            newSet.remove(app.packageName)
                        } else {
                            newSet.add(app.packageName)
                        }
                        selectedPackages = newSet
                    }
                )
            }
        }
    }
}

@Composable
fun AppItem(app: AppInfo, isSelected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = app.label, modifier = Modifier.weight(1f))
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
    }
}
