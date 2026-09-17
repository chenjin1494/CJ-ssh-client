package io.github.chenjin.androidsshclient.core.ui.font

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build
import io.github.chenjin.androidsshclient.core.model.TerminalFont
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppFonts {
    private const val CUSTOM_FONT_FILE = "fonts/custom-terminal-font"
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    @Volatile private var regular: Typeface = Typeface.MONOSPACE
    @Volatile private var bold: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    @Volatile private var custom: Typeface? = null
    @Volatile private var customModifiedAt: Long = -1

    fun initialize(context: Context) {
        runCatching {
            regular = buildFallback(context, "fonts/FiraCodeNerdFontMono-Regular.ttf", "fonts/SourceHanSansSC-Regular.otf")
            bold = buildFallback(context, "fonts/FiraCodeNerdFontMono-Bold.ttf", "fonts/SourceHanSansSC-Bold.otf")
            reloadCustom(context)
            _ready.value = true
        }
    }

    fun terminal(context: Context, font: TerminalFont = TerminalFont.FIRA_CODE, isBold: Boolean = false): Typeface = when (font) {
        TerminalFont.FIRA_CODE -> if (isBold) bold else regular
        TerminalFont.JETBRAINS_MONO -> Typeface.create("monospace", if (isBold) Typeface.BOLD else Typeface.NORMAL)
        TerminalFont.SYSTEM_MONO -> Typeface.create(Typeface.MONOSPACE, if (isBold) Typeface.BOLD else Typeface.NORMAL)
        TerminalFont.CUSTOM -> {
            reloadCustom(context)
            custom?.let { Typeface.create(it, if (isBold) Typeface.BOLD else Typeface.NORMAL) }
                ?: if (isBold) bold else regular
        }
    }

    fun ui(isBold: Boolean = false): Typeface = if (isBold) bold else regular

    fun customFontFile(context: Context): File = File(context.filesDir, CUSTOM_FONT_FILE)

    @Synchronized
    fun invalidateCustom(context: Context) {
        customModifiedAt = Long.MIN_VALUE
        reloadCustom(context)
    }

    @Synchronized
    fun reloadCustom(context: Context) {
        val file = customFontFile(context)
        val modified = if (file.isFile) file.lastModified() else -1
        if (modified == customModifiedAt) return
        custom = if (file.isFile) runCatching { buildCustomFallback(context, file) }.getOrNull() else null
        customModifiedAt = modified
        _ready.value = true
    }

    private fun buildCustomFallback(context: Context, file: File): Typeface {
        if (Build.VERSION.SDK_INT < 29) return Typeface.createFromFile(file)
        val primary = FontFamily.Builder(Font.Builder(file).build()).build()
        val fallback = FontFamily.Builder(Font.Builder(context.assets, "fonts/SourceHanSansSC-Regular.otf").build()).build()
        return Typeface.CustomFallbackBuilder(primary)
            .addCustomFallback(fallback)
            .setSystemFallback("sans-serif")
            .build()
    }

    private fun buildFallback(context: Context, primaryPath: String, fallbackPath: String): Typeface {
        if (Build.VERSION.SDK_INT < 29) return Typeface.createFromAsset(context.assets, primaryPath)
        val primary = FontFamily.Builder(Font.Builder(context.assets, primaryPath).build()).build()
        val fallback = FontFamily.Builder(Font.Builder(context.assets, fallbackPath).build()).build()
        return Typeface.CustomFallbackBuilder(primary)
            .addCustomFallback(fallback)
            .setSystemFallback("sans-serif")
            .build()
    }
}
