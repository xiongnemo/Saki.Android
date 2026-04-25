package org.hdhmc.saki.domain.repository

import org.hdhmc.saki.domain.model.ConnectionTestRequest
import org.hdhmc.saki.domain.model.ConnectionTestResult

interface ServerConnectionTester {
    suspend fun testConnection(request: ConnectionTestRequest): ConnectionTestResult
}
