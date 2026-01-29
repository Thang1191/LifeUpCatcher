package com.skibidi.lifeupcatcher

import java.util.Calendar

object TimeUtils {

    /**
     * Checks if the current time is within a specified time window.
     * Handles overnight windows (e.g., 22:00 to 08:00).
     *
     * @param startTime The start of the window in "HH:mm" format.
     * @param endTime The end of the window in "HH:mm" format.
     * @return True if the current time is within the window, false otherwise.
     */
    fun isCurrentTimeInWindow(startTime: String, endTime: String): Boolean {
        val (startHour, startMinute) = startTime.split(":").map { it.toInt() }
        val (endHour, endMinute) = endTime.split(":").map { it.toInt() }

        val now = Calendar.getInstance()
        val startCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
        }
        val endCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
            set(Calendar.SECOND, 0)
        }

        return if (startCalendar.after(endCalendar)) {
            // Overnight case (e.g., 22:00 to 08:00)
            now.after(startCalendar) || now.before(endCalendar)
        } else {
            // Same day case (e.g., 09:00 to 17:00)
            now.after(startCalendar) && now.before(endCalendar)
        }
    }
}