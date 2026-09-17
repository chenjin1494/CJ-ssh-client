package io.github.chenjin.androidsshclient.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenjin.androidsshclient.core.model.SshKeyInfo
import io.github.chenjin.androidsshclient.core.ssh.PortForward
import io.github.chenjin.androidsshclient.core.ssh.RemoteFile
import io.github.chenjin.androidsshclient.core.ssh.SshManager
import io.github.chenjin.androidsshclient.data.KeyRepository
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val manager: SshManager,
    private val keyRepository: KeyRepository,
) : ViewModel() {
    val sessions = manager.tabs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val keys = keyRepository.keys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<SshKeyInfo>())
    val selectedSession = MutableStateFlow<String?>(null)
    val path = MutableStateFlow("/")
    val files = MutableStateFlow<List<RemoteFile>>(emptyList())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val forwards = MutableStateFlow<List<PortForward>>(emptyList())

    fun load(pathValue: String = path.value) = viewModelScope.launch {
        val tab = selectedSession.value ?: return@launch
        busy.value = true
        runCatching { manager.listFiles(tab, pathValue) }
            .onSuccess { path.value = pathValue; files.value = it }
            .onFailure { message.value = safe(it) }
        busy.value = false
    }

    fun upload(source: InputStream, name: String) = viewModelScope.launch {
        val tab = selectedSession.value ?: return@launch
        busy.value = true
        val remote = if (path.value == "/") "/$name" else "${path.value}/$name"
        runCatching { manager.upload(tab, source, remote) }.onFailure { message.value = safe(it) }
        busy.value = false; load()
    }

    fun download(file: RemoteFile, destination: OutputStream) = viewModelScope.launch {
        val tab = selectedSession.value ?: return@launch
        busy.value = true
        runCatching { manager.download(tab, file.path, destination) }
            .onSuccess { message.value = "已下载 ${file.name}" }.onFailure { message.value = safe(it) }
        busy.value = false
    }

    fun delete(file: RemoteFile) = viewModelScope.launch {
        val tab = selectedSession.value ?: return@launch
        runCatching { manager.deleteRemote(tab, file) }.onFailure { message.value = safe(it) }
        load()
    }

    fun rename(file: RemoteFile, name: String) = viewModelScope.launch {
        val tab = selectedSession.value ?: return@launch
        val target = file.path.substringBeforeLast('/', "") + "/" + name
        runCatching { manager.renameRemote(tab, file.path, target) }.onFailure { message.value = safe(it) }
        load()
    }

    fun addForward(local: Int, remoteHost: String, remotePort: Int) = viewModelScope.launch {
        val tab = selectedSession.value ?: return@launch
        runCatching { manager.addForward(tab, local, remoteHost, remotePort) }
            .onSuccess { forwards.value = forwards.value + it }.onFailure { message.value = safe(it) }
    }

    fun removeForward(forward: PortForward) = viewModelScope.launch {
        selectedSession.value?.let { manager.removeForward(it, forward.localPort) }
        forwards.value = forwards.value - forward
    }

    fun generateKey(name: String, type: String) = viewModelScope.launch {
        runCatching { keyRepository.generate(name, type) }.onFailure { message.value = safe(it) }
    }

    fun importKey(name: String, bytes: ByteArray, passphrase: CharArray?) = viewModelScope.launch {
        runCatching { keyRepository.import(name, bytes, passphrase) }.onFailure { message.value = safe(it) }
    }

    suspend fun publicKey(id: Long) = keyRepository.exportPublic(id)
    fun deleteKey(id: Long) = viewModelScope.launch { keyRepository.delete(id) }
    fun clearMessage() { message.value = null }
    private fun safe(error: Throwable) = "操作失败 (${error::class.java.simpleName})"
}
