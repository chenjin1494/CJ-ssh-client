package io.github.chenjin.androidsshclient.core.model

enum class AuthType { PASSWORD, PRIVATE_KEY, PRIVATE_KEY_PASSWORD }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class TerminalFont { FIRA_CODE, JETBRAINS_MONO, SYSTEM_MONO, CUSTOM }
enum class LogLevel { ERROR, WARN, INFO, DEBUG }

data class ConnectionProfile(
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType,
    val keyId: Long? = null,
    val autoReconnect: Boolean = false,
    val lastConnectedAt: Long? = null,
)

data class ConnectionDraft(
    val id: Long = 0,
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    val password: String = "",
    val keyId: Long? = null,
    val keyPassphrase: String = "",
    val autoReconnect: Boolean = false,
)

data class ConnectionCredentials(
    val profile: ConnectionProfile,
    val password: CharArray?,
    val privateKey: ByteArray?,
    val keyPassphrase: CharArray?,
)

data class SshKeyInfo(val id: Long, val name: String, val algorithm: String, val publicKey: String, val createdAt: Long)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val terminalFont: TerminalFont = TerminalFont.FIRA_CODE,
    val customFontName: String? = null,
    val customFontRevision: Long = 0,
    val fontSize: Float = 14f,
    val lineHeight: Float = 1.2f,
    val ligatures: Boolean = true,
    val keepScreenOn: Boolean = false,
    val logLevel: LogLevel = LogLevel.INFO,
    val terminalScheme: String = "One Dark",
)
