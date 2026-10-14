package com.keyspaceapps.keyspace

import android.os.Build
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Keyspace design tokens. One idea: the keyboard is the instrument. Color follows the wallpaper,
 * everything else stays quiet and consistent.
 */

// Archivo is one variable font (width 62–125, weight 100–900). Normal width for labels and settings;
// expanded width for key legends and typed text, echoing engraved keycaps.
private fun archivo(weight: FontWeight, width: Float) =
    Font(R.font.archivo, weight, variationSettings = FontVariation.Settings(weight, FontStyle.Normal, FontVariation.width(width)))

val Archivo = FontFamily(archivo(FontWeight.Normal, 100f), archivo(FontWeight.Medium, 100f))
private val KeyWeight = FontWeight(450)
val ArchivoExpanded = FontFamily(archivo(KeyWeight, 118f))

/** Used where dynamic color isn't available (before Android 12). */
private val SlateScheme = darkColorScheme(
    primary = Color(0xFF9DB4D6),
    onPrimary = Color(0xFF14202F),
    surface = Color(0xFF1A1F26),
    onSurface = Color(0xFFE3E7ED),
    onSurfaceVariant = Color(0xFFAEB6C2),
    surfaceContainer = Color(0xFF20262E),
    surfaceContainerHigh = Color(0xFF2A313B),
    surfaceContainerHighest = Color(0xFF353D48),
)

object KeyspaceTokens {
    /** Keys and small controls (buttons, menus). */
    val SmallRadius = 8.dp
    /** Panels and dialogs. */
    val PanelRadius = 20.dp
    val SmallShape = RoundedCornerShape(SmallRadius)
    val PanelShape = RoundedCornerShape(PanelRadius)

    val KeyLegend = TextStyle(fontFamily = ArchivoExpanded, fontWeight = KeyWeight, fontSize = 22.sp)
    val TypedLine = TextStyle(fontFamily = ArchivoExpanded, fontWeight = KeyWeight, fontSize = 32.sp)
    val IconLabel = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Medium, fontSize = 12.sp)
}

/** Shared colors derived from the current scheme (and so from the wallpaper). */
object KeyspaceColors {
    val panel: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
    val key: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
    val keyPressed: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val keyLit: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val keyDim: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val accent: Color @Composable get() = MaterialTheme.colorScheme.primary
    /** Plate behind legacy (non-adaptive) app icons. */
    val iconPlate: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
fun KeyspaceTheme(content: @Composable () -> Unit) {
    val scheme: ColorScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(LocalContext.current) else SlateScheme
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Archivo),
        displayMedium = base.displayMedium.copy(fontFamily = Archivo),
        displaySmall = base.displaySmall.copy(fontFamily = Archivo),
        headlineLarge = base.headlineLarge.copy(fontFamily = Archivo),
        headlineMedium = base.headlineMedium.copy(fontFamily = Archivo),
        headlineSmall = base.headlineSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.Medium),
        titleLarge = base.titleLarge.copy(fontFamily = Archivo),
        titleMedium = base.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.copy(fontFamily = Archivo),
        bodyLarge = base.bodyLarge.copy(fontFamily = Archivo),
        bodyMedium = base.bodyMedium.copy(fontFamily = Archivo),
        bodySmall = base.bodySmall.copy(fontFamily = Archivo),
        labelLarge = base.labelLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = Archivo),
        labelSmall = KeyspaceTokens.IconLabel,
    )
    // Two radii only: small controls and panels.
    val shapes = Shapes(
        extraSmall = KeyspaceTokens.SmallShape,
        small = KeyspaceTokens.SmallShape,
        medium = KeyspaceTokens.SmallShape,
        large = KeyspaceTokens.PanelShape,
        extraLarge = KeyspaceTokens.PanelShape,
    )
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}

/** Every popup menu in Keyspace, styled like the panels. */
@Composable
fun KeyspaceMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = KeyspaceTokens.SmallShape,
        containerColor = KeyspaceColors.panel,
        content = content,
    )
}
