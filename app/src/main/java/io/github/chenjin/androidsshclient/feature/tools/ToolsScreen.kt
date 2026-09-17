package io.github.chenjin.androidsshclient.feature.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenjin.androidsshclient.core.ssh.RemoteFile
import io.github.chenjin.androidsshclient.core.ssh.SshStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(viewModel: ToolsViewModel = hiltViewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selected by viewModel.selectedSession.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(sessions, selected) {
        if (selected == null || sessions.none { it.id == selected }) {
            viewModel.selectedSession.value = sessions.firstOrNull { it.status == SshStatus.CONNECTED }?.id
        }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("工具") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(tab) {
                listOf("SFTP", "端口转发", "密钥").forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                }
            }
            if (tab < 2) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sessions.filter { it.status == SshStatus.CONNECTED }.forEach { session ->
                        AssistChip(onClick = { viewModel.selectedSession.value = session.id; if (tab == 0) viewModel.load() }, label = { Text(session.title) })
                    }
                    if (sessions.none { it.status == SshStatus.CONNECTED }) Text("需要一个活动 SSH 会话", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (tab) {
                0 -> SftpPane(viewModel)
                1 -> ForwardPane(viewModel)
                else -> KeysPane(viewModel)
            }
        }
    }
}

@Composable
private fun SftpPane(viewModel: ToolsViewModel) {
    val context = LocalContext.current
    val path by viewModel.path.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var downloadTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var renameTarget by remember { mutableStateOf<RemoteFile?>(null) }
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { context.contentResolver.openInputStream(it)?.let { stream -> viewModel.upload(stream, uri.lastPathSegment?.substringAfterLast('/') ?: "upload") } }
    }
    val download = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val file = downloadTarget
        if (uri != null && file != null) context.contentResolver.openOutputStream(uri)?.let { viewModel.download(file, it) }
        downloadTarget = null
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (path != "/") viewModel.load(path.substringBeforeLast('/').ifBlank { "/" }) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "上级目录") }
            Text(path, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { viewModel.load() }) { Icon(Icons.Outlined.Refresh, "刷新") }
            IconButton(onClick = { upload.launch(arrayOf("*/*")) }) { Icon(Icons.Outlined.Upload, "上传") }
        }
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
        if (!busy && files.isEmpty()) EmptyTool(Icons.Outlined.Folder, "目录为空", "可上传文件到当前目录")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(files, key = { it.path }) { file ->
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (file.directory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
                    Text(file.name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    if (file.directory) TextButton(onClick = { viewModel.load(file.path) }) { Text("打开") }
                    else IconButton(onClick = { downloadTarget = file; download.launch(file.name) }) { Icon(Icons.Outlined.Download, "下载") }
                    TextButton(onClick = { renameTarget = file }) { Text("重命名") }
                    IconButton(onClick = { viewModel.delete(file) }) { Icon(Icons.Outlined.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    renameTarget?.let { file ->
        var name by remember(file) { mutableStateOf(file.name) }
        AlertDialog(onDismissRequest = { renameTarget = null }, title = { Text("重命名") }, text = { OutlinedTextField(name, { name = it }, singleLine = true) }, confirmButton = { Button(onClick = { viewModel.rename(file, name); renameTarget = null }) { Text("保存") } }, dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } })
    }
}

@Composable
private fun ForwardPane(viewModel: ToolsViewModel) {
    val forwards by viewModel.forwards.collectAsStateWithLifecycle()
    var local by remember { mutableStateOf("0") }
    var host by remember { mutableStateOf("") }
    var remote by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("本地端口仅绑定 127.0.0.1，不会暴露到局域网。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(local, { local = it }, Modifier.weight(.7f), label = { Text("本地端口（0 自动）") }, singleLine = true)
            OutlinedTextField(host, { host = it }, Modifier.weight(1f), label = { Text("远端主机") }, singleLine = true)
            OutlinedTextField(remote, { remote = it }, Modifier.weight(.7f), label = { Text("远端端口") }, singleLine = true)
        }
        Button(onClick = { viewModel.addForward(local.toIntOrNull() ?: 0, host, remote.toIntOrNull() ?: 0) }, enabled = host.isNotBlank() && (remote.toIntOrNull() ?: 0) in 1..65535) { Text("启动转发") }
        forwards.forEach { forward ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("127.0.0.1:${forward.localPort}  →  ${forward.remoteHost}:${forward.remotePort}", Modifier.weight(1f))
                IconButton(onClick = { viewModel.removeForward(forward) }) { Icon(Icons.Outlined.Delete, "停止") }
            }
        }
        if (forwards.isEmpty()) EmptyTool(Icons.Outlined.Refresh, "暂无端口转发", "配置远端目标后启动安全隧道")
    }
}

@Composable
private fun KeysPane(viewModel: ToolsViewModel) {
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var generator by remember { mutableStateOf(false) }
    var importedBytes by remember { mutableStateOf<ByteArray?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importedBytes = uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { generator = true }) { Icon(Icons.Outlined.Key, null); Text("生成密钥", Modifier.padding(start = 8.dp)) }
            FilledTonalButton(onClick = { importer.launch(arrayOf("*/*")) }) { Icon(Icons.Outlined.Upload, null); Text("导入私钥", Modifier.padding(start = 8.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        if (keys.isEmpty()) EmptyTool(Icons.Outlined.Key, "还没有密钥", "生成 Ed25519/RSA 密钥或导入 OpenSSH、PEM、PKCS#8 私钥")
        LazyColumn {
            items(keys, key = { it.id }) { key ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(key.name, style = MaterialTheme.typography.titleMedium); Text(key.algorithm, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { scope.launch { clipboard.setText(AnnotatedString(viewModel.publicKey(key.id))) } }) { Icon(Icons.Outlined.ContentCopy, "复制公钥") }
                    IconButton(onClick = { viewModel.deleteKey(key.id) }) { Icon(Icons.Outlined.Delete, "删除") }
                }
            }
        }
    }
    if (generator) KeyDialog("生成密钥", onDismiss = { generator = false }) { name, phrase, type -> viewModel.generateKey(name, type); generator = false }
    importedBytes?.let { bytes -> KeyDialog("导入私钥", onDismiss = { bytes.fill(0); importedBytes = null }) { name, phrase, _ -> viewModel.importKey(name, bytes, phrase.takeIf(String::isNotBlank)?.toCharArray()); importedBytes = null } }
}

@Composable
private fun KeyDialog(title: String, onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var phrase by remember { mutableStateOf("") }; var type by remember { mutableStateOf("ED25519") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
        OutlinedTextField(phrase, { phrase = it }, label = { Text("现有私钥口令（导入时可选）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("ED25519", "RSA").forEach { AssistChip(onClick = { type = it }, label = { Text(it) }) } }
    } }, confirmButton = { Button(onClick = { onConfirm(name, phrase, type) }, enabled = name.isNotBlank()) { Text("确认") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun EmptyTool(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp)); Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
