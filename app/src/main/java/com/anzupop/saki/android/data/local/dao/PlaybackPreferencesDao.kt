package com.anzupop.saki.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anzupop.saki.android.data.local.entity.PlaybackPreferencesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackPreferencesDao {
    @Query(
        """
        SELECT * FROM playback_preferences
        WHERE id = ${PlaybackPreferencesEntity.DEFAULT_ID}
        LIMIT 1
        """,
    )
    fun observePreferences(): Flow<PlaybackPreferencesEntity?>

    @Query(
        """
        SELECT * FROM playback_preferences
        WHERE id = ${PlaybackPreferencesEntity.DEFAULT_ID}
        LIMIT 1
        """,
    )
    suspend fun getPreferences(): PlaybackPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreferences(entity: PlaybackPreferencesEntity)
}
