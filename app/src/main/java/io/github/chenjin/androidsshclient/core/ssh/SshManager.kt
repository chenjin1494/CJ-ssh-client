package io.github.chenjin.androidsshclient.core.ssh

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chenjin.androidsshclient.core.database.KnownHostDao
import io.github.chenjin.androidsshclient.core.database.KnownHostEntity
import io.github.chenjin.androidsshclient.core.logging.SecureLogger
import io.github.chenjin.androidsshclient.core.model.ConnectionCredentials
import io.github.chenjin.androidsshclient.core.terminal.TerminalSessionState
import io.github.chenjin.androidsshclient.data.ConnectionRepository
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class SshStatus { CONNECTING, VERIFYING_HOST, CONNECTED, DISCONNECTED, ERROR }

data class SshTab(
    val id: String,
    val connectionId: Long,
    val title: String,
    val endpoint: String,
    val status: SshStatus,
    val transcript: String = "",
    val outputLength: Long = 0,
    val error: String? = null,
)

data class HostKeyPrompt(
    val requestId: String,
    val host: String,
    val port: Int,
    val algorithm: String,
    val fingerprint: String,
    val changed: Boolean,
)

data class RemoteFile(val name: String, val path: String, val directory: Boolean, val size: Long, val modifiedAt: Long)
data class PortForward(val localPort: Int, val remoteHost: String, val remotePort: Int)

@Singleton
class SshManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connections: ConnectionRepository,
    private val knownHosts: KnownHostDao,
    private val logger: SecureLogger,
) {
    private data class PtySize(val columns: Int, val rows: Int, val width: Int, val height: Int)

    private data class Runtime(
        val tabId: String,
        val connectionId: Long,
        val terminalState: TerminalSessionState,
        var session: Session? = null,
        var shell: ChannelShell? = null,
        var input: OutputStream? = null,
        var reader: Job? = null,
        val forwards: MutableSet<Int> = mutableSetOf(),
        @Volatile var ptySize: PtySize = PtySize(120, 36, 0, 0),
        val resizeMutex: Mutex = Mutex(),
        var resizeJob: Job? = null,
        var intentionalClose: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtimes = ConcurrentHashMap<String, Runtime>()
    private val terminalStates = ConcurrentHashMap<String, TerminalSessionState>()
    private val pendingPrompts = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val _tabs = MutableStateFlow<List<SshTab>>(emptyList())
    private val _prompts = MutableSharedFlow<HostKeyPrompt>(extraBufferCapacity = 4)
    val tabs = _tabs.asStateFlow()
    val prompts = _prompts.asSharedFlow()

    fun connect(connectionId: Long) {
        if (_tabs.value.any { it.connectionId == connectionId && it.status in listOf(SshStatus.CONNECTING, SshStatus.CONNECTED, SshStatus.VERIFYING_HOST) }) return
        scope.launch {
            val credentials = runCatching { connections.credentials(connectionId) }.getOrElse {
                logger.warn("credential_load_failed", it); return@launch
            }
            val tabId = UUID.randomUUID().toString()
            val terminalState = TerminalSessionState()
            val runtime = Runtime(tabId, connectionId, terminalState)
            runtimes[tabId] = runtime
            terminalStates[tabId] = terminalState
            val profile = credentials.profile
            putTab(SshTab(tabId, connectionId, profile.name, "${profile.username}@${profile.host}:${profile.port}", SshStatus.CONNECTING))
            try {
                val session = establish(credentials, tabId)
                runtime.session = session
                val shell = session.openChannel("shell") as ChannelShell
                runtime.ptySize.let { shell.setPtyType("xterm-256color", it.columns, it.rows, it.width, it.height) }
                shell.setEnv("TERM", "xterm-256color")
                val output = shell.outputStream
                val input = shell.inputStream
                shell.connect(CHANNEL_TIMEOUT_MS)
                runtime.shell = shell
                runtime.input = output
                updateTab(tabId) { it.copy(status = SshStatus.CONNECTED, error = null) }
                connections.touch(connectionId)
                startForegroundService()
                logger.info("ssh_connected", profile.host)
                runtime.reader = scope.launch { readLoop(runtime, input, profile.autoReconnect) }
            } catch (error: Throwable) {
                logger.warn("ssh_connect_failed", error)
                updateTab(tabId) { it.copy(status = SshStatus.ERROR, error = safeMessage(error)) }
                closeRuntime(runtime)
            } finally {
                credentials.password?.fill('\u0000')
                credentials.keyPassphrase?.fill('\u0000')
                credentials.privateKey?.fill(0)
            }
        }
    }

    private suspend fun establish(credentials: ConnectionCredentials, tabId: String): Session {
        val profile = credentials.profile
        val known = knownHosts.get(profile.host, profile.port)
        val probe = CapturingHostKeyRepository(known)
        val first = createSession(credentials, probe)
        return try {
            withContext(Dispatchers.IO) { first.connect(CONNECT_TIMEOUT_MS) }
            first
        } catch (error: Throwable) {
            first.disconnect()
            val presented = probe.presented ?: throw error
            val changed = known != null
            updateTab(tabId) { it.copy(status = SshStatus.VERIFYING_HOST) }
            val requestId = UUID.randomUUID().toString()
            val answer = CompletableDeferred<Boolean>()
            pendingPrompts[requestId] = answer
            _prompts.emit(
                HostKeyPrompt(requestId, profile.host, profile.port, presented.type,
                    HostKeyFingerprint.sha256(presented.key), changed),
            )
            if (changed) {
                pendingPrompts.remove(requestId)
                throw SecurityException("Host key changed. Connection blocked.")
            }
            val accepted = try { withTimeout(120_000) { answer.await() } } finally { pendingPrompts.remove(requestId) }
            if (!accepted) throw SecurityException("Host key was not accepted")
            knownHosts.put(
                KnownHostEntity(profile.host, profile.port, presented.type,
                    Base64.getEncoder().encodeToString(presented.key), HostKeyFingerprint.sha256(presented.key), System.currentTimeMillis()),
            )
            val strict = CapturingHostKeyRepository(knownHosts.get(profile.host, profile.port))
            createSession(credentials, strict).also { withContext(Dispatchers.IO) { it.connect(CONNECT_TIMEOUT_MS) } }
        }
    }

    private fun createSession(credentials: ConnectionCredentials, repository: HostKeyRepository): Session {
        val jsch = JSch().apply {
            hostKeyRepository = repository
            credentials.privateKey?.let { key ->
                addIdentity(
                    "androidssh-${credentials.profile.id}", key, null,
                    credentials.keyPassphrase?.concatToString()?.encodeToByteArray(),
                )
            }
        }
        return jsch.getSession(credentials.profile.username, credentials.profile.host, credentials.profile.port).apply {
        credentials.password?.let { setPassword(it.concatToString()) }
            setConfig("StrictHostKeyChecking", "yes")
            setConfig("PreferredAuthentications", preferredAuth(credentials))
            serverAliveInterval = 15_000
            serverAliveCountMax = 3
            timeout = 30_000
        }
    }

    private fun preferredAuth(credentials: ConnectionCredentials): String = when {
        credentials.privateKey != null && credentials.password != null -> "publickey,password,keyboard-interactive"
        credentials.privateKey != null -> "publickey"
        else -> "password,keyboard-interactive"
    }

    fun respondToHostKey(requestId: String, accept: Boolean) {
        pendingPrompts.remove(requestId)?.complete(accept)
    }

    fun terminalState(tabId: String): TerminalSessionState = terminalStates.getOrPut(tabId) { TerminalSessionState() }

    fun send(tabId: String, text: String) {
        scope.launch { runtimes[tabId]?.input?.run { write(text.encodeToByteArray()); flush() } }
    }

    fun resize(tabId: String, columns: Int, rows: Int, width: Int, height: Int) {
        runtimes[tabId]?.let { runtime ->
            val size = PtySize(columns.coerceAtLeast(2), rows.coerceAtLeast(1), width.coerceAtLeast(0), height.coerceAtLeast(0))
            runtime.ptySize = size
            runtime.resizeJob?.cancel()
            runtime.resizeJob = scope.launch {
                delay(75)
                runtime.resizeMutex.withLock {
                    if (runtime.ptySize != size) return@withLock
                    runCatching { runtime.shell?.setPtySize(size.columns, size.rows, size.width, size.height) }
                        .onFailure { logger.warn("pty_resize_failed", it) }
                }
            }
        }
    }

    fun reconnect(tabId: String) {
        val id = runtimes[tabId]?.connectionId ?: _tabs.value.firstOrNull { it.id == tabId }?.connectionId ?: return
        disconnect(tabId)
        connect(id)
    }

    fun disconnect(tabId: String) {
        runtimes.remove(tabId)?.let { runtime ->
            runtime.intentionalClose = true
            closeRuntime(runtime)
        }
        updateTab(tabId) { it.copy(status = SshStatus.DISCONNECTED) }
        stopServiceIfIdle()
    }

    fun closeTab(tabId: String) {
        disconnect(tabId)
        terminalStates.remove(tabId)
        _tabs.update { tabs -> tabs.filterNot { it.id == tabId } }
    }

    suspend fun listFiles(tabId: String, path: String): List<RemoteFile> = withSftp(tabId) { sftp ->
        @Suppress("UNCHECKED_CAST")
        (sftp.ls(path) as java.util.Vector<ChannelSftp.LsEntry>)
            .filter { it.filename !in setOf(".", "..") }
            .map { entry ->
                val child = if (path == "/") "/${entry.filename}" else "${path.trimEnd('/')}/${entry.filename}"
                RemoteFile(entry.filename, child, entry.attrs.isDir, entry.attrs.size, entry.attrs.mTime.toLong() * 1000)
            }
            .sortedWith(compareByDescending<RemoteFile> { it.directory }.thenBy { it.name.lowercase() })
    }

    suspend fun upload(tabId: String, source: InputStream, remotePath: String) = withSftp(tabId) { sftp ->
        val temporary = "$remotePath.androidssh-part"
        source.use { sftp.put(it, temporary, ChannelSftp.OVERWRITE) }
        runCatching { sftp.rm(remotePath) }
        sftp.rename(temporary, remotePath)
    }

    suspend fun download(tabId: String, remotePath: String, destination: OutputStream) = withSftp(tabId) { sftp ->
        destination.use { sftp.get(remotePath, it) }
    }

    suspend fun deleteRemote(tabId: String, file: RemoteFile) = withSftp(tabId) { sftp ->
        if (file.directory) sftp.rmdir(file.path) else sftp.rm(file.path)
    }

    suspend fun renameRemote(tabId: String, from: String, to: String) = withSftp(tabId) { it.rename(from, to) }

    suspend fun addForward(tabId: String, localPort: Int, remoteHost: String, remotePort: Int): PortForward =
        withContext(Dispatchers.IO) {
            require(remotePort in 1..65535 && localPort in 0..65535)
            val runtime = requireNotNull(runtimes[tabId])
            val assigned = requireNotNull(runtime.session).setPortForwardingL("127.0.0.1", localPort, remoteHost, remotePort)
            runtime.forwards += assigned
            PortForward(assigned, remoteHost, remotePort)
        }

    suspend fun removeForward(tabId: String, localPort: Int) = withContext(Dispatchers.IO) {
        runtimes[tabId]?.let { runtime ->
            runtime.session?.delPortForwardingL("127.0.0.1", localPort)
            runtime.forwards -= localPort
        }
    }

    private suspend fun <T> withSftp(tabId: String, block: (ChannelSftp) -> T): T = withContext(Dispatchers.IO) {
        val session = requireNotNull(runtimes[tabId]?.session) { "Session is not connected" }
        val channel = session.openChannel("sftp") as ChannelSftp
        try { channel.connect(CHANNEL_TIMEOUT_MS); block(channel) } finally { channel.disconnect() }
    }

    private suspend fun readLoop(runtime: Runtime, input: InputStream, autoReconnect: Boolean) {
        val characters = CharArray(8192)
        val reader = InputStreamReader(input, Charsets.UTF_8)
        try {
            while (true) {
                val count = reader.read(characters)
                if (count < 0) break
                val chunk = characters.concatToString(0, count)
                runtime.terminalState.append(chunk)
                updateTab(runtime.tabId) { tab ->
                    val combined = tab.transcript + chunk
                    tab.copy(
                        transcript = if (combined.length > MAX_TRANSCRIPT) combined.takeLast(MAX_TRANSCRIPT) else combined,
                        outputLength = tab.outputLength + chunk.length,
                    )
                }
            }
        } catch (error: Throwable) {
            if (!runtime.intentionalClose) logger.warn("ssh_stream_closed", error)
        } finally {
            closeRuntime(runtime)
            updateTab(runtime.tabId) { it.copy(status = SshStatus.DISCONNECTED) }
            if (!runtime.intentionalClose && autoReconnect) {
                delay(2_000); closeTab(runtime.tabId); connect(runtime.connectionId)
            } else stopServiceIfIdle()
        }
    }

    private fun closeRuntime(runtime: Runtime) {
        runtime.resizeJob?.cancel()
        runtime.forwards.toList().forEach { port -> runCatching { runtime.session?.delPortForwardingL("127.0.0.1", port) } }
        runCatching { runtime.input?.close() }
        runtime.shell?.disconnect()
        runtime.session?.disconnect()
    }

    private fun putTab(tab: SshTab) = _tabs.update { it + tab }
    private fun updateTab(id: String, transform: (SshTab) -> SshTab) =
        _tabs.update { tabs -> tabs.map { if (it.id == id) transform(it) else it } }

    private fun startForegroundService() {
        ContextCompat.startForegroundService(context, Intent(context, SshSessionService::class.java))
    }
    private fun stopServiceIfIdle() {
        if (_tabs.value.none { it.status == SshStatus.CONNECTED }) context.stopService(Intent(context, SshSessionService::class.java))
    }
    private fun safeMessage(error: Throwable): String = when (error) {
        is SecurityException -> error.message ?: "Security check failed"
        else -> "Connection failed (${error::class.java.simpleName})"
    }
    private data class CapturedHostKey(val type: String, val key: ByteArray)

    private class CapturingHostKeyRepository(private val expected: KnownHostEntity?) : HostKeyRepository {
        @Volatile var presented: CapturedHostKey? = null
        override fun check(host: String, key: ByteArray): Int {
            val candidate = HostKey(host, key)
            presented = CapturedHostKey(candidate.type, key.copyOf())
            if (expected == null) return HostKeyRepository.NOT_INCLUDED
            val same = Base64.getEncoder().encodeToString(key) == expected.publicKey && candidate.type == expected.algorithm
            return if (same) HostKeyRepository.OK else HostKeyRepository.CHANGED
        }
        override fun add(hostkey: HostKey?, ui: com.jcraft.jsch.UserInfo?) = Unit
        override fun remove(host: String?, type: String?) = Unit
        override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
        override fun getKnownHostsRepositoryID(): String = "AndroidSSH strict known hosts"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val CHANNEL_TIMEOUT_MS = 10_000
        const val MAX_TRANSCRIPT = 250_000
    }
}
