package io.github.jqssun.airplay

import android.app.Activity
import android.os.Bundle

/**
 * Trampolino invisibile per la voce launcher: apre l'interfaccia scelta
 * nelle impostazioni (classica o TV) senza toccare MainActivity.
 */
class LauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(UiVariant.launchIntent(this))
        finish()
    }
}
