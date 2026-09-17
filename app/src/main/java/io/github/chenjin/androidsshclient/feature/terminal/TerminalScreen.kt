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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenjin.androidsshclient.core.ssh.SshStatus
import io.github.chenjin.androidsshclient.core.terminal.TerminalColorSchemes
import io.github.chenjin.androidsshclient.core.terminal.TerminalKey
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
    val hostView = LocalView.current
    DisposableEffect(hostView, settings.keepScreenOn) {
        val previous = hostView.keepScreenOn
        hostView.keepScreenOn = settings.keepScreenOn
        onDispose { hostView.keepScreenOn = previous }
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

    val scheme = TerminalColorSchemes.byName(settings.terminalScheme)
    var ctrl by remember(active.id) { mutableStateOf(false) }
    var alt by remember(active.id) { mutableStateOf(false) }
    var terminalView by remember(active.id) { mutableStateOf<TerminalView?>(null) }
    var pendingMultilinePaste by remember(active.id) { mutableStateOf<String?>(null) }
    val dispatch: (String) -> Unit = { input ->
        var payload = input
        if (ctrl && payload.isNotEmpty()) {
            val first = payload.first()
            payload = ((first.uppercaseChar().code and 0x1f).toChar()).toString() + payload.drop(1)
        }
        if (alt) payload = "\u001b$payload"
        viewModel.send(active.id, payload)
        if (ctrl) ctrl = false
        if (alt) alt = false
    }

    Column(Modifier.fillMaxSize().background(Color(scheme.background)).imePadding()) {
        TabRow(
            selectedTabIndex = tabs.indexOfFirst { it.id == active.id }.coerceAtLeast(0),
            containerColor = Color(scheme.background),
            contentColor = Color(scheme.foreground),
        ) {
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
            factory = { context -> TerminalView(context).also { terminalView = it } },
            update = { terminal ->
                terminalView = terminal
                terminal.setInputListener(dispatch)
                terminal.setPasteConfirmationListener { pendingMultilinePaste = it }
                terminal.setTerminalSizeListener(active.id) { columns, rows, width, height ->
                    viewModel.resize(active.id, columns, rows, width, height)
                }
                terminal.configure(settings.fontSize, settings.lineHeight, settings.ligatures, settings.terminalFont, settings.terminalScheme, settings.customFontRevision)
                terminal.setTerminalText(active.transcript)
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        ExtraKeys(
            terminalView = terminalView,
            ctrl = ctrl,
            alt = alt,
            background = Color(scheme.background),
            foreground = Color(scheme.foreground),
            onToggleCtrl = { ctrl = !ctrl },
            onToggleAlt = { alt = !alt },
            onSend = dispatch,
            onPaste = { text -> terminalView?.paste(text) ?: dispatch(text) },
            onKeyboard = { terminalView?.focusInput() },
        )
    }
    pendingMultilinePaste?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingMultilinePaste = null },
            title = { Text("粘贴多行文本？") },
            text = { Text("远端未启用 bracketed paste。继续可能立即执行多条命令。") },
            confirmButton = {
                TextButton(onClick = {
                    terminalView?.pasteConfirmed(text)
                    pendingMultilinePaste = null
                }) { Text("继续粘贴") }
            },
            dismissButton = { TextButton(onClick = { pendingMultilinePaste = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ExtraKeys(
    terminalView: TerminalView?,
    ctrl: Boolean,
    alt: Boolean,
    background: Color,
    foreground: Color,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onSend: (String) -> Unit,
    onPaste: (String) -> Unit,
    onKeyboard: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val keys = listOf(
        "ESC" to TerminalKey.ESC, "TAB" to TerminalKey.TAB, "HOME" to TerminalKey.HOME,
        "↑" to TerminalKey.UP, "END" to TerminalKey.END, "PGUP" to TerminalKey.PAGE_UP,
        "←" to TerminalKey.LEFT, "↓" to TerminalKey.DOWN, "→" to TerminalKey.RIGHT,
        "PGDN" to TerminalKey.PAGE_DOWN,
    )
    Row(
        Modifier.fillMaxWidth().background(background).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(onClick = onToggleCtrl, label = { Text("CTRL") }, leadingIcon = if (ctrl) ({ Text("●", color = Color(0xff62d9ba)) }) else null)
        AssistChip(onClick = onToggleAlt, label = { Text("ALT") }, leadingIcon = if (alt) ({ Text("●", color = Color(0xff62d9ba)) }) else null)
        AssistChip(onClick = { onSend("/") }, label = { Text("/") })
        AssistChip(onClick = { onSend("-") }, label = { Text("-") })
        keys.forEach { (label, key) ->
            AssistChip(onClick = { onSend(terminalView?.keySequence(key) ?: fallbackKeySequence(key)) }, label = { Text(label) })
        }
        IconButton(onClick = { clipboard.getText()?.text?.let(onPaste) }) { Icon(Icons.Outlined.ContentPaste, "粘贴", tint = foreground) }
        IconButton(onClick = onKeyboard) { Icon(Icons.Outlined.Keyboard, "显示输入法", tint = foreground) }
    }
}

private fun fallbackKeySequence(key: TerminalKey): String = when (key) {
    TerminalKey.ESC -> "\u001b"
    TerminalKey.TAB -> "\t"
    TerminalKey.HOME -> "\u001b[H"
    TerminalKey.UP -> "\u001b[A"
    TerminalKey.END -> "\u001b[F"
    TerminalKey.PAGE_UP -> "\u001b[5~"
    TerminalKey.LEFT -> "\u001b[D"
    TerminalKey.DOWN -> "\u001b[B"
    TerminalKey.RIGHT -> "\u001b[C"
    TerminalKey.PAGE_DOWN -> "\u001b[6~"
}

private fun statusColor(status: SshStatus): Color = when (status) {
    SshStatus.CONNECTED -> Color(0xff62d9ba)
    SshStatus.ERROR -> Color(0xffff6b6b)
    SshStatus.DISCONNECTED -> Color(0xff8b949e)
    else -> Color(0xffffcb6b)
}
