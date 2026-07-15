package com.aiteacher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Forge Orange brand tokens (theme-independent, NEVER change) ─────────────
object ForgeBrand {
    val Orange        = Color(0xFFF97316)   // primary brand colour
    val OrangeLight   = Color(0xFFFB923C)   // lighter tint for text on dark
    val OrangeDark    = Color(0xFFEA6C04)   // pressed / deeper shade
    val OrangeContainer_Dark  = Color(0xFF4A1F07)  // container on dark surface
    val OrangeContainer_Light = Color(0xFFFFEDD5)  // container on light surface
    val Success       = Color(0xFF22C55E)
    val Error         = Color(0xFFEF4444)
    val Warning       = Color(0xFFF59E0B)
    val Teal          = Color(0xFF06D6A0)   // playful accent
    val Pink          = Color(0xFFFF006E)   // playful accent
    val Gold          = Color(0xFFFFD166)   // playful accent
}

// ─── Extended colours — theme-aware, accessed via LocalForgeColors ────────────
data class ForgeColorSet(
    // Surfaces
    val bgBase       : Color,
    val bgDeep       : Color,
    val bgCard       : Color,
    val bgElevated   : Color,
    val glassFill    : Color,
    val glassBorder  : Color,
    val divider      : Color,
    // Text
    val textPrimary  : Color,
    val textSecondary: Color,
    val textMuted    : Color,
    // Brand (same in both modes)
    val primary      : Color  = ForgeBrand.Orange,
    val primaryLight : Color  = ForgeBrand.OrangeLight,
    val primaryDark  : Color  = ForgeBrand.OrangeDark,
    val success      : Color  = ForgeBrand.Success,
    val error        : Color  = ForgeBrand.Error,
    val warning      : Color  = ForgeBrand.Warning,
    val teal         : Color  = ForgeBrand.Teal,
    val pink         : Color  = ForgeBrand.Pink,
    val gold         : Color  = ForgeBrand.Gold
)

private val DarkForgeColors = ForgeColorSet(
    bgBase        = Color(0xFF0D0D1A),   // warm deep navy
    bgDeep        = Color(0xFF080812),   // deeper for bottom nav / status bar
    bgCard        = Color(0xFF18182A),   // rich dark card
    bgElevated    = Color(0xFF22223A),   // elevated surfaces
    glassFill     = Color(0x1AFFFFFF),   // 20% white — visible glass
    glassBorder   = Color(0x22FFFFFF),   // 25% white border
    divider       = Color(0x1AFFFFFF),
    textPrimary   = Color(0xFFF5F0EB),   // warm white
    textSecondary = Color(0xFFB0A8A0),   // warm grey
    textMuted     = Color(0xFF706868)    // muted warm grey
)

private val LightForgeColors = ForgeColorSet(
    bgBase        = Color(0xFFFAF6F1),   // warm off-white
    bgDeep        = Color(0xFFF0ECE7),   // slightly deeper
    bgCard        = Color(0xFFFFFFFF),
    bgElevated    = Color(0xFFFFFFFF),
    glassFill     = Color(0x0D000000),   // subtle dark glass
    glassBorder   = Color(0x14000000),
    divider       = Color(0x14000000),
    textPrimary   = Color(0xFF1A1512),   // warm dark
    textSecondary = Color(0xFF5A524E),
    textMuted     = Color(0xFF9C9490)
)

val LocalForgeColors = staticCompositionLocalOf { DarkForgeColors }

// Convenience accessor — use inside any @Composable
val forgeColors: ForgeColorSet
    @Composable get() = LocalForgeColors.current

// ─── Material3 colour schemes ─────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary              = ForgeBrand.Orange,
    onPrimary            = Color.White,
    primaryContainer     = ForgeBrand.OrangeContainer_Dark,
    onPrimaryContainer   = ForgeBrand.OrangeLight,
    secondary            = ForgeBrand.OrangeLight,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFF3A1A06),
    onSecondaryContainer = ForgeBrand.OrangeLight,
    tertiary             = Color(0xFF06D6A0),
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFF052E16),
    onTertiaryContainer  = Color(0xFF86EFAC),
    background           = Color(0xFF0D0D1A),
    onBackground         = Color(0xFFF5F0EB),
    surface              = Color(0xFF18182A),
    onSurface            = Color(0xFFF5F0EB),
    surfaceVariant       = Color(0xFF22223A),
    onSurfaceVariant     = Color(0xFFB0A8A0),
    outline              = Color(0xFF3A3A4E),
    outlineVariant       = Color(0xFF2A2A3E),
    error                = ForgeBrand.Error,
    onError              = Color.White,
    inverseSurface       = Color(0xFFF5F0EB),
    inverseOnSurface     = Color(0xFF1A1512),
    inversePrimary       = ForgeBrand.OrangeDark,
    scrim                = Color(0xFF000000)
)

private val LightColorScheme = lightColorScheme(
    primary              = ForgeBrand.Orange,
    onPrimary            = Color.White,
    primaryContainer     = ForgeBrand.OrangeContainer_Light,
    onPrimaryContainer   = ForgeBrand.OrangeDark,
    secondary            = ForgeBrand.OrangeDark,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFFFEDD5),
    onSecondaryContainer = ForgeBrand.OrangeDark,
    tertiary             = Color(0xFF059669),
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFFD1FAE5),
    onTertiaryContainer  = Color(0xFF064E3B),
    background           = Color(0xFFFAF6F1),
    onBackground         = Color(0xFF1A1512),
    surface              = Color(0xFFFFFFFF),
    onSurface            = Color(0xFF1A1512),
    surfaceVariant       = Color(0xFFF0ECE7),
    onSurfaceVariant     = Color(0xFF5A524E),
    outline              = Color(0xFFDDD8D0),
    outlineVariant       = Color(0xFFEEE8E0),
    error                = ForgeBrand.Error,
    onError              = Color.White,
    inverseSurface       = Color(0xFF18182A),
    inverseOnSurface     = Color(0xFFF5F0EB),
    inversePrimary       = ForgeBrand.OrangeLight,
    scrim                = Color(0xFF000000)
)

// ─── Shapes — more dynamic rhythm ─────────────────────────────────────────────

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(14.dp),
    medium     = RoundedCornerShape(20.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// ─── Typography — stronger hierarchy, no hardcoded colours ────────────────────

private val AppTypography = Typography(
    displayLarge  = TextStyle(fontSize = 42.sp, fontWeight = FontWeight.Black,   lineHeight = 48.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Black,   lineHeight = 44.sp, letterSpacing = (-0.5).sp),
    displaySmall  = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black,   lineHeight = 40.sp),
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Black,   lineHeight = 38.sp, letterSpacing = (-0.3).sp),
    headlineMedium= TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold,    lineHeight = 32.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold,    lineHeight = 28.sp),
    titleLarge    = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium   = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall    = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium,   lineHeight = 20.sp),
    bodyLarge     = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal,   lineHeight = 26.sp),
    bodyMedium    = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal,   lineHeight = 22.sp),
    bodySmall     = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal,   lineHeight = 18.sp),
    labelLarge    = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    labelMedium   = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium,   lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold,     lineHeight = 14.sp, letterSpacing = 1.2.sp)
)

// ─── Theme entry point ────────────────────────────────────────────────────────

@Composable
fun AiTeacherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val forgeColorSet = if (darkTheme) DarkForgeColors else LightForgeColors

    CompositionLocalProvider(LocalForgeColors provides forgeColorSet) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = AppTypography,
            shapes      = AppShapes,
            content     = content
        )
    }
}
