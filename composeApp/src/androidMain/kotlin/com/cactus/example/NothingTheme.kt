package com.cactus.example

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Nothing Phone LED Dot-Matrix Font
val NothingFontFamily = FontFamily(
    Font(R.font.led_dot_matrix, FontWeight.Normal)
)

// Nothing Phone Color Palette
object NothingColors {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Red = Color(0xFFFF0000)
    val DarkGray = Color(0xFF1A1A1A)
    val MediumGray = Color(0xFF2D2D2D)
    val LightGray = Color(0xFF8A8A8A)
    val OffWhite = Color(0xFFF5F5F5)
}

private val NothingDarkColorScheme = darkColorScheme(
    primary = NothingColors.Red,
    onPrimary = NothingColors.White,
    primaryContainer = NothingColors.DarkGray,
    onPrimaryContainer = NothingColors.White,

    secondary = NothingColors.White,
    onSecondary = NothingColors.Black,
    secondaryContainer = NothingColors.MediumGray,
    onSecondaryContainer = NothingColors.White,

    tertiary = NothingColors.Red,
    onTertiary = NothingColors.White,

    background = NothingColors.Black,
    onBackground = NothingColors.White,

    surface = NothingColors.DarkGray,
    onSurface = NothingColors.White,
    surfaceVariant = NothingColors.MediumGray,
    onSurfaceVariant = NothingColors.LightGray,

    error = NothingColors.Red,
    onError = NothingColors.White,

    outline = NothingColors.MediumGray,
    outlineVariant = NothingColors.LightGray
)

private val NothingLightColorScheme = lightColorScheme(
    primary = NothingColors.Red,
    onPrimary = NothingColors.White,
    primaryContainer = NothingColors.OffWhite,
    onPrimaryContainer = NothingColors.Black,

    secondary = NothingColors.Black,
    onSecondary = NothingColors.White,
    secondaryContainer = NothingColors.OffWhite,
    onSecondaryContainer = NothingColors.Black,

    tertiary = NothingColors.Red,
    onTertiary = NothingColors.White,

    background = NothingColors.White,
    onBackground = NothingColors.Black,

    surface = NothingColors.OffWhite,
    onSurface = NothingColors.Black,
    surfaceVariant = NothingColors.OffWhite,
    onSurfaceVariant = NothingColors.LightGray,

    error = NothingColors.Red,
    onError = NothingColors.White,

    outline = NothingColors.LightGray,
    outlineVariant = NothingColors.LightGray
)

// Nothing Phone Typography - Using LED Dot-Matrix font
private val NothingTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = NothingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)

// Nothing Phone Shapes - Rounded corners
private val NothingShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NothingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        NothingDarkColorScheme
    } else {
        NothingLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NothingTypography,
        shapes = NothingShapes,
        content = content
    )
}
