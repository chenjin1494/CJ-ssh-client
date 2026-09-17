package io.github.chenjin.androidsshclient.data

import android.content.Context
import android.graphics.Typeface
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chenjin.androidsshclient.core.model.AppSettings
import io.github.chenjin.androidsshclient.core.model.LogLevel
import io.github.chenjin.androidsshclient.core.model.TerminalFont
import io.github.chenjin.androidsshclient.core.model.ThemeMode
import io.github.chenjin.androidsshclient.core.ui.font.AppFonts
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.settingsDataStore by preferencesDataStore("settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val store = context.settingsDataStore
    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[DYNAMIC] ?: true,
            terminalFont = p[FONT]?.let { runCatching { TerminalFont.valueOf(it) }.getOrNull() } ?: TerminalFont.FIRA_CODE,
            customFontName = p[CUSTOM_FONT_NAME],
            customFontRevision = p[CUSTOM_FONT_REVISION] ?: 0,
            fontSize = p[FONT_SIZE] ?: 14f,
            lineHeight = p[LINE_HEIGHT] ?: 1.2f,
            ligatures = p[LIGATURES] ?: true,
            keepScreenOn = p[KEEP_SCREEN] ?: false,
            logLevel = p[LOG_LEVEL]?.let { runCatching { LogLevel.valueOf(it) }.getOrNull() } ?: LogLevel.INFO,
            terminalScheme = p[SCHEME] ?: "One Dark",
            extraKeysLayout = p[EXTRA_KEYS_LAYOUT] ?: AppSettings.DEFAULT_EXTRA_KEYS_LAYOUT,
        )
    }

    suspend fun update(value: AppSettings) = store.edit { p ->
        p[THEME] = value.themeMode.name; p[DYNAMIC] = value.dynamicColor
        p[FONT] = value.terminalFont.name; p[FONT_SIZE] = value.fontSize
        value.customFontName?.let { p[CUSTOM_FONT_NAME] = it }
        p[CUSTOM_FONT_REVISION] = value.customFontRevision
        p[LINE_HEIGHT] = value.lineHeight; p[LIGATURES] = value.ligatures
        p[KEEP_SCREEN] = value.keepScreenOn; p[LOG_LEVEL] = value.logLevel.name
        p[SCHEME] = value.terminalScheme
        p[EXTRA_KEYS_LAYOUT] = value.extraKeysLayout
    }

    suspend fun updateExtraKeysLayout(value: String) = store.edit { preferences ->
        preferences[EXTRA_KEYS_LAYOUT] = value
    }

    suspend fun importCustomFont(displayName: String, source: InputStream) = withContext(Dispatchers.IO) {
        val target = AppFonts.customFontFile(context)
        target.parentFile?.mkdirs()
        val temporary = java.io.File(target.parentFile, target.name + ".tmp")
        try {
            source.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_CUSTOM_FONT_BYTES) { "字体文件不能超过 20 MB" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            Typeface.createFromFile(temporary)
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            AppFonts.invalidateCustom(context)
            val safeName = displayName.substringAfterLast('/').take(80).ifBlank { "自定义字体" }
            store.edit { p ->
                p[CUSTOM_FONT_NAME] = safeName
                p[CUSTOM_FONT_REVISION] = System.nanoTime()
                p[FONT] = TerminalFont.CUSTOM.name
            }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    suspend fun removeCustomFont() = withContext(Dispatchers.IO) {
        AppFonts.customFontFile(context).delete()
        AppFonts.reloadCustom(context)
        store.edit { p ->
            p.remove(CUSTOM_FONT_NAME)
            p.remove(CUSTOM_FONT_REVISION)
            p[FONT] = TerminalFont.FIRA_CODE.name
        }
    }

    private companion object {
        const val MAX_CUSTOM_FONT_BYTES = 20L * 1024 * 1024
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic")
        val FONT = stringPreferencesKey("terminal_font")
        val CUSTOM_FONT_NAME = stringPreferencesKey("custom_font_name")
        val CUSTOM_FONT_REVISION = longPreferencesKey("custom_font_revision")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val LIGATURES = booleanPreferencesKey("ligatures")
        val KEEP_SCREEN = booleanPreferencesKey("keep_screen")
        val LOG_LEVEL = stringPreferencesKey("log_level")
        val SCHEME = stringPreferencesKey("terminal_scheme")
        val EXTRA_KEYS_LAYOUT = stringPreferencesKey("extra_keys_layout")
    }
}
