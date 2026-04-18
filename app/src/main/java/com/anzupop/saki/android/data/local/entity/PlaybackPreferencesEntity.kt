package com.anzupop.saki.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_preferences")
data class PlaybackPreferencesEntity(
    @PrimaryKey
    val id: Int = DEFAULT_ID,
    val streamQualityKey: String,
    val soundBalancingEnabled: Boolean,
    val soundBalancingModeKey: String,
    val streamCacheSizeMb: Int,
) {
    companion object {
        const val DEFAULT_ID = 0
    }
}
