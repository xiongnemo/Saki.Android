package org.hdhmc.saki.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.hdhmc.saki.data.local.entity.AppPreferencesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppPreferencesDao {
    @Query(
        """
        SELECT * FROM app_preferences
        WHERE id = ${AppPreferencesEntity.DEFAULT_ID}
        LIMIT 1
        """,
    )
    fun observePreferences(): Flow<AppPreferencesEntity?>

    @Query(
        """
        SELECT * FROM app_preferences
        WHERE id = ${AppPreferencesEntity.DEFAULT_ID}
        LIMIT 1
        """,
    )
    suspend fun getPreferences(): AppPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreferences(entity: AppPreferencesEntity)
}
