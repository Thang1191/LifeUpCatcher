package com.skibidi.lifeupcatcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun DebugScreen() {
    val isDebuggingEnabled by ShopItemRepository.isDebuggingEnabled.collectAsState()
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Debug Mode", modifier = Modifier.weight(1f))
                Switch(
                    checked = isDebuggingEnabled,
                    onCheckedChange = { ShopItemRepository.setDebuggingEnabled(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isDebuggingEnabled) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("Shizuku Command") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        if (command.isNotBlank() && Shizuku.pingBinder()) {
                            try {
                                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                                    "newProcess",
                                    Array<String>::class.java,
                                    Array<String>::class.java,
                                    String::class.java
                                )
                                newProcessMethod.isAccessible = true
                                val process = newProcessMethod.invoke(
                                    null,
                                    arrayOf("sh", "-c", command),
                                    null,
                                    null
                                ) as Process

                                val stdOut = BufferedReader(InputStreamReader(process.inputStream)).readText()
                                val stdErr = BufferedReader(InputStreamReader(process.errorStream)).readText()
                                process.waitFor()
                                output = "Exit Code: ${process.exitValue()}\n\nSTDOUT:\n$stdOut\n\nSTDERR:\n$stdErr"
                            } catch (e: Exception) {
                                output = "Error: ${e.message}"
                            }
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Send")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Output:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = output,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
