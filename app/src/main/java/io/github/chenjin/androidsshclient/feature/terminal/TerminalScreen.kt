package io.github.chenjin.androidsshclient.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenjin.androidsshclient.core.model.AppSettings
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
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.Terminal, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("没有活动终端", style = MaterialTheme.typography.titleLarge)
            Text("从连接页选择服务器开始会话", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAddConnection) {
                Icon(Icons.Outlined.Add, null)
                Text("打开连接", Modifier.padding(start = 8.dp))
            }
        }
        return
    }

    val scheme = TerminalColorSchemes.byName(settings.terminalScheme)
    val terminalState = viewModel.terminalState(active.id)
    var ctrl by remember(active.id) { mutableStateOf(false) }
    var alt by remember(active.id) { mutableStateOf(false) }
    var terminalView by remember(active.id) { mutableStateOf<TerminalView?>(null) }
    var pendingMultilinePaste by remember(active.id) { mutableStateOf<String?>(null) }
    var editingKeys by remember { mutableStateOf(false) }
    var layoutDraft by remember(settings.extraKeysLayout, editingKeys) { mutableStateOf(settings.extraKeysLayout) }
    val dispatch: (String) -> Unit = { input ->
        viewModel.send(active.id, applyKeyModifiers(input, ctrl, alt))
        if (ctrl) ctrl = false
        if (alt) alt = false
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(scheme.background)).statusBarsPadding().imePadding(),
    ) {
        val errorReserve = if (active.status == SshStatus.ERROR || active.status == SshStatus.DISCONNECTED) ERROR_BANNER_RESERVE else 0.dp
        val keyPanelMaxHeight = (maxHeight - SESSION_BAR_HEIGHT - MIN_TERMINAL_HEIGHT - errorReserve)
            .coerceAtLeast(MIN_KEY_PANEL_HEIGHT)
            .coerceAtMost(MAX_KEY_PANEL_HEIGHT)
        Column(Modifier.fillMaxSize()) {
        SessionBar(
            tabs = tabs,
            activeId = active.id,
            background = Color(scheme.background),
            foreground = Color(scheme.foreground),
            onSelect = { viewModel.selectedId.value = it },
            onClose = viewModel::close,
        )
        if (active.status == SshStatus.ERROR || active.status == SshStatus.DISCONNECTED) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                terminal.setTerminalSizeListener(active.id, terminalState) { columns, rows, width, height ->
                    viewModel.resize(active.id, columns, rows, width, height)
                }
                terminal.configure(settings.fontSize, settings.lineHeight, settings.ligatures, settings.terminalFont, settings.terminalScheme, settings.customFontRevision)
                terminal.setTerminalText(active.transcript, active.outputLength)
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        ExtraKeys(
            terminalView = terminalView,
            maxHeight = keyPanelMaxHeight,
            layout = settings.extraKeysLayout,
            ctrl = ctrl,
            alt = alt,
            background = Color(scheme.background),
            foreground = Color(scheme.foreground),
            onToggleCtrl = { ctrl = !ctrl },
            onToggleAlt = { alt = !alt },
            onSend = dispatch,
            onPaste = { text -> terminalView?.paste(text) ?: dispatch(text) },
            onKeyboard = { terminalView?.focusInput() },
            onEdit = { editingKeys = true },
        )
        }
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
    if (editingKeys) {
        AlertDialog(
            onDismissRequest = { editingKeys = false },
            title = { Text("自定义扩展键") },
            text = {
                OutlinedTextField(
                    value = layoutDraft,
                    onValueChange = { layoutDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    label = { Text("每行一排，按空格分隔") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalized = normalizeKeyLayout(layoutDraft)
                    viewModel.updateExtraKeysLayout(normalized)
                    editingKeys = false
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    IconButton(onClick = { layoutDraft = AppSettings.DEFAULT_EXTRA_KEYS_LAYOUT }) {
                        Icon(Icons.Outlined.Restore, "恢复默认")
                    }
                    TextButton(onClick = { editingKeys = false }) { Text("取消") }
                }
            },
        )
    }
}

@Composable
private fun SessionBar(
    tabs: List<io.github.chenjin.androidsshclient.core.ssh.SshTab>,
    activeId: String,
    background: Color,
    foreground: Color,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(background).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = tab.id == activeId
            Surface(
                modifier = Modifier.height(38.dp).clickable { onSelect(tab.id) },
                color = if (selected) foreground.copy(alpha = 0.14f) else Color.Transparent,
                shape = RoundedCornerShape(4.dp),
            ) {
                Row(Modifier.padding(start = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(statusColor(tab.status), CircleShape))
                    Text(
                        tab.title,
                        Modifier.padding(start = 7.dp).width(96.dp),
                        color = foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onClose(tab.id) }, Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Close, "关闭 ${tab.title}", Modifier.size(16.dp), tint = foreground)
                    }
                }
            }
        }
    }
    HorizontalDivider(color = foreground.copy(alpha = 0.16f))
}

@Composable
private fun ExtraKeys(
    terminalView: TerminalView?,
    maxHeight: Dp,
    layout: String,
    ctrl: Boolean,
    alt: Boolean,
    background: Color,
    foreground: Color,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onSend: (String) -> Unit,
    onPaste: (String) -> Unit,
    onKeyboard: () -> Unit,
    onEdit: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val rows = remember(layout) { parseKeyLayout(layout) }
    val scroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    HorizontalDivider(color = foreground.copy(alpha = 0.16f))
    Row(
        Modifier.fillMaxWidth().heightIn(max = maxHeight).background(background).navigationBarsPadding(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier.weight(1f).heightIn(max = maxHeight).verticalScroll(verticalScroll)
                .horizontalScroll(scroll).padding(horizontal = 4.dp, vertical = 3.dp),
        ) {
            rows.forEach { keys ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    keys.forEach { token ->
                        if (token == "_") {
                            Spacer(Modifier.width(KEY_WIDTH).height(KEY_HEIGHT))
                        } else {
                            ExtraKeyButton(
                                token = token,
                                selected = token == "CTRL" && ctrl || token == "ALT" && alt,
                                foreground = foreground,
                                onClick = {
                                    when (token) {
                                        "CTRL" -> onToggleCtrl()
                                        "ALT" -> onToggleAlt()
                                        "PASTE" -> clipboard.getText()?.text?.let(onPaste)
                                        "KEYBOARD" -> onKeyboard()
                                        else -> onSend(resolveKeySequence(token, terminalView))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        IconButton(onClick = onEdit, Modifier.size(40.dp)) { Icon(Icons.Outlined.Edit, "自定义扩展键", tint = foreground) }
    }
}

@Composable
private fun ExtraKeyButton(token: String, selected: Boolean, foreground: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.width(KEY_WIDTH).height(KEY_HEIGHT),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(0.dp),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            containerColor = if (selected) foreground.copy(alpha = 0.2f) else foreground.copy(alpha = 0.07f),
            contentColor = foreground,
        ),
    ) {
        when (token) {
            "PASTE" -> Icon(Icons.Outlined.ContentPaste, "粘贴", Modifier.size(18.dp))
            "KEYBOARD" -> Icon(Icons.Outlined.Keyboard, "显示输入法", Modifier.size(18.dp))
            else -> Text(keyLabel(token), maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun parseKeyLayout(value: String): List<List<String>> = normalizeKeyLayout(value)
    .lineSequence()
    .map { row -> row.trim().split(Regex("\\s+")).take(MAX_KEYS_PER_ROW) }
    .filter { it.isNotEmpty() }
    .take(MAX_KEY_ROWS)
    .toList()
    .ifEmpty { parseKeyLayout(AppSettings.DEFAULT_EXTRA_KEYS_LAYOUT) }

private fun normalizeKeyLayout(value: String): String = value.lineSequence()
    .map { row -> row.trim().split(Regex("\\s+")).filter(String::isNotBlank).take(MAX_KEYS_PER_ROW).joinToString(" ") }
    .filter(String::isNotBlank)
    .take(MAX_KEY_ROWS)
    .joinToString("\n")
    .ifBlank { AppSettings.DEFAULT_EXTRA_KEYS_LAYOUT }

private fun resolveKeySequence(token: String, terminalView: TerminalView?): String {
    val key = when (token) {
        "ESC" -> TerminalKey.ESC
        "TAB" -> TerminalKey.TAB
        "HOME" -> TerminalKey.HOME
        "UP" -> TerminalKey.UP
        "END" -> TerminalKey.END
        "PGUP" -> TerminalKey.PAGE_UP
        "LEFT" -> TerminalKey.LEFT
        "DOWN" -> TerminalKey.DOWN
        "RIGHT" -> TerminalKey.RIGHT
        "PGDN" -> TerminalKey.PAGE_DOWN
        else -> null
    }
    return if (key != null) terminalView?.keySequence(key) ?: fallbackKeySequence(key) else when (token) {
        "SPACE" -> " "
        "PIPE" -> "|"
        else -> token
    }
}

private fun keyLabel(token: String): String = when (token) {
    "UP" -> "↑"
    "DOWN" -> "↓"
    "LEFT" -> "←"
    "RIGHT" -> "→"
    "KEYBOARD" -> "⌨"
    else -> token
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

internal fun applyKeyModifiers(input: String, ctrl: Boolean, alt: Boolean): String {
    if (!ctrl && !alt) return input
    val cursorFinal = when (input) {
        "\u001b[A", "\u001bOA" -> 'A'
        "\u001b[B", "\u001bOB" -> 'B'
        "\u001b[C", "\u001bOC" -> 'C'
        "\u001b[D", "\u001bOD" -> 'D'
        "\u001b[H", "\u001bOH" -> 'H'
        "\u001b[F", "\u001bOF" -> 'F'
        else -> null
    }
    if (cursorFinal != null) {
        val modifier = when {
            ctrl && alt -> 7
            ctrl -> 5
            else -> 3
        }
        return "\u001b[1;${modifier}${cursorFinal}"
    }
    val tildeKey = when (input) {
        "\u001b[5~" -> 5
        "\u001b[6~" -> 6
        else -> null
    }
    if (tildeKey != null) {
        val modifier = when {
            ctrl && alt -> 7
            ctrl -> 5
            else -> 3
        }
        return "\u001b[$tildeKey;${modifier}~"
    }
    var result = input
    if (ctrl && result.isNotEmpty()) {
        val first = result.first()
        result = ((first.uppercaseChar().code and 0x1f).toChar()).toString() + result.drop(1)
    }
    return if (alt) "\u001b$result" else result
}

private fun statusColor(status: SshStatus): Color = when (status) {
    SshStatus.CONNECTED -> Color(0xff62d9ba)
    SshStatus.ERROR -> Color(0xffff6b6b)
    SshStatus.DISCONNECTED -> Color(0xff8b949e)
    else -> Color(0xffffcb6b)
}

private val KEY_WIDTH = 52.dp
private val KEY_HEIGHT = 36.dp
private val SESSION_BAR_HEIGHT = 44.dp
private val MIN_TERMINAL_HEIGHT = 64.dp
private val ERROR_BANNER_RESERVE = 48.dp
private val MIN_KEY_PANEL_HEIGHT = 0.dp
private val MAX_KEY_PANEL_HEIGHT = 114.dp
private const val MAX_KEY_ROWS = 5
private const val MAX_KEYS_PER_ROW = 20
