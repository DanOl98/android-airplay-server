package io.github.jqssun.airplay.ui.tv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.jqssun.airplay.HotkeyConfig
import io.github.jqssun.airplay.HotkeyService
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.MainActivity
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.TvOptions
import io.github.jqssun.airplay.UiVariant
import io.github.jqssun.airplay.service.AirPlayService.ServerState
import io.github.jqssun.airplay.ui.VideoSurfaceView
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlinx.coroutines.delay

private enum class TvSection { MAIN, SETTINGS, LOGS }

/** Quanto resta visibile il pannello richiamato durante la riproduzione. */
private const val PANEL_AUTO_HIDE_MS = 6000L

private val HOTKEY_PRESETS = listOf(
    AndroidKeyEvent.KEYCODE_PROG_BLUE to R.string.tv_key_blue,
    AndroidKeyEvent.KEYCODE_PROG_RED to R.string.tv_key_red,
    AndroidKeyEvent.KEYCODE_PROG_GREEN to R.string.tv_key_green,
    AndroidKeyEvent.KEYCODE_PROG_YELLOW to R.string.tv_key_yellow,
    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to R.string.tv_key_play_pause,
    AndroidKeyEvent.KEYCODE_VOLUME_MUTE to R.string.tv_key_mute,
)

@Composable
private fun modeLabel(mode: String): String = stringResource(
    when (mode) {
        HotkeyConfig.MODE_SINGLE_PRESS -> R.string.tv_gesture_single
        HotkeyConfig.MODE_DOUBLE_PRESS -> R.string.tv_gesture_double
        else -> R.string.tv_gesture_long
    }
)

/** Cosa succede alla pressione breve con il tasto e il gesto scelti. */
@Composable
private fun shortPressNote(keycode: Int, mode: String): String = stringResource(
    when {
        mode == HotkeyConfig.MODE_SINGLE_PRESS -> R.string.tv_shortpress_reserved
        HotkeyService.canReEmit(keycode) -> R.string.tv_shortpress_kept
        else -> R.string.tv_shortpress_native
    }
)

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

    // il pannello prende i colori dalla copertina solo mentre suona qualcosa
    val track by viewModel.trackInfo.collectAsState()
    val themeCover = track.coverArt.takeIf {
        audioOnly && connections > 0 && state == ServerState.RUNNING
    }
    val accent = rememberCoverAccent(themeCover)

    val optionsContext = LocalContext.current
    var showVolume by remember { mutableStateOf(TvOptions.showVolume(optionsContext)) }

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
        else -> CompositionLocalProvider(LocalTvAccent provides accent) {
            TvBlurredCoverBackground(themeCover) {
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
                    audioOnly = audioOnly,
                    showVolume = showVolume,
                    onShowVolumeChange = {
                        showVolume = it
                        TvOptions.setShowVolume(optionsContext, it)
                    }
                )
            }
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
                Text(
                    stringResource(R.string.dialog_pin_title),
                    color = TvPalette.TextSecondary,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = pin ?: "",
                    color = TvPalette.Accent,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp
                )
                Spacer(Modifier.height(16.dp))
                TvRow(
                    label = stringResource(R.string.btn_ok),
                    onClick = { viewModel.dismissPin() }
                )
            }
        }
    }
}

/**
 * Copertina sfocata come sfondo del pannello. La leggibilità è garantita da
 * tre livelli: la copertina viene ridotta a pochi pixel (sfocatura che non
 * dipende dall'API), poi sfocata davvero dove disponibile, infine coperta da
 * una velatura scura. Senza copertina resta lo sfondo piatto di sempre.
 */
@Composable
private fun TvBlurredCoverBackground(
    cover: Bitmap?,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(TvPalette.Background)) {
        if (cover != null && !cover.isRecycled) {
            val tiny = remember(cover) {
                runCatching { cover.scale(48, 48, filter = true) }.getOrNull()
            }
            if (tiny != null) {
                Image(
                    bitmap = tiny.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(56.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                )
            }
            // velatura: senza questa il testo chiaro sparirebbe sulle copertine chiare
            val scrim = LocalTvAccent.current.scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black.copy(alpha = scrim),
                                Color.Black.copy(alpha = (scrim + 0.12f).coerceAtMost(0.85f)),
                            )
                        )
                    )
            )
        }
        content()
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
    showVolume: Boolean,
    onShowVolumeChange: (Boolean) -> Unit,
) {
    val idlePreview by viewModel.idlePreview.collectAsState()
    val nowPlaying = state == ServerState.RUNNING && audioOnly && connections > 0

    // durante la riproduzione il pannello sparisce e la copertina si prende
    // tutto lo schermo; → (o ↑/MENU) lo richiama, e si rinasconde da solo
    var panelRevealed by remember { mutableStateOf(false) }
    var revealTick by remember { mutableIntStateOf(0) }
    val panelShown = !nowPlaying || panelRevealed
    val immersive = nowPlaying && !panelRevealed

    LaunchedEffect(nowPlaying) { if (!nowPlaying) panelRevealed = false }
    LaunchedEffect(panelRevealed, revealTick, section, nowPlaying) {
        if (panelRevealed && nowPlaying && section == TvSection.MAIN) {
            delay(PANEL_AUTO_HIDE_MS)
            panelRevealed = false
        }
    }
    if (nowPlaying && panelRevealed && section == TvSection.MAIN) {
        BackHandler { panelRevealed = false }
    }

    // niente fondo opaco qui: lo sfondo (piatto o copertina sfocata) è già
    // dipinto da TvBlurredCoverBackground, coprirlo lo annullerebbe
    Row(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || !nowPlaying) {
                    return@onPreviewKeyEvent false
                }
                when (e.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_DPAD_UP,
                    AndroidKeyEvent.KEYCODE_MENU -> {
                        if (!panelRevealed) {
                            panelRevealed = true
                            true // il tasto serve solo a richiamare il pannello
                        } else {
                            revealTick++
                            false
                        }
                    }
                    else -> {
                        if (panelRevealed) revealTick++
                        false
                    }
                }
            }
            .padding(horizontal = 40.dp, vertical = 28.dp)
    ) {
        // area anteprima / stato: durante la riproduzione niente riquadro, così
        // copertina e comandi galleggiano sullo sfondo sfocato senza interromperlo
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(TvPanelShape)
                .background(
                    if (nowPlaying) Color.Transparent else LocalTvAccent.current.surface
                ),
            contentAlignment = Alignment.Center
        ) {
            if (nowPlaying) {
                TvNowPlaying(viewModel, showVolume, immersive)
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
                            text = stringResource(
                                when (state) {
                                    ServerState.STOPPED -> R.string.server_stopped
                                    ServerState.RUNNING -> R.string.waiting_for_connection
                                    ServerState.ERROR -> R.string.error_starting_server
                                }
                            ),
                            color = TvPalette.TextSecondary,
                            fontSize = 16.sp
                        )
                        if (state == ServerState.RUNNING) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.tv_find_server, serverName),
                                color = TvPalette.TextSecondary.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        if (!panelShown) return@Row

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
                    TvSettingsSection(viewModel, showVolume, onShowVolumeChange)
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

    TvPanelTitle(stringResource(R.string.tv_panel_title), serverName)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
        TvRow(
            label = stringResource(R.string.section_server),
            icon = if (state == ServerState.RUNNING) Icons.Default.Stop else Icons.Default.PlayArrow,
            value = when (state) {
                ServerState.RUNNING ->
                    if (connections > 0) stringResource(R.string.tv_status_active_count, connections)
                    else stringResource(R.string.tv_status_active)
                ServerState.ERROR -> stringResource(R.string.error_label)
                ServerState.STOPPED -> stringResource(R.string.stopped_label)
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
                label = stringResource(R.string.tv_fullscreen),
                icon = Icons.Default.Fullscreen,
                showChevron = true,
                onClick = onFullscreen
            )
        }
        TvRow(
            label = stringResource(R.string.tab_logs),
            icon = Icons.AutoMirrored.Filled.Article,
            showChevron = true,
            onClick = onOpenLogs
        )
        TvRow(
            label = stringResource(R.string.tab_settings),
            icon = Icons.Default.Settings,
            showChevron = true,
            onClick = onOpenSettings
        )
    }

    TvHint(stringResource(R.string.tv_hint_main))
}

@Composable
private fun TvSettingsSection(
    viewModel: MainViewModel,
    showVolume: Boolean,
    onShowVolumeChange: (Boolean) -> Unit,
) {
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

    // il servizio hotkey non deve filtrare nulla mentre si sceglie il tasto
    DisposableEffect(captureMode) {
        HotkeyService.captureInProgress = captureMode
        onDispose { HotkeyService.captureInProgress = false }
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
        TvPanelTitle(
            stringResource(R.string.tab_settings),
            stringResource(R.string.tv_settings_subtitle)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---------- Hotkey ----------
            TvSectionLabel(stringResource(R.string.tv_section_hotkey))
            Text(
                text = stringResource(
                    if (serviceEnabled) R.string.tv_hotkey_service_active
                    else R.string.tv_hotkey_service_inactive
                ),
                color = if (serviceEnabled) TvPalette.Positive else TvPalette.Negative,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
            )
            TvRow(
                label = stringResource(R.string.tv_accessibility_settings),
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
                label = stringResource(R.string.tv_hotkey_key),
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
                label = stringResource(
                    if (captureMode) R.string.tv_hotkey_capture_active
                    else R.string.tv_hotkey_capture
                ),
                onClick = { captureMode = true }
            )
            TvRow(
                label = stringResource(R.string.tv_hotkey_gesture),
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
            TvSectionLabel(stringResource(R.string.tv_section_interface))
            TvRow(
                label = stringResource(R.string.tv_show_volume),
                sublabel = stringResource(R.string.tv_show_volume_desc),
                checked = showVolume,
                onClick = { onShowVolumeChange(!showVolume) }
            )
            TvRow(
                label = stringResource(R.string.setting_tv_ui),
                sublabel = stringResource(R.string.tv_ui_switch_desc),
                checked = true,
                onClick = {
                    UiVariant.setTvUi(context, false)
                    context.startActivity(Intent(context, MainActivity::class.java))
                    activity?.finish()
                }
            )

            // ---------- Server ----------
            TvSectionLabel(stringResource(R.string.section_server))
            TvTextInputRow(
                label = stringResource(R.string.setting_server_name),
                value = serverName,
                numeric = false,
                onCommit = { if (it.isNotBlank()) viewModel.setServerName(it.trim()) }
            )
            TvTextInputRow(
                label = stringResource(R.string.setting_server_port),
                value = serverPort.toString(),
                numeric = true,
                range = 1..65535,
                onCommit = { it.toIntOrNull()?.let { p -> viewModel.setServerPort(p) } }
            )
            TvRow(label = stringResource(R.string.setting_boot_auto_start), checked = bootAutoStart,
                onClick = { viewModel.setBootAutoStart(!bootAutoStart) })
            TvRow(label = stringResource(R.string.setting_run_in_background), checked = runInBackground,
                onClick = { viewModel.setRunInBackground(!runInBackground) })

            // ---------- Connessione ----------
            TvSectionLabel(stringResource(R.string.section_connection))
            TvRow(label = stringResource(R.string.setting_require_pin), checked = requirePin,
                onClick = { viewModel.setRequirePin(!requirePin) })
            TvRow(label = stringResource(R.string.setting_allow_new_conn), checked = allowNewConn,
                onClick = { viewModel.setAllowNewConn(!allowNewConn) })
            TvRow(
                label = stringResource(R.string.setting_launch_on_connect),
                sublabel = if (launchOnConnect && !hasOverlay)
                    stringResource(R.string.setting_launch_on_connect_no_permission) else null,
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
            TvSectionLabel(stringResource(R.string.section_display))
            TvRow(label = stringResource(R.string.setting_auto_fullscreen), checked = autoFullscreen,
                onClick = { viewModel.setAutoFullscreen(!autoFullscreen) })
            TvRow(
                label = stringResource(R.string.setting_resolution),
                picker = true,
                value = if (resolution == "auto") stringResource(R.string.setting_resolution_auto)
                    else resolution,
                onAdjust = { delta ->
                    val options = listOf("auto", "3840x2160", "1920x1080", "1280x720")
                    val idx = options.indexOf(resolution).coerceAtLeast(0)
                    val next = ((idx + delta) % options.size + options.size) % options.size
                    viewModel.setResolution(options[next])
                }
            )
            TvRow(
                label = stringResource(R.string.setting_max_fps),
                picker = true,
                value = maxFps.toString(),
                onAdjust = { delta ->
                    val options = listOf(24, 30, 60, 120)
                    val idx = options.indexOf(maxFps).coerceAtLeast(0)
                    val next = ((idx + delta) % options.size + options.size) % options.size
                    viewModel.setMaxFps(options[next])
                }
            )
            TvRow(label = stringResource(R.string.setting_overscanned), checked = overscanned,
                onClick = { viewModel.setOverscanned(!overscanned) })

            // ---------- Decodifica ----------
            TvSectionLabel(stringResource(R.string.section_decode))
            TvRow(label = stringResource(R.string.setting_h265), checked = h265,
                onClick = { viewModel.setH265Enabled(!h265) })
            TvRow(label = stringResource(R.string.setting_sw_alac), checked = forceSwAlac,
                onClick = { viewModel.setForceSwAlac(!forceSwAlac) })

            // ---------- Sviluppatore ----------
            TvSectionLabel(stringResource(R.string.section_developer))
            TvRow(
                label = stringResource(R.string.setting_developer_options),
                sublabel = stringResource(
                    if (developerOptions) R.string.tv_dev_options_shown
                    else R.string.setting_developer_options_desc
                ),
                checked = developerOptions,
                onClick = { viewModel.setDeveloperOptions(!developerOptions) }
            )

            if (developerOptions) {
                TvRow(label = stringResource(R.string.setting_auto_start), checked = autoStart,
                    onClick = { viewModel.setAutoStart(!autoStart) })
                TvRow(label = stringResource(R.string.setting_keep_screen_on), checked = keepScreenOn,
                    onClick = { viewModel.setKeepScreenOn(!keepScreenOn) })
                TvRow(label = stringResource(R.string.setting_idle_preview), checked = idlePreview,
                    onClick = { viewModel.setIdlePreview(!idlePreview) })
                TvRow(label = stringResource(R.string.setting_advertise_video), checked = advertiseVideo,
                    onClick = { viewModel.setAdvertiseVideo(!advertiseVideo) })
                TvRow(label = stringResource(R.string.setting_advertise_audio), checked = advertiseAudio,
                    onClick = { viewModel.setAdvertiseAudio(!advertiseAudio) })
                TvRow(label = stringResource(R.string.setting_alac), checked = alac,
                    onClick = { viewModel.setAlacEnabled(!alac) })
                TvRow(label = stringResource(R.string.setting_aac), checked = aac,
                    onClick = { viewModel.setAacEnabled(!aac) })
                TvRow(label = stringResource(R.string.setting_key_allow_frame_drop), checked = keyAllowFrameDrop,
                    onClick = { viewModel.setKeyAllowFrameDrop(!keyAllowFrameDrop) })
                TvRow(label = stringResource(R.string.setting_enforce_sdr), checked = enforceSdr,
                    onClick = { viewModel.setEnforceSdr(!enforceSdr) })
                TvRow(label = stringResource(R.string.setting_realtime_decoder_priority), checked = realtimeDecoderPriority,
                    onClick = { viewModel.setRealtimeDecoderPriority(!realtimeDecoderPriority) })
                TvRow(label = stringResource(R.string.setting_operating_rate_hint), checked = operatingRateHint,
                    onClick = { viewModel.setOperatingRateHint(!operatingRateHint) })
                TvRow(label = stringResource(R.string.setting_low_latency), checked = lowLatency,
                    onClick = { viewModel.setLowLatency(!lowLatency) })
                TvRow(label = stringResource(R.string.setting_scheduled_output_buffer_release),
                    checked = scheduledOutputBufferRelease,
                    onClick = { viewModel.setScheduledOutputBufferRelease(!scheduledOutputBufferRelease) })

                // ritardo audio: toggle + valore ±50 ms (0..1000)
                TvRow(label = stringResource(R.string.setting_audio_delay), checked = audioLatencyMs >= 0,
                    onClick = { viewModel.setAudioLatencyMs(if (audioLatencyMs >= 0) -1 else 250) })
                if (audioLatencyMs >= 0) {
                    TvRow(
                        label = stringResource(R.string.tv_audio_delay_value),
                        picker = true,
                        value = stringResource(R.string.audio_delay_value, audioLatencyMs),
                        onAdjust = { delta ->
                            viewModel.setAudioLatencyMs((audioLatencyMs + delta * 50).coerceIn(0, 1000))
                        }
                    )
                }

                // buffer audio: automatico (step adattivo) oppure cushion fisso
                TvRow(label = stringResource(R.string.setting_audio_auto_buffer), checked = audioAutoBuffer,
                    onClick = { viewModel.setAudioAutoBuffer(!audioAutoBuffer) })
                if (audioAutoBuffer) {
                    val maxStep = Prefs.ADAPTIVE_PERCENTILES.size - 1
                    val step = audioAdaptiveStep.coerceIn(0, maxStep)
                    val stepNames = stringArrayResource(R.array.audio_adaptive_step_names)
                    TvRow(
                        label = stringResource(R.string.tv_audio_buffer_stability),
                        picker = true,
                        value = stringResource(
                            R.string.audio_adaptive_value,
                            Prefs.ADAPTIVE_PERCENTILES[step],
                            stepNames[step]
                        ),
                        onAdjust = { delta ->
                            viewModel.setAudioAdaptiveStep((audioAdaptiveStep + delta).coerceIn(0, maxStep))
                        }
                    )
                } else {
                    TvTextInputRow(
                        label = stringResource(R.string.setting_audio_cushion_ms),
                        value = audioCushionMs.toString(),
                        numeric = true,
                        range = 1..1000,
                        onCommit = { it.toIntOrNull()?.let { v -> viewModel.setAudioCushionMs(v) } }
                    )
                }

                TvTextInputRow(
                    label = stringResource(R.string.setting_oboe_buffer_frames),
                    value = oboeBufferFrames.toString(),
                    numeric = true,
                    range = 0..8192,
                    onCommit = { it.toIntOrNull()?.let { v -> viewModel.setOboeBufferFrames(v) } }
                )
                TvRow(label = stringResource(R.string.setting_debug_overlay), checked = debugEnabled,
                    onClick = { viewModel.setDebugEnabled(!debugEnabled) })
                TvRow(label = stringResource(R.string.setting_benchmark_log), checked = benchmarkLog,
                    onClick = { viewModel.setBenchmarkLog(!benchmarkLog) })
            }

            Spacer(Modifier.height(8.dp))
        }

        TvHint(stringResource(R.string.tv_hint_settings))
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
                        TvRow(
                            label = stringResource(R.string.tv_cancel),
                            onClick = { showDialog = false }
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        TvRow(label = stringResource(R.string.btn_ok), onClick = commit)
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
        TvPanelTitle(
            stringResource(R.string.tab_logs),
            stringResource(R.string.tv_logs_lines, logs.size)
        )

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
        TvRow(
            label = stringResource(R.string.tv_logs_clear),
            onClick = { viewModel.clearLogs() }
        )
        TvHint(stringResource(R.string.tv_hint_back))
    }
}

@Composable
private fun TvNowPlaying(
    viewModel: MainViewModel,
    showVolume: Boolean,
    immersive: Boolean,
) {
    val track by viewModel.trackInfo.collectAsState()
    val player = viewModel.dacpPlayer
    val playFocus = remember { FocusRequester() }

    // a tutto schermo i comandi sono l'unica cosa navigabile: il focus va lì
    LaunchedEffect(immersive) {
        if (immersive) {
            delay(120)
            runCatching { playFocus.requestFocus() }
        }
    }

    var playing by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (player != null) {
            playing = player.playWhenReady
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0 } ?: 0L
            delay(500)
        }
    }

    val contentWidth = if (immersive) 520.dp else 300.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // senza i tasti volume avanza spazio: la copertina si prende quello
        Box(
            modifier = Modifier
                .size(
                    when {
                        immersive && !showVolume -> 340.dp
                        immersive -> 300.dp
                        showVolume -> 200.dp
                        else -> 260.dp
                    }
                )
                .clip(RoundedCornerShape(16.dp))
                .background(LocalTvAccent.current.row),
            contentAlignment = Alignment.Center
        ) {
            val art = track.coverArt
            if (art != null) {
                Image(
                    bitmap = art.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_cover_art),
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

        Spacer(Modifier.height(16.dp))
        // larghezza limitata: i titoli lunghi andrebbero a toccare i bordi
        Text(
            text = track.title.ifEmpty { stringResource(R.string.tv_now_playing) },
            color = TvPalette.TextPrimary,
            fontSize = if (immersive) 22.sp else 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = contentWidth)
        )
        if (track.artist.isNotEmpty()) {
            Text(
                track.artist,
                color = TvPalette.TextSecondary,
                fontSize = if (immersive) 16.sp else 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = contentWidth)
            )
        }

        if (durationMs > 0) {
            Spacer(Modifier.height(14.dp))
            TvProgressBar(
                progress = positionMs.toFloat() / durationMs,
                modifier = Modifier.width(contentWidth)
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.width(contentWidth)) {
                Text(formatTvTime(positionMs), color = TvPalette.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(formatTvTime(durationMs), color = TvPalette.TextSecondary, fontSize = 12.sp)
            }
        }

        if (player != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvIconButton(
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.cd_rewind),
                    onClick = { player.seekToPrevious() }
                )
                TvIconButton(
                    icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_pause),
                    size = if (immersive) 72.dp else 60.dp,
                    primary = true,
                    modifier = Modifier.focusRequester(playFocus),
                    onClick = {
                        if (playing) player.pause() else player.play()
                        playing = !playing
                    }
                )
                TvIconButton(
                    icon = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.cd_fast_forward),
                    onClick = { player.seekToNext() }
                )
            }

            if (showVolume) {
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvIconButton(
                    icon = Icons.AutoMirrored.Filled.VolumeDown,
                    contentDescription = stringResource(R.string.cd_volume_down),
                    size = 40.dp,
                    onClick = { viewModel.audioVolumeDown() }
                )
                TvIconButton(
                    icon = Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = stringResource(R.string.cd_mute),
                    size = 40.dp,
                    onClick = { viewModel.audioMuteToggle() }
                )
                TvIconButton(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.cd_volume_up),
                    size = 40.dp,
                    onClick = { viewModel.audioVolumeUp() }
                )
                }
            }
        }

        if (immersive) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.tv_hint_now_playing),
                color = TvPalette.TextSecondary.copy(alpha = 0.8f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
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
                        stringResource(R.string.tv_hint_player),
                        color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun hotkeyLabel(keycode: Int): String =
    HOTKEY_PRESETS.firstOrNull { it.first == keycode }?.let { stringResource(it.second) }
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
