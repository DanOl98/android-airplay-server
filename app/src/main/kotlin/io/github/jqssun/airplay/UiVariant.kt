package io.github.jqssun.airplay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import io.github.jqssun.airplay.ui.isTvDevice

/**
 * Scelta dell'interfaccia (classica o TV). Default: TV sui dispositivi
 * leanback, classica altrove. Cambiabile dalle impostazioni di entrambe
 * le interfacce.
 */
object UiVariant {
    const val PREF_TV_UI = "tv_ui_enabled"

    fun isTvUi(context: Context): Boolean =
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_TV_UI, context.isTvDevice())

    fun setTvUi(context: Context, enabled: Boolean) {
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_TV_UI, enabled).apply()
    }

    fun launchIntent(context: Context): Intent = Intent(
        context,
        if (isTvUi(context)) TvMainActivity::class.java else MainActivity::class.java
    )
}

/** Opzioni proprie dell'interfaccia TV. */
object TvOptions {
    private const val SHOW_VOLUME = "tv_show_volume"

    private fun prefs(context: Context) =
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    /** Tasti volume nella schermata di riproduzione: spenti di default. */
    fun showVolume(context: Context): Boolean =
        prefs(context).getBoolean(SHOW_VOLUME, false)

    fun setShowVolume(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(SHOW_VOLUME, enabled).apply()
    }
}

/**
 * Nome annunciato di default: quello con cui la TV è già conosciuta in casa,
 * preceduto da "AirPlay" (es. "AirPlay TV della camera da letto").
 *
 * Il prefisso non è estetico ma necessario: usando il nome della TV tale e
 * quale il servizio `_airplay._tcp` non supera il probing mDNS, perché quel
 * nome è già occupato sulla rete dalla TV stessa (Google Cast / AirPlay
 * integrato) — risultato: il dispositivo sparisce dalla lista.
 *
 * Resta comunque modificabile a mano dalle impostazioni.
 */
fun defaultServerName(context: Context): String {
    val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()
    } else {
        null
    }
    val base = deviceName?.takeIf { it.isNotBlank() }
        ?: Build.MODEL?.takeIf { it.isNotBlank() }
        ?: return Prefs.DEF_SERVER_NAME

    return if (base.contains("airplay", ignoreCase = true)) base else "AirPlay $base"
}

/**
 * Configurazione dell'hotkey del telecomando che apre l'app tramite il
 * servizio accessibilità. Default: pressione prolungata del tasto Blu
 * (non Mute, per non entrare in conflitto con altre app che già filtrano
 * Mute sulla stessa TV).
 */
object HotkeyConfig {
    private const val KEY_KEYCODE = "hotkey_keycode"
    private const val KEY_MODE = "hotkey_mode"

    const val MODE_LONG_PRESS = "long"
    const val MODE_DOUBLE_PRESS = "double"
    const val MODE_SINGLE_PRESS = "single"

    /** Ordine usato dal picker nelle impostazioni. */
    val MODES = listOf(MODE_LONG_PRESS, MODE_DOUBLE_PRESS, MODE_SINGLE_PRESS)

    const val DEFAULT_KEYCODE = KeyEvent.KEYCODE_PROG_BLUE

    private fun prefs(context: Context) =
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    fun getKeycode(context: Context): Int =
        prefs(context).getInt(KEY_KEYCODE, DEFAULT_KEYCODE)

    fun setKeycode(context: Context, keycode: Int) {
        prefs(context).edit().putInt(KEY_KEYCODE, keycode).apply()
    }

    fun getMode(context: Context): String =
        prefs(context).getString(KEY_MODE, MODE_LONG_PRESS)!!

    fun setMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_MODE, mode).apply()
    }

    /** Alcuni telecomandi inviano KEYCODE_MUTE al posto di VOLUME_MUTE. */
    fun isTriggerKey(context: Context, keyCode: Int): Boolean {
        val trigger = getKeycode(context)
        if (keyCode == trigger) return true
        return trigger == KeyEvent.KEYCODE_VOLUME_MUTE &&
            keyCode == KeyEvent.KEYCODE_MUTE
    }
}
