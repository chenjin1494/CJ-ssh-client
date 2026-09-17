package io.github.chenjin.androidsshclient.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chenjin.androidsshclient.core.model.AppSettings
import io.github.chenjin.androidsshclient.core.model.LogLevel
import io.github.chenjin.androidsshclient.core.model.TerminalFont
import io.github.chenjin.androidsshclient.core.model.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsDataStore
    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            themeMode = p[THEME]?.let(ThemeMode::valueOf) ?: ThemeMode.SYSTEM,
            dynamicColor = p[DYNAMIC] ?: true,
            terminalFont = p[FONT]?.let(TerminalFont::valueOf) ?: TerminalFont.FIRA_CODE,
            fontSize = p[FONT_SIZE] ?: 14f,
            lineHeight = p[LINE_HEIGHT] ?: 1.2f,
            ligatures = p[LIGATURES] ?: true,
            keepScreenOn = p[KEEP_SCREEN] ?: false,
            logLevel = p[LOG_LEVEL]?.let(LogLevel::valueOf) ?: LogLevel.INFO,
            terminalScheme = p[SCHEME] ?: "One Dark",
        )
    }

    suspend fun update(value: AppSettings) = store.edit { p ->
        p[THEME] = value.themeMode.name; p[DYNAMIC] = value.dynamicColor
        p[FONT] = value.terminalFont.name; p[FONT_SIZE] = value.fontSize
        p[LINE_HEIGHT] = value.lineHeight; p[LIGATURES] = value.ligatures
        p[KEEP_SCREEN] = value.keepScreenOn; p[LOG_LEVEL] = value.logLevel.name
        p[SCHEME] = value.terminalScheme
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic")
        val FONT = stringPreferencesKey("terminal_font")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val LIGATURES = booleanPreferencesKey("ligatures")
        val KEEP_SCREEN = booleanPreferencesKey("keep_screen")
        val LOG_LEVEL = stringPreferencesKey("log_level")
        val SCHEME = stringPreferencesKey("terminal_scheme")
    }
}
