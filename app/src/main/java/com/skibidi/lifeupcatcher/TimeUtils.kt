package com.skibidi.lifeupcatcher

import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun formatTime(hour: Int, minute: Int): String {
    return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm"))
}

fun isCurrentTimeInWindow(startTime: String, endTime: String): Boolean {
    val now = LocalTime.now()
    val start = LocalTime.parse(startTime, DateTimeFormatter.ofPattern("HH:mm"))
    val end = LocalTime.parse(endTime, DateTimeFormatter.ofPattern("HH:mm"))

    return if (start.isBefore(end)) {
        // Time window is within the same day
        !now.isBefore(start) && now.isBefore(end)
    } else {
        // Time window spans across midnight
        !now.isBefore(start) || now.isBefore(end)
    }
}
