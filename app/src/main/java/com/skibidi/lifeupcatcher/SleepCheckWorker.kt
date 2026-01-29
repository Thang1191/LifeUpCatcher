package com.skibidi.lifeupcatcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.*
import kotlinx.coroutines.flow.first
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class SleepCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val settingsRepository = SleepSettingsRepository(applicationContext)

    override suspend fun doWork(): Result {
        Log.d("SleepCheckWorker", "Worker started.")

        createNotificationChannel(applicationContext)
        
        try {
            val healthConnectClient = HealthConnectClient.getOrCreate(applicationContext)
            val permissions = setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

            if (!hasPermissions(healthConnectClient, permissions)) {
                Log.e("SleepCheckWorker", "Health Connect permissions not granted. Cannot perform check.")
                return Result.failure()
            }

            val settings = settingsRepository.sleepSettingsFlow.first()
            val threshold = Duration.ofHours(settings.thresholdHours.toLong())
                .plusMinutes(settings.thresholdMinutes.toLong())
            Log.d("SleepCheckWorker", "Read settings: Threshold=${threshold.toMinutes()} minutes")

            val sleepDuration = fetchAndProcessSleepData(healthConnectClient)
            if (sleepDuration == null) {
                Log.w("SleepCheckWorker", "No sleep data found for the last 24 hours.")
            } else {
                Log.d("SleepCheckWorker", "Total sleep duration: ${sleepDuration.toMinutes()} minutes")
                val isSuccess = sleepDuration >= threshold
                val coinAmount = if (isSuccess) settings.rewardAmount else -settings.punishmentAmount
                val message = if (isSuccess) settings.successMessage else settings.failureMessage
                val title = if (isSuccess) settings.successTitle else settings.failureTitle

                Log.d("SleepCheckWorker", "Sleep duration (${sleepDuration.toMinutes()} min) vs Threshold (${threshold.toMinutes()} min). Coin change: $coinAmount")
                triggerLifeUpIntent(coinAmount, message)
                showSleepCheckNotification(applicationContext, title, message)
            }

            scheduleNextWork()

        } catch (e: Exception) {
            Log.e("SleepCheckWorker", "An error occurred during work execution.", e)
            return Result.retry()
        }

        return Result.success()
    }

    private suspend fun hasPermissions(client: HealthConnectClient, permissions: Set<String>): Boolean {
        return client.permissionController.getGrantedPermissions().containsAll(permissions)
    }

    private suspend fun fetchAndProcessSleepData(client: HealthConnectClient): Duration? {
        val end = ZonedDateTime.now()
        val start = end.minusDays(1)
        var totalSleepTime = Duration.ZERO

        try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start.toInstant(), end.toInstant())
                )
            )

            if (response.records.isEmpty()) return null

            response.records.forEach { session ->
                if (session.stages.isNotEmpty()) {
                    session.stages.forEach { stage ->
                        if (stage.stage != SleepSessionRecord.STAGE_TYPE_AWAKE) {
                            totalSleepTime = totalSleepTime.plus(Duration.between(stage.startTime, stage.endTime))
                        }
                    }
                } else {
                    totalSleepTime = totalSleepTime.plus(Duration.between(session.startTime, session.endTime))
                }
            }
            return totalSleepTime
        } catch (e: Exception) {
            Log.e("SleepCheckWorker", "Error reading sleep data from Health Connect", e)
            return null
        }
    }

    private fun triggerLifeUpIntent(coinAmount: Int, content: String) {
        if (coinAmount == 0) {
            Log.d("SleepCheckWorker", "Coin amount is 0, not triggering LifeUp.")
            return
        }

        val encodedContent = URLEncoder.encode(content, StandardCharsets.UTF_8.toString())
        val uriString = "lifeup://api/reward?type=coin&content=$encodedContent&number=$coinAmount"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = uriString.toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            Log.d("SleepCheckWorker", "Starting LifeUp intent with URI: $uriString")
            applicationContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SleepCheckWorker", "Failed to start LifeUp intent. Is LifeUp installed?", e)
        }
    }

    private suspend fun scheduleNextWork() {
        Log.d("SleepCheckWorker", "Re-scheduling next worker.")
        val workManager = WorkManager.getInstance(applicationContext)
        val workRequestTag = "sleep-check-worker"
        
        val settings = settingsRepository.sleepSettingsFlow.first()

        val now = LocalDateTime.now()
        var nextCheck = now
            .withHour(settings.checkHour)
            .withMinute(settings.checkMinute)
            .withSecond(0)

        if (nextCheck.isBefore(now)) {
            nextCheck = nextCheck.plusDays(1)
        }

        val initialDelay = Duration.between(now, nextCheck).toMillis()

        Log.d("SleepCheckWorker", "Next check: $nextCheck, Initial delay: $initialDelay ms")

        val sleepCheckWorkRequest = OneTimeWorkRequestBuilder<SleepCheckWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(workRequestTag)
            .build()

        workManager.enqueueUniqueWork(
            workRequestTag,
            ExistingWorkPolicy.REPLACE,
            sleepCheckWorkRequest
        )
        Log.d("SleepCheckWorker", "Next work request enqueued.")
    }
}
