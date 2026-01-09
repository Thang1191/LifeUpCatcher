package com.skibidi.lifeupcatcher

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Bitmap?
)

data class AppGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packageNames: Set<String>
)

class AppPickerViewModel(application: Application) : AndroidViewModel(application) {
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _groups = MutableStateFlow<List<AppGroup>>(emptyList())
    val groups: StateFlow<List<AppGroup>> = _groups

    private val prefs = application.getSharedPreferences("app_picker_prefs", 0)

    init {
        loadApps()
        loadGroups()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            val appList = packages.mapNotNull { packInfo ->
                val intent = pm.getLaunchIntentForPackage(packInfo.packageName)
                val appInfo = packInfo.applicationInfo
                if (intent != null && appInfo != null) {
                    val label = appInfo.loadLabel(pm).toString()
                    val iconDrawable = appInfo.loadIcon(pm)
                    val iconBitmap = drawableToBitmap(iconDrawable)
                    AppInfo(packInfo.packageName, label, iconBitmap)
                } else {
                    null
                }
            }.sortedBy { it.label }
            _apps.value = appList
        }
    }

    private fun loadGroups() {
        val jsonString = prefs.getString("app_groups", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<AppGroup>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val packagesArray = obj.getJSONArray("packages")
                val packages = mutableSetOf<String>()
                for (j in 0 until packagesArray.length()) {
                    packages.add(packagesArray.getString(j))
                }
                list.add(AppGroup(id, name, packages))
            }
            _groups.value = list
        } catch (e: Exception) {
            e.printStackTrace()
            _groups.value = emptyList()
        }
    }

    fun saveGroup(name: String, selectedPackages: Set<String>) {
        val newGroup = AppGroup(name = name, packageNames = selectedPackages)
        val currentGroups = _groups.value.toMutableList()
        currentGroups.add(newGroup)
        _groups.value = currentGroups
        persistGroups(currentGroups)
    }

    fun updateGroup(id: String, name: String, selectedPackages: Set<String>) {
        val currentGroups = _groups.value.toMutableList()
        val index = currentGroups.indexOfFirst { it.id == id }
        if (index != -1) {
            currentGroups[index] = AppGroup(id, name, selectedPackages)
            _groups.value = currentGroups
            persistGroups(currentGroups)
        }
    }
    
    fun deleteGroup(groupId: String) {
        val currentGroups = _groups.value.filter { it.id != groupId }
        _groups.value = currentGroups
        persistGroups(currentGroups)
    }

    private fun persistGroups(groups: List<AppGroup>) {
        val jsonArray = JSONArray()
        groups.forEach { group ->
            val obj = JSONObject()
            obj.put("id", group.id)
            obj.put("name", group.name)
            val packagesArray = JSONArray()
            group.packageNames.forEach { packagesArray.put(it) }
            obj.put("packages", packagesArray)
            jsonArray.put(obj)
        }
        prefs.edit().putString("app_groups", jsonArray.toString()).apply()
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
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
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(allApps) { app ->
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
