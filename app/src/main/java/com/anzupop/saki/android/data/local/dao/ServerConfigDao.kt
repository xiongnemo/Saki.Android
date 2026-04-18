package com.anzupop.saki.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.anzupop.saki.android.data.local.entity.ServerEndpointEntity
import com.anzupop.saki.android.data.local.entity.ServerEntity
import com.anzupop.saki.android.data.local.model.ServerWithEndpoints
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerConfigDao {
    @Transaction
    @Query("SELECT * FROM servers ORDER BY updatedAt DESC")
    fun observeServers(): Flow<List<ServerWithEndpoints>>

    @Transaction
    @Query("SELECT * FROM servers WHERE id = :serverId")
    suspend fun getServerWithEndpoints(serverId: Long): ServerWithEndpoints?

    @Insert
    suspend fun insertServer(server: ServerEntity): Long

    @Update
    suspend fun updateServer(server: ServerEntity)

    @Insert
    suspend fun insertEndpoints(endpoints: List<ServerEndpointEntity>)

    @Query("DELETE FROM server_endpoints WHERE serverId = :serverId")
    suspend fun deleteEndpointsByServerId(serverId: Long)

    @Query("DELETE FROM servers WHERE id = :serverId")
    suspend fun deleteServerById(serverId: Long)

    @Transaction
    suspend fun upsertServerWithEndpoints(
        server: ServerEntity,
        endpoints: List<ServerEndpointEntity>,
    ): Long {
        val serverId = if (server.id == 0L) {
            insertServer(server)
        } else {
            updateServer(server)
            server.id
        }

        deleteEndpointsByServerId(serverId)
        if (endpoints.isNotEmpty()) {
            insertEndpoints(
                endpoints.mapIndexed { index, endpoint ->
                    endpoint.copy(
                        serverId = serverId,
                        displayOrder = index,
                    )
                },
            )
        }

        return serverId
    }

    @Transaction
    suspend fun deleteServerAndEndpoints(serverId: Long) {
        deleteEndpointsByServerId(serverId)
        deleteServerById(serverId)
    }
}
