package app.traceday.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TraceDayScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    secondary = SecondaryTeal,
    onSecondary = Color(0xFF00352C),
    background = Bg,
    onBackground = OnBg,
    surface = SurfaceDark,
    onSurface = OnBg,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnBgDim,
    error = DangerRed,
    onError = Color.White,
    outline = OutlineDark
)

@Composable
fun TraceDayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TraceDayScheme,
        content = content
    )
}
