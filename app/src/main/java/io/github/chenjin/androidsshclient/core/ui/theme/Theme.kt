package io.github.chenjin.androidsshclient.core.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.chenjin.androidsshclient.core.model.AppSettings
import io.github.chenjin.androidsshclient.core.model.ThemeMode
import io.github.chenjin.androidsshclient.core.ui.font.AppFonts

private val LightColors = lightColorScheme(
    primary = Color(0xff006b57), onPrimary = Color.White, primaryContainer = Color(0xff83f8d5),
    secondary = Color(0xff4d635b), tertiary = Color(0xff47647a),
    background = Color(0xfff7f9f7), surface = Color(0xfff7f9f7), surfaceVariant = Color(0xffdce5e0),
    error = Color(0xffba1a1a),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xff62d9ba), onPrimary = Color(0xff00382c), primaryContainer = Color(0xff005140),
    secondary = Color(0xffb4ccc2), tertiary = Color(0xffafc9e6),
    background = Color(0xff101412), surface = Color(0xff101412), surfaceVariant = Color(0xff3f4945),
    error = Color(0xffffb4ab),
)

@Composable
fun AndroidSshTheme(settings: AppSettings, content: @Composable () -> Unit) {
    AppFonts.ready.collectAsState()
    val dark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colors = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val family = FontFamily(AppFonts.ui())
    val typography = Typography(
        headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
        titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
        titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
