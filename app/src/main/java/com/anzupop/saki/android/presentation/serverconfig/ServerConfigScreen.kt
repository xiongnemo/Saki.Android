package com.anzupop.saki.android.presentation.serverconfig

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anzupop.saki.android.domain.model.ServerConfig
import com.anzupop.saki.android.domain.model.ServerEndpoint
import com.anzupop.saki.android.ui.theme.SakiAndroidTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ServerConfigRoute(
    modifier: Modifier = Modifier,
    onCloseManager: (() -> Unit)? = null,
    viewModel: ServerConfigViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }

    BackHandler(enabled = uiState.editor != null || onCloseManager != null) {
        if (uiState.editor != null) {
            viewModel.dismissEditor()
        } else {
            onCloseManager?.invoke()
        }
    }

    ServerConfigScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        onAddServer = viewModel::startAddingServer,
        onEditServer = viewModel::editServer,
        onDeleteServer = viewModel::deleteServer,
        onDismissEditor = viewModel::dismissEditor,
        onNameChanged = viewModel::updateServerName,
        onUsernameChanged = viewModel::updateUsername,
        onPasswordChanged = viewModel::updatePassword,
        onClientNameChanged = viewModel::updateClientName,
        onApiVersionChanged = viewModel::updateApiVersion,
        onAddEndpoint = viewModel::addEndpoint,
        onRemoveEndpoint = viewModel::removeEndpoint,
        onEndpointLabelChanged = viewModel::updateEndpointLabel,
        onEndpointUrlChanged = viewModel::updateEndpointUrl,
        onSetPrimaryEndpoint = viewModel::setPrimaryEndpoint,
        onTestEndpoint = viewModel::testEndpoint,
        onSaveServer = viewModel::saveServer,
    )
}

@Composable
fun ServerConfigScreen(
    uiState: ServerConfigUiState,
    snackbarHostState: SnackbarHostState,
    onAddServer: () -> Unit,
    onEditServer: (Long) -> Unit,
    onDeleteServer: (Long) -> Unit,
    onDismissEditor: () -> Unit,
    onNameChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onClientNameChanged: (String) -> Unit,
    onApiVersionChanged: (String) -> Unit,
    onAddEndpoint: () -> Unit,
    onRemoveEndpoint: (Long) -> Unit,
    onEndpointLabelChanged: (Long, String) -> Unit,
    onEndpointUrlChanged: (Long, String) -> Unit,
    onSetPrimaryEndpoint: (Long) -> Unit,
    onTestEndpoint: (Long) -> Unit,
    onSaveServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundBrush = remember(colorScheme) {
        Brush.verticalGradient(
            colors = listOf(
                colorScheme.primary.copy(alpha = 0.14f).compositeOver(colorScheme.background),
                colorScheme.secondary.copy(alpha = 0.10f).compositeOver(colorScheme.surface),
                colorScheme.background,
            ),
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (uiState.servers.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddServer,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        text = "Add server",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    HeroSection(
                        serverCount = uiState.servers.size,
                        modifier = Modifier.statusBarsPadding(),
                    )
                }

                if (uiState.servers.isEmpty()) {
                    item {
                        EmptyStateCard(onAddServer = onAddServer)
                    }
                } else {
                    items(
                        items = uiState.servers,
                        key = ServerConfig::id,
                    ) { server ->
                        ServerCard(
                            server = server,
                            onEditServer = { onEditServer(server.id) },
                            onDeleteServer = { onDeleteServer(server.id) },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.editor != null,
                enter = fadeIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                ) + slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                    initialOffsetY = { fullHeight -> fullHeight / 3 },
                ),
                exit = fadeOut(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + slideOutVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    targetOffsetY = { fullHeight -> fullHeight / 2 },
                ),
            ) {
                uiState.editor?.let { editor ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onDismissEditor,
                                ),
                        )

                        ServerEditorSheet(
                            editor = editor,
                            isSaving = uiState.isSaving,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .safeDrawingPadding()
                                .navigationBarsPadding()
                                .imePadding()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            onDismissEditor = onDismissEditor,
                            onNameChanged = onNameChanged,
                            onUsernameChanged = onUsernameChanged,
                            onPasswordChanged = onPasswordChanged,
                            onClientNameChanged = onClientNameChanged,
                            onApiVersionChanged = onApiVersionChanged,
                            onAddEndpoint = onAddEndpoint,
                            onRemoveEndpoint = onRemoveEndpoint,
                            onEndpointLabelChanged = onEndpointLabelChanged,
                            onEndpointUrlChanged = onEndpointUrlChanged,
                            onSetPrimaryEndpoint = onSetPrimaryEndpoint,
                            onTestEndpoint = onTestEndpoint,
                            onSaveServer = onSaveServer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSection(
    serverCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Server Profiles",
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = "Store one Subsonic server with multiple endpoints so local LAN and remote WAN addresses live under the same profile.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = if (serverCount == 0) "No saved servers yet" else "$serverCount server profile${if (serverCount == 1) "" else "s"} ready",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    onAddServer: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Start with one server profile",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Each profile can carry a primary endpoint plus fallbacks you will use in Phase 3. Save the local and remote addresses together now.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAddServer) {
                Text("Create server")
            }
        }
    }
}

@Composable
private fun ServerCard(
    server: ServerConfig,
    onEditServer: () -> Unit,
    onDeleteServer: () -> Unit,
) {
    val primaryEndpoint = server.endpoints.firstOrNull(ServerEndpoint::isPrimary) ?: server.endpoints.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "${server.username}  •  ${server.clientName}  •  API ${server.apiVersion}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = "${server.endpoints.size} endpoint${if (server.endpoints.size == 1) "" else "s"}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            primaryEndpoint?.let { endpoint ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Primary endpoint",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = endpoint.baseUrl,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                server.endpoints.forEach { endpoint ->
                    EndpointBadge(endpoint = endpoint)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onEditServer) {
                    Text("Manage")
                }
                TextButton(onClick = onDeleteServer) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun EndpointBadge(
    endpoint: ServerEndpoint,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (endpoint.isPrimary) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            text = buildString {
                append(endpoint.label)
                append(" • ")
                append(endpoint.baseUrl)
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (endpoint.isPrimary) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ServerEditorSheet(
    editor: ServerEditorState,
    isSaving: Boolean,
    onDismissEditor: () -> Unit,
    onNameChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onClientNameChanged: (String) -> Unit,
    onApiVersionChanged: (String) -> Unit,
    onAddEndpoint: () -> Unit,
    onRemoveEndpoint: (Long) -> Unit,
    onEndpointLabelChanged: (Long, String) -> Unit,
    onEndpointUrlChanged: (Long, String) -> Unit,
    onSetPrimaryEndpoint: (Long) -> Unit,
    onTestEndpoint: (Long) -> Unit,
    onSaveServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (editor.serverId == 0L) "New server" else "Edit server",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "Define credentials once and attach as many endpoints as you need.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismissEditor) {
                    Text("Close")
                }
            }

            editor.formError?.let { error ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            OutlinedTextField(
                value = editor.name,
                onValueChange = onNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Profile name") },
                placeholder = { Text("Home library") },
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = editor.username,
                    onValueChange = onUsernameChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("Username") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = editor.password,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = editor.clientName,
                    onValueChange = onClientNameChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("Client name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = editor.apiVersion,
                    onValueChange = onApiVersionChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("API version") },
                    singleLine = true,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Endpoints",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Mark one endpoint as primary. Use the test action to confirm the draft credentials work before saving.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            editor.endpoints.forEachIndexed { index, endpoint ->
                EndpointEditorCard(
                    index = index,
                    endpoint = endpoint,
                    canRemove = editor.endpoints.size > 1,
                    onLabelChanged = { onEndpointLabelChanged(endpoint.editorId, it) },
                    onUrlChanged = { onEndpointUrlChanged(endpoint.editorId, it) },
                    onSetPrimary = { onSetPrimaryEndpoint(endpoint.editorId) },
                    onRemove = { onRemoveEndpoint(endpoint.editorId) },
                    onTest = { onTestEndpoint(endpoint.editorId) },
                )
            }

            TextButton(onClick = onAddEndpoint) {
                Text("Add another endpoint")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSaveServer,
                    enabled = !isSaving,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (editor.serverId == 0L) "Save server" else "Save changes")
                }
                TextButton(
                    onClick = onDismissEditor,
                    enabled = !isSaving,
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun EndpointEditorCard(
    index: Int,
    endpoint: ServerEndpointEditorState,
    canRemove: Boolean,
    onLabelChanged: (String) -> Unit,
    onUrlChanged: (String) -> Unit,
    onSetPrimary: () -> Unit,
    onRemove: () -> Unit,
    onTest: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Endpoint ${index + 1}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = endpoint.isPrimary,
                        onClick = onSetPrimary,
                    )
                    Text(
                        text = "Primary",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            OutlinedTextField(
                value = endpoint.label,
                onValueChange = onLabelChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Label") },
                placeholder = { Text("LAN") },
                singleLine = true,
            )

            OutlinedTextField(
                value = endpoint.baseUrl,
                onValueChange = onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                placeholder = { Text("https://music.example.com") },
                singleLine = true,
            )

            ConnectionStateBanner(
                testState = endpoint.testState,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onTest,
                    enabled = endpoint.testState !is EndpointConnectionState.Testing,
                ) {
                    if (endpoint.testState is EndpointConnectionState.Testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Testing")
                    } else {
                        Text("Test")
                    }
                }

                if (canRemove) {
                    TextButton(onClick = onRemove) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStateBanner(
    testState: EndpointConnectionState,
) {
    when (testState) {
        EndpointConnectionState.Idle -> {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    text = "No test run yet.",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        EndpointConnectionState.Testing -> {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Contacting the endpoint...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        is EndpointConnectionState.Success -> {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = if (testState.serverVersion.isNullOrBlank()) {
                        "Connection successful."
                    } else {
                        "Connection successful. Server API ${testState.serverVersion} responded."
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        is EndpointConnectionState.Failure -> {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = testState.message,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerConfigScreenPreview() {
    SakiAndroidTheme {
        ServerConfigScreen(
            uiState = ServerConfigUiState(
                servers = listOf(
                    ServerConfig(
                        id = 1,
                        name = "Home",
                        username = "nemo",
                        password = "secret",
                        clientName = "Saki.Android",
                        apiVersion = "1.16.1",
                        endpoints = listOf(
                            ServerEndpoint(
                                id = 1,
                                label = "LAN",
                                baseUrl = "http://192.168.1.50:4533",
                                isPrimary = true,
                            ),
                            ServerEndpoint(
                                id = 2,
                                label = "WAN",
                                baseUrl = "https://music.example.com",
                            ),
                        ),
                    ),
                ),
                editor = ServerEditorState(
                    name = "Studio",
                    username = "nemo",
                    password = "secret",
                    endpoints = listOf(
                        ServerEndpointEditorState(
                            editorId = 1,
                            label = "LAN",
                            baseUrl = "http://192.168.1.10:4040",
                            isPrimary = true,
                            testState = EndpointConnectionState.Success(serverVersion = "1.16.1"),
                        ),
                        ServerEndpointEditorState(
                            editorId = 2,
                            label = "WAN",
                            baseUrl = "https://music.example.com",
                            testState = EndpointConnectionState.Failure("HTTP 401"),
                        ),
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAddServer = {},
            onEditServer = {},
            onDeleteServer = {},
            onDismissEditor = {},
            onNameChanged = {},
            onUsernameChanged = {},
            onPasswordChanged = {},
            onClientNameChanged = {},
            onApiVersionChanged = {},
            onAddEndpoint = {},
            onRemoveEndpoint = {},
            onEndpointLabelChanged = { _, _ -> },
            onEndpointUrlChanged = { _, _ -> },
            onSetPrimaryEndpoint = {},
            onTestEndpoint = {},
            onSaveServer = {},
        )
    }
}
