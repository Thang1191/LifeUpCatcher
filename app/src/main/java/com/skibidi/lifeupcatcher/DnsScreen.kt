package com.skibidi.lifeupcatcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun DnsScreen(
    viewModel: DnsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Private DNS Locking",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = viewModel.dnsHostname,
                    onValueChange = viewModel::updateDnsHostname,
                    label = { Text("DNS Hostname") },
                    placeholder = { Text("e.g., dns.google") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isDnsLockingEnabled
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Enable DNS Locking",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.isDnsLockingEnabled,
                        onCheckedChange = viewModel::setDnsLockingEnabled,
                        enabled = uiState.isShizukuAvailable && viewModel.dnsHostname.isNotBlank()
                    )
                }

                if (!uiState.isShizukuAvailable) {
                    Text(
                        text = "Shizuku is required for this feature.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "When enabled, this app will force the device to use the specified private DNS and revert any manual changes automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
