package io.github.chenjin.androidsshclient.feature.connections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenjin.androidsshclient.core.model.AuthType
import io.github.chenjin.androidsshclient.core.model.ConnectionDraft
import io.github.chenjin.androidsshclient.core.model.ConnectionProfile
import io.github.chenjin.androidsshclient.core.model.SshKeyInfo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConnectionsScreen(
    onOpenTerminal: () -> Unit,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val query by viewModel.search.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<ConnectionDraft?>(null) }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected.isEmpty()) "连接" else "已选择 ${selected.size} 项") },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { viewModel.delete(selected); selected = emptySet() }) {
                            Icon(Icons.Outlined.Delete, "删除选中连接")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editor = ConnectionDraft() }) { Icon(Icons.Outlined.Add, "新建连接") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { viewModel.search.value = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("搜索名称、主机或用户") },
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(Modifier.height(12.dp))
            AnimatedVisibility(connections.isEmpty()) {
                EmptyConnections { editor = ConnectionDraft() }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(connections, key = { it.id }) { profile ->
                    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) viewModel.delete(profile.id)
                        value == SwipeToDismissBoxValue.EndToStart
                    })
                    SwipeToDismissBox(
                        state = dismiss,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(Modifier.fillMaxSize().padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) {
                                Icon(Icons.Outlined.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        },
                    ) {
                        ConnectionCard(
                            profile = profile, selected = profile.id in selected,
                            onClick = {
                                if (selected.isNotEmpty()) selected = selected.toggle(profile.id)
                                else { viewModel.connect(profile); onOpenTerminal() }
                            },
                            onLongClick = { selected = selected.toggle(profile.id) },
                            onEdit = {
                                editor = ConnectionDraft(
                                    profile.id, profile.name, profile.host, profile.port, profile.username,
                                    profile.authType, keyId = profile.keyId, autoReconnect = profile.autoReconnect,
                                )
                            },
                        )
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
    editor?.let { draft ->
        ConnectionDialog(draft, keys, onDismiss = { editor = null }) {
            viewModel.save(it); editor = null
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConnectionCard(
    profile: ConnectionProfile,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp), contentAlignment = Alignment.Center,
            ) { Icon(if (selected) Icons.Outlined.Check else Icons.Outlined.Computer, null, tint = MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text("${profile.username}@${profile.host}:${profile.port}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                profile.lastConnectedAt?.let { timestamp ->
                    Text("上次连接 ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(timestamp))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    when (profile.authType) {
                        AuthType.PASSWORD -> "密码认证"
                        AuthType.PRIVATE_KEY -> "私钥认证"
                        AuthType.PRIVATE_KEY_PASSWORD -> "私钥 + 密码"
                    },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "编辑") }
        }
    }
}

@Composable
private fun EmptyConnections(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Terminal, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text("还没有 SSH 连接", style = MaterialTheme.typography.titleLarge)
        Text("添加服务器后即可打开安全终端", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAdd) { Icon(Icons.Outlined.Add, null); Text("添加连接", Modifier.padding(start = 8.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionDialog(
    initial: ConnectionDraft,
    keys: List<SshKeyInfo>,
    onDismiss: () -> Unit,
    onSave: (ConnectionDraft) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    val valid = value.name.isNotBlank() && value.host.isNotBlank() && value.username.isNotBlank() && value.port in 1..65535 &&
        (value.authType == AuthType.PASSWORD || value.keyId != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (value.id == 0L) "新建连接" else "编辑连接") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { OutlinedTextField(value.name, { value = value.copy(name = it) }, label = { Text("名称") }, singleLine = true) }
                item { OutlinedTextField(value.host, { value = value.copy(host = it) }, label = { Text("主机") }, singleLine = true) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value.username, { value = value.copy(username = it) }, Modifier.weight(1f), label = { Text("用户") }, singleLine = true)
                        OutlinedTextField(value.port.toString(), { value = value.copy(port = it.toIntOrNull() ?: 0) }, Modifier.weight(.55f), label = { Text("端口") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AuthType.entries.forEach { type ->
                            AssistChip(onClick = { value = value.copy(authType = type) }, label = { Text(when(type) { AuthType.PASSWORD -> "密码"; AuthType.PRIVATE_KEY -> "私钥"; else -> "组合" }) }, leadingIcon = if (value.authType == type) ({ Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }) else null)
                        }
                    }
                }
                if (value.authType != AuthType.PRIVATE_KEY) item {
                    OutlinedTextField(value.password, { value = value.copy(password = it) }, label = { Text(if (value.id == 0L) "密码" else "密码（留空则不修改）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
                if (value.authType != AuthType.PASSWORD) {
                    item { Text("私钥", style = MaterialTheme.typography.labelLarge) }
                    items(keys, key = { it.id }) { key ->
                        AssistChip(
                            onClick = { value = value.copy(keyId = key.id) },
                            label = { Text(key.name) },
                            leadingIcon = { Icon(if (value.keyId == key.id) Icons.Outlined.Check else Icons.Outlined.Key, null, Modifier.size(18.dp)) },
                        )
                    }
                    item { OutlinedTextField(value.keyPassphrase, { value = value.copy(keyPassphrase = it) }, label = { Text("私钥口令（可选）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("自动重连", style = MaterialTheme.typography.bodyLarge)
                            Text("意外断开后自动建立新会话", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(value.autoReconnect, { value = value.copy(autoReconnect = it) })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(value) }, enabled = valid) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id
