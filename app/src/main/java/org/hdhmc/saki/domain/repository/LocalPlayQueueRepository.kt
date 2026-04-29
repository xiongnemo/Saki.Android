package org.hdhmc.saki.domain.repository

import org.hdhmc.saki.domain.model.LocalPlayQueueSnapshot

interface LocalPlayQueueRepository {
    suspend fun get(serverId: Long): LocalPlayQueueSnapshot?

    suspend fun save(snapshot: LocalPlayQueueSnapshot)

    suspend fun clear(serverId: Long)
}
