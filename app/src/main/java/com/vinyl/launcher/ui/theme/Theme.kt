@file:OptIn(ExperimentalTextApi::class)

package com.vinyl.launcher.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.R
import com.vinyl.launcher.data.Anchor
import com.vinyl.launcher.data.FontChoice

/*
 * Bahasa desain (tema TV tabung, lihat DESIGN.md):
 * - Permukaan gelap bernada hangat (charcoal), aksen amber lampu indikator; ruang TV punya palet sendiri (Tokens.Retro).
 * - Teks putih dengan 3 tingkat opasitas (primer, sekunder, tersier), >= 4,5:1 di semua permukaan & latar cover.
 * - Permukaan "kaca": putih transparan tipis + garis tepi halus (lihat GlassSurface).
 * - Pengelompokan ala One UI: kartu besar membulat, judul besar, banyak ruang kosong.
 * - Tipografi Inter (variable font) dengan tracking rapat untuk angka besar; JetBrains Mono untuk angka VFD/console.
 */

// ===== Warna =====
val Ink = Color(Tokens.Argb.INK)
val InkElevated = Color(Tokens.Argb.INK_ELEVATED)
val InkCard = Color(Tokens.Argb.INK_CARD)
val InkCardHigh = Color(Tokens.Argb.INK_CARD_HIGH)
val Hairline = Color(Tokens.Argb.HAIRLINE)
val TextPrimary = Color(Tokens.Argb.TEXT_PRIMARY)
val TextSecondary = Color(Tokens.Argb.TEXT_SECONDARY)
val TextTertiary = Color(Tokens.Argb.TEXT_TERTIARY)
val Accent = Color(Tokens.Argb.ACCENT)          // amber lampu indikator TV
val AccentOn = Color(Tokens.Argb.ACCENT_ON)

// ===== Tema TV tabung ("Retro Projection") — nilai dari Tokens.Retro =====
object RetroColors {
    val WarmWhite = Color(Tokens.Retro.WARM_WHITE)
    val WallTop = Color(Tokens.Retro.VOID_WALL_TOP)
    val Floor = Color(Tokens.Retro.VOID_FLOOR)
    val Corner = Color(Tokens.Retro.VOID_CORNER)
    val ContactShadow = Color(Tokens.Retro.CONTACT_SHADOW)
    val CreamBeige = Color(Tokens.Retro.CREAM_BEIGE)
    val Walnut = Color(Tokens.Retro.WALNUT)
    val Charcoal = Color(Tokens.Retro.CHARCOAL)
    val CharcoalDeep = Color(Tokens.Retro.CHARCOAL_DEEP)
    val CharcoalRaised = Color(Tokens.Retro.CHARCOAL_RAISED)
    val Amber = Color(Tokens.Retro.AMBER)
    val MutedGreen = Color(Tokens.Retro.MUTED_GREEN)
    val Ink = Color(Tokens.Retro.INK)
    val InkSecondary = Color(Tokens.Retro.INK_SECONDARY)
    val InkTertiary = Color(Tokens.Retro.INK_TERTIARY)
    val CreamText = Color(Tokens.Retro.CREAM_TEXT)
    val CreamTextSecondary = Color(Tokens.Retro.CREAM_TEXT_SECONDARY)
    val VfdGlass = Color(Tokens.Retro.VFD_GLASS)
    val VfdAmber = Color(Tokens.Retro.VFD_AMBER)
    val VfdGreen = Color(Tokens.Retro.VFD_GREEN)
    val VfdGhost = Color(Tokens.Retro.VFD_GHOST)
    val CrtGlass = Color(Tokens.Retro.CRT_GLASS)
    val StandbyLed = Color(Tokens.Retro.STANDBY_LED)
}

// Nama lama dipertahankan agar komponen lain tetap kompatibel
val VinylBlack = Ink
val PaperCream = TextPrimary
val WarmAccent = Accent
val MutedText = TextSecondary

// ===== Font =====
private fun inter(weight: FontWeight, opsz: Float) = Font(
    R.font.inter, weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.Setting("opsz", opsz)),
)

/** Inter untuk teks UI (optical size kecil). */
val InterText = FontFamily(
    listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { inter(it, 14f) }
)

/** Inter versi "display" untuk angka & judul besar (optical size besar = lebih rapat & elegan). */
val InterDisplay = FontFamily(
    listOf(
        FontWeight.ExtraLight, FontWeight.Light, FontWeight.Normal, FontWeight.Medium,
        FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black,
    ).map { inter(it, 32f) }
)

val SerifDisplay = FontFamily(Font(R.font.instrument_serif))

private fun mono(weight: FontWeight) = Font(
    R.font.jetbrains_mono, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)
val MonoFamily = FontFamily(listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.Bold).map { mono(it) })

/** Huruf layar kamera/VCR (OSD): jam, tanggal, status, penghitung pita. */
val OsdFamily = FontFamily(Font(R.font.vt323))

/** Tulisan tangan spidol untuk label kaset. */
val MarkerFamily = FontFamily(Font(R.font.permanent_marker))

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = AccentOn,
    primaryContainer = Color(Tokens.Argb.PRIMARY_CONTAINER),
    onPrimaryContainer = Color(Tokens.Argb.ON_PRIMARY_CONTAINER),
    secondary = Color(Tokens.Argb.SECONDARY),
    background = Ink,
    onBackground = TextPrimary,
    surface = InkElevated,
    onSurface = TextPrimary,
    surfaceVariant = InkCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = InkElevated,
    surfaceContainer = InkCard,
    surfaceContainerHigh = InkCardHigh,
    surfaceContainerHighest = Color(Tokens.Argb.INK_CARD_HIGHEST),
    outline = Color(Tokens.Argb.OUTLINE),
    outlineVariant = Hairline,
    errorContainer = Color(Tokens.Argb.ERROR_CONTAINER),
    onErrorContainer = Color(Tokens.Argb.ON_ERROR_CONTAINER),
)

// Arah teks mengikuti isinya: teks Latin (mis. "-4 dB", "≥ 5,0 ms") tetap terbaca benar saat tata letak RTL,
// teks Arab/Ibrani tetap RTL.
private val base = TextStyle(fontFamily = InterText, letterSpacing = 0.sp, textDirection = TextDirection.Content)

private val VinylTypography = Typography(
    displayLarge = base.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 57.sp, letterSpacing = (-0.04).em),
    displayMedium = base.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 45.sp, letterSpacing = (-0.035).em),
    displaySmall = base.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = (-0.03).em),
    headlineLarge = base.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.025).em),
    headlineMedium = base.copy(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.02).em),
    headlineSmall = base.copy(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.015).em),
    titleLarge = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.01).em),
    titleMedium = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = base.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = base.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = base.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.copy(fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.02.em),
    labelSmall = base.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.03.em),
)

/**
 * Tema aplikasi. Mode gelap adalah default (dan satu-satunya mode).
 *
 * [LocalContentColor] diberi nilai di sini: MaterialTheme sendiri TIDAK memberinya, dan nilai bawaan Compose adalah
 * hitam. Tanpa ini setiap teks/ikon yang tidak diberi warna eksplisit dan tidak berada di dalam Surface/tombol/dialog
 * tampil hitam di atas latar gelap (terbukti di tangkapan layar uji emulator: judul layar sambutan nyaris tak terbaca).
 */
@Composable
fun VinylTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = VinylTypography) {
        CompositionLocalProvider(LocalContentColor provides DarkScheme.onBackground, content = content)
    }
}

// ===== Helper konversi pengaturan → nilai Compose =====

fun Long.toColor(): Color = Color(this)
fun Color.toLongArgb(): Long = toArgb().toLong() and 0xFFFFFFFFL
fun Int.toComposeColor(): Color = Color(this)

fun FontChoice.toFamily(): FontFamily = when (this) {
    FontChoice.DEFAULT -> InterDisplay
    FontChoice.SANS -> FontFamily.SansSerif
    FontChoice.SERIF -> SerifDisplay
    FontChoice.MONO -> MonoFamily
    FontChoice.CURSIVE -> FontFamily.Cursive
}

fun FontChoice.label(): String = when (this) {
    FontChoice.DEFAULT -> "Inter"
    FontChoice.SANS -> "Sistem"
    FontChoice.SERIF -> "Serif"
    FontChoice.MONO -> "Mono"
    FontChoice.CURSIVE -> "Tulisan tangan"
}

fun Anchor.toAlignment(): Alignment = when (this) {
    Anchor.TOP_START -> Alignment.TopStart
    Anchor.TOP_CENTER -> Alignment.TopCenter
    Anchor.TOP_END -> Alignment.TopEnd
    Anchor.CENTER_START -> Alignment.CenterStart
    Anchor.CENTER -> Alignment.Center
    Anchor.CENTER_END -> Alignment.CenterEnd
    Anchor.BOTTOM_START -> Alignment.BottomStart
    Anchor.BOTTOM_CENTER -> Alignment.BottomCenter
    Anchor.BOTTOM_END -> Alignment.BottomEnd
}

fun Anchor.horizontal(): Alignment.Horizontal = when (this) {
    Anchor.TOP_START, Anchor.CENTER_START, Anchor.BOTTOM_START -> Alignment.Start
    Anchor.TOP_CENTER, Anchor.CENTER, Anchor.BOTTOM_CENTER -> Alignment.CenterHorizontally
    else -> Alignment.End
}

/** Warna teks yang paling terbaca di atas [bg] (membandingkan rasio kontras sebenarnya, bukan ambang luminans). */
fun contentColorFor(bg: Color): Color =
    Color(if (ColorMath.darkTextIsBetter(bg.toLongArgb())) Tokens.Argb.TEXT_ON_LIGHT else Tokens.Argb.TEXT_ON_DARK)

fun Color.darken(factor: Float): Color =
    Color(red * (1 - factor), green * (1 - factor), blue * (1 - factor), alpha)

fun Color.lighten(factor: Float): Color =
    Color(red + (1 - red) * factor, green + (1 - green) * factor, blue + (1 - blue) * factor, alpha)

/**
 * Warna dengan saturasi, kecerahan, dan luminans dibatasi agar enak — dan terbaca — sebagai latar gelap
 * di bawah teks putih (lihat [AmbientColor]).
 */
fun Color.ambient(maxValue: Float = 0.55f, minSat: Float = 0.25f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    val out = AmbientColor.limit(Hsv(hsv[0], hsv[1], hsv[2]), maxValue, minSat)
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(out.h, out.s, out.v)))
}
