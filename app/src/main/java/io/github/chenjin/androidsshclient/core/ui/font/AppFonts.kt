package io.github.chenjin.androidsshclient.core.ui.font

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build
import io.github.chenjin.androidsshclient.core.model.TerminalFont
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppFonts {
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    @Volatile private var regular: Typeface = Typeface.MONOSPACE
    @Volatile private var bold: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    fun initialize(context: Context) {
        runCatching {
            regular = buildFallback(context, "fonts/FiraCodeNerdFontMono-Regular.ttf", "fonts/SourceHanSansSC-Regular.otf")
            bold = buildFallback(context, "fonts/FiraCodeNerdFontMono-Bold.ttf", "fonts/SourceHanSansSC-Bold.otf")
            _ready.value = true
        }
    }

    fun terminal(font: TerminalFont = TerminalFont.FIRA_CODE, isBold: Boolean = false): Typeface = when (font) {
        TerminalFont.FIRA_CODE -> if (isBold) bold else regular
        TerminalFont.JETBRAINS_MONO -> Typeface.create("monospace", if (isBold) Typeface.BOLD else Typeface.NORMAL)
        TerminalFont.SYSTEM_MONO -> Typeface.create(Typeface.MONOSPACE, if (isBold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun ui(isBold: Boolean = false): Typeface = if (isBold) bold else regular

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
