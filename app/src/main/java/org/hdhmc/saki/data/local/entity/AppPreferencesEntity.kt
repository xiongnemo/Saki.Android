package org.hdhmc.saki.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.hdhmc.saki.domain.model.TextScale

@Entity(tableName = "app_preferences")
data class AppPreferencesEntity(
    @PrimaryKey
    val id: Int = DEFAULT_ID,
    val onboardingCompleted: Boolean,
    val textScaleKey: String = TextScale.DEFAULT.storageKey,
) {
    companion object {
        const val DEFAULT_ID = 0
    }
}
