package org.hdhmc.saki.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import org.hdhmc.saki.data.local.entity.ServerEndpointEntity
import org.hdhmc.saki.data.local.entity.ServerEntity

data class ServerWithEndpoints(
    @Embedded
    val server: ServerEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "serverId",
    )
    val endpoints: List<ServerEndpointEntity>,
)
