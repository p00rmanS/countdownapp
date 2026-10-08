package app.pawcount

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Theme.kt - the design tokens from the web app's css/styles.css: colours (light + dark), the two fonts
 * (Fredoka for headings and numbers, Nunito for text) and a few text styles.
 */

class PawColors(
    val bg: Color, val surface: Color, val surface2: Color, val ink: Color, val ink2: Color, val line: Color, val dark: Boolean,
) {
    val kibble = Color(0xFFE8A15C); val collar = Color(0xFFE5574F); val ball = Color(0xFFD4E157); val night = Color(0xFF1F2433)
    val onKibble = Color(0xFF3B2314)
}

val LightColors = PawColors(Color(0xFFFFF8EE), Color.White, Color(0xFFFFF0DC), Color(0xFF3D2616), Color(0xFF765039), Color(0x298A5A3B), false)
val DarkColors = PawColors(Color(0xFF171B28), Color(0xFF252B3D), Color(0xFF2E3550), Color(0xFFFFF1E0), Color(0xFFCDBFAE), Color(0x21FFF1E0), true)

val LocalPaw = staticCompositionLocalOf { LightColors }

class PawFonts(ctx: Context) {
    private val am = ctx.assets
    val fredokaTypeface: Typeface = Typeface.createFromAsset(am, "fonts/Fredoka-Bold.ttf")
    val display = FontFamily(
        Font("fonts/Fredoka-Medium.ttf", am, FontWeight.Medium), Font("fonts/Fredoka-SemiBold.ttf", am, FontWeight.SemiBold), Font("fonts/Fredoka-Bold.ttf", am, FontWeight.Bold),
    )
    val body = FontFamily(
        Font("fonts/Nunito-Regular.ttf", am, FontWeight.Normal), Font("fonts/Nunito-Bold.ttf", am, FontWeight.Bold), Font("fonts/Nunito-ExtraBold.ttf", am, FontWeight.ExtraBold),
    )
}

val LocalFonts = staticCompositionLocalOf<PawFonts> { error("fonts not provided") }

/** dark when the user chose dark, or chose "system" and the phone is dark */
@Composable
fun PawTheme(settings: Settings, fonts: PawFonts, content: @Composable () -> Unit) {
    val dark = when (settings.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    // status / navigation bar icons follow the app's theme (which may differ from the phone's)
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val w = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
    }
    CompositionLocalProvider(LocalPaw provides (if (dark) DarkColors else LightColors), LocalFonts provides fonts, content = content)
}

object T {
    @Composable fun display(size: Int, weight: FontWeight = FontWeight.SemiBold) = TextStyle(fontFamily = LocalFonts.current.display, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.15).sp, color = LocalPaw.current.ink)
    @Composable fun body(size: Int = 16, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = LocalFonts.current.body, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.35).sp, color = LocalPaw.current.ink)
    @Composable fun muted(size: Int = 14, weight: FontWeight = FontWeight.Bold) = body(size, weight).copy(color = LocalPaw.current.ink2)
}

fun accentColor(hex: String) = Color(parseHex(hex))
/** readable text colour on top of an accent colour (dark ink or white, whichever has more contrast) */
fun onAccent(c: Color): Color {
    fun lum(x: Float) = if (x <= 0.03928f) x / 12.92f else Math.pow(((x + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    val l = 0.2126f * lum(c.red) + 0.7152f * lum(c.green) + 0.0722f * lum(c.blue)
    val darkInk = 0.0286f // luminance of #3B2314
    return if ((l + 0.05f) / (darkInk + 0.05f) >= 1.05f / (l + 0.05f)) Color(0xFF3B2314) else Color.White
}
fun mixColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)
