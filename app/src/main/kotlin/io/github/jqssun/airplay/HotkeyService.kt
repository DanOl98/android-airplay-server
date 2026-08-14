package io.github.jqssun.airplay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Apre l'app con un tasto del telecomando.
 *
 * Il filtro accessibilità impone di decidere **al momento** se consumare
 * ogni evento: quando arriva il DOWN non si sa ancora se diventerà una
 * pressione lunga. Da qui tre comportamenti diversi:
 *
 * - gesto "pressione singola": il tasto è riservato all'app, quindi viene
 *   consumato del tutto (è l'unico caso in cui bloccarlo ha senso);
 * - gesto lungo/doppio su tasti ri-emettibili (media, mute, volume): il
 *   tasto viene trattenuto e la pressione breve viene rimessa in circolo
 *   al rilascio, così resta funzionante e il gesto non attiva l'azione
 *   nativa;
 * - gesto lungo/doppio sugli altri tasti: non si consuma nulla, il tasto
 *   resta nativo e il gesto viene solo osservato (l'azione nativa parte
 *   comunque, ma è preferibile a perdere la pressione breve).
 */
class HotkeyService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var longPressTriggered = false
    private var waitingSecondPress = false
    private var secondPressActive = false
    private var lastTriggerKeyCode = HotkeyConfig.DEFAULT_KEYCODE

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        openApp()
    }

    private val singlePressRunnable = Runnable {
        waitingSecondPress = false
        // solo in modalità "trattieni": altrimenti la pressione è già passata
        if (canReEmit(lastTriggerKeyCode)) emulateShortPress(lastTriggerKeyCode)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d(TAG, "onKeyEvent keyCode=${event.keyCode} action=${event.action} repeat=${event.repeatCount}")

        if (!HotkeyConfig.isTriggerKey(this, event.keyCode)) return false

        lastTriggerKeyCode = event.keyCode

        return when (HotkeyConfig.getMode(this)) {
            HotkeyConfig.MODE_SINGLE_PRESS -> handleSinglePress(event)
            HotkeyConfig.MODE_DOUBLE_PRESS -> handleDoublePress(event, canReEmit(event.keyCode))
            else -> handleLongPress(event, canReEmit(event.keyCode))
        }
    }

    /** Tasto riservato all'app: consumato sempre. */
    private fun handleSinglePress(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            openApp()
        }
        return true
    }

    private fun handleLongPress(event: KeyEvent, hold: Boolean): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    longPressTriggered = false
                    handler.removeCallbacks(longPressRunnable)
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                } else if (!longPressTriggered) {
                    handler.removeCallbacks(longPressRunnable)
                    longPressTriggered = true
                    openApp()
                }
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (hold && !longPressTriggered) emulateShortPress(event.keyCode)
                longPressTriggered = false
            }
        }
        return hold
    }

    private fun handleDoublePress(event: KeyEvent, hold: Boolean): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0 && waitingSecondPress) {
                    handler.removeCallbacks(singlePressRunnable)
                    waitingSecondPress = false
                    secondPressActive = true
                }
            }
            KeyEvent.ACTION_UP -> {
                if (secondPressActive) {
                    secondPressActive = false
                    openApp()
                } else {
                    waitingSecondPress = true
                    handler.postDelayed(singlePressRunnable, DOUBLE_PRESS_MS)
                }
            }
        }
        return hold
    }

    private fun openApp() {
        val intent = UiVariant.launchIntent(this)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch UI", e)
        }
    }

    private fun emulateShortPress(keyCode: Int) {
        val audioManager = getSystemService(AudioManager::class.java) ?: return
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE ->
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_TOGGLE_MUTE,
                    AudioManager.FLAG_SHOW_UI
                )
            KeyEvent.KEYCODE_VOLUME_UP ->
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )
            KeyEvent.KEYCODE_VOLUME_DOWN ->
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
            in MEDIA_KEYS -> {
                val now = SystemClock.uptimeMillis()
                audioManager.dispatchMediaKeyEvent(
                    KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
                )
                audioManager.dispatchMediaKeyEvent(
                    KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
                )
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}

    companion object {
        private const val TAG = "HotkeyService"
        private const val LONG_PRESS_MS = 600L
        private const val DOUBLE_PRESS_MS = 400L

        private val MEDIA_KEYS = setOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_HEADSETHOOK,
        )

        private val VOLUME_KEYS = setOf(
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MUTE,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
        )

        /**
         * Tasti la cui pressione breve l'app sa rimettere in circolo: solo
         * per questi conviene trattenere il tasto in attesa del gesto.
         */
        fun canReEmit(keyCode: Int): Boolean =
            keyCode in MEDIA_KEYS || keyCode in VOLUME_KEYS
    }
}
