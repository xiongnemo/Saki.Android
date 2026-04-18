package com.anzupop.saki.android.domain.repository

import com.anzupop.saki.android.domain.model.ConnectionTestRequest
import com.anzupop.saki.android.domain.model.ConnectionTestResult

interface ServerConnectionTester {
    suspend fun testConnection(request: ConnectionTestRequest): ConnectionTestResult
}
