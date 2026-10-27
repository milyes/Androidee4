package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.model.AudioRecording
import com.example.data.viewmodel.AudioSyncViewModel
import com.example.data.viewmodel.NavigationTab
import com.example.ui.components.AudioPlayerCard
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.theme.EmeraldSynced
import com.example.ui.theme.tr
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * VoiceRecorderScreen component for real device microphone audio recording
 * with scoped local storage persistence, live waveform amplitude visualization,
 * and comprehensive runtime permission handling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecorderScreen(
    viewModel: AudioSyncViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val recorderState by viewModel.recorderState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()

    // Handle back button to return to RECORDER_VAULT
    BackHandler {
        if (onNavigateBack != null) {
            onNavigateBack()
        } else {
            viewModel.setTab(NavigationTab.RECORDER_VAULT)
        }
    }

    // Permission state handling
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            Toast.makeText(
                context,
                appLanguage.tr("Permission microphone accordée. Prêt à enregistrer.", "Microphone permission granted. Ready to record."),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                context,
                appLanguage.tr("Permission refusée. Impossible d'accéder au microphone.", "Permission denied. Microphone cannot be accessed."),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Auto-check permission on initial composition
    LaunchedEffect(Unit) {
        val currentStatus = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasMicPermission = currentStatus
    }

    var customRecordingName by remember {
        mutableStateOf(
            "rec_vocal_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        )
    }

    // Pulsing recording animation
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("voice_recorder_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tr("Enregistreur Vocal", "Voice Recorder"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "MODE RÉEL",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = tr("Microphone Matériel Direct • Sauvegarde Locale", "Hardware Direct Microphone • Local Storage"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (onNavigateBack != null) onNavigateBack()
                            else viewModel.setTab(NavigationTab.RECORDER_VAULT)
                        },
                        modifier = Modifier.testTag("voice_recorder_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr("Retour", "Back")
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            Toast.makeText(
                                context,
                                appLanguage.tr("Stockage local : fichiers .m4a sauvegardés dans files/recordings", "Local storage: .m4a files stored in files/recordings"),
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier.testTag("voice_recorder_info_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Informations"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // 1. Certification & Hardware Signature Banner
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "LICENSE NETSECUREPRO.CA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                                Text(
                                    text = "GEMIN CORE V7 PRO FINAL • IA_LOGIC_SINGNATURE:ZOUBIROU-IA-2025",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF38BDF8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "44.1 kHz AAC",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // 2. Microphone Permission Card (If not granted)
            if (!hasMicPermission) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mic_permission_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.MicOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = tr("Autorisation Microphone Requise", "Microphone Permission Required"),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = tr("L'accès au microphone physique est indispensable pour enregistrer vos vocaux.", "Access to the physical microphone is required to record voice notes."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("request_mic_permission_btn")
                                ) {
                                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(tr("Autoriser l'accès", "Grant Access"), fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("open_settings_btn")
                                ) {
                                    Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(tr("Paramètres", "Settings"), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Live Voice Recorder Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (recorderState.isRecording) Color(0xFFEF4444) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_recorder_main_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Status Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (recorderState.isRecording) {
                                    if (recorderState.isPaused) Color(0xFFF59E0B).copy(alpha = 0.2f)
                                    else Color(0xFFEF4444).copy(alpha = 0.2f)
                                } else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (recorderState.isRecording) {
                                                    if (recorderState.isPaused) Color(0xFFF59E0B)
                                                    else Color(0xFFEF4444)
                                                } else Color(0xFF94A3B8)
                                            )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            !recorderState.isRecording -> tr("PRÊT À ENREGISTRER", "READY TO RECORD")
                                            recorderState.isPaused -> tr("EN PAUSE", "PAUSED")
                                            else -> tr("ENREGISTREMENT EN COURS", "RECORDING LIVE")
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            !recorderState.isRecording -> MaterialTheme.colorScheme.onSurfaceVariant
                                            recorderState.isPaused -> Color(0xFFF59E0B)
                                            else -> Color(0xFFEF4444)
                                        }
                                    )
                                }
                            }

                            // Silence VAD switch/badge
                            Surface(
                                onClick = { viewModel.toggleSilenceDetection(!recorderState.isSilenceDetectionEnabled) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (recorderState.isSilenceDetectionEnabled) EmeraldSynced.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (recorderState.isSilenceDetectionEnabled) EmeraldSynced.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        tint = if (recorderState.isSilenceDetectionEnabled) EmeraldSynced else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (recorderState.isSilenceDetectionEnabled) "VAD ON" else "VAD OFF",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (recorderState.isSilenceDetectionEnabled) EmeraldSynced else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        // Large Digital Timer
                        val duration = recorderState.durationSeconds
                        val minutes = duration / 60
                        val seconds = duration % 60
                        val formattedTime = String.format("%02d:%02d", minutes, seconds)

                        Box(contentAlignment = Alignment.Center) {
                            if (recorderState.isRecording && !recorderState.isPaused) {
                                Box(
                                    modifier = Modifier
                                        .size(130.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444).copy(alpha = 0.08f))
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = formattedTime,
                                    fontSize = 46.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (recorderState.isRecording) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("recorder_timer_text")
                                )
                                Text(
                                    text = if (recorderState.isRecording) tr("DURÉE ACTUELLE", "CURRENT DURATION") else tr("TEMPS ÉCOULÉ", "ELAPSED TIME"),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // Real-Time Waveform Visualizer
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = tr("Spectre sonore direct", "Live Audio Spectrum"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (recorderState.isRecording) "${recorderState.liveAmplitudes.lastOrNull() ?: 0} / 100" else "0 / 100",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                AudioWaveformVisualizer(
                                    amplitudes = if (recorderState.isRecording) recorderState.liveAmplitudes else listOf(10, 15, 20, 15, 10, 8, 12, 10),
                                    isLive = recorderState.isRecording && !recorderState.isPaused,
                                    barColor = if (recorderState.isRecording) Color(0xFFEF4444) else Color(0xFF00E5FF),
                                    activeBarColor = Color(0xFF00E5FF),
                                    inactiveBarColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }

                        // Recording Name Input Field
                        if (!recorderState.isRecording) {
                            OutlinedTextField(
                                value = customRecordingName,
                                onValueChange = { customRecordingName = it },
                                label = { Text(tr("Nom du fichier audio (.m4a)", "Audio File Name (.m4a)")) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = {
                                    Icon(Icons.Filled.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            customRecordingName = "rec_vocal_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                        }
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Générer horodatage")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("recording_name_input")
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Save, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${recorderState.recordingName}.m4a",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Main Recording Control Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (recorderState.isRecording) {
                                // Pause / Resume Button
                                OutlinedIconButton(
                                    onClick = { viewModel.togglePauseRecording() },
                                    shape = CircleShape,
                                    border = BorderStroke(2.dp, if (recorderState.isPaused) Color(0xFF10B981) else Color(0xFFF59E0B)),
                                    modifier = Modifier
                                        .size(54.dp)
                                        .testTag("pause_resume_btn")
                                ) {
                                    Icon(
                                        imageVector = if (recorderState.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                        contentDescription = if (recorderState.isPaused) "Reprendre" else "Pause",
                                        tint = if (recorderState.isPaused) Color(0xFF10B981) else Color(0xFFF59E0B),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(Modifier.width(20.dp))

                                // Stop and Save Main Button
                                Button(
                                    onClick = {
                                        viewModel.stopAndSaveRecording()
                                        Toast.makeText(
                                            context,
                                            appLanguage.tr("Enregistrement sauvegardé avec succès !", "Recording saved successfully!"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .size(76.dp)
                                        .testTag("stop_recording_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Stop,
                                        contentDescription = "Arrêter et sauvegarder",
                                        tint = Color.White,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }

                                Spacer(Modifier.width(20.dp))

                                // Discard Button
                                OutlinedIconButton(
                                    onClick = {
                                        viewModel.stopAndSaveRecording()
                                        Toast.makeText(context, appLanguage.tr("Enregistrement arrêté.", "Recording stopped."), Toast.LENGTH_SHORT).show()
                                    },
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .size(54.dp)
                                        .testTag("discard_recording_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Annuler",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                // Record Start Button
                                Button(
                                    onClick = {
                                        if (!hasMicPermission) {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        } else {
                                            viewModel.startRecording(customRecordingName)
                                        }
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                                    modifier = Modifier
                                        .height(60.dp)
                                        .testTag("record_audio_fab")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = tr("DÉMARRER L'ENREGISTREMENT", "START RECORDING"),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Scoped Storage Location Display
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Dossier : ${context.filesDir.path}/recordings/",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 4. Last Saved Recording Card (Instant Playback & Share)
            val latestRec = recordings.firstOrNull()
            if (latestRec != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("latest_recording_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = tr("Dernier fichier sauvegardé localement", "Latest locally saved file"),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                                Text(
                                    text = "${latestRec.fileSizeMb} MB",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = latestRec.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = latestRec.localPath,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // In-line Play/Pause
                                    val isCurrentPlaying = playerState.playingRecordingId == latestRec.id && playerState.isPlaying
                                    IconButton(
                                        onClick = { viewModel.togglePlayPause(latestRec) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                            .testTag("play_latest_rec_btn")
                                    ) {
                                        Icon(
                                            imageVector = if (isCurrentPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = "Lecture",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Share button
                                    IconButton(
                                        onClick = {
                                            try {
                                                val file = File(latestRec.localPath)
                                                if (file.exists()) {
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        file
                                                    )
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "audio/*"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "Partager l'audio"))
                                                } else {
                                                    Toast.makeText(context, "Fichier physique introuvable.", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erreur partage: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                                            .testTag("share_latest_rec_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Share,
                                            contentDescription = "Partager",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Recent Recordings Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Enregistrements Locaux (${recordings.size})", "Local Recordings (${recordings.size})"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { viewModel.setTab(NavigationTab.RECORDER_VAULT) }
                    ) {
                        Text(tr("Voir tout le coffre", "View Full Vault"), fontSize = 12.sp)
                    }
                }
            }

            // 6. List of Recent Recordings (Up to 5)
            if (recordings.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MicNone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = tr("Aucun enregistrement local pour le moment.", "No local recordings yet."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = tr("Appuyez sur le bouton rouge ci-dessus pour capturer votre premier fichier audio.", "Press the red button above to capture your first audio file."),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(recordings.take(5), key = { it.id }) { rec ->
                    AudioPlayerCard(
                        recording = rec,
                        playerState = playerState,
                        onPlayPause = { viewModel.togglePlayPause(rec) },
                        onSeek = { seconds -> viewModel.seekTo(seconds, rec) },
                        onSpeedChange = { speed -> viewModel.setPlaybackSpeed(speed) },
                        onSyncClick = { viewModel.runRcloneSync(rec) },
                        onAiAnalyzeClick = {
                            viewModel.analyzeRecordingWithAi(rec)
                            viewModel.setTab(NavigationTab.AI_STUDIO)
                        },
                        onDeleteClick = { viewModel.deleteRecording(rec) },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
