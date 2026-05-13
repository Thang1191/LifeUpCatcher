package com.skibidi.lifeupcatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.skibidi.lifeupcatcher.data.repository.MonitoredItemRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LifeUpBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var monitoredItemRepository: MonitoredItemRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val itemName = intent.getStringExtra("item") ?: intent.getStringExtra("name") ?: intent.getStringExtra("title")

        if (itemName != null) {
            val isStart = action == "app.lifeup.item.countdown.start"
            Log.d("LifeUpBroadcastReceiver", "Received broadcast: $action for item: $itemName")

            CoroutineScope(Dispatchers.IO).launch {
                val item = monitoredItemRepository.getItemByName(itemName)
                if (item != null) {
                    monitoredItemRepository.updateItemState(itemName, isStart)
                    
                    val message = if (isStart) item.startMessage else item.stopMessage
                    if (!message.isNullOrBlank()) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    // Trigger a check in the service if it's running
                    val serviceIntent = Intent(context, MyAccessibilityService::class.java).apply {
                        this.action = "com.skibidi.lifeupcatcher.CHECK_ENFORCE"
                    }
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
