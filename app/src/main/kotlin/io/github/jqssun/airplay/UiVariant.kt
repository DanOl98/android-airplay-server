package io.github.jqssun.airplay

import android.content.Context
import android.content.Intent
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
