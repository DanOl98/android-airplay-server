package io.github.jqssun.airplay.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Stile "pannello Google TV": ricalca il pannello impostazioni immagine
 * di sistema (dark, card riga arrotondate, riga a fuoco azzurro chiaro
 * con testo scuro, slider/picker senza pomello).
 */
object TvPalette {
    val Background = Color(0xFF16181D)
    val Surface = Color(0xFF20242E)
    val Row = Color(0xFF2A303C)
    val FocusFill = Color(0xFFD9E2F4)
    val TextPrimary = Color(0xFFE8EAED)
    val TextSecondary = Color(0xFF9AA0A6)
    val TextOnFocus = Color(0xFF1C232E)
    val Accent = Color(0xFF8AB4F8)
    val TrackEmpty = Color(0xFF454B55)
    val Positive = Color(0xFF81C995)
    val Negative = Color(0xFFF28B82)
}

val TvRowShape = RoundedCornerShape(18.dp)
val TvPanelShape = RoundedCornerShape(22.dp)

@Composable
fun TvSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = TvPalette.TextSecondary,
        fontSize = 13.sp,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 14.dp, top = 24.dp, bottom = 6.dp)
    )
}

/**
 * Card riga generica in stile sistema. `onAdjust` abilita ←/→ (picker);
 * `checked` mostra uno switch; `value` un testo a destra; `showChevron`
 * la freccia "›" delle righe azione.
 */
@Composable
fun TvRow(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    sublabel: String? = null,
    value: String? = null,
    valueColor: Color? = null,
    showChevron: Boolean = false,
    checked: Boolean? = null,
    picker: Boolean = false,
    onAdjust: ((Int) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalTvAccent.current
    var focused by remember { mutableStateOf(false) }
    val contentColor = if (focused) TvPalette.TextOnFocus else TvPalette.TextPrimary
    val dimColor = if (focused) TvPalette.TextOnFocus else TvPalette.TextSecondary
    val subColor = if (focused) TvPalette.TextOnFocus.copy(alpha = 0.7f) else TvPalette.TextSecondary

    var rowModifier = modifier
        .fillMaxWidth()
        .onFocusChanged { focused = it.isFocused }

    if (onAdjust != null) {
        rowModifier = rowModifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionLeft -> { onAdjust(-1); true }
                Key.DirectionRight -> { onAdjust(1); true }
                else -> false
            }
        }
    }

    rowModifier = rowModifier
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick?.invoke() ?: onAdjust?.invoke(1) }
        .background(if (focused) palette.focusFill else palette.row, TvRowShape)
        .padding(horizontal = 18.dp, vertical = 16.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Box(Modifier.size(14.dp, 1.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = contentColor, fontSize = 16.sp)
            if (sublabel != null) {
                Text(text = sublabel, color = subColor, fontSize = 12.sp)
            }
        }
        if (picker) {
            Text("‹", color = dimColor, fontSize = 20.sp)
            Text(
                text = value ?: "",
                color = valueColor?.takeIf { !focused } ?: contentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 96.dp)
                    .padding(horizontal = 10.dp)
            )
            Text("›", color = dimColor, fontSize = 20.sp)
        } else {
            if (value != null) {
                Text(
                    text = value,
                    color = valueColor?.takeIf { !focused } ?: contentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (checked != null) {
                Box(Modifier.size(12.dp, 1.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    colors = if (focused) {
                        SwitchDefaults.colors(
                            checkedThumbColor = palette.focusFill,
                            checkedTrackColor = TvPalette.TextOnFocus,
                            uncheckedThumbColor = TvPalette.TextOnFocus,
                            uncheckedTrackColor = palette.focusFill,
                            uncheckedBorderColor = TvPalette.TextOnFocus
                        )
                    } else {
                        SwitchDefaults.colors(
                            checkedThumbColor = TvPalette.Surface,
                            checkedTrackColor = palette.accent,
                            uncheckedThumbColor = TvPalette.TextSecondary,
                            uncheckedTrackColor = palette.row,
                            uncheckedBorderColor = TvPalette.TextSecondary
                        )
                    }
                )
            }
            if (showChevron) {
                Box(Modifier.size(12.dp, 1.dp))
                Text("›", color = dimColor, fontSize = 20.sp)
            }
        }
    }
}

/**
 * Pulsante tondo per i comandi di riproduzione: tinta della copertina a
 * riposo, riempimento chiaro con icona scura quando ha il focus.
 */
@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    /** Il comando principale (play/pausa): pieno in tinta, si vede da lontano. */
    primary: Boolean = false,
) {
    val palette = LocalTvAccent.current
    var focused by remember { mutableStateOf(false) }

    val background = when {
        focused -> palette.focusFill
        primary -> palette.accent
        else -> palette.accent.copy(alpha = 0.30f)
    }
    val tint = if (focused || primary) TvPalette.TextOnFocus else TvPalette.TextPrimary

    Box(
        modifier = modifier
            .size(size)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** Barra di avanzamento sottile, senza pomello, in tinta con la copertina. */
@Composable
fun TvProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val palette = LocalTvAccent.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            // la parte non riprodotta segue la tinta della copertina: un grigio
            // fisso stonerebbe sulle palette calde
            .background(palette.accent.copy(alpha = 0.28f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(3.dp))
                .background(palette.accent)
        )
    }
}

@Composable
fun TvPanelTitle(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, color = TvPalette.TextPrimary, fontSize = 22.sp)
        Text(text = subtitle, color = TvPalette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
fun TvHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TvPalette.TextSecondary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(top = 12.dp)
    )
}
