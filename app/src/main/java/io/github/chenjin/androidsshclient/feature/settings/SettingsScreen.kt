package io.github.chenjin.androidsshclient.feature.settings

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenjin.androidsshclient.core.model.LogLevel
import io.github.chenjin.androidsshclient.core.model.TerminalFont
import io.github.chenjin.androidsshclient.core.model.ThemeMode
import io.github.chenjin.androidsshclient.core.terminal.TerminalColorSchemes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val value by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: "自定义字体"
            val stream = context.contentResolver.openInputStream(uri)
            if (stream != null) viewModel.importFont(name, stream) else viewModel.showMessage("无法读取该字体文件")
        }
    }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Section("外观")
            ChoiceRow(ThemeMode.entries.map { it.name }, value.themeMode.name) { viewModel.update(value.copy(themeMode = ThemeMode.valueOf(it))) }
            Toggle("动态取色", "Android 12+ 跟随系统配色", value.dynamicColor) { viewModel.update(value.copy(dynamicColor = it)) }
            HorizontalDivider()
            Section("终端")
            Text("字体")
            val fonts = buildList {
                add("Fira Code Nerd Font" to TerminalFont.FIRA_CODE)
                add("JetBrains Mono" to TerminalFont.JETBRAINS_MONO)
                add("系统等宽" to TerminalFont.SYSTEM_MONO)
                if (value.customFontName != null) add("自定义" to TerminalFont.CUSTOM)
            }
            ChoiceRow(fonts.map { it.first }, fonts.firstOrNull { it.second == value.terminalFont }?.first ?: fonts.first().first) { label ->
                viewModel.update(value.copy(terminalFont = fonts.first { it.first == label }.second))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/vnd.ms-opentype", "application/octet-stream")) }) {
                    Icon(Icons.Outlined.UploadFile, null)
                    Text(if (value.customFontName == null) "导入 TTF/OTF" else "更换字体", Modifier.padding(start = 8.dp))
                }
                value.customFontName?.let { name ->
                    Text(name, Modifier.weight(1f), maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = viewModel::removeCustomFont) { Icon(Icons.Outlined.Delete, "移除自定义字体") }
                }
            }
            Text("字号 ${value.fontSize.toInt()} sp")
            Slider(value.fontSize, { viewModel.update(value.copy(fontSize = it)) }, valueRange = 9f..28f, steps = 18)
            Text("行高 ${"%.2f".format(value.lineHeight)}")
            Slider(value.lineHeight, { viewModel.update(value.copy(lineHeight = it)) }, valueRange = 1f..1.6f, steps = 5)
            Toggle("编程连字", "启用 =>、!=、->、<= 等连字", value.ligatures) { viewModel.update(value.copy(ligatures = it)) }
            Toggle("保持屏幕常亮", "终端打开时不自动锁屏", value.keepScreenOn) { viewModel.update(value.copy(keepScreenOn = it)) }
            HorizontalDivider()
            Section("终端配色")
            ChoiceRow(TerminalColorSchemes.all.map { it.name }, value.terminalScheme) { viewModel.update(value.copy(terminalScheme = it)) }
            Section("日志级别")
            ChoiceRow(LogLevel.entries.map { it.name }, value.logLevel.name) { viewModel.update(value.copy(logLevel = LogLevel.valueOf(it))) }
            Text("日志始终脱敏，不记录密码、密钥正文或会话明文。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))

@Composable
private fun ChoiceRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item -> FilterChip(selected = item == selected, onClick = { onSelect(item) }, label = { Text(item) }) }
    }
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}
