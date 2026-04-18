package com.anzupop.saki.android.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.anzupop.saki.android.data.local.entity.ServerEndpointEntity
import com.anzupop.saki.android.data.local.entity.ServerEntity

data class ServerWithEndpoints(
    @Embedded
    val server: ServerEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "serverId",
    )
    val endpoints: List<ServerEndpointEntity>,
)
