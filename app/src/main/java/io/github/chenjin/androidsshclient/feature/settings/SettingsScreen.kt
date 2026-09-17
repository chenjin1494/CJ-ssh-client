package io.github.chenjin.androidsshclient.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenjin.androidsshclient.core.model.LogLevel
import io.github.chenjin.androidsshclient.core.model.TerminalFont
import io.github.chenjin.androidsshclient.core.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val value by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("外观")
            ChoiceRow(ThemeMode.entries.map { it.name }, value.themeMode.name) { viewModel.update(value.copy(themeMode = ThemeMode.valueOf(it))) }
            Toggle("动态取色", "Android 12+ 跟随系统配色", value.dynamicColor) { viewModel.update(value.copy(dynamicColor = it)) }
            HorizontalDivider()
            Section("终端")
            Text("字体")
            ChoiceRow(TerminalFont.entries.map { it.name.replace('_', ' ') }, value.terminalFont.name.replace('_', ' ')) {
                viewModel.update(value.copy(terminalFont = TerminalFont.valueOf(it.replace(' ', '_'))))
            }
            Text("字号 ${value.fontSize.toInt()} sp")
            Slider(value.fontSize, { viewModel.update(value.copy(fontSize = it)) }, valueRange = 9f..28f, steps = 18)
            Text("行高 ${"%.2f".format(value.lineHeight)}")
            Slider(value.lineHeight, { viewModel.update(value.copy(lineHeight = it)) }, valueRange = 1f..1.6f, steps = 5)
            Toggle("编程连字", "启用 =>、!=、->、<= 等连字", value.ligatures) { viewModel.update(value.copy(ligatures = it)) }
            Toggle("保持屏幕常亮", "终端打开时不自动锁屏", value.keepScreenOn) { viewModel.update(value.copy(keepScreenOn = it)) }
            HorizontalDivider()
            Section("终端配色")
            ChoiceRow(listOf("One Dark", "Dracula", "Solarized"), value.terminalScheme) { viewModel.update(value.copy(terminalScheme = it)) }
            Section("日志级别")
            ChoiceRow(LogLevel.entries.map { it.name }, value.logLevel.name) { viewModel.update(value.copy(logLevel = LogLevel.valueOf(it))) }
            Text("日志始终脱敏，不记录密码、密钥正文或会话明文。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun Section(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
@Composable private fun ChoiceRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { items.forEach { item -> AssistChip(onClick = { onSelect(item) }, label = { Text(item) }) } }
}
@Composable private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, onChange)
    }
}
