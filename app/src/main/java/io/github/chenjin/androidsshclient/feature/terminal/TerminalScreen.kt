package io.github.chenjin.androidsshclient.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenjin.androidsshclient.core.ssh.SshStatus
import io.github.chenjin.androidsshclient.core.terminal.TerminalView

@Composable
fun TerminalScreen(
    onAddConnection: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selected by viewModel.selectedId.collectAsStateWithLifecycle()
    val active = tabs.firstOrNull { it.id == selected } ?: tabs.lastOrNull()
    LaunchedEffect(active?.id) { viewModel.selectedId.value = active?.id }
    val view = LocalView.current
    DisposableEffect(view, settings.keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }

    if (active == null) {
        Column(
            Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.Terminal, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp)); Text("没有活动终端", style = MaterialTheme.typography.titleLarge)
            Text("从连接页选择服务器开始会话", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp)); Button(onClick = onAddConnection) { Icon(Icons.Outlined.Add, null); Text("打开连接", Modifier.padding(start = 8.dp)) }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(Color(0xff101315))) {
        TabRow(selectedTabIndex = tabs.indexOfFirst { it.id == active.id }.coerceAtLeast(0), containerColor = Color(0xff171b1e), contentColor = Color(0xffd8e2dc)) {
            tabs.forEach { tab ->
                Tab(
                    selected = tab.id == active.id,
                    onClick = { viewModel.selectedId.value = tab.id },
                    text = { Text(tab.title, maxLines = 1) },
                    icon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(statusColor(tab.status), androidx.compose.foundation.shape.CircleShape))
                            IconButton(onClick = { viewModel.close(tab.id) }, Modifier.size(30.dp)) { Icon(Icons.Outlined.Close, "关闭 ${tab.title}", Modifier.size(16.dp)) }
                        }
                    },
                )
            }
        }
        if (active.status == SshStatus.ERROR || active.status == SshStatus.DISCONNECTED) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(active.error ?: "连接已断开", Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    IconButton(onClick = { viewModel.reconnect(active.id) }) { Icon(Icons.Outlined.Refresh, "重连") }
                }
            }
        }
        AndroidView(
            factory = { context -> TerminalView(context) },
            update = { terminal ->
                terminal.setTerminalSizeListener(active.id) { columns, rows, width, height ->
                    viewModel.resize(active.id, columns, rows, width, height)
                }
                terminal.configure(settings.fontSize, settings.lineHeight, settings.ligatures, settings.terminalFont, settings.terminalScheme, settings.customFontRevision)
                terminal.setTerminalText(active.transcript)
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        TerminalInput(active.id, active.transcript, viewModel::send)
    }
}

@Composable
private fun TerminalInput(tabId: String, transcript: String, send: (String, String) -> Unit) {
    var command by remember(tabId) { mutableStateOf("") }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val keys = listOf("Esc" to "\u001b", "Tab" to "\t", "↑" to "\u001b[A", "↓" to "\u001b[B", "←" to "\u001b[D", "→" to "\u001b[C")
    Column(Modifier.background(Color(0xff171b1e))) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { ctrl = !ctrl }, label = { Text("Ctrl") }, leadingIcon = if (ctrl) ({ Text("●", color = Color(0xff62d9ba)) }) else null)
            AssistChip(onClick = { alt = !alt }, label = { Text("Alt") }, leadingIcon = if (alt) ({ Text("●", color = Color(0xff62d9ba)) }) else null)
            keys.forEach { (label, sequence) -> AssistChip(onClick = { send(tabId, sequence) }, label = { Text(label) }) }
            IconButton(onClick = { clipboard.setText(AnnotatedString(stripAnsi(transcript))) }) { Icon(Icons.Outlined.ContentCopy, "复制全部", tint = Color.White) }
            IconButton(onClick = { clipboard.getText()?.text?.let { send(tabId, it) } }) { Icon(Icons.Outlined.ContentPaste, "粘贴", tint = Color.White) }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$", color = Color(0xff62d9ba), style = MaterialTheme.typography.titleMedium)
            BasicTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xffe7eee9)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    var payload = command
                    if (ctrl && payload.isNotEmpty()) payload = ((payload.first().uppercaseChar().code and 0x1f).toChar()).toString() + payload.drop(1)
                    if (alt) payload = "\u001b$payload"
                    send(tabId, "$payload\n"); command = ""; ctrl = false; alt = false
                }),
            )
        }
    }
}

private fun statusColor(status: SshStatus): Color = when (status) {
    SshStatus.CONNECTED -> Color(0xff62d9ba)
    SshStatus.ERROR -> Color(0xffff6b6b)
    SshStatus.DISCONNECTED -> Color(0xff8b949e)
    else -> Color(0xffffcb6b)
}
private fun stripAnsi(value: String): String = value.replace(Regex("\\u001B\\[[0-?]*[ -/]*[@-~]"), "")
