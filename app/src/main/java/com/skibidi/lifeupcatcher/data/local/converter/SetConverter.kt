package com.skibidi.lifeupcatcher.data.local.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SetConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromString(value: String?): Set<String> {
        if (value == null) return emptySet()
        val setType = object : TypeToken<Set<String>>() {}.type
        return gson.fromJson(value, setType)
    }

    @TypeConverter
    fun fromSet(set: Set<String>?): String {
        return gson.toJson(set ?: emptySet<String>())
    }
}
