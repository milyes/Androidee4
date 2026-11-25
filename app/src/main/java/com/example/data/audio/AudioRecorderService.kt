package com.example.data.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AudioRecorderService - Service Android d'enregistrement audio matériel direct (Mode Réel)
 * 
 * Utilise MediaRecorder (Source MICROPHONE, encodeur AAC haute fidélité 192 kbps / 44.1 kHz).
 * Fonctionne en tant que Foreground Service de type "microphone" avec WakeLock matériel,
 * notification interactive temps réel et diffusion des états d'amplitude/durée.
 * 
 * CERTIFICATION & LICENCES :
 * • LICENSE NETSECUREPRO.CA
 * • GEMIN CORE V7 PRO FINAL
 * • IA_LOGIC_SINGNATURE:ZOUBIROU-IA-2025
 * • VERSION FR 🇫🇷
 */
class AudioRecorderService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordingStartTime: Long = 0L
    private var totalPausedDuration: Long = 0L
    private var pauseStartTime: Long = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentRecordingName: String = "enregistrement_real"
    private var autoSyncRequested: Boolean = true

    inner class LocalBinder : Binder() {
        fun getService(): AudioRecorderService = this@AudioRecorderService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AudioRecorderService initialisé en Mode Réel [ZOUBIROU-IA-2025]")
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand reçu avec action: $action")

        when (action) {
            ACTION_START -> {
                val outputPath = intent?.getStringExtra(EXTRA_OUTPUT_PATH)
                val name = intent?.getStringExtra(EXTRA_RECORDING_NAME) ?: "capture_audio_real"
                val autoSync = intent?.getBooleanExtra(EXTRA_AUTO_SYNC, true) ?: true
                startRecording(outputPath, name, autoSync)
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AudioRecorderService:WakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes max safety timeout
            }
        } catch (e: Exception) {
            Log.w(TAG, "Impossible d'acquérir le WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur libération WakeLock: ${e.message}")
        }
    }

    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    @Synchronized
    private fun startRecording(customPath: String?, name: String, autoSync: Boolean) {
        if (_isRecordingState.value) {
            Log.w(TAG, "Enregistrement déjà en cours. Ignoré.")
            return
        }

        try {
            currentRecordingName = name
            autoSyncRequested = autoSync

            // Déterminer le fichier de sortie sur stockage physique local
            val outputFile = if (!customPath.isNullOrEmpty()) {
                File(customPath)
            } else {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val filename = "REAL_AUDIO_${timestamp}.m4a"
                val baseDir = getExternalFilesDir(null) ?: filesDir
                val recordingsDir = File(baseDir, "recordings")
                if (!recordingsDir.exists()) recordingsDir.mkdirs()
                File(recordingsDir, filename)
            }

            outputFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
            currentOutputFile = outputFile

            // Configuration MediaRecorder professionnelle (AAC 192k / 44.1kHz)
            val recorder = createMediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(192000) // 192 kbps
                setAudioSamplingRate(44100)     // 44.1 kHz Studio
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            recordingStartTime = System.currentTimeMillis()
            totalPausedDuration = 0L
            pauseStartTime = 0L

            _isRecordingState.value = true
            _isPausedState.value = false
            _currentDurationSecondsState.value = 0
            _currentAmplitudeState.value = 0

            // Lancer le Foreground Service avec notification persistante
            startForegroundServiceWithNotification(0, isPaused = false)

            // Démarrer la boucle de rafraîchissement d'amplitude et de durée
            startAmplitudeAndDurationTicker()

            Log.i(TAG, "Enregistrement MediaRecorder actif sur : ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Échec démarrage MediaRecorder en Mode Réel", e)
            cleanUpAndStop()
        }
    }

    private fun startAmplitudeAndDurationTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive && _isRecordingState.value) {
                if (!_isPausedState.value) {
                    val now = System.currentTimeMillis()
                    val elapsed = ((now - recordingStartTime - totalPausedDuration) / 1000).toInt()
                    _currentDurationSecondsState.value = maxOf(0, elapsed)

                    // Lecture de l'amplitude crête matérielle
                    val amp = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (e: Exception) {
                        0
                    }
                    _currentAmplitudeState.value = amp

                    // Mise à jour de la notification toutes les secondes
                    if (elapsed % 1 == 0) {
                        updateNotification(elapsed, isPaused = false)
                    }
                }
                delay(100) // 10 Hz pour visualisation fluide du waveform
            }
        }
    }

    @Synchronized
    private fun pauseRecording() {
        if (!_isRecordingState.value || _isPausedState.value) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                pauseStartTime = System.currentTimeMillis()
                _isPausedState.value = true
                updateNotification(_currentDurationSecondsState.value, isPaused = true)
                Log.d(TAG, "MediaRecorder mis en pause")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur pause MediaRecorder", e)
            }
        }
    }

    @Synchronized
    private fun resumeRecording() {
        if (!_isRecordingState.value || !_isPausedState.value) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                if (pauseStartTime > 0L) {
                    totalPausedDuration += (System.currentTimeMillis() - pauseStartTime)
                    pauseStartTime = 0L
                }
                _isPausedState.value = false
                updateNotification(_currentDurationSecondsState.value, isPaused = false)
                Log.d(TAG, "MediaRecorder repris")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur reprise MediaRecorder", e)
            }
        }
    }

    @Synchronized
    private fun stopRecording() {
        if (!_isRecordingState.value) {
            cleanUpAndStop()
            return
        }

        tickerJob?.cancel()
        tickerJob = null

        val recordedFile = currentOutputFile
        val finalDuration = _currentDurationSecondsState.value

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: RuntimeException) {
                    Log.w(TAG, "MediaRecorder arrêté immédiatement après start: ${e.message}")
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'arrêt du MediaRecorder", e)
        } finally {
            mediaRecorder = null
        }

        _isRecordingState.value = false
        _isPausedState.value = false
        _currentAmplitudeState.value = 0

        if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
            _lastRecordedFileState.value = recordedFile
            Log.i(TAG, "Enregistrement sauvegardé avec succès: ${recordedFile.absolutePath} (${recordedFile.length()} octets, ${finalDuration}s)")

            // Diffuser le broadcast pour informer l'application / ViewModel
            val broadcastIntent = Intent(ACTION_RECORDING_SAVED).apply {
                putExtra(EXTRA_RECORDING_PATH, recordedFile.absolutePath)
                putExtra(EXTRA_RECORDING_DURATION, finalDuration)
                putExtra(EXTRA_RECORDING_NAME, currentRecordingName)
                putExtra(EXTRA_AUTO_SYNC, autoSyncRequested)
                setPackage(packageName)
            }
            sendBroadcast(broadcastIntent)
        } else {
            Log.w(TAG, "Fichier enregistré inexistant ou vide.")
        }

        cleanUpAndStop()
    }

    private fun cleanUpAndStop() {
        tickerJob?.cancel()
        tickerJob = null
        releaseWakeLock()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur stopForeground: ${e.message}")
        }

        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaRecorder?.apply {
            try {
                release()
            } catch (ignored: Exception) {}
        }
        mediaRecorder = null
        releaseWakeLock()
        _isRecordingState.value = false
        _isPausedState.value = false
        Log.i(TAG, "AudioRecorderService détruit")
    }

    // ==========================================
    // GESTION DES NOTIFICATIONS FOREGROUND
    // ==========================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Enregistreur Vocal Z-CORE (Mode Réel)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification persistante pour l'enregistrement audio direct en tâche de fond"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(seconds: Int, isPaused: Boolean): Notification {
        val minutes = seconds / 60
        val remainingSec = seconds % 60
        val timeFormatted = String.format(Locale.US, "%02d:%02d", minutes, remainingSec)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AudioRecorderService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleActionIntent = Intent(this, AudioRecorderService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            2,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isPaused) "En pause ($timeFormatted)" else "Enregistrement en direct ($timeFormatted)"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Z-CORE Voice Vault • MODE RÉEL")
            .setContentText(statusText)
            .setSubText("VERSION FR 🇫🇷")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Reprendre" else "Pause",
                togglePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Arrêter & Sauvegarder",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)

        return builder.build()
    }

    private fun startForegroundServiceWithNotification(seconds: Int, isPaused: Boolean) {
        val notification = buildNotification(seconds, isPaused)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Échec appel startForeground", e)
        }
    }

    private fun updateNotification(seconds: Int, isPaused: Boolean) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, buildNotification(seconds, isPaused))
        } catch (e: Exception) {
            Log.w(TAG, "Échec mise à jour notification: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AudioRecorderService"
        private const val NOTIFICATION_ID = 2025
        private const val CHANNEL_ID = "audio_recorder_real_mode_channel"

        const val ACTION_START = "com.example.audiosynchub.ACTION_START_RECORDING"
        const val ACTION_STOP = "com.example.audiosynchub.ACTION_STOP_RECORDING"
        const val ACTION_PAUSE = "com.example.audiosynchub.ACTION_PAUSE_RECORDING"
        const val ACTION_RESUME = "com.example.audiosynchub.ACTION_RESUME_RECORDING"

        const val ACTION_RECORDING_SAVED = "com.example.audiosynchub.RECORDING_SAVED"

        const val EXTRA_OUTPUT_PATH = "extra_output_path"
        const val EXTRA_RECORDING_NAME = "extra_recording_name"
        const val EXTRA_AUTO_SYNC = "extra_auto_sync"
        const val EXTRA_RECORDING_PATH = "extra_recording_path"
        const val EXTRA_RECORDING_DURATION = "extra_recording_duration"

        // StateFlows observables par les ViewModels et Composables
        private val _isRecordingState = MutableStateFlow(false)
        val isRecordingState: StateFlow<Boolean> = _isRecordingState.asStateFlow()

        private val _isPausedState = MutableStateFlow(false)
        val isPausedState: StateFlow<Boolean> = _isPausedState.asStateFlow()

        private val _currentDurationSecondsState = MutableStateFlow(0)
        val currentDurationSecondsState: StateFlow<Int> = _currentDurationSecondsState.asStateFlow()

        private val _currentAmplitudeState = MutableStateFlow(0)
        val currentAmplitudeState: StateFlow<Int> = _currentAmplitudeState.asStateFlow()

        private val _lastRecordedFileState = MutableStateFlow<File?>(null)
        val lastRecordedFileState: StateFlow<File?> = _lastRecordedFileState.asStateFlow()

        /**
         * Lance l'enregistrement via le Foreground Service
         */
        fun start(context: Context, outputPath: String? = null, name: String = "enregistrement_real", autoSync: Boolean = true) {
            val intent = Intent(context, AudioRecorderService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_OUTPUT_PATH, outputPath)
                putExtra(EXTRA_RECORDING_NAME, name)
                putExtra(EXTRA_AUTO_SYNC, autoSync)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            val intent = Intent(context, AudioRecorderService::class.java).apply { action = ACTION_PAUSE }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, AudioRecorderService::class.java).apply { action = ACTION_RESUME }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioRecorderService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
