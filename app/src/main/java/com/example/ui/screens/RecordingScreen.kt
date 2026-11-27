package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.viewmodel.AudioSyncViewModel
import com.example.data.viewmodel.NavigationTab
import com.example.ui.components.AudioPlayerCard
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.EmeraldSynced
import com.example.ui.theme.LocalAppLanguage
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecordingScreen composable powered directly by Android's MediaRecorder API
 * with explicit Start/Stop controls, runtime microphone permission handling,
 * local .m4a file persistence, and a live auto-scrolling waveform visualizer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    viewModel: AudioSyncViewModel? = null,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val localLanguage = LocalAppLanguage.current
    val vmLanguage by (viewModel?.appLanguage?.collectAsState() ?: remember { mutableStateOf(localLanguage) })
    val isFr = vmLanguage == AppLanguage.FRENCH

    val recordings by (viewModel?.recordings?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val playerState by (viewModel?.playerState?.collectAsState() ?: remember { mutableStateOf(com.example.data.viewmodel.PlayerState()) })
    val searchQuery by (viewModel?.searchQuery?.collectAsState() ?: remember { mutableStateOf("") })

    BackHandler(enabled = onNavigateBack != null || viewModel != null) {
        if (onNavigateBack != null) {
            onNavigateBack()
        } else {
            viewModel?.setTab(NavigationTab.RECORDER_VAULT)
        }
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingStartAfterPermission by remember { mutableStateOf(false) }

    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var durationSeconds by remember { mutableIntStateOf(0) }
    var liveAmplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentPeakPercent by remember { mutableIntStateOf(0) }
    var currentOutputFile by remember { mutableStateOf<File?>(null) }
    var lastSavedFile by remember { mutableStateOf<File?>(null) }
    var localSavedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var customFileName by remember {
        mutableStateOf(
            "rec_studio_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        )
    }

    fun startRecording(): Boolean {
        return try {
            errorMessage = null
            mediaRecorder?.let { old ->
                try {
                    if (isRecording) old.stop()
                    old.reset()
                    old.release()
                } catch (_: Exception) {
                }
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val sanitizedName = customFileName.ifBlank { "recording" }
                .trim()
                .replace("\\s+".toRegex(), "_")
            val storageDir = context.getExternalFilesDir("audio_recordings") ?: context.filesDir
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            val outputFile = File(storageDir, "${sanitizedName}_$timestamp.m4a")

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            currentOutputFile = outputFile
            isRecording = true
            isPaused = false
            durationSeconds = 0
            currentPeakPercent = 0
            liveAmplitudes = emptyList()
            true
        } catch (e: Exception) {
            Log.e("RecordingScreen", "Failed to start MediaRecorder", e)
            errorMessage = if (isFr) {
                "Erreur MediaRecorder : ${e.localizedMessage ?: "microphone indisponible"}"
            } else {
                "MediaRecorder error: ${e.localizedMessage ?: "microphone unavailable"}"
            }
            isRecording = false
            isPaused = false
            mediaRecorder = null
            false
        }
    }

    fun pauseOrResumeRecording() {
        if (!isRecording || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            if (isPaused) {
                mediaRecorder?.resume()
                isPaused = false
            } else {
                mediaRecorder?.pause()
                isPaused = true
                currentPeakPercent = 0
            }
        } catch (e: Exception) {
            Log.e("RecordingScreen", "Failed to pause/resume MediaRecorder", e)
        }
    }

    fun stopRecording() {
        val file = currentOutputFile
        val finalDuration = durationSeconds.coerceAtLeast(1)
        try {
            if (isRecording) {
                mediaRecorder?.apply {
                    try {
                        stop()
                    } catch (e: Exception) {
                        Log.w("RecordingScreen", "MediaRecorder.stop notice: ${e.message}")
                    }
                    reset()
                    release()
                }
            }
        } catch (e: Exception) {
            Log.e("RecordingScreen", "Error releasing MediaRecorder", e)
        } finally {
            mediaRecorder = null
            isRecording = false
            isPaused = false
            currentPeakPercent = 0
            currentOutputFile = null
        }

        if (file != null && file.exists() && file.length() > 0) {
            lastSavedFile = file
            localSavedFiles = listOf(file) + localSavedFiles
            viewModel?.registerRecordedFile(file, finalDuration)
            Toast.makeText(
                context,
                if (isFr) "Enregistrement sauvegardé : ${file.name}" else "Recording saved: ${file.name}",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            errorMessage = if (isFr) {
                "Aucun flux audio capturé (enregistrement trop court)."
            } else {
                "No audio data captured (recording too short)."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted && pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            startRecording()
        } else {
            pendingStartAfterPermission = false
            if (!granted) {
                Toast.makeText(
                    context,
                    if (isFr) "Permission microphone refusée." else "Microphone permission denied.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Real-time amplitude sampler for waveform visualizer
    LaunchedEffect(isRecording, isPaused) {
        if (isRecording && !isPaused) {
            while (isRecording && !isPaused) {
                delay(80L)
                val rawAmp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (_: Exception) {
                    0
                }
                val scaled = ((rawAmp / 32767f) * 100f).toInt().coerceIn(8, 100)
                currentPeakPercent = ((rawAmp / 32767f) * 100f).toInt().coerceIn(0, 100)
                liveAmplitudes = (liveAmplitudes + scaled).takeLast(48)
            }
        }
    }

    // Duration timer
    LaunchedEffect(isRecording, isPaused) {
        if (isRecording && !isPaused) {
            while (isRecording && !isPaused) {
                delay(1000L)
                durationSeconds++
            }
        }
    }

    // Release MediaRecorder cleanly when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaRecorder?.apply {
                    if (isRecording) {
                        try {
                            stop()
                        } catch (_: Exception) {
                        }
                    }
                    reset()
                    release()
                }
            } catch (_: Exception) {
            } finally {
                mediaRecorder = null
            }
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_dot_scale"
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("recording_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isFr) "Studio d'Enregistrement" else "Recording Screen",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = EmeraldSynced.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, EmeraldSynced.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "MEDIARECORDER",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = EmeraldSynced,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isFr) "Capture AAC 44.1 kHz • Visualiseur d'onde en direct" else "AAC 44.1 kHz Capture • Live Waveform Visualizer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null || viewModel != null) {
                        IconButton(
                            onClick = {
                                if (onNavigateBack != null) onNavigateBack()
                                else viewModel?.setTab(NavigationTab.RECORDER_VAULT)
                            },
                            modifier = Modifier.testTag("recording_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isFr) "Retour" else "Back"
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (hasMicPermission) {
                            EmeraldSynced.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (hasMicPermission) Icons.Filled.Mic else Icons.Filled.MicOff,
                                contentDescription = null,
                                tint = if (hasMicPermission) EmeraldSynced else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (hasMicPermission) {
                                    if (isFr) "Micro OK" else "Mic OK"
                                } else {
                                    if (isFr) "Micro Requis" else "Mic Required"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasMicPermission) EmeraldSynced else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Permission prompt banner
            if (!hasMicPermission) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isFr) {
                                        "Autorisez l'accès au microphone pour enregistrer via MediaRecorder."
                                    } else {
                                        "Grant microphone access to record audio via MediaRecorder."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("request_mic_permission_btn"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (isFr) "Autoriser" else "Allow")
                            }
                        }
                    }
                }
            }

            // Main MediaRecorder + Waveform Studio Card
            item {
                val activeCrimson = Color(0xFFEF4444)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recording_studio_card"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.5.dp,
                        when {
                            isRecording && !isPaused -> activeCrimson.copy(alpha = 0.65f)
                            isRecording && isPaused -> Color(0xFFF59E0B).copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        if (isRecording) activeCrimson.copy(alpha = 0.08f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Status Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .scale(if (isRecording && !isPaused) pulseScale else 1f)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isRecording && !isPaused -> activeCrimson
                                                isRecording && isPaused -> Color(0xFFF59E0B)
                                                else -> EmeraldSynced
                                            }
                                        )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        isRecording && isPaused -> if (isFr) "EN PAUSE" else "PAUSED"
                                        isRecording -> if (isFr) "ENREGISTREMENT ACTIF" else "RECORDING LIVE"
                                        else -> if (isFr) "PRÊT À CAPTURER" else "READY TO RECORD"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        isRecording && !isPaused -> activeCrimson
                                        isRecording && isPaused -> Color(0xFFF59E0B)
                                        else -> EmeraldSynced
                                    }
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "$currentPeakPercent%",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Monospace Duration Timer
                        Text(
                            text = formatRecordingTime(durationSeconds),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isRecording && !isPaused) activeCrimson else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("recording_timer_text")
                        )

                        Spacer(Modifier.height(14.dp))

                        // Waveform Visualizer Container
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                            ) {
                                AudioWaveformVisualizer(
                                    amplitudes = liveAmplitudes,
                                    isLive = isRecording && !isPaused,
                                    autoScrollShowFixed = true,
                                    showFixedPlayhead = true,
                                    barColor = if (isRecording) activeCrimson else MaterialTheme.colorScheme.primary,
                                    activeBarColor = EmeraldSynced,
                                    inactiveBarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp)
                                        .testTag("recording_waveform_visualizer")
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isFr) "Visualiseur d'onde (maxAmplitude)" else "Waveform Visualizer (maxAmplitude)",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "AAC • 44.1 kHz",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Filename input when idle
                        AnimatedVisibility(visible = !isRecording) {
                            OutlinedTextField(
                                value = customFileName,
                                onValueChange = { customFileName = it },
                                label = { Text(if (isFr) "Nom du fichier audio (.m4a)" else "Audio filename (.m4a)") },
                                leadingIcon = { Icon(Icons.Outlined.Save, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                                    .testTag("recording_filename_input")
                            )
                        }

                        // Explicit Start / Stop / Pause Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (!hasMicPermission) {
                                        pendingStartAfterPermission = true
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        startRecording()
                                    }
                                },
                                enabled = !isRecording,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldSynced,
                                    contentColor = Color(0xFF022C22)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 50.dp)
                                    .testTag("start_recording_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = if (isFr) "Démarrer" else "Start"
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isFr) "Démarrer" else "Start",
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            if (isRecording && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                FilledTonalIconButton(
                                    onClick = { pauseOrResumeRecording() },
                                    modifier = Modifier
                                        .size(50.dp)
                                        .testTag("pause_recording_button")
                                ) {
                                    Icon(
                                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                        contentDescription = if (isPaused) (if (isFr) "Reprendre" else "Resume") else (if (isFr) "Pause" else "Pause")
                                    )
                                }
                            }

                            Button(
                                onClick = { stopRecording() },
                                enabled = isRecording,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = activeCrimson,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 50.dp)
                                    .testTag("stop_recording_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = if (isFr) "Arrêter" else "Stop"
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isFr) "Arrêter" else "Stop",
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        // Error or Last Saved File Notice
                        errorMessage?.let { err ->
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        lastSavedFile?.let { saved ->
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                color = EmeraldSynced.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, EmeraldSynced.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldSynced,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = saved.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${(saved.length() / 1024f).toInt()} KB • ${saved.parentFile?.name ?: "audio_recordings"}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recordings History Section
            if (viewModel != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isFr) "Enregistrements Récents" else "Recent Recordings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${recordings.size} ${if (isFr) "fichier(s)" else "file(s)"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = {
                            Text(
                                if (isFr) "Rechercher par nom ou date..." else "Search by name or date..."
                            )
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.Search, contentDescription = if (isFr) "Rechercher" else "Search")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(imageVector = Icons.Filled.Close, contentDescription = if (isFr) "Effacer" else "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_recordings_input_history"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                items(recordings, key = { it.id }) { item ->
                    AudioPlayerCard(
                        recording = item,
                        playerState = playerState,
                        onPlayPause = { viewModel.togglePlayPause(item) },
                        onSeek = { seconds -> viewModel.seekTo(seconds, item) },
                        onSpeedChange = { speed -> viewModel.setPlaybackSpeed(speed) },
                        onSyncClick = { viewModel.runRcloneSync(item) },
                        onAiAnalyzeClick = {
                            viewModel.analyzeRecordingWithAi(item)
                            viewModel.setTab(NavigationTab.AI_STUDIO)
                        },
                        onDeleteClick = { viewModel.deleteRecording(item) },
                        viewModel = viewModel
                    )
                }
            } else if (localSavedFiles.isNotEmpty()) {
                item {
                    Text(
                        text = if (isFr) "Fichiers locaux capturés" else "Captured Local Files",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(localSavedFiles, key = { it.absolutePath }) { file ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    file.absolutePath,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRecordingTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
