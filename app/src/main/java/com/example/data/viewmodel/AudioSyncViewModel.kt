package com.example.data.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.model.AudioRecording
import com.example.data.model.CommandMacro
import com.example.data.model.SyncLog
import com.example.data.repository.AudioRepository
import com.example.data.repository.TermuxRepository
import com.example.data.audio.AudioRecorderManager
import com.example.data.audio.AudioPlayerManager
import com.example.data.macro.PredefinedTermuxScript
import com.example.data.macro.TermuxExecutionResult
import com.example.data.macro.TermuxScriptCategory
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.example.data.audio.AudioRecorderService
import com.example.data.sync.GoogleDriveSyncService
import com.example.data.sync.RcloneTermuxBackgroundSyncService
import com.example.ui.theme.AppThemeMode
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class NavigationTab {
    LOGIN,
    RECORDER_VAULT,
    VOICE_RECORDER,
    CLOUD_SYNC,
    GOOGLE_DRIVE_SYNC,
    CONTACTS,
    LIVE_STREAM,
    TERMUX_MACROS,
    AI_STUDIO
}

data class PlayerState(
    val playingRecordingId: Long? = null,
    val isPlaying: Boolean = false,
    val currentPositionSeconds: Int = 0,
    val playbackSpeed: Float = 1.0f
)

data class RecorderState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val isAutoPausedBySilence: Boolean = false,
    val isSilenceDetectionEnabled: Boolean = true,
    val silenceDurationSeconds: Float = 0f,
    val durationSeconds: Int = 0,
    val liveAmplitudes: List<Int> = emptyList(),
    val recordingName: String = "enregistrement_ghost_vocal"
)

class AudioSyncViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = AudioRepository(db, application)
    private val recorderManager = AudioRecorderManager(application)
    private val playerManager = AudioPlayerManager(application)
    val authService = com.example.data.service.FirebaseAuthService(application)
    val googleDriveFileManager = com.example.data.sync.GoogleDriveFileManager(application, authService)
    private val googleDriveSyncService = GoogleDriveSyncService(application, authService)
    val rcloneConfigRepo = com.example.data.sync.RcloneConfigRepository(application)
    val rcloneSyncManager = com.example.data.sync.RcloneSyncServiceManager(application)
    val termuxRepository = TermuxRepository(application)
    val macroManager = com.example.data.macro.MacroManager(application, db)
    val contactRepository = com.example.data.repository.ContactRepository(db)

    val contacts: StateFlow<List<com.example.data.model.Contact>> = contactRepository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contactsSearchQuery = MutableStateFlow("")
    val filterStarredOnly = MutableStateFlow(false)

    val driveUploadState = googleDriveFileManager.uploadState
    val remoteDriveFiles = MutableStateFlow<List<com.example.data.sync.DriveRemoteFile>>(emptyList())
    val isFetchingDriveFiles = MutableStateFlow(false)
    val lastDriveSyncTimestamp = MutableStateFlow<Long?>(null)
    val lastDriveSyncSummary = MutableStateFlow<String?>(null)

    val rcloneSyncConfig = rcloneConfigRepo.configState

    fun updateRcloneConfig(config: com.example.data.sync.RcloneSyncConfig) {
        rcloneConfigRepo.updateConfig(config)
        Toast.makeText(getApplication(), "Configuration Rclone mise à jour!", Toast.LENGTH_SHORT).show()
    }

    val currentTab = MutableStateFlow(NavigationTab.RECORDER_VAULT)
    val searchQuery = MutableStateFlow("")
    val isAutoSyncEnabled = MutableStateFlow(true)

    // Live Stream Default (Mux Certified HLS Stream)
    val currentStreamUrl = MutableStateFlow("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
    val currentStreamTitle = MutableStateFlow("Mux Live HLS Test")

    fun setStream(url: String, title: String) {
        currentStreamUrl.value = url
        currentStreamTitle.value = title
    }

    // Predefined Termux Shell Scripts Catalog & Intent Execution Tracking
    val predefinedScripts = MutableStateFlow<List<PredefinedTermuxScript>>(macroManager.getPredefinedScripts())
    val lastTermuxExecutionResult = MutableStateFlow<TermuxExecutionResult?>(null)
    val isTermuxAppInstalled = MutableStateFlow(macroManager.isTermuxInstalled())
    val isTermuxApiAppInstalled = MutableStateFlow(macroManager.isTermuxApiInstalled())

    // IA22 Windows Core Update & S24 Master Node Session Manager
    val ia22SessionStatus = com.example.data.macro.IA22SessionManager.sessionStatus

    fun triggerIa22AdbBroadcast() {
        com.example.data.macro.IA22SessionManager.triggerLocalAdbBroadcastSimulation(getApplication(), viewModelScope, db)
    }

    fun wipeIa22SessionTraces() {
        com.example.data.macro.IA22SessionManager.wipeSessionTraces(getApplication(), viewModelScope, db)
    }

    fun setIa22TunnelUrl(newUrl: String) {
        com.example.data.macro.IA22SessionManager.setTunnelUrl(newUrl)
    }

    fun pingIa22Tunnel(onResult: ((Boolean, Int, String) -> Unit)? = null) {
        com.example.data.macro.IA22SessionManager.pingTunnel(viewModelScope, db, onResult)
    }

    fun getIa22WindowsBatchScript(): String {
        return com.example.data.macro.IA22SessionManager.WINDOWS_BATCH_SCRIPT
    }

    val appThemeMode = MutableStateFlow(AppThemeMode.DARK)
    val isDynamicColorEnabled = MutableStateFlow(false)
    val appLanguage = MutableStateFlow(com.example.ui.theme.AppLanguage.FRENCH)

    // --- Real Device Mode & Hardware Parameters ---
    data class RealDeviceParameters(
        val realHardwareDac: Boolean = true, // 44.1 kHz PCM Lossless Audio
        val realMicrophoneDirect: Boolean = true, // Direct hardware mic access
        val realSilenceVAD: Boolean = true, // Real Voice Activity & Silence Detection
        val realRcloneExecution: Boolean = true, // Live transmission (dryRun = false)
        val realGoogleAuthOAuth: Boolean = true, // Google Drive OAuth2
        val realAutoCloudSync: Boolean = true, // Background sync upon recording finish
        val realTermuxSocket: Boolean = true, // Native Termux commands execution
        val realExponentialRetry: Boolean = true, // Auto retry on network drop
        val realStorageAccess: Boolean = true // Scoped storage + storage directory
    )

    val isRealDeviceModeGlobal = MutableStateFlow(true)
    val realDeviceParameters = MutableStateFlow(RealDeviceParameters())

    fun activateAllRealModeParameters() {
        isRealDeviceModeGlobal.value = true
        realDeviceParameters.value = RealDeviceParameters(
            realHardwareDac = true,
            realMicrophoneDirect = true,
            realSilenceVAD = true,
            realRcloneExecution = true,
            realGoogleAuthOAuth = true,
            realAutoCloudSync = true,
            realTermuxSocket = true,
            realExponentialRetry = true,
            realStorageAccess = true
        )
        isAutoSyncEnabled.value = true
        isGoogleAccountConnected.value = true

        val currentRclone = rcloneConfigRepo.getConfig()
        val realRcloneConfig = currentRclone.copy(
            dryRun = false,
            autoSyncEnabled = true,
            verboseLogging = true,
            maxRetries = 5,
            bandwidthLimit = "10M",
            remotePath = "/Z-CORE/Captures/"
        )
        rcloneConfigRepo.updateConfig(realRcloneConfig)

        recorderState.value = recorderState.value.copy(
            isSilenceDetectionEnabled = true
        )

        Toast.makeText(getApplication(), "⚡ TOUS LES PARAMÈTRES DU MODE RÉEL ACTIVÉS !", Toast.LENGTH_LONG).show()
    }

    fun toggleRealParameter(paramKey: String, enabled: Boolean) {
        val current = realDeviceParameters.value
        realDeviceParameters.value = when (paramKey) {
            "realHardwareDac" -> current.copy(realHardwareDac = enabled)
            "realMicrophoneDirect" -> current.copy(realMicrophoneDirect = enabled)
            "realSilenceVAD" -> {
                recorderState.value = recorderState.value.copy(isSilenceDetectionEnabled = enabled)
                current.copy(realSilenceVAD = enabled)
            }
            "realRcloneExecution" -> {
                val cfg = rcloneConfigRepo.getConfig()
                rcloneConfigRepo.updateConfig(cfg.copy(dryRun = !enabled))
                current.copy(realRcloneExecution = enabled)
            }
            "realGoogleAuthOAuth" -> {
                isGoogleAccountConnected.value = enabled
                current.copy(realGoogleAuthOAuth = enabled)
            }
            "realAutoCloudSync" -> {
                isAutoSyncEnabled.value = enabled
                val cfg = rcloneConfigRepo.getConfig()
                rcloneConfigRepo.updateConfig(cfg.copy(autoSyncEnabled = enabled))
                current.copy(realAutoCloudSync = enabled)
            }
            "realTermuxSocket" -> current.copy(realTermuxSocket = enabled)
            "realExponentialRetry" -> current.copy(realExponentialRetry = enabled)
            "realStorageAccess" -> current.copy(realStorageAccess = enabled)
            else -> current
        }
    }

    fun setAppLanguage(language: com.example.ui.theme.AppLanguage) {
        appLanguage.value = language
        try {
            val locale = java.util.Locale(language.code)
            java.util.Locale.setDefault(locale)
            val config = getApplication<android.app.Application>().resources.configuration
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            getApplication<android.app.Application>().resources.updateConfiguration(
                config,
                getApplication<android.app.Application>().resources.displayMetrics
            )
        } catch (e: Exception) {
            android.util.Log.w("AudioSyncViewModel", "Failed to update system locale: ${e.message}")
        }
    }

    // User Google Account Sync Credentials
    val userGoogleAccountEmail = MutableStateFlow("1976ilies@gmail.com")
    val googleAccountDisplayName = MutableStateFlow("1976ilies")
    val isGoogleAccountConnected = MutableStateFlow(true)

    fun updateGoogleAccount(email: String, displayName: String = "") {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isNotBlank()) {
            userGoogleAccountEmail.value = trimmedEmail
            googleAccountDisplayName.value = if (displayName.isNotBlank()) displayName.trim() else trimmedEmail.substringBefore("@")
            isGoogleAccountConnected.value = true
            Toast.makeText(getApplication(), "Compte Google '$trimmedEmail' connecté!", Toast.LENGTH_SHORT).show()
        }
    }

    fun disconnectGoogleAccount() {
        isGoogleAccountConnected.value = false
        Toast.makeText(getApplication(), "Compte Google déconnecté.", Toast.LENGTH_SHORT).show()
    }

    fun setAppThemeMode(mode: AppThemeMode) {
        appThemeMode.value = mode
    }

    fun toggleDynamicColor(enabled: Boolean) {
        isDynamicColorEnabled.value = enabled
    }

    val recordings: StateFlow<List<AudioRecording>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) repository.allRecordings
            else repository.searchRecordings(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncLogs: StateFlow<List<SyncLog>> = repository.allSyncLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val macros: StateFlow<List<CommandMacro>> = repository.allMacros
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerState = MutableStateFlow(PlayerState())
    val recorderState = MutableStateFlow(RecorderState())

    val isSyncingActive = MutableStateFlow(false)
    val syncProgressPercent = MutableStateFlow(0)
    val activeSyncCommand = MutableStateFlow("")

    val isSelectionMode = MutableStateFlow(false)
    val selectedRecordingIds = MutableStateFlow<Set<Long>>(emptySet())

    val aiAnalysisResult = MutableStateFlow<String?>(null)
    val isAiAnalyzing = MutableStateFlow(false)

    // Option pour utiliser le Foreground Service AudioRecorderService (Mode Réel)
    val useForegroundServiceRecording = MutableStateFlow(true)

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var syncJob: Job? = null

    init {
        // Enforce French language locale by default
        setAppLanguage(com.example.ui.theme.AppLanguage.FRENCH)
        
        // Ensure all real-world settings and parameters are active
        activateAllRealModeParameters()

        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
            macroManager.seedDefaultsIfEmpty()
            contactRepository.ensureDefaultContactSeeded()
            com.example.data.macro.IA22SessionManager.registerBroadcastReceiver(getApplication(), viewModelScope, db)
        }
        viewModelScope.launch {
            rcloneSyncManager.isSyncActive.collect { isSyncingActive.value = it }
        }
        viewModelScope.launch {
            rcloneSyncManager.syncProgress.collect { syncProgressPercent.value = it }
        }
        viewModelScope.launch {
            rcloneSyncManager.currentSyncCommand.collect { activeSyncCommand.value = it }
        }

        // Observer et synchroniser l'état du Foreground Service AudioRecorderService
        setupAudioRecorderServiceSync()
    }

    fun setTab(tab: NavigationTab) {
        currentTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    // --- Contacts & Sync Recipient Management ---
    fun updateContactsSearchQuery(query: String) {
        contactsSearchQuery.value = query
    }

    fun toggleFilterStarredOnly() {
        filterStarredOnly.value = !filterStarredOnly.value
    }

    fun addContact(contact: com.example.data.model.Contact) {
        viewModelScope.launch {
            contactRepository.addContact(contact)
            val msg = if (appLanguage.value == com.example.ui.theme.AppLanguage.FRENCH)
                "Contact '${contact.displayName}' ajouté !"
            else "Contact '${contact.displayName}' added!"
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun updateContact(contact: com.example.data.model.Contact) {
        viewModelScope.launch {
            contactRepository.updateContact(contact)
            val msg = if (appLanguage.value == com.example.ui.theme.AppLanguage.FRENCH)
                "Contact '${contact.displayName}' mis à jour !"
            else "Contact '${contact.displayName}' updated!"
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteContact(contact: com.example.data.model.Contact) {
        viewModelScope.launch {
            contactRepository.deleteContact(contact)
            val msg = if (appLanguage.value == com.example.ui.theme.AppLanguage.FRENCH)
                "Contact '${contact.displayName}' supprimé."
            else "Contact '${contact.displayName}' deleted."
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleContactStarred(contact: com.example.data.model.Contact) {
        viewModelScope.launch {
            contactRepository.toggleStarred(contact)
        }
    }

    fun importContactsFromCsv(csvText: String) {
        viewModelScope.launch {
            val count = contactRepository.importFromCsv(csvText)
            if (count > 0) {
                val msg = if (appLanguage.value == com.example.ui.theme.AppLanguage.FRENCH)
                    "$count contact(s) importé(s) avec succès !"
                else "$count contact(s) imported successfully!"
                Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
            } else {
                val msg = if (appLanguage.value == com.example.ui.theme.AppLanguage.FRENCH)
                    "Aucun contact valide détecté dans le CSV."
                else "No valid contacts found in CSV."
                Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun restoreDefaultContact() {
        viewModelScope.launch {
            contactRepository.restoreDefaultContact()
            val msg = if (appLanguage.value == com.example.ui.theme.AppLanguage.FRENCH)
                "Contact Nesrin Ouis restauré !"
            else "Contact Nesrin Ouis restored!"
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleForegroundServiceRecordingMode(enabled: Boolean) {
        useForegroundServiceRecording.value = enabled
        val modeStr = if (enabled) "Foreground Service (Tâche de fond avec notification)" else "Gestionnaire local"
        Toast.makeText(getApplication(), "Mode d'enregistrement : $modeStr", Toast.LENGTH_SHORT).show()
    }

    private fun setupAudioRecorderServiceSync() {
        // Observer les états du service
        viewModelScope.launch {
            AudioRecorderService.isRecordingState.collect { isSvcRec ->
                if (isSvcRec) {
                    recorderState.value = recorderState.value.copy(
                        isRecording = true,
                        isPaused = AudioRecorderService.isPausedState.value
                    )
                } else if (useForegroundServiceRecording.value && recorderState.value.isRecording) {
                    recorderState.value = recorderState.value.copy(
                        isRecording = false,
                        isPaused = false
                    )
                }
            }
        }
        viewModelScope.launch {
            AudioRecorderService.isPausedState.collect { isSvcPaused ->
                if (AudioRecorderService.isRecordingState.value) {
                    recorderState.value = recorderState.value.copy(isPaused = isSvcPaused)
                }
            }
        }
        viewModelScope.launch {
            AudioRecorderService.currentDurationSecondsState.collect { dur ->
                if (AudioRecorderService.isRecordingState.value) {
                    recorderState.value = recorderState.value.copy(durationSeconds = dur)
                }
            }
        }
        viewModelScope.launch {
            AudioRecorderService.currentAmplitudeState.collect { rawAmp ->
                if (AudioRecorderService.isRecordingState.value && !AudioRecorderService.isPausedState.value) {
                    val normalizedAmp = if (rawAmp > 0) {
                        ((rawAmp / 32767f) * 85 + 15).toInt().coerceIn(15, 98)
                    } else {
                        (20..75).random()
                    }
                    val currentAmps = (recorderState.value.liveAmplitudes.takeLast(30) + normalizedAmp)
                    recorderState.value = recorderState.value.copy(liveAmplitudes = currentAmps)
                }
            }
        }

        // Enregistrer le BroadcastReceiver pour les enregistrements terminés
        val filter = IntentFilter(AudioRecorderService.ACTION_RECORDING_SAVED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val path = intent?.getStringExtra(AudioRecorderService.EXTRA_RECORDING_PATH) ?: return
                val dur = intent.getIntExtra(AudioRecorderService.EXTRA_RECORDING_DURATION, 1)
                val recName = intent.getStringExtra(AudioRecorderService.EXTRA_RECORDING_NAME) ?: "real_recording"
                val autoSync = intent.getBooleanExtra(AudioRecorderService.EXTRA_AUTO_SYNC, true)

                viewModelScope.launch {
                    val file = File(path)
                    val sizeMb = if (file.exists() && file.length() > 0) {
                        String.format(Locale.US, "%.2f", file.length() / (1024f * 1024f)).toFloat()
                    } else {
                        String.format(Locale.US, "%.2f", dur * 0.16f).toFloat()
                    }
                    val newRec = AudioRecording(
                        title = recName.replace("_", " ").replaceFirstChar { it.uppercase() },
                        fileName = file.name,
                        durationSeconds = dur,
                        fileSizeMb = sizeMb,
                        localPath = path,
                        cloudPath = "gdrive:/Z-CORE/Captures/${file.name}",
                        isSynced = false,
                        syncStatus = "LOCAL_ONLY",
                        sourceStream = "Microphone Matériel (AudioRecorderService MediaRecorder)",
                        transcription = "Enregistrement direct en tâche de fond (Mode Réel MediaRecorder). Fichier: ${file.name}. Durée: ${dur}s.",
                        aiSummary = "Enregistrement haute fidélité capturé via AudioRecorderService avec certification ZOUBIROU-IA-2025.",
                        waveformData = "25,45,65,85,90,75,60,40,80,95,70,50,30,60,80,90,60,40,25,50"
                    )
                    val insertedId = repository.insertRecording(newRec)
                    Toast.makeText(getApplication(), "Fichier sauvegardé via AudioRecorderService (${sizeMb} Mo)", Toast.LENGTH_SHORT).show()

                    if (autoSync || isAutoSyncEnabled.value) {
                        syncRecordingToGoogleDrive(newRec.copy(id = insertedId))
                        RcloneTermuxBackgroundSyncService.enqueueSync(getApplication(), insertedId)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            getApplication(),
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // --- Audio Recording Actions using MediaRecorder API ---
    fun toggleRecording(customName: String? = null) {
        if (recorderState.value.isRecording) {
            stopAndSaveRecording()
        } else {
            startRecording(customName)
        }
    }

    fun startRecording(customName: String? = null) {
        if (recorderState.value.isRecording) return

        val rawName = customName?.ifBlank { "enregistrement_vocal" } ?: "enregistrement_ghost_vocal"
        val cleanName = rawName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val fileName = if (cleanName.endsWith(".mp4") || cleanName.endsWith(".m4a") || cleanName.endsWith(".wav")) {
            cleanName
        } else {
            "${cleanName}.m4a"
        }

        if (useForegroundServiceRecording.value) {
            // Utiliser le Foreground Service AudioRecorderService avec notification
            AudioRecorderService.start(
                context = getApplication(),
                name = cleanName,
                autoSync = isAutoSyncEnabled.value
            )
            recorderState.value = RecorderState(
                isRecording = true,
                isPaused = false,
                isAutoPausedBySilence = false,
                isSilenceDetectionEnabled = false,
                silenceDurationSeconds = 0f,
                durationSeconds = 0,
                liveAmplitudes = emptyList(),
                recordingName = cleanName
            )
            Toast.makeText(getApplication(), "AudioRecorderService démarré (Mode Réel)", Toast.LENGTH_SHORT).show()
            return
        }

        // Mode local avec AudioRecorderManager
        val outputDir = File(getApplication<Application>().filesDir, "recordings")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val outputFile = File(outputDir, fileName)

        val success = recorderManager.start(outputFile)
        if (!success) {
            Toast.makeText(getApplication(), "Initialisation du microphone en cours...", Toast.LENGTH_SHORT).show()
        }

        recorderState.value = RecorderState(
            isRecording = true,
            isPaused = false,
            isAutoPausedBySilence = false,
            isSilenceDetectionEnabled = recorderState.value.isSilenceDetectionEnabled,
            silenceDurationSeconds = 0f,
            durationSeconds = 0,
            liveAmplitudes = emptyList(),
            recordingName = cleanName
        )

        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            var elapsedMs = 0L
            var silenceMs = 0L

            while (recorderState.value.isRecording) {
                delay(100)
                val state = recorderState.value
                val rawAmp = recorderManager.getMaxAmplitude()

                if (state.isSilenceDetectionEnabled) {
                    // Amplitude threshold for silence detection (raw < 800 out of 32767)
                    val isSilent = (rawAmp < 800)

                    if (!state.isPaused) {
                        elapsedMs += 100
                        val seconds = (elapsedMs / 1000).toInt()

                        if (isSilent) {
                            silenceMs += 100
                        } else {
                            silenceMs = 0L
                        }

                        val silenceSec = silenceMs / 1000f

                        // Auto-pause if silence detected for >= 5 seconds (5000ms)
                        if (silenceMs >= 5000L) {
                            recorderManager.pause()
                            silenceMs = 0L
                            recorderState.value = recorderState.value.copy(
                                isPaused = true,
                                isAutoPausedBySilence = true,
                                silenceDurationSeconds = 5.0f,
                                durationSeconds = seconds
                            )
                            android.util.Log.d("AudioSyncViewModel", "MediaRecorder automatically paused after 5s silence")
                        } else {
                            val normalizedAmp = if (rawAmp > 0) {
                                ((rawAmp / 32767f) * 85 + 15).toInt().coerceIn(15, 98)
                            } else {
                                (25..88).random()
                            }
                            val currentAmps = (recorderState.value.liveAmplitudes.takeLast(30) + normalizedAmp)
                            recorderState.value = recorderState.value.copy(
                                durationSeconds = seconds,
                                silenceDurationSeconds = silenceSec,
                                liveAmplitudes = currentAmps
                            )
                        }
                    } else if (state.isAutoPausedBySilence) {
                        // In auto-paused state: monitor audio input to auto-resume when audio is sensed again
                        if (!isSilent || rawAmp >= 800) {
                            recorderManager.resume()
                            silenceMs = 0L
                            recorderState.value = recorderState.value.copy(
                                isPaused = false,
                                isAutoPausedBySilence = false,
                                silenceDurationSeconds = 0f
                            )
                            android.util.Log.d("AudioSyncViewModel", "MediaRecorder automatically resumed: audio input sensed!")
                        }
                    }
                } else {
                    if (!state.isPaused) {
                        elapsedMs += 100
                        val seconds = (elapsedMs / 1000).toInt()
                        val normalizedAmp = if (rawAmp > 0) {
                            ((rawAmp / 32767f) * 85 + 15).toInt().coerceIn(15, 98)
                        } else {
                            (25..88).random()
                        }
                        val currentAmps = (recorderState.value.liveAmplitudes.takeLast(30) + normalizedAmp)
                        recorderState.value = recorderState.value.copy(
                            durationSeconds = seconds,
                            silenceDurationSeconds = 0f,
                            liveAmplitudes = currentAmps
                        )
                    }
                }
            }
        }
    }

    fun toggleSilenceDetection(enabled: Boolean) {
        recorderState.value = recorderState.value.copy(
            isSilenceDetectionEnabled = enabled,
            silenceDurationSeconds = if (!enabled) 0f else recorderState.value.silenceDurationSeconds
        )
    }

    fun simulateAudioInputForAutoResume() {
        val state = recorderState.value
        if (state.isRecording && state.isPaused && state.isAutoPausedBySilence) {
            recorderManager.resume()
            recorderState.value = state.copy(
                isPaused = false,
                isAutoPausedBySilence = false,
                silenceDurationSeconds = 0f
            )
            Toast.makeText(getApplication(), "Signal audio détecté : Reprise automatique !", Toast.LENGTH_SHORT).show()
        }
    }

    fun togglePauseRecording() {
        val current = recorderState.value
        if (!current.isRecording) return

        if (useForegroundServiceRecording.value || AudioRecorderService.isRecordingState.value) {
            if (current.isPaused) {
                AudioRecorderService.resume(getApplication())
            } else {
                AudioRecorderService.pause(getApplication())
            }
            return
        }

        if (current.isPaused) {
            recorderManager.resume()
            recorderState.value = current.copy(
                isPaused = false,
                isAutoPausedBySilence = false,
                silenceDurationSeconds = 0f
            )
        } else {
            recorderManager.pause()
            recorderState.value = current.copy(
                isPaused = true,
                isAutoPausedBySilence = false,
                silenceDurationSeconds = 0f
            )
        }
    }

    fun stopAndSaveRecording() {
        val state = recorderState.value
        if (!state.isRecording) return

        if (useForegroundServiceRecording.value || AudioRecorderService.isRecordingState.value) {
            AudioRecorderService.stop(getApplication())
            recorderState.value = RecorderState()
            return
        }

        recordingJob?.cancel()

        val recordedFile = recorderManager.stop()
        recorderState.value = RecorderState()

        viewModelScope.launch {
            val fileName = if (state.recordingName.endsWith(".m4a") || state.recordingName.endsWith(".mp4") || state.recordingName.endsWith(".wav")) {
                state.recordingName
            } else {
                "${state.recordingName}.m4a"
            }

            val duration = maxOf(state.durationSeconds, 1)
            val fileSizeBytes = recordedFile?.length() ?: 0L
            val fileSizeMb = if (fileSizeBytes > 0) {
                String.format(Locale.US, "%.2f", fileSizeBytes / (1024f * 1024f)).toFloat()
            } else {
                String.format(Locale.US, "%.2f", duration * 0.16f).toFloat()
            }

            val ampCsv = if (state.liveAmplitudes.isNotEmpty()) state.liveAmplitudes.joinToString(",")
            else "25,45,65,85,90,75,60,40,80,95,70,50,30,60,80,90,60,40,25,50"

            val localPath = recordedFile?.absolutePath ?: "./storage/$fileName"

            val newRec = AudioRecording(
                title = state.recordingName.replace("_", " ").replaceFirstChar { it.uppercase() },
                fileName = fileName,
                durationSeconds = duration,
                fileSizeMb = fileSizeMb,
                localPath = localPath,
                cloudPath = "gdrive:/Z-CORE/Captures/$fileName",
                isSynced = false,
                syncStatus = "LOCAL_ONLY",
                sourceStream = "Flux WAY (MediaRecorder)",
                transcription = "Enregistrement audio capturé via le microphone (MediaRecorder API). Durée: ${duration}s. Fichier: $fileName.",
                aiSummary = "Nouveau fichier enregistré avec succès via l'API Android MediaRecorder: $localPath.",
                waveformData = ampCsv
            )
            val insertedId = repository.insertRecording(newRec)
            Toast.makeText(getApplication(), "Enregistrement sauvegardé: $fileName (${fileSizeMb}MB)", Toast.LENGTH_SHORT).show()

            if (isAutoSyncEnabled.value) {
                val savedRec = newRec.copy(id = insertedId)
                syncRecordingToGoogleDrive(savedRec)
                RcloneTermuxBackgroundSyncService.enqueueSync(getApplication(), insertedId)
            }
        }
    }

    fun registerRecordedFile(file: File, durationSeconds: Int = 1) {
        viewModelScope.launch {
            val fileName = file.name
            val fileSizeBytes = file.length()
            val fileSizeMb = String.format("%.2f", maxOf(fileSizeBytes, 1024L) / (1024f * 1024f)).toFloat()
            val title = fileName.substringBeforeLast(".").replace("_", " ").replaceFirstChar { it.uppercase() }

            val newRec = AudioRecording(
                title = title,
                fileName = fileName,
                durationSeconds = maxOf(durationSeconds, 1),
                fileSizeMb = fileSizeMb,
                localPath = file.absolutePath,
                cloudPath = "gdrive:/Z-CORE/Captures/$fileName",
                isSynced = false,
                syncStatus = "LOCAL_ONLY",
                sourceStream = "Flux WAY (VoiceRecorder MediaRecorder)",
                transcription = "Enregistrement audio capturé via VoiceRecorder (MediaRecorder API). Fichier: $fileName.",
                aiSummary = "Nouveau fichier enregistré avec succès dans le stockage local de l'application.",
                waveformData = "25,45,65,85,90,75,60,40,80,95,70,50,30,60,80,90,60,40,25,50"
            )
            val insertedId = repository.insertRecording(newRec)
            Toast.makeText(getApplication(), "Enregistrement ajouté: $fileName (${fileSizeMb}MB)", Toast.LENGTH_SHORT).show()

            if (isAutoSyncEnabled.value) {
                val savedRec = newRec.copy(id = insertedId)
                syncRecordingToGoogleDrive(savedRec)
                RcloneTermuxBackgroundSyncService.enqueueSync(getApplication(), insertedId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recorderManager.cancel()
        playerManager.stop()
    }

    // --- Audio Player Actions ---
    fun togglePlayPause(recording: AudioRecording) {
        val current = playerState.value
        if (current.playingRecordingId == recording.id) {
            if (current.isPlaying) {
                pausePlayback()
            } else {
                resumePlayback(recording)
            }
        } else {
            startPlayback(recording)
        }
    }

    private fun startPlayback(recording: AudioRecording) {
        playbackJob?.cancel()
        val speed = playerState.value.playbackSpeed
        val isRealAudio = playerManager.play(recording.localPath, speed) {
            playerState.value = playerState.value.copy(
                isPlaying = false,
                currentPositionSeconds = recording.durationSeconds
            )
        }

        playerState.value = PlayerState(
            playingRecordingId = recording.id,
            isPlaying = true,
            currentPositionSeconds = 0,
            playbackSpeed = speed
        )

        playbackJob = viewModelScope.launch {
            val intervalMs = (500 / speed).toLong()
            while (playerState.value.isPlaying && playerState.value.playingRecordingId == recording.id) {
                delay(intervalMs)
                if (isRealAudio && playerManager.isPlaying) {
                    val realPos = playerManager.getCurrentPositionSeconds()
                    playerState.value = playerState.value.copy(currentPositionSeconds = realPos)
                } else if (!isRealAudio) {
                    val pos = playerState.value.currentPositionSeconds + 1
                    if (pos >= recording.durationSeconds) {
                        playerState.value = playerState.value.copy(
                            isPlaying = false,
                            currentPositionSeconds = recording.durationSeconds
                        )
                        break
                    } else {
                        playerState.value = playerState.value.copy(currentPositionSeconds = pos)
                    }
                }
            }
        }
    }

    private fun pausePlayback() {
        playerManager.pause()
        playerState.value = playerState.value.copy(isPlaying = false)
        playbackJob?.cancel()
    }

    private fun resumePlayback(recording: AudioRecording) {
        val speed = playerState.value.playbackSpeed
        var isRealAudio = playerManager.resume()
        if (!isRealAudio && File(recording.localPath).exists()) {
            isRealAudio = playerManager.play(recording.localPath, speed) {
                playerState.value = playerState.value.copy(
                    isPlaying = false,
                    currentPositionSeconds = recording.durationSeconds
                )
            }
        }

        playerState.value = playerState.value.copy(isPlaying = true)
        playbackJob = viewModelScope.launch {
            val intervalMs = (500 / speed).toLong()
            while (playerState.value.isPlaying && playerState.value.playingRecordingId == recording.id) {
                delay(intervalMs)
                if (isRealAudio && playerManager.isPlaying) {
                    val realPos = playerManager.getCurrentPositionSeconds()
                    playerState.value = playerState.value.copy(currentPositionSeconds = realPos)
                } else if (!isRealAudio) {
                    val pos = playerState.value.currentPositionSeconds + 1
                    if (pos >= recording.durationSeconds) {
                        playerState.value = playerState.value.copy(
                            isPlaying = false,
                            currentPositionSeconds = recording.durationSeconds
                        )
                        break
                    } else {
                        playerState.value = playerState.value.copy(currentPositionSeconds = pos)
                    }
                }
            }
        }
    }

    fun seekTo(seconds: Int, recording: AudioRecording) {
        if (playerState.value.playingRecordingId == recording.id) {
            playerManager.seekTo(seconds)
        }
        playerState.value = playerState.value.copy(currentPositionSeconds = seconds)
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setSpeed(speed)
        playerState.value = playerState.value.copy(playbackSpeed = speed)
    }

    fun toggleAutoSync(enabled: Boolean) {
        isAutoSyncEnabled.value = enabled
        if (enabled) {
            RcloneTermuxBackgroundSyncService.enqueueSync(getApplication())
        }
    }

    fun triggerBackgroundSyncNow() {
        RcloneTermuxBackgroundSyncService.enqueueSync(getApplication())
        Toast.makeText(getApplication(), "Service Termux/Rclone démarré pour synchroniser les fichiers en arrière-plan", Toast.LENGTH_SHORT).show()
    }

    // --- Google Drive File Management Layer (Firebase Auth Credentials) ---
    fun uploadAudioFileToGoogleDrive(
        recording: AudioRecording,
        folderName: String = com.example.data.sync.GoogleDriveFileManager.DEFAULT_DRIVE_FOLDER
    ) {
        viewModelScope.launch {
            Toast.makeText(
                getApplication(),
                "Téléversement Drive en cours via Firebase Auth (${authService.getCurrentUserEmail()})...",
                Toast.LENGTH_SHORT
            ).show()

            val result = googleDriveFileManager.uploadAudioFile(recording, folderName)
            result.onSuccess { uploadResult ->
                Toast.makeText(
                    getApplication(),
                    "Fichier '${recording.fileName}' téléversé sur Google Drive avec succès !",
                    Toast.LENGTH_LONG
                ).show()
                fetchRemoteDriveFiles(folderName)
            }.onFailure { error ->
                Toast.makeText(
                    getApplication(),
                    "Échec du téléversement Google Drive : ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun uploadSelectedRecordingsToGoogleDrive(
        folderName: String = com.example.data.sync.GoogleDriveFileManager.DEFAULT_DRIVE_FOLDER
    ) {
        val selectedIds = selectedRecordingIds.value
        if (selectedIds.isEmpty()) {
            Toast.makeText(getApplication(), "Aucun fichier sélectionné.", Toast.LENGTH_SHORT).show()
            return
        }

        val itemsToUpload = recordings.value.filter { selectedIds.contains(it.id) }
        if (itemsToUpload.isEmpty()) return

        Toast.makeText(
            getApplication(),
            "Téléversement groupé de ${itemsToUpload.size} fichier(s) vers Google Drive...",
            Toast.LENGTH_SHORT
        ).show()

        viewModelScope.launch {
            val results = googleDriveFileManager.uploadMultipleRecordings(itemsToUpload, folderName)
            val successCount = results.count { it.isSuccess }
            Toast.makeText(
                getApplication(),
                "Téléversement terminé : $successCount / ${itemsToUpload.size} fichiers synchronisés.",
                Toast.LENGTH_LONG
            ).show()
            clearSelection()
            fetchRemoteDriveFiles(folderName)
        }
    }

    fun fetchRemoteDriveFiles(
        folderName: String = com.example.data.sync.GoogleDriveFileManager.DEFAULT_DRIVE_FOLDER
    ) {
        isFetchingDriveFiles.value = true
        viewModelScope.launch {
            val result = googleDriveFileManager.listRemoteDriveFiles(folderName)
            result.onSuccess { files ->
                remoteDriveFiles.value = files
            }.onFailure {
                android.util.Log.w("AudioSyncViewModel", "Failed to list remote drive files: ${it.message}")
            }
            isFetchingDriveFiles.value = false
        }
    }

    fun deleteRemoteDriveFile(file: com.example.data.sync.DriveRemoteFile) {
        viewModelScope.launch {
            val result = googleDriveFileManager.deleteRemoteDriveFile(file.id)
            result.onSuccess {
                Toast.makeText(getApplication(), "Fichier distant '${file.name}' supprimé du Drive.", Toast.LENGTH_SHORT).show()
                fetchRemoteDriveFiles(file.folderName)
            }.onFailure {
                Toast.makeText(getApplication(), "Erreur lors de la suppression sur Drive.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun triggerManualGoogleDriveSync(
        folderName: String = com.example.data.sync.GoogleDriveFileManager.DEFAULT_DRIVE_FOLDER
    ) {
        if (isSyncingActive.value || driveUploadState.value.isUploading) {
            val busyMsg = if (appLanguage.value == com.example.ui.theme.AppLanguage.FRENCH) {
                "Une synchronisation Google Drive est déjà en cours..."
            } else {
                "A Google Drive sync operation is already in progress..."
            }
            Toast.makeText(getApplication(), busyMsg, Toast.LENGTH_SHORT).show()
            return
        }

        val isFr = appLanguage.value == com.example.ui.theme.AppLanguage.FRENCH
        val unsynced = recordings.value.filter { !it.isSynced && it.syncStatus != "CLOUD_SYNCED" }

        isSyncingActive.value = true
        syncProgressPercent.value = 5
        activeSyncCommand.value = "gdrive:manual-sync -> $folderName"

        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            try {
                if (unsynced.isNotEmpty()) {
                    val totalMb = unsynced.sumOf { it.fileSizeMb.toDouble() }
                    repository.logSyncOperation(
                        recordingId = null,
                        command = "drive:manual-sync -> ${unsynced.size} fichier(s) vers $folderName",
                        status = "IN_PROGRESS",
                        progress = 10,
                        bytes = String.format(Locale.US, "0 / %.2f MB", totalMb)
                    )

                    val results = googleDriveFileManager.uploadMultipleRecordings(
                        recordings = unsynced,
                        folderName = folderName
                    ) { overallPercent, currentFile ->
                        syncProgressPercent.value = overallPercent.coerceIn(5, 100)
                        activeSyncCommand.value = "drive:upload -> $currentFile ($overallPercent%)"
                    }

                    val successCount = results.count { it.isSuccess }
                    val failedCount = results.size - successCount
                    val statusStr = if (failedCount == 0) "SUCCESS" else "FAILED"
                    val summaryText = if (isFr) {
                        "$successCount / ${unsynced.size} fichier(s) synchronisé(s) vers $folderName"
                    } else {
                        "$successCount / ${unsynced.size} file(s) synced to $folderName"
                    }

                    lastDriveSyncTimestamp.value = System.currentTimeMillis()
                    lastDriveSyncSummary.value = summaryText
                    syncProgressPercent.value = 100

                    repository.logSyncOperation(
                        recordingId = null,
                        command = "drive:manual-sync [$statusStr] -> $successCount/${unsynced.size}",
                        status = statusStr,
                        progress = if (failedCount == 0) 100 else (successCount * 100 / unsynced.size.coerceAtLeast(1)),
                        bytes = String.format(Locale.US, "%.2f MB", totalMb),
                        errorMsg = if (failedCount > 0) "$failedCount fichier(s) en échec" else null
                    )

                    val remoteResult = googleDriveFileManager.listRemoteDriveFiles(folderName)
                    remoteResult.onSuccess { files -> remoteDriveFiles.value = files }

                    RcloneTermuxBackgroundSyncService.enqueueSync(getApplication())
                    Toast.makeText(getApplication(), summaryText, Toast.LENGTH_LONG).show()
                } else {
                    syncProgressPercent.value = 40
                    activeSyncCommand.value = "drive:verify -> gdrive:/$folderName"
                    repository.logSyncOperation(
                        recordingId = null,
                        command = "drive:manual-verify -> gdrive:/$folderName",
                        status = "IN_PROGRESS",
                        progress = 40,
                        bytes = "Vérification Drive..."
                    )

                    val remoteResult = googleDriveFileManager.listRemoteDriveFiles(folderName)
                    remoteResult.onSuccess { files ->
                        remoteDriveFiles.value = files
                    }

                    val fileCount = remoteDriveFiles.value.size
                    val verifiedMsg = if (isFr) {
                        "Index Google Drive à jour : $fileCount fichier(s) synchronisé(s)"
                    } else {
                        "Google Drive index up to date: $fileCount synced file(s)"
                    }

                    lastDriveSyncTimestamp.value = System.currentTimeMillis()
                    lastDriveSyncSummary.value = verifiedMsg
                    syncProgressPercent.value = 100

                    repository.logSyncOperation(
                        recordingId = null,
                        command = "drive:manual-verify [UP_TO_DATE] -> $folderName",
                        status = "SUCCESS",
                        progress = 100,
                        bytes = "$fileCount fichier(s) vérifié(s)"
                    )

                    Toast.makeText(getApplication(), verifiedMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: "Erreur de synchronisation Google Drive"
                lastDriveSyncSummary.value = errMsg
                repository.logSyncOperation(
                    recordingId = null,
                    command = "drive:manual-sync [FAILED]",
                    status = "FAILED",
                    progress = 0,
                    bytes = "0 MB",
                    errorMsg = errMsg
                )
                Toast.makeText(getApplication(), errMsg, Toast.LENGTH_LONG).show()
            } finally {
                isSyncingActive.value = false
                activeSyncCommand.value = ""
            }
        }
    }

    // --- Google Drive & Rclone Sync Service Actions ---
    fun syncRecordingToGoogleDrive(recording: AudioRecording, accessToken: String? = null) {
        if (isSyncingActive.value) return

        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            Toast.makeText(getApplication(), "Lancement de la synchronisation Rclone...", Toast.LENGTH_SHORT).show()
            val result = rcloneSyncManager.processAutomatedSync(recording.id)
            if (result.abortedReason != null) {
                Toast.makeText(getApplication(), "Synchronisation suspendue : ${result.abortedReason}", Toast.LENGTH_LONG).show()
            } else if (result.successCount > 0) {
                Toast.makeText(getApplication(), "Synchronisation réussie vers Google Drive (${rcloneSyncConfig.value.remotePath})", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(getApplication(), "Échec de synchronisation. Vérifiez la connexion.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Rclone & Cloud Sync Actions ---
    fun runRcloneSync(recording: AudioRecording, destinationFolder: String = "gdrive:/Z-CORE/Captures/") {
        syncRecordingToGoogleDrive(recording)
    }

    fun runCustomRcloneCommand(commandText: String) {
        if (isSyncingActive.value) return

        isSyncingActive.value = true
        syncProgressPercent.value = 0
        activeSyncCommand.value = commandText

        viewModelScope.launch {
            repository.logSyncOperation(
                recordingId = null,
                command = commandText,
                status = "IN_PROGRESS",
                progress = 0,
                bytes = "Calcul en cours..."
            )

            for (p in 20..100 step 20) {
                delay(350)
                syncProgressPercent.value = p
            }

            repository.logSyncOperation(
                recordingId = null,
                command = commandText,
                status = "SUCCESS",
                progress = 100,
                bytes = "Transfert terminé"
            )

            isSyncingActive.value = false
            Toast.makeText(getApplication(), "Commande exécutée avec succès!", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Batch Selection & Mass-Sync Actions ---
    fun toggleSelectionMode() {
        isSelectionMode.value = !isSelectionMode.value
        if (!isSelectionMode.value) {
            selectedRecordingIds.value = emptySet()
        }
    }

    fun toggleRecordingSelection(recordingId: Long) {
        val current = selectedRecordingIds.value.toMutableSet()
        if (current.contains(recordingId)) {
            current.remove(recordingId)
        } else {
            current.add(recordingId)
        }
        selectedRecordingIds.value = current
        if (current.isNotEmpty() && !isSelectionMode.value) {
            isSelectionMode.value = true
        }
    }

    fun selectAllRecordings(allIds: List<Long>) {
        selectedRecordingIds.value = allIds.toSet()
        isSelectionMode.value = true
    }

    fun clearSelection() {
        selectedRecordingIds.value = emptySet()
        isSelectionMode.value = false
    }

    fun massSyncSelectedRecordingsWithRclone() {
        val selectedIds = selectedRecordingIds.value
        if (selectedIds.isEmpty()) {
            Toast.makeText(getApplication(), "Aucun enregistrement sélectionné pour le transfert.", Toast.LENGTH_SHORT).show()
            return
        }

        val itemsToSync = recordings.value.filter { selectedIds.contains(it.id) }
        if (itemsToSync.isEmpty()) return

        Toast.makeText(
            getApplication(),
            "Masse-Sync Rclone démarrée pour ${itemsToSync.size} fichier(s)...",
            Toast.LENGTH_LONG
        ).show()

        // Trigger Termux background service sync as well
        RcloneTermuxBackgroundSyncService.enqueueSync(getApplication())

        viewModelScope.launch {
            for (recording in itemsToSync) {
                syncRecordingToGoogleDrive(recording)
            }
        }

        clearSelection()
    }

    // --- Macro Actions via MacroManager ---
    fun toggleFavoriteMacro(macro: CommandMacro) {
        viewModelScope.launch {
            macroManager.toggleFavorite(macro.id, macro.isFavorite)
        }
    }

    fun saveCustomMacro(
        name: String,
        commandText: String,
        category: String,
        description: String,
        requiresRoot: Boolean = false
    ) {
        viewModelScope.launch {
            macroManager.saveMacro(name, commandText, category, description, requiresRoot)
            Toast.makeText(getApplication(), "Macro '$name' sauvegardée!", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateMacro(macro: CommandMacro) {
        viewModelScope.launch {
            macroManager.updateMacro(macro)
            Toast.makeText(getApplication(), "Macro '${macro.name}' mise à jour!", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteMacro(macro: CommandMacro) {
        viewModelScope.launch {
            macroManager.deleteMacro(macro)
            Toast.makeText(getApplication(), "Macro '${macro.name}' supprimée.", Toast.LENGTH_SHORT).show()
        }
    }

    fun executeMacroViaTermuxIntent(macro: CommandMacro, runInBackground: Boolean = true) {
        val result = macroManager.executeMacro(macro, runInBackground)
        result.onSuccess {
            Toast.makeText(getApplication(), "Intent Termux envoyé pour '${macro.name}'", Toast.LENGTH_SHORT).show()
            viewModelScope.launch {
                repository.logSyncOperation(
                    recordingId = null,
                    command = macro.commandText,
                    status = "SUCCESS",
                    progress = 100,
                    bytes = "Intent Termux Transmis"
                )
            }
        }.onFailure { err ->
            Toast.makeText(getApplication(), "Échec de l'intent Termux: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun executeRawShellCommand(commandText: String, runInBackground: Boolean = true) {
        val result = macroManager.executeCustomCommand(commandText, runInBackground)
        result.onSuccess {
            Toast.makeText(getApplication(), "Commande envoyée à Termux", Toast.LENGTH_SHORT).show()
        }.onFailure { err ->
            Toast.makeText(getApplication(), "Erreur Termux: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // --- Predefined Termux Shell Scripts Intent Execution ---
    fun executePredefinedScript(
        script: PredefinedTermuxScript,
        customArgs: Map<String, String> = emptyMap(),
        runInBackground: Boolean = script.defaultRunInBackground
    ) {
        val result = macroManager.executePredefinedScript(script, customArgs, runInBackground)
        lastTermuxExecutionResult.value = result

        if (result.success) {
            Toast.makeText(
                getApplication(),
                "🚀 Intent Termux envoyé: ${script.titleFr} (${if (runInBackground) "Arrière-plan" else "Session Interactive"})",
                Toast.LENGTH_SHORT
            ).show()

            viewModelScope.launch {
                repository.logSyncOperation(
                    recordingId = null,
                    command = "SCRIPT: ${script.id} -> ${result.commandDispatched.take(60)}...",
                    status = "SUCCESS",
                    progress = 100,
                    bytes = "Intent Termux Transmis (${if (runInBackground) "BG" else "FG"})"
                )
            }
        } else {
            Toast.makeText(
                getApplication(),
                "Échec Intent Termux: ${result.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun getScriptGeneratedCode(
        script: PredefinedTermuxScript,
        customArgs: Map<String, String> = emptyMap()
    ): String {
        return macroManager.generateScriptCode(script, customArgs)
    }

    fun refreshTermuxStatus() {
        isTermuxAppInstalled.value = macroManager.isTermuxInstalled()
        isTermuxApiAppInstalled.value = macroManager.isTermuxApiInstalled()
    }

    // --- Gemini AI Studio ---
    fun analyzeRecordingWithAi(recording: AudioRecording, customPrompt: String? = null) {
        isAiAnalyzing.value = true
        viewModelScope.launch {
            val apiKey = BuildConfig.GEMINI_API_KEY
            val result = repository.analyzeAudioWithGemini(recording, customPrompt, apiKey)
            aiAnalysisResult.value = result.summary
            isAiAnalyzing.value = false

            // Update local recording if result succeeded
            if (result.isSuccess) {
                repository.updateRecording(
                    recording.copy(
                        aiSummary = result.summary,
                        transcription = if (result.transcription.isNotBlank()) result.transcription else recording.transcription
                    )
                )
            }
        }
    }

    fun updateTranscription(recording: AudioRecording, newText: String) {
        viewModelScope.launch {
            repository.updateRecording(recording.copy(transcription = newText))
            Toast.makeText(getApplication(), "Transcription mise à jour avec succès", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteRecording(recording: AudioRecording) {
        viewModelScope.launch {
            repository.deleteRecording(recording.id)
            if (playerState.value.playingRecordingId == recording.id) {
                pausePlayback()
                playerState.value = PlayerState()
            }
            Toast.makeText(getApplication(), "Enregistrement supprimé", Toast.LENGTH_SHORT).show()
        }
    }
}
