package io.github.jqssun.airplay.ui.tv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.jqssun.airplay.HotkeyConfig
import io.github.jqssun.airplay.HotkeyService
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.MainActivity
import io.github.jqssun.airplay.UiVariant
import io.github.jqssun.airplay.service.AirPlayService.ServerState
import io.github.jqssun.airplay.ui.VideoSurfaceView
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlinx.coroutines.delay

private enum class TvSection { MAIN, SETTINGS, LOGS }

private val HOTKEY_PRESETS = listOf(
    AndroidKeyEvent.KEYCODE_PROG_BLUE to "Tasto Blu",
    AndroidKeyEvent.KEYCODE_PROG_RED to "Tasto Rosso",
    AndroidKeyEvent.KEYCODE_PROG_GREEN to "Tasto Verde",
    AndroidKeyEvent.KEYCODE_PROG_YELLOW to "Tasto Giallo",
    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Play/Pausa",
    AndroidKeyEvent.KEYCODE_VOLUME_MUTE to "Mute",
)

private fun modeLabel(mode: String): String = when (mode) {
    HotkeyConfig.MODE_SINGLE_PRESS -> "Pressione singola"
    HotkeyConfig.MODE_DOUBLE_PRESS -> "Doppia pressione"
    else -> "Pressione lunga"
}

/** Cosa succede alla pressione breve con il tasto e il gesto scelti. */
private fun shortPressNote(keycode: Int, mode: String): String = when {
    mode == HotkeyConfig.MODE_SINGLE_PRESS ->
        "Il tasto sarà riservato all'app"
    HotkeyService.canReEmit(keycode) ->
        "La pressione breve continua a funzionare normalmente"
    else ->
        "Tasto lasciato nativo: il gesto attiverà anche la sua funzione"
}

@Composable
fun TvMainScreen(
    viewModel: MainViewModel,
    onSurfaceAvailable: (android.view.Surface) -> Unit,
    onSurfaceDestroyed: (android.view.Surface) -> Unit,
) {
    var section by remember { mutableStateOf(TvSection.MAIN) }
    var fullscreen by remember { mutableStateOf(false) }

    val state by viewModel.serverState.collectAsState()
    val connections by viewModel.connectionCount.collectAsState()
    val serverName by viewModel.serverName.collectAsState()
    val pin by viewModel.pinCode.collectAsState()
    val audioOnly by viewModel.audioOnly.collectAsState()
    val mirroringActive by viewModel.mirroringActive.collectAsState()
    val videoPlaybackActive by viewModel.videoPlaybackActive.collectAsState()
    val videoSessionPending by viewModel.videoSessionPending.collectAsState()
    val autoFullscreen by viewModel.autoFullscreen.collectAsState()

    // fullscreen automatico all'avvio del mirroring (come l'interfaccia classica)
    var prevMirroring by remember { mutableStateOf(false) }
    LaunchedEffect(mirroringActive, audioOnly, videoPlaybackActive, pin) {
        val justStarted = !prevMirroring && mirroringActive
        prevMirroring = mirroringActive
        if (justStarted && !audioOnly && !videoPlaybackActive && autoFullscreen && pin == null) {
            fullscreen = true
        }
    }
    LaunchedEffect(connections) { if (connections == 0) fullscreen = false }
    LaunchedEffect(pin) { if (pin != null) fullscreen = false }

    val video: @Composable () -> Unit = {
        val aspect by viewModel.videoAspect.collectAsState()
        VideoSurfaceView(
            onSurfaceAvailable = onSurfaceAvailable,
            onSurfaceDestroyed = onSurfaceDestroyed,
            aspectRatio = aspect
        )
    }

    when {
        videoPlaybackActive || videoSessionPending -> {
            TvVideoPlayer(viewModel)
        }
        fullscreen -> {
            BackHandler { fullscreen = false }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) { video() }
        }
        else -> {
            TvPanelLayout(
                viewModel = viewModel,
                section = section,
                onSectionChange = { section = it },
                video = video,
                onFullscreen = { fullscreen = true },
                serverName = serverName,
                state = state,
                connections = connections,
                mirroringActive = mirroringActive,
                audioOnly = audioOnly
            )
        }
    }

    if (pin != null) {
        Dialog(onDismissRequest = { viewModel.dismissPin() }) {
            Column(
                modifier = Modifier
                    .background(TvPalette.Surface, TvPanelShape)
                    .padding(horizontal = 48.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Codice AirPlay", color = TvPalette.TextSecondary, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = pin ?: "",
                    color = TvPalette.Accent,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp
                )
                Spacer(Modifier.height(16.dp))
                TvRow(label = "OK", onClick = { viewModel.dismissPin() })
            }
        }
    }
}

@Composable
private fun TvPanelLayout(
    viewModel: MainViewModel,
    section: TvSection,
    onSectionChange: (TvSection) -> Unit,
    video: @Composable () -> Unit,
    onFullscreen: () -> Unit,
    serverName: String,
    state: ServerState,
    connections: Int,
    mirroringActive: Boolean,
    audioOnly: Boolean,
) {
    val idlePreview by viewModel.idlePreview.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TvPalette.Background)
            .padding(horizontal = 40.dp, vertical = 28.dp)
    ) {
        // area anteprima / stato
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(TvPanelShape)
                .background(TvPalette.Surface),
            contentAlignment = Alignment.Center
        ) {
            if (state == ServerState.RUNNING && audioOnly && connections > 0) {
                TvNowPlaying(viewModel)
            } else {
                if (state == ServerState.RUNNING && (mirroringActive || idlePreview)) {
                    video()
                }
                if (state != ServerState.RUNNING || connections == 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (connections > 0) Icons.Default.CastConnected else Icons.Default.Cast,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = TvPalette.TextSecondary.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = when (state) {
                                ServerState.STOPPED -> "Server fermo"
                                ServerState.RUNNING -> "In attesa di connessione…"
                                ServerState.ERROR -> "Errore di avvio del server"
                            },
                            color = TvPalette.TextSecondary,
                            fontSize = 16.sp
                        )
                        if (state == ServerState.RUNNING) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Cerca “$serverName” dal tuo iPhone/iPad/Mac",
                                color = TvPalette.TextSecondary.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(28.dp))

        // pannello destro
        Column(modifier = Modifier.width(400.dp).fillMaxHeight()) {
            when (section) {
                TvSection.MAIN -> TvMainSection(
                    viewModel, state, connections, serverName,
                    mirroringActive = mirroringActive,
                    onFullscreen = onFullscreen,
                    onOpenSettings = { onSectionChange(TvSection.SETTINGS) },
                    onOpenLogs = { onSectionChange(TvSection.LOGS) }
                )
                TvSection.SETTINGS -> {
                    BackHandler { onSectionChange(TvSection.MAIN) }
                    TvSettingsSection(viewModel)
                }
                TvSection.LOGS -> {
                    BackHandler { onSectionChange(TvSection.MAIN) }
                    TvLogsSection(viewModel)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.TvMainSection(
    viewModel: MainViewModel,
    state: ServerState,
    connections: Int,
    serverName: String,
    mirroringActive: Boolean,
    onFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val serverFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { serverFocus.requestFocus() } }

    TvPanelTitle("AirPlay", serverName)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
        TvRow(
            label = "Server",
            icon = if (state == ServerState.RUNNING) Icons.Default.Stop else Icons.Default.PlayArrow,
            value = when (state) {
                ServerState.RUNNING ->
                    if (connections > 0) "Attivo · $connections" else "Attivo"
                ServerState.ERROR -> "Errore"
                ServerState.STOPPED -> "Fermo"
            },
            valueColor = when (state) {
                ServerState.RUNNING -> TvPalette.Positive
                ServerState.ERROR -> TvPalette.Negative
                ServerState.STOPPED -> TvPalette.TextSecondary
            },
            onClick = {
                if (state == ServerState.RUNNING) viewModel.stopServer() else viewModel.startServer()
            },
            modifier = Modifier.focusRequester(serverFocus)
        )
        if (mirroringActive) {
            TvRow(
                label = "Schermo intero",
                icon = Icons.Default.Fullscreen,
                showChevron = true,
                onClick = onFullscreen
            )
        }
        TvRow(
            label = "Log",
            icon = Icons.AutoMirrored.Filled.Article,
            showChevron = true,
            onClick = onOpenLogs
        )
        TvRow(
            label = "Impostazioni",
            icon = Icons.Default.Settings,
            showChevron = true,
            onClick = onOpenSettings
        )
    }

    TvHint("OK seleziona  •  BACK esci")
}

private val ADAPTIVE_STEP_NAMES = listOf(
    "min latenza", "bassa", "media", "alta", "max stabilità"
)

@Composable
private fun TvSettingsSection(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity

    val serverName by viewModel.serverName.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val autoStart by viewModel.autoStart.collectAsState()
    val bootAutoStart by viewModel.bootAutoStart.collectAsState()
    val runInBackground by viewModel.runInBackground.collectAsState()
    val requirePin by viewModel.requirePin.collectAsState()
    val allowNewConn by viewModel.allowNewConn.collectAsState()
    val launchOnConnect by viewModel.launchOnConnect.collectAsState()
    val advertiseVideo by viewModel.advertiseVideo.collectAsState()
    val advertiseAudio by viewModel.advertiseAudio.collectAsState()
    val h265 by viewModel.h265Enabled.collectAsState()
    val forceSwAlac by viewModel.forceSwAlac.collectAsState()
    val alac by viewModel.alacEnabled.collectAsState()
    val aac by viewModel.aacEnabled.collectAsState()
    val enforceSdr by viewModel.enforceSdr.collectAsState()
    val autoFullscreen by viewModel.autoFullscreen.collectAsState()
    val idlePreview by viewModel.idlePreview.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val overscanned by viewModel.overscanned.collectAsState()
    val resolution by viewModel.resolution.collectAsState()
    val maxFps by viewModel.maxFps.collectAsState()
    val developerOptions by viewModel.developerOptions.collectAsState()
    val keyAllowFrameDrop by viewModel.keyAllowFrameDrop.collectAsState()
    val realtimeDecoderPriority by viewModel.realtimeDecoderPriority.collectAsState()
    val operatingRateHint by viewModel.operatingRateHint.collectAsState()
    val lowLatency by viewModel.lowLatency.collectAsState()
    val scheduledOutputBufferRelease by viewModel.scheduledOutputBufferRelease.collectAsState()
    val audioLatencyMs by viewModel.audioLatencyMs.collectAsState()
    val audioAutoBuffer by viewModel.audioAutoBuffer.collectAsState()
    val audioAdaptiveStep by viewModel.audioAdaptiveStep.collectAsState()
    val audioCushionMs by viewModel.audioCushionMs.collectAsState()
    val oboeBufferFrames by viewModel.oboeBufferFrames.collectAsState()
    val debugEnabled by viewModel.debugEnabled.collectAsState()
    val benchmarkLog by viewModel.benchmarkLog.collectAsState()

    var hotkeyKeycode by remember { mutableIntStateOf(HotkeyConfig.getKeycode(context)) }
    var hotkeyMode by remember { mutableStateOf(HotkeyConfig.getMode(context)) }
    var captureMode by remember { mutableStateOf(false) }
    var serviceEnabled by remember { mutableStateOf(isHotkeyServiceEnabled(context)) }
    var hasOverlay by remember { mutableStateOf(canDrawOverlays(context)) }

    // ricontrolla lo stato del servizio / permesso overlay al ritorno dalle impostazioni di sistema
    LaunchedEffect(Unit) {
        while (true) {
            serviceEnabled = isHotkeyServiceEnabled(context)
            hasOverlay = canDrawOverlays(context)
            delay(1000)
        }
    }

    var rootModifier: Modifier = Modifier
    if (captureMode) {
        rootModifier = rootModifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val keyCode = e.key.nativeKeyCode
            when (keyCode) {
                AndroidKeyEvent.KEYCODE_BACK -> { captureMode = false; true }
                AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_VOLUME_UP, AndroidKeyEvent.KEYCODE_VOLUME_DOWN -> true
                else -> {
                    HotkeyConfig.setKeycode(context, keyCode)
                    hotkeyKeycode = keyCode
                    captureMode = false
                    true
                }
            }
        }
    }

    Column(modifier = rootModifier) {
        TvPanelTitle("Impostazioni", "Server, hotkey e interfaccia")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---------- Hotkey ----------
            TvSectionLabel("Hotkey telecomando")
            Text(
                text = if (serviceEnabled) "● Servizio hotkey attivo"
                    else "● Servizio non attivo — abilitalo in Accessibilità",
                color = if (serviceEnabled) TvPalette.Positive else TvPalette.Negative,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
            )
            TvRow(
                label = "Impostazioni Accessibilità",
                icon = Icons.Default.Settings,
                showChevron = true,
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: ActivityNotFoundException) {}
                }
            )
            TvRow(
                label = "Tasto",
                icon = Icons.Default.Keyboard,
                sublabel = shortPressNote(hotkeyKeycode, hotkeyMode),
                picker = true,
                value = hotkeyLabel(hotkeyKeycode),
                onAdjust = { delta ->
                    val idx = HOTKEY_PRESETS.indexOfFirst { it.first == hotkeyKeycode }
                    val next = if (idx < 0) 0
                        else ((idx + delta) % HOTKEY_PRESETS.size + HOTKEY_PRESETS.size) % HOTKEY_PRESETS.size
                    hotkeyKeycode = HOTKEY_PRESETS[next].first
                    HotkeyConfig.setKeycode(context, hotkeyKeycode)
                }
            )
            TvRow(
                label = if (captureMode) "Premi ora un tasto… (BACK annulla)" else "Cattura un altro tasto…",
                onClick = { captureMode = true }
            )
            TvRow(
                label = "Gesto",
                picker = true,
                value = modeLabel(hotkeyMode),
                onAdjust = { delta ->
                    val modes = HotkeyConfig.MODES
                    val idx = modes.indexOf(hotkeyMode).coerceAtLeast(0)
                    val next = ((idx + delta) % modes.size + modes.size) % modes.size
                    hotkeyMode = modes[next]
                    HotkeyConfig.setMode(context, hotkeyMode)
                }
            )

            // ---------- Interfaccia ----------
            TvSectionLabel("Interfaccia")
            TvRow(
                label = "Interfaccia TV",
                sublabel = "Disattiva per tornare all'interfaccia classica",
                checked = true,
                onClick = {
                    UiVariant.setTvUi(context, false)
                    context.startActivity(Intent(context, MainActivity::class.java))
                    activity?.finish()
                }
            )

            // ---------- Server ----------
            TvSectionLabel("Server")
            TvTextInputRow(
                label = "Nome server",
                value = serverName,
                numeric = false,
                onCommit = { if (it.isNotBlank()) viewModel.setServerName(it.trim()) }
            )
            TvTextInputRow(
                label = "Porta",
                value = serverPort.toString(),
                numeric = true,
                range = 1..65535,
                onCommit = { it.toIntOrNull()?.let { p -> viewModel.setServerPort(p) } }
            )
            TvRow(label = "Avvio al boot", checked = bootAutoStart,
                onClick = { viewModel.setBootAutoStart(!bootAutoStart) })
            TvRow(label = "Esegui in background", checked = runInBackground,
                onClick = { viewModel.setRunInBackground(!runInBackground) })

            // ---------- Connessione ----------
            TvSectionLabel("Connessione")
            TvRow(label = "Richiedi PIN", checked = requirePin,
                onClick = { viewModel.setRequirePin(!requirePin) })
            TvRow(label = "Consenti nuove connessioni", checked = allowNewConn,
                onClick = { viewModel.setAllowNewConn(!allowNewConn) })
            TvRow(
                label = "Apri app alla connessione",
                sublabel = if (launchOnConnect && !hasOverlay)
                    "Serve il permesso \"Mostra sopra le app\"" else null,
                checked = launchOnConnect,
                onClick = {
                    val enabling = !launchOnConnect
                    viewModel.setLaunchOnConnect(enabling)
                    if (enabling && !canDrawOverlays(context)) {
                        try {
                            context.startActivity(overlayPermissionIntent(context))
                        } catch (_: ActivityNotFoundException) {}
                    }
                }
            )

            // ---------- Schermo ----------
            TvSectionLabel("Schermo")
            TvRow(label = "Schermo intero automatico", checked = autoFullscreen,
                onClick = { viewModel.setAutoFullscreen(!autoFullscreen) })
            TvRow(
                label = "Risoluzione",
                picker = true,
                value = if (resolution == "auto") "Auto" else resolution,
                onAdjust = { delta ->
                    val options = listOf("auto", "3840x2160", "1920x1080", "1280x720")
                    val idx = options.indexOf(resolution).coerceAtLeast(0)
                    val next = ((idx + delta) % options.size + options.size) % options.size
                    viewModel.setResolution(options[next])
                }
            )
            TvRow(
                label = "FPS massimi",
                picker = true,
                value = maxFps.toString(),
                onAdjust = { delta ->
                    val options = listOf(24, 30, 60, 120)
                    val idx = options.indexOf(maxFps).coerceAtLeast(0)
                    val next = ((idx + delta) % options.size + options.size) % options.size
                    viewModel.setMaxFps(options[next])
                }
            )
            TvRow(label = "Overscan", checked = overscanned,
                onClick = { viewModel.setOverscanned(!overscanned) })

            // ---------- Decodifica ----------
            TvSectionLabel("Decodifica")
            TvRow(label = "Codifica H265", checked = h265,
                onClick = { viewModel.setH265Enabled(!h265) })
            TvRow(label = "Forza ALAC software", checked = forceSwAlac,
                onClick = { viewModel.setForceSwAlac(!forceSwAlac) })

            // ---------- Sviluppatore ----------
            TvSectionLabel("Sviluppatore")
            TvRow(
                label = "Opzioni sviluppatore",
                sublabel = if (developerOptions) "Opzioni avanzate mostrate" else "Mostra opzioni avanzate",
                checked = developerOptions,
                onClick = { viewModel.setDeveloperOptions(!developerOptions) }
            )

            if (developerOptions) {
                TvRow(label = "Avvia server all'apertura", checked = autoStart,
                    onClick = { viewModel.setAutoStart(!autoStart) })
                TvRow(label = "Mantieni schermo acceso", checked = keepScreenOn,
                    onClick = { viewModel.setKeepScreenOn(!keepScreenOn) })
                TvRow(label = "Anteprima da fermo", checked = idlePreview,
                    onClick = { viewModel.setIdlePreview(!idlePreview) })
                TvRow(label = "Annuncia video", checked = advertiseVideo,
                    onClick = { viewModel.setAdvertiseVideo(!advertiseVideo) })
                TvRow(label = "Annuncia audio", checked = advertiseAudio,
                    onClick = { viewModel.setAdvertiseAudio(!advertiseAudio) })
                TvRow(label = "ALAC", checked = alac,
                    onClick = { viewModel.setAlacEnabled(!alac) })
                TvRow(label = "AAC", checked = aac,
                    onClick = { viewModel.setAacEnabled(!aac) })
                TvRow(label = "Consenti frame drop", checked = keyAllowFrameDrop,
                    onClick = { viewModel.setKeyAllowFrameDrop(!keyAllowFrameDrop) })
                TvRow(label = "Forza SDR", checked = enforceSdr,
                    onClick = { viewModel.setEnforceSdr(!enforceSdr) })
                TvRow(label = "Priorità decoder realtime", checked = realtimeDecoderPriority,
                    onClick = { viewModel.setRealtimeDecoderPriority(!realtimeDecoderPriority) })
                TvRow(label = "Operating rate hint", checked = operatingRateHint,
                    onClick = { viewModel.setOperatingRateHint(!operatingRateHint) })
                TvRow(label = "Bassa latenza decoder", checked = lowLatency,
                    onClick = { viewModel.setLowLatency(!lowLatency) })
                TvRow(label = "Scheduled output buffer release", checked = scheduledOutputBufferRelease,
                    onClick = { viewModel.setScheduledOutputBufferRelease(!scheduledOutputBufferRelease) })

                // ritardo audio: toggle + valore ±50 ms (0..1000)
                TvRow(label = "Ritardo audio", checked = audioLatencyMs >= 0,
                    onClick = { viewModel.setAudioLatencyMs(if (audioLatencyMs >= 0) -1 else 250) })
                if (audioLatencyMs >= 0) {
                    TvRow(
                        label = "  Valore",
                        picker = true,
                        value = "$audioLatencyMs ms",
                        onAdjust = { delta ->
                            viewModel.setAudioLatencyMs((audioLatencyMs + delta * 50).coerceIn(0, 1000))
                        }
                    )
                }

                // buffer audio: automatico (step adattivo) oppure cushion fisso
                TvRow(label = "Buffer audio automatico", checked = audioAutoBuffer,
                    onClick = { viewModel.setAudioAutoBuffer(!audioAutoBuffer) })
                if (audioAutoBuffer) {
                    val maxStep = Prefs.ADAPTIVE_PERCENTILES.size - 1
                    val step = audioAdaptiveStep.coerceIn(0, maxStep)
                    TvRow(
                        label = "  Stabilità buffer",
                        picker = true,
                        value = "${Prefs.ADAPTIVE_PERCENTILES[step]}% · ${ADAPTIVE_STEP_NAMES[step]}",
                        onAdjust = { delta ->
                            viewModel.setAudioAdaptiveStep((audioAdaptiveStep + delta).coerceIn(0, maxStep))
                        }
                    )
                } else {
                    TvTextInputRow(
                        label = "  Cushion buffer (ms)",
                        value = audioCushionMs.toString(),
                        numeric = true,
                        range = 1..1000,
                        onCommit = { it.toIntOrNull()?.let { v -> viewModel.setAudioCushionMs(v) } }
                    )
                }

                TvTextInputRow(
                    label = "Oboe buffer frames",
                    value = oboeBufferFrames.toString(),
                    numeric = true,
                    range = 0..8192,
                    onCommit = { it.toIntOrNull()?.let { v -> viewModel.setOboeBufferFrames(v) } }
                )
                TvRow(label = "Overlay debug", checked = debugEnabled,
                    onClick = { viewModel.setDebugEnabled(!debugEnabled) })
                TvRow(label = "Log benchmark", checked = benchmarkLog,
                    onClick = { viewModel.setBenchmarkLog(!benchmarkLog) })
            }

            Spacer(Modifier.height(8.dp))
        }

        TvHint("OK cambia  •  ←/→ scegli  •  BACK indietro")
    }
}

/**
 * Riga che apre un dialog con campo di testo: il focus sul campo richiama
 * la tastiera di sistema (numerica se numeric=true). Con range, il valore
 * numerico viene limitato al commit.
 */
@Composable
private fun TvTextInputRow(
    label: String,
    value: String,
    numeric: Boolean,
    onCommit: (String) -> Unit,
    range: IntRange? = null,
) {
    var showDialog by remember { mutableStateOf(false) }

    TvRow(label = label.trim(), value = value, onClick = { showDialog = true })

    if (showDialog) {
        var text by remember { mutableStateOf(value) }
        val focusRequester = remember { FocusRequester() }

        val commit: () -> Unit = {
            var out = text.trim()
            if (numeric && range != null) {
                val n = out.toIntOrNull()
                if (n != null) out = n.coerceIn(range).toString()
            }
            onCommit(out)
            showDialog = false
        }

        Dialog(onDismissRequest = { showDialog = false }) {
            Column(
                modifier = Modifier
                    .widthIn(min = 380.dp)
                    .background(TvPalette.Surface, TvPanelShape)
                    .padding(24.dp)
            ) {
                Text(label.trim(), color = TvPalette.TextPrimary, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = if (numeric) it.filter { c -> c.isDigit() } else it
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        TvRow(label = "Annulla", onClick = { showDialog = false })
                    }
                    Box(Modifier.weight(1f)) {
                        TvRow(label = "OK", onClick = commit)
                    }
                }
            }
        }

        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }
}

@Composable
private fun TvLogsSection(viewModel: MainViewModel) {
    val logs by viewModel.logs.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.scrollToItem(logs.size - 1)
    }

    Column {
        TvPanelTitle("Log", "${logs.size} righe")

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(TvRowShape)
                .background(TvPalette.Row)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(12.dp)
            ) {
                items(logs) { line ->
                    Text(
                        text = line,
                        color = TvPalette.TextPrimary.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        TvRow(label = "Cancella log", onClick = { viewModel.clearLogs() })
        TvHint("BACK indietro")
    }
}

@Composable
private fun TvNowPlaying(viewModel: MainViewModel) {
    val track by viewModel.trackInfo.collectAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TvPalette.Row),
            contentAlignment = Alignment.Center
        ) {
            val art = track.coverArt
            if (art != null) {
                Image(
                    bitmap = art.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote, contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = TvPalette.TextSecondary.copy(alpha = 0.5f)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = track.title.ifEmpty { "In riproduzione" },
            color = TvPalette.TextPrimary, fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (track.artist.isNotEmpty()) {
            Text(track.artist, color = TvPalette.TextSecondary, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Player AirPlay video minimale per D-pad: OK pausa, ←/→ ±10 s, BACK chiude. */
@Composable
private fun TvVideoPlayer(viewModel: MainViewModel) {
    val playing by viewModel.videoPlaying.collectAsState()
    val positionMs by viewModel.videoPositionMs.collectAsState()
    val durationMs by viewModel.videoDurationMs.collectAsState()
    val buffering by viewModel.videoBuffering.collectAsState()
    val title by viewModel.videoTitle.collectAsState()
    val aspect by viewModel.videoPlaybackAspect.collectAsState()
    val active by viewModel.videoPlaybackActive.collectAsState()

    var overlayVisible by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(overlayTick, playing, active) {
        overlayVisible = true
        if (active && playing) {
            delay(4000)
            overlayVisible = false
        }
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    BackHandler { viewModel.stopVideoPlayback() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        viewModel.toggleVideoPlayPause(showOverlay = false); overlayTick++; true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        viewModel.seekVideoBy(-10_000L); overlayTick++; true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        viewModel.seekVideoBy(10_000L); overlayTick++; true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_UP,
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { overlayTick++; true }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        VideoSurfaceView(
            onSurfaceAvailable = { viewModel.onVideoPlaybackSurfaceAvailable(it) },
            onSurfaceDestroyed = { viewModel.onVideoPlaybackSurfaceDestroyed(it) },
            aspectRatio = aspect
        )

        if (buffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(72.dp),
                color = TvPalette.Accent
            )
        }

        if (overlayVisible) {
            // barra superiore: titolo
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(32.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            // barra inferiore: progresso + tempi
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 32.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                if (durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = TvPalette.Accent,
                        trackColor = TvPalette.TrackEmpty,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = (if (playing) "▶  " else "⏸  ") + formatTvTime(positionMs),
                        color = Color.White, fontSize = 13.sp
                    )
                    Spacer(Modifier.weight(1f))
                    if (durationMs > 0) {
                        Text(formatTvTime(durationMs), color = Color.White, fontSize = 13.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "OK pausa  •  ←/→ ±10s  •  BACK chiudi",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp
                    )
                }
            }
        }
    }
}

private fun hotkeyLabel(keycode: Int): String =
    HOTKEY_PRESETS.firstOrNull { it.first == keycode }?.second
        ?: AndroidKeyEvent.keyCodeToString(keycode).removePrefix("KEYCODE_")

private fun canDrawOverlays(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)

private fun overlayPermissionIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun isHotkeyServiceEnabled(context: android.content.Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.contains("${context.packageName}/${HotkeyService::class.java.name}")
}

private fun formatTvTime(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
