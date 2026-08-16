package io.github.jqssun.airplay.ui.tv

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Colori del pannello derivati dalla copertina in riproduzione.
 *
 * La leggibilità non è lasciata al caso: della copertina si prende solo la
 * *tinta*, mentre luminosità e saturazione vengono forzate in intervalli
 * fissi — righe e superfici sempre scure (testo chiaro sopra), riga a fuoco
 * sempre molto chiara (testo scuro sopra). Così qualunque copertina, anche
 * accesa o scurissima, resta leggibile.
 */
@Immutable
data class TvAccentColors(
    val accent: Color,
    val focusFill: Color,
    val row: Color,
    val surface: Color,
    /** Quanto scurire la copertina di sfondo: cresce con la sua luminosità. */
    val scrim: Float = 0.5f,
) {
    companion object {
        val Default = TvAccentColors(
            accent = TvPalette.Accent,
            focusFill = TvPalette.FocusFill,
            row = TvPalette.Row,
            surface = TvPalette.Surface,
        )
    }
}

val LocalTvAccent = staticCompositionLocalOf { TvAccentColors.Default }

/** Ricalcola i colori a ogni cambio di copertina; torna ai default se manca. */
@Composable
fun rememberCoverAccent(cover: Bitmap?): TvAccentColors {
    var colors by remember { mutableStateOf(TvAccentColors.Default) }

    LaunchedEffect(cover) {
        colors = if (cover == null || cover.isRecycled) {
            TvAccentColors.Default
        } else {
            withContext(Dispatchers.Default) {
                runCatching { extractAccent(cover) }.getOrDefault(TvAccentColors.Default)
            }
        }
    }

    return colors
}

private fun extractAccent(cover: Bitmap): TvAccentColors {
    val palette = Palette.from(cover).clearFilters().maximumColorCount(24).generate()

    // la velatura segue la luminosità media della copertina: una copertina
    // scura può restare quasi nuda, una chiara va scurita di più perché il
    // testo bianco sopra resti leggibile
    val dominantHsl = FloatArray(3)
    ColorUtils.colorToHSL(palette.dominantSwatch?.rgb ?: 0x202020, dominantHsl)
    val scrim = (0.30f + 0.42f * dominantHsl[2]).coerceIn(0.30f, 0.72f)

    val rgb = palette.vibrantSwatch?.rgb
        ?: palette.lightVibrantSwatch?.rgb
        ?: palette.darkVibrantSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
        ?: return TvAccentColors.Default.copy(scrim = scrim)

    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    val hue = hsl[0]
    val sat = hsl[1]

    // copertine in scala di grigi: nessuna tinta da estrarre, si resta ai default
    if (sat < 0.12f) return TvAccentColors.Default.copy(scrim = scrim)

    return TvAccentColors(
        accent = hsl(hue, sat.coerceIn(0.45f, 0.95f), 0.68f),
        focusFill = hsl(hue, (sat * 0.45f).coerceIn(0.10f, 0.32f), 0.88f),
        row = hsl(hue, (sat * 0.35f).coerceIn(0.10f, 0.30f), 0.20f, alpha = 0.78f),
        surface = hsl(hue, (sat * 0.30f).coerceIn(0.08f, 0.25f), 0.13f, alpha = 0.72f),
        scrim = scrim,
    )
}

private fun hsl(hue: Float, sat: Float, light: Float, alpha: Float = 1f): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(hue, sat, light))).copy(alpha = alpha)
