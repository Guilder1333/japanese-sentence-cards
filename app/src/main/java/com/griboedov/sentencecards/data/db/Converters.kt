package com.griboedov.sentencecards.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromTokenList(tokens: List<SentenceToken>): String = json.encodeToString(tokens)

    @TypeConverter
    fun toTokenList(data: String): List<SentenceToken> = json.decodeFromString(data)

    @TypeConverter
    fun fromLongList(ids: List<Long>): String = json.encodeToString(ids)

    @TypeConverter
    fun toLongList(data: String): List<Long> = json.decodeFromString(data)

    // Single nullable-parameter converters double up for both the non-null `queueLevel` field
    // and the nullable `lastMarkedLevel` field - Kotlin doesn't allow overloading by nullability
    // alone (JVM signature clash), and Room is happy calling a nullable-returning converter for
    // a non-null column since we never actually store a null queueLevel.
    @TypeConverter
    fun fromQueueLevel(level: QueueLevel?): String? = level?.name

    @TypeConverter
    fun toQueueLevel(name: String?): QueueLevel? = name?.let { QueueLevel.valueOf(it) }
}
