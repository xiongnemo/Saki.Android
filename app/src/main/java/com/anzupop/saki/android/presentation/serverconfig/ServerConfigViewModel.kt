package com.anzupop.saki.android.presentation.serverconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anzupop.saki.android.R
import com.anzupop.saki.android.domain.model.ConnectionTestRequest
import com.anzupop.saki.android.domain.model.ConnectionTestResult
import com.anzupop.saki.android.domain.model.DEFAULT_SUBSONIC_API_VERSION
import com.anzupop.saki.android.domain.model.DEFAULT_SUBSONIC_CLIENT
import com.anzupop.saki.android.domain.model.ServerConfig
import com.anzupop.saki.android.domain.model.ServerEndpoint
import com.anzupop.saki.android.domain.repository.ServerConfigRepository
import com.anzupop.saki.android.domain.repository.ServerConnectionTester
import com.anzupop.saki.android.presentation.UiText
import com.anzupop.saki.android.presentation.localizedOr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ServerConfigViewModel @Inject constructor(
    private val serverConfigRepository: ServerConfigRepository,
    private val serverConnectionTester: ServerConnectionTester,
) : ViewModel() {
    private val editorState = MutableStateFlow<ServerEditorState?>(null)
    private val isSaving = MutableStateFlow(false)
    private val snackbarMessages = MutableSharedFlow<UiText>(extraBufferCapacity = 1)
    private var nextEditorId = 0L

    val messages = snackbarMessages.asSharedFlow()

    val uiState: StateFlow<ServerConfigUiState> = combine(
        serverConfigRepository.observeServerConfigs(),
        editorState,
        isSaving,
    ) { servers, editor, saving ->
        ServerConfigUiState(
            servers = servers,
            editor = editor,
            isSaving = saving,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ServerConfigUiState(),
    )

    fun startAddingServer() {
        editorState.value = ServerEditorState(
            endpoints = listOf(newEndpoint()),
        )
    }

    fun editServer(serverId: Long) {
        viewModelScope.launch {
            val server = serverConfigRepository.getServerConfig(serverId) ?: return@launch
            editorState.value = server.toEditorState()
        }
    }

    fun dismissEditor() {
        editorState.value = null
    }

    fun updateServerName(name: String) {
        updateEditor { copy(name = name, formError = null) }
    }

    fun updateUsername(username: String) {
        updateEditor {
            copy(
                username = username,
                endpoints = endpoints.resetConnectionStates(),
                formError = null,
            )
        }
    }

    fun updatePassword(password: String) {
        updateEditor {
            copy(
                password = password,
                endpoints = endpoints.resetConnectionStates(),
                formError = null,
            )
        }
    }

    fun updateClientName(clientName: String) {
        updateEditor {
            copy(
                clientName = clientName,
                endpoints = endpoints.resetConnectionStates(),
                formError = null,
            )
        }
    }

    fun updateApiVersion(apiVersion: String) {
        updateEditor {
            copy(
                apiVersion = apiVersion,
                endpoints = endpoints.resetConnectionStates(),
                formError = null,
            )
        }
    }

    fun addEndpoint() {
        updateEditor {
            copy(
                endpoints = endpoints + newEndpoint(),
                formError = null,
            )
        }
    }

    fun removeEndpoint(editorId: Long) {
        updateEditor {
            val remaining = endpoints.filterNot { it.editorId == editorId }
            copy(
                endpoints = remaining.ifEmpty { listOf(newEndpoint()) },
                formError = null,
            )
        }
    }

    fun updateEndpointLabel(editorId: Long, label: String) {
        updateEditor {
            copy(
                endpoints = endpoints.map { endpoint ->
                    if (endpoint.editorId == editorId) {
                        endpoint.copy(
                            label = label,
                            testState = EndpointConnectionState.Idle,
                        )
                    } else {
                        endpoint
                    }
                },
                formError = null,
            )
        }
    }

    fun updateEndpointUrl(editorId: Long, baseUrl: String) {
        updateEditor {
            copy(
                endpoints = endpoints.map { endpoint ->
                    if (endpoint.editorId == editorId) {
                        endpoint.copy(
                            baseUrl = baseUrl,
                            testState = EndpointConnectionState.Idle,
                        )
                    } else {
                        endpoint
                    }
                },
                formError = null,
            )
        }
    }

    fun testEndpoint(editorId: Long) {
        val current = editorState.value ?: return
        val endpoint = current.endpoints.firstOrNull { it.editorId == editorId } ?: return
        val validationError = validateForConnectionTest(current, endpoint)
        if (validationError != null) {
            updateEndpointState(editorId) {
                copy(testState = EndpointConnectionState.Failure(validationError))
            }
            return
        }

        updateEndpointState(editorId) {
            copy(testState = EndpointConnectionState.Testing)
        }

        viewModelScope.launch {
            val result = serverConnectionTester.testConnection(
                ConnectionTestRequest(
                    endpointUrl = endpoint.baseUrl,
                    username = current.username.trim(),
                    password = current.password,
                    clientName = current.clientName.ifBlank { DEFAULT_SUBSONIC_CLIENT },
                    apiVersion = current.apiVersion.ifBlank { DEFAULT_SUBSONIC_API_VERSION },
                ),
            )

            when (result) {
                is ConnectionTestResult.Success -> {
                    updateEndpointState(editorId) {
                        copy(
                            testState = EndpointConnectionState.Success(
                                serverVersion = result.serverVersion,
                                latencyMs = result.latencyMs,
                            ),
                        )
                    }
                    snackbarMessages.tryEmit(UiText.resource(R.string.server_config_connected_to, result.endpointUrl))
                }

                is ConnectionTestResult.Failure -> {
                    updateEndpointState(editorId) {
                        copy(testState = EndpointConnectionState.Failure(UiText.dynamic(result.message)))
                    }
                    snackbarMessages.tryEmit(UiText.dynamic(result.message))
                }
            }
        }
    }

    fun saveServer() {
        val current = editorState.value ?: return
        val validationError = validateDraft(current)
        if (validationError != null) {
            updateEditor { copy(formError = validationError) }
            return
        }

        viewModelScope.launch {
            isSaving.value = true
            runCatching {
                serverConfigRepository.saveServerConfig(current.toDomain())
            }.onSuccess {
                snackbarMessages.emit(
                    if (current.serverId == 0L) {
                        UiText.resource(R.string.server_config_server_saved)
                    } else {
                        UiText.resource(R.string.server_config_server_updated)
                    },
                )
                editorState.value = null
            }.onFailure { throwable ->
                updateEditor {
                    copy(formError = throwable.localizedOr(R.string.server_config_error_save))
                }
            }
            isSaving.value = false
        }
    }

    fun deleteServer(serverId: Long) {
        viewModelScope.launch {
            serverConfigRepository.deleteServerConfig(serverId)
            if (editorState.value?.serverId == serverId) {
                editorState.value = null
            }
            snackbarMessages.emit(UiText.resource(R.string.server_config_server_removed))
        }
    }

    private fun validateDraft(draft: ServerEditorState): UiText? {
        if (draft.name.isBlank()) return UiText.resource(R.string.server_config_error_enter_name)
        if (draft.username.isBlank()) return UiText.resource(R.string.server_config_error_enter_username)
        if (draft.password.isBlank()) return UiText.resource(R.string.server_config_error_enter_password)
        if (draft.endpoints.isEmpty()) return UiText.resource(R.string.server_config_error_add_endpoint)
        if (draft.endpoints.any { it.baseUrl.isBlank() }) return UiText.resource(R.string.server_config_error_endpoint_url)
        return null
    }

    private fun validateForConnectionTest(
        draft: ServerEditorState,
        endpoint: ServerEndpointEditorState,
    ): UiText? {
        if (draft.username.isBlank()) return UiText.resource(R.string.server_config_error_test_username)
        if (draft.password.isBlank()) return UiText.resource(R.string.server_config_error_test_password)
        if (endpoint.baseUrl.isBlank()) return UiText.resource(R.string.server_config_error_test_endpoint_url)
        return null
    }

    private fun updateEditor(transform: ServerEditorState.() -> ServerEditorState) {
        editorState.update { current ->
            current?.transform()
        }
    }

    private fun updateEndpointState(
        editorId: Long,
        transform: ServerEndpointEditorState.() -> ServerEndpointEditorState,
    ) {
        updateEditor {
            copy(
                endpoints = endpoints.map { endpoint ->
                    if (endpoint.editorId == editorId) endpoint.transform() else endpoint
                },
            )
        }
    }

    private fun newEndpoint(): ServerEndpointEditorState {
        nextEditorId += 1
        return ServerEndpointEditorState(
            editorId = nextEditorId,
        )
    }

    private fun ServerConfig.toEditorState(): ServerEditorState {
        return ServerEditorState(
            serverId = id,
            name = name,
            username = username,
            password = password,
            clientName = clientName,
            apiVersion = apiVersion,
            endpoints = endpoints.map { endpoint ->
                nextEditorId += 1
                ServerEndpointEditorState(
                    editorId = nextEditorId,
                    persistedId = endpoint.id,
                    label = endpoint.label,
                    baseUrl = endpoint.baseUrl,
                )
            },
        )
    }

    private fun ServerEditorState.toDomain(): ServerConfig {
        return ServerConfig(
            id = serverId,
            name = name.trim(),
            username = username.trim(),
            password = password,
            clientName = clientName.ifBlank { DEFAULT_SUBSONIC_CLIENT }.trim(),
            apiVersion = apiVersion.ifBlank { DEFAULT_SUBSONIC_API_VERSION }.trim(),
            endpoints = endpoints.mapIndexed { index, endpoint ->
                ServerEndpoint(
                    id = endpoint.persistedId,
                    label = endpoint.label.trim(),
                    baseUrl = endpoint.baseUrl.trim(),
                    order = index,
                )
            },
        )
    }
}

data class ServerConfigUiState(
    val servers: List<ServerConfig> = emptyList(),
    val editor: ServerEditorState? = null,
    val isSaving: Boolean = false,
)

data class ServerEditorState(
    val serverId: Long = 0,
    val name: String = "",
    val username: String = "",
    val password: String = "",
    val clientName: String = DEFAULT_SUBSONIC_CLIENT,
    val apiVersion: String = DEFAULT_SUBSONIC_API_VERSION,
    val endpoints: List<ServerEndpointEditorState> = emptyList(),
    val formError: UiText? = null,
)

data class ServerEndpointEditorState(
    val editorId: Long,
    val persistedId: Long = 0,
    val label: String = "",
    val baseUrl: String = "",
    val testState: EndpointConnectionState = EndpointConnectionState.Idle,
)

sealed interface EndpointConnectionState {
    data object Idle : EndpointConnectionState

    data object Testing : EndpointConnectionState

    data class Success(
        val serverVersion: String?,
        val latencyMs: Long,
    ) : EndpointConnectionState

    data class Failure(
        val message: UiText,
    ) : EndpointConnectionState
}

private fun List<ServerEndpointEditorState>.resetConnectionStates(): List<ServerEndpointEditorState> {
    return map { endpoint ->
        endpoint.copy(testState = EndpointConnectionState.Idle)
    }
}
