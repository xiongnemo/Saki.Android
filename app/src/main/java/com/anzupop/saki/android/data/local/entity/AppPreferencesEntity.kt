package com.anzupop.saki.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.anzupop.saki.android.domain.model.TextScale

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
