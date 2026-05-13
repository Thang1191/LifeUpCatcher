package com.skibidi.lifeupcatcher.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        val DNS_LOCKING_ENABLED = booleanPreferencesKey("dns_locking_enabled")
        val DNS_HOSTNAME = stringPreferencesKey("dns_hostname")
    }

    val isDnsLockingEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.DNS_LOCKING_ENABLED] ?: false }
    val dnsHostname: Flow<String> = dataStore.data.map { it[PreferencesKeys.DNS_HOSTNAME] ?: "" }

    suspend fun setDnsLockingEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DNS_LOCKING_ENABLED] = enabled }
    }

    suspend fun setDnsHostname(hostname: String) {
        dataStore.edit { it[PreferencesKeys.DNS_HOSTNAME] = hostname }
    }
}
