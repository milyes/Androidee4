package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudioRecording
import com.example.data.model.SyncLog
import com.example.data.sync.DriveRemoteFile
import com.example.data.sync.GoogleDriveFileManager
import com.example.data.viewmodel.AudioSyncViewModel
import com.example.data.viewmodel.NavigationTab
import com.example.ui.theme.AmberPending
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldSynced
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DriveAccentBlue = Color(0xFF4285F4)

private enum class DriveSyncOpFilter {
    ALL,
    SUCCESS,
    IN_PROGRESS,
    FAILED
}

private data class SyncedDriveDisplayItem(
    val key: String,
    val fileName: String,
    val sizeMb: Float,
    val cloudPath: String,
    val driveId: String?,
    val formattedDate: String,
    val mimeBadge: String,
    val durationLabel: String?,
    val remoteFile: DriveRemoteFile?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleDriveSyncScreen(
    viewModel: AudioSyncViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null
) {
    val appLanguage by viewModel.appLanguage.collectAsState()
    val isFr = appLanguage == AppLanguage.FRENCH

    val recordings by viewModel.recordings.collectAsState()
    val syncLogs by viewModel.syncLogs.collectAsState()
    val remoteDriveFiles by viewModel.remoteDriveFiles.collectAsState()
    val isFetchingDriveFiles by viewModel.isFetchingDriveFiles.collectAsState()
    val driveUploadState by viewModel.driveUploadState.collectAsState()

    val isSyncingActive by viewModel.isSyncingActive.collectAsState()
    val syncProgressPercent by viewModel.syncProgressPercent.collectAsState()
    val activeSyncCommand by viewModel.activeSyncCommand.collectAsState()
    val lastDriveSyncTimestamp by viewModel.lastDriveSyncTimestamp.collectAsState()
    val lastDriveSyncSummary by viewModel.lastDriveSyncSummary.collectAsState()

    val userGoogleEmail by viewModel.userGoogleAccountEmail.collectAsState()
    val isGoogleConnected by viewModel.isGoogleAccountConnected.collectAsState()

    var targetFolder by remember { mutableStateOf(GoogleDriveFileManager.DEFAULT_DRIVE_FOLDER) }
    var syncedFilesQuery by remember { mutableStateOf("") }
    var selectedOpFilter by remember { mutableStateOf(DriveSyncOpFilter.ALL) }

    BackHandler {
        if (onNavigateBack != null) {
            onNavigateBack()
        } else {
            viewModel.setTab(NavigationTab.CLOUD_SYNC)
        }
    }

    LaunchedEffect(targetFolder) {
        viewModel.fetchRemoteDriveFiles(targetFolder)
    }

    val isCurrentlySyncing = isSyncingActive || driveUploadState.isUploading

    val pendingRecordings = remember(recordings) {
        recordings.filter { !it.isSynced && it.syncStatus != "CLOUD_SYNCED" }
    }
    val localSyncedRecordings = remember(recordings) {
        recordings.filter { it.isSynced || it.syncStatus == "CLOUD_SYNCED" }
    }

    // Build unified synced files list combining remote Drive listing and synced local recordings
    val syncedDisplayItems = remember(remoteDriveFiles, localSyncedRecordings, targetFolder) {
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val localByName = localSyncedRecordings.associateBy { it.fileName.lowercase(Locale.ROOT) }
        val result = mutableListOf<SyncedDriveDisplayItem>()
        val seenNames = mutableSetOf<String>()

        for (remote in remoteDriveFiles) {
            val lower = remote.name.lowercase(Locale.ROOT)
            seenNames.add(lower)
            val matchedLocal = localByName[lower]
            val ext = remote.name.substringAfterLast('.', "M4A").uppercase(Locale.ROOT).take(4)
            val displaySizeMb = if (remote.sizeMb > 0f) remote.sizeMb else (matchedLocal?.fileSizeMb ?: 0f)
            val formattedTime = formatDriveTimestamp(remote.modifiedTime)
                ?: matchedLocal?.let { dateFormatter.format(Date(it.timestamp)) }
                ?: "Drive Cloud"

            result.add(
                SyncedDriveDisplayItem(
                    key = "remote_${remote.id}",
                    fileName = remote.name,
                    sizeMb = displaySizeMb,
                    cloudPath = "gdrive:/${remote.folderName}/${remote.name}",
                    driveId = remote.id,
                    formattedDate = formattedTime,
                    mimeBadge = ext,
                    durationLabel = matchedLocal?.let { formatDurationCompact(it.durationSeconds) },
                    remoteFile = remote
                )
            )
        }

        for (rec in localSyncedRecordings) {
            val lower = rec.fileName.lowercase(Locale.ROOT)
            if (seenNames.add(lower)) {
                val ext = rec.fileName.substringAfterLast('.', "M4A").uppercase(Locale.ROOT).take(4)
                result.add(
                    SyncedDriveDisplayItem(
                        key = "local_${rec.id}",
                        fileName = rec.fileName,
                        sizeMb = rec.fileSizeMb,
                        cloudPath = rec.cloudPath.ifBlank { "gdrive:/$targetFolder/${rec.fileName}" },
                        driveId = null,
                        formattedDate = dateFormatter.format(Date(rec.timestamp)),
                        mimeBadge = ext,
                        durationLabel = formatDurationCompact(rec.durationSeconds),
                        remoteFile = null
                    )
                )
            }
        }
        result
    }

    val filteredSyncedFiles = remember(syncedDisplayItems, syncedFilesQuery) {
        val q = syncedFilesQuery.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) syncedDisplayItems
        else syncedDisplayItems.filter {
            it.fileName.lowercase(Locale.ROOT).contains(q) ||
                it.cloudPath.lowercase(Locale.ROOT).contains(q)
        }
    }

    val filteredSyncLogs = remember(syncLogs, selectedOpFilter) {
        when (selectedOpFilter) {
            DriveSyncOpFilter.ALL -> syncLogs
            DriveSyncOpFilter.SUCCESS -> syncLogs.filter { it.status == "SUCCESS" }
            DriveSyncOpFilter.IN_PROGRESS -> syncLogs.filter { it.status == "IN_PROGRESS" || it.status == "PENDING" }
            DriveSyncOpFilter.FAILED -> syncLogs.filter { it.status == "FAILED" }
        }
    }

    val totalSyncedMb = remember(syncedDisplayItems) {
        syncedDisplayItems.sumOf { it.sizeMb.toDouble() }
    }
    val successOpsCount = remember(syncLogs) { syncLogs.count { it.status == "SUCCESS" } }
    val failedOpsCount = remember(syncLogs) { syncLogs.count { it.status == "FAILED" } }

    val latestSyncTime = lastDriveSyncTimestamp ?: syncLogs.firstOrNull()?.timestamp
    val formattedLatestSync = remember(latestSyncTime) {
        latestSyncTime?.let {
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(it))
        }
    }

    val hasActiveError = !isCurrentlySyncing && driveUploadState.errorMessage != null
    val statusColor by animateColorAsState(
        targetValue = when {
            isCurrentlySyncing -> DriveAccentBlue
            hasActiveError -> CrimsonError
            pendingRecordings.isNotEmpty() -> AmberPending
            else -> EmeraldSynced
        },
        label = "drive_status_color"
    )

    val statusLabel = when {
        isCurrentlySyncing -> if (isFr) "SYNCHRO EN COURS" else "SYNCING"
        hasActiveError -> if (isFr) "ERREUR" else "ERROR"
        pendingRecordings.isNotEmpty() -> if (isFr) "EN ATTENTE (${pendingRecordings.size})" else "PENDING (${pendingRecordings.size})"
        else -> if (isFr) "SYNCHRONISÉ" else "UP TO DATE"
    }

    val activeProgressFraction = when {
        driveUploadState.isUploading -> (driveUploadState.progressPercent / 100f).coerceIn(0f, 1f)
        isSyncingActive -> (syncProgressPercent / 100f).coerceIn(0f, 1f)
        else -> 1f
    }

    val activeProgressPercentInt = when {
        driveUploadState.isUploading -> driveUploadState.progressPercent
        isSyncingActive -> syncProgressPercent
        else -> 100
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Screen Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = {
                            if (onNavigateBack != null) onNavigateBack()
                            else viewModel.setTab(NavigationTab.CLOUD_SYNC)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("gdrive_sync_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isFr) "Retour" else "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isFr) "Synchro Google Drive" else "Google Drive Sync",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF002654).copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "VERSION FR 🇫🇷",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isFr) "Statut des opérations & fichiers cloud" else "Sync operations status & cloud files",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.fetchRemoteDriveFiles(targetFolder) },
                    enabled = !isFetchingDriveFiles,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("refresh_gdrive_screen_btn")
                ) {
                    if (isFetchingDriveFiles) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = DriveAccentBlue
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = if (isFr) "Actualiser les fichiers Drive" else "Refresh Drive files",
                            tint = DriveAccentBlue
                        )
                    }
                }
            }
        }

        // Scrollable Adaptive Content
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("gdrive_sync_scroll_list"),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- 1. Sync Operations Status Hero Card + Manual Sync Trigger ---
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gdrive_sync_status_card"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Top status row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = statusColor.copy(alpha = 0.16f),
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = when {
                                                    isCurrentlySyncing -> Icons.Filled.Sync
                                                    hasActiveError -> Icons.Filled.CloudOff
                                                    pendingRecordings.isNotEmpty() -> Icons.Filled.CloudUpload
                                                    else -> Icons.Filled.CloudDone
                                                },
                                                contentDescription = null,
                                                tint = statusColor,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isFr) "État Google Drive" else "Google Drive Status",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isGoogleConnected) userGoogleEmail else (if (isFr) "Compte non connecté" else "Account disconnected"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = statusColor.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                                    modifier = Modifier.testTag("gdrive_sync_status_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(statusColor, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = statusLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = statusColor,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Target folder & account connection metadata strip
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Folder,
                                            contentDescription = null,
                                            tint = DriveAccentBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = if (isFr) "Dossier cible Drive" else "Target Drive Folder",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                text = "gdrive:/$targetFolder",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isGoogleConnected) EmeraldSynced.copy(alpha = 0.16f) else CrimsonError.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = if (isGoogleConnected) {
                                                if (isFr) "OAUTH2 ACTIF" else "OAUTH2 ACTIVE"
                                            } else {
                                                if (isFr) "HORS LIGNE" else "OFFLINE"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = if (isGoogleConnected) EmeraldSynced else CrimsonError,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Active transfer progress indicator
                            AnimatedVisibility(visible = isCurrentlySyncing) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = DriveAccentBlue.copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, DriveAccentBlue.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp)
                                        .testTag("gdrive_live_progress_panel")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = driveUploadState.currentFileName
                                                    ?: activeSyncCommand.ifBlank {
                                                        if (isFr) "Transfert Google Drive..." else "Google Drive transfer..."
                                                    },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = DriveAccentBlue,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "$activeProgressPercentInt%",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = DriveAccentBlue
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = { activeProgressFraction },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(7.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = DriveAccentBlue,
                                            trackColor = DriveAccentBlue.copy(alpha = 0.2f)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = buildString {
                                                append(driveUploadState.statusText)
                                                if (driveUploadState.bytesTransferredText.isNotBlank()) {
                                                    append(" • ")
                                                    append(driveUploadState.bytesTransferredText)
                                                }
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            // Error Banner if upload failed
                            AnimatedVisibility(visible = hasActiveError) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = CrimsonError.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ErrorOutline,
                                            contentDescription = null,
                                            tint = CrimsonError,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = driveUploadState.errorMessage ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CrimsonError
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Key Sync Metrics Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DriveMetricTile(
                                    label = if (isFr) "Synchronisés" else "Synced",
                                    value = syncedDisplayItems.size.toString(),
                                    subtitle = String.format(Locale.US, "%.2f MB", totalSyncedMb),
                                    accentColor = EmeraldSynced,
                                    icon = Icons.Filled.CloudDone,
                                    modifier = Modifier.weight(1f)
                                )
                                DriveMetricTile(
                                    label = if (isFr) "En attente" else "Pending",
                                    value = pendingRecordings.size.toString(),
                                    subtitle = if (isFr) "à envoyer" else "queued",
                                    accentColor = if (pendingRecordings.isNotEmpty()) AmberPending else MaterialTheme.colorScheme.onSurfaceVariant,
                                    icon = Icons.Outlined.CloudUpload,
                                    modifier = Modifier.weight(1f)
                                )
                                DriveMetricTile(
                                    label = if (isFr) "Opérations" else "Operations",
                                    value = syncLogs.size.toString(),
                                    subtitle = "$successOpsCount OK / $failedOpsCount Err",
                                    accentColor = DriveAccentBlue,
                                    icon = Icons.Outlined.History,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (formattedLatestSync != null || lastDriveSyncSummary != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = buildString {
                                            if (formattedLatestSync != null) {
                                                append(if (isFr) "Dernière activité : " else "Last activity: ")
                                                append(formattedLatestSync)
                                            }
                                            if (!lastDriveSyncSummary.isNullOrBlank()) {
                                                if (formattedLatestSync != null) append(" • ")
                                                append(lastDriveSyncSummary)
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Prominent Manual Sync Trigger Button + Refresh Index Action
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.triggerManualGoogleDriveSync(targetFolder) },
                                    enabled = !isCurrentlySyncing,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DriveAccentBlue,
                                        contentColor = Color.White,
                                        disabledContainerColor = DriveAccentBlue.copy(alpha = 0.5f),
                                        disabledContentColor = Color.White.copy(alpha = 0.85f)
                                    ),
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .heightIn(min = 52.dp)
                                        .testTag("manual_drive_sync_btn")
                                ) {
                                    if (isCurrentlySyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isFr) "Synchro en cours..." else "Syncing...",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Sync,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (pendingRecordings.isNotEmpty()) {
                                                if (isFr) "Synchroniser (${pendingRecordings.size})" else "Sync Now (${pendingRecordings.size})"
                                            } else {
                                                if (isFr) "Synchroniser Maintenant" else "Manual Sync Now"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.fetchRemoteDriveFiles(targetFolder) },
                                    enabled = !isFetchingDriveFiles && !isCurrentlySyncing,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, DriveAccentBlue.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .heightIn(min = 52.dp)
                                        .testTag("refresh_drive_index_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudDownload,
                                        contentDescription = null,
                                        tint = DriveAccentBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isFr) "Vérifier" else "Refresh",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DriveAccentBlue
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 2. Pending Upload Queue Callout (if unsynced local recordings exist) ---
                if (pendingRecordings.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gdrive_pending_queue_card"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = AmberPending.copy(alpha = 0.08f)
                            ),
                            border = BorderStroke(1.dp, AmberPending.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.PendingActions,
                                            contentDescription = null,
                                            tint = AmberPending,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isFr) {
                                                "${pendingRecordings.size} fichier(s) local(aux) en attente d'envoi"
                                            } else {
                                                "${pendingRecordings.size} local file(s) awaiting Drive sync"
                                            },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = AmberPending
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = pendingRecordings.take(3).joinToString(", ") { it.fileName } +
                                        if (pendingRecordings.size > 3) " +${pendingRecordings.size - 3}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // --- 3. Synced Files List Header & Search ---
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gdrive_synced_files_section")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.FolderShared,
                                    contentDescription = null,
                                    tint = EmeraldSynced,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isFr) "Fichiers synchronisés sur Drive" else "Synced Files on Google Drive",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = EmeraldSynced.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "${filteredSyncedFiles.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSynced,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = syncedFilesQuery,
                            onValueChange = { syncedFilesQuery = it },
                            placeholder = {
                                Text(
                                    text = if (isFr) "Rechercher un fichier synchronisé..." else "Search synced files...",
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                if (syncedFilesQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { syncedFilesQuery = "" },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Clear,
                                            contentDescription = if (isFr) "Effacer" else "Clear"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gdrive_synced_search_input")
                        )
                    }
                }

                // Synced Files Items or Empty State
                if (filteredSyncedFiles.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gdrive_synced_empty_state"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudQueue,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (syncedFilesQuery.isNotBlank()) {
                                        if (isFr) "Aucun fichier correspondant à \"$syncedFilesQuery\"" else "No synced file matching \"$syncedFilesQuery\""
                                    } else {
                                        if (isFr) "Aucun fichier synchronisé sur Google Drive" else "No files synced to Google Drive yet"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isFr) {
                                        "Appuyez sur « Synchroniser Maintenant » pour envoyer vos captures audio locales vers Drive."
                                    } else {
                                        "Tap \"Manual Sync Now\" above to upload your local audio recordings to Google Drive."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredSyncedFiles, key = { it.key }) { item ->
                        SyncedDriveFileRowCard(
                            item = item,
                            isFr = isFr,
                            onDeleteRemote = item.remoteFile?.let { remote ->
                                { viewModel.deleteRemoteDriveFile(remote) }
                            }
                        )
                    }
                }

                // --- 4. Google Drive Sync Operations Feed ---
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("gdrive_sync_operations_section")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Timeline,
                                    contentDescription = null,
                                    tint = DriveAccentBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isFr) "Opérations de synchronisation" else "Sync Operations History",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "${filteredSyncLogs.size} ${if (isFr) "entrées" else "entries"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedOpFilter == DriveSyncOpFilter.ALL,
                                    onClick = { selectedOpFilter = DriveSyncOpFilter.ALL },
                                    label = { Text(if (isFr) "Toutes (${syncLogs.size})" else "All (${syncLogs.size})") },
                                    modifier = Modifier.testTag("filter_ops_all")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedOpFilter == DriveSyncOpFilter.SUCCESS,
                                    onClick = { selectedOpFilter = DriveSyncOpFilter.SUCCESS },
                                    label = { Text(if (isFr) "Succès ($successOpsCount)" else "Success ($successOpsCount)") },
                                    modifier = Modifier.testTag("filter_ops_success")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedOpFilter == DriveSyncOpFilter.IN_PROGRESS,
                                    onClick = { selectedOpFilter = DriveSyncOpFilter.IN_PROGRESS },
                                    label = { Text(if (isFr) "En cours" else "In Progress") },
                                    modifier = Modifier.testTag("filter_ops_progress")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedOpFilter == DriveSyncOpFilter.FAILED,
                                    onClick = { selectedOpFilter = DriveSyncOpFilter.FAILED },
                                    label = { Text(if (isFr) "Échecs ($failedOpsCount)" else "Failed ($failedOpsCount)") },
                                    modifier = Modifier.testTag("filter_ops_failed")
                                )
                            }
                        }
                    }
                }

                if (filteredSyncLogs.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isFr) "Aucune opération enregistrée pour ce filtre." else "No sync operations recorded for this filter.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredSyncLogs.take(25), key = { "op_${it.id}" }) { log ->
                        DriveSyncOperationRow(log = log, isFr = isFr)
                    }
                }

                // --- 5. Standing Compliance Footer ---
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "LICENSE NETSECUREPRO.CA • GEMIN CORE V7 PRO FINAL • IA_LOGIC_SINGNATURE:ZOUBIROU-IA-2025",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DriveMetricTile(
    label: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SyncedDriveFileRowCard(
    item: SyncedDriveDisplayItem,
    isFr: Boolean,
    onDeleteRemote: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("synced_file_row_${item.key}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, EmeraldSynced.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DriveAccentBlue.copy(alpha = 0.14f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.AudioFile,
                                contentDescription = null,
                                tint = DriveAccentBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = item.mimeBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = buildString {
                                    append(String.format(Locale.US, "%.2f MB", item.sizeMb))
                                    if (item.durationLabel != null) {
                                        append(" • ")
                                        append(item.durationLabel)
                                    }
                                    append(" • ")
                                    append(item.formattedDate)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EmeraldSynced.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldSynced,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isFr) "SYNC" else "SYNCED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSynced,
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    if (onDeleteRemote != null) {
                        IconButton(
                            onClick = onDeleteRemote,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("delete_synced_drive_file_${item.key}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = if (isFr) "Supprimer de Google Drive" else "Delete from Google Drive",
                                tint = CrimsonError,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDone,
                        contentDescription = null,
                        tint = EmeraldSynced,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = buildString {
                            append(item.cloudPath)
                            if (!item.driveId.isNullOrBlank()) {
                                append(" • ID: ")
                                append(item.driveId.take(14))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
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

@Composable
private fun DriveSyncOperationRow(
    log: SyncLog,
    isFr: Boolean
) {
    val opColor = when (log.status) {
        "SUCCESS" -> EmeraldSynced
        "IN_PROGRESS" -> DriveAccentBlue
        "PENDING" -> AmberPending
        else -> CrimsonError
    }
    val statusText = when (log.status) {
        "SUCCESS" -> if (isFr) "SUCCÈS" else "SUCCESS"
        "IN_PROGRESS" -> if (isFr) "EN COURS" else "RUNNING"
        "PENDING" -> if (isFr) "ATTENTE" else "PENDING"
        else -> if (isFr) "ÉCHEC" else "FAILED"
    }
    val formattedTimestamp = remember(log.timestamp) {
        SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sync_operation_row_${log.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, opColor.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when (log.status) {
                            "SUCCESS" -> Icons.Filled.CheckCircle
                            "IN_PROGRESS" -> Icons.Filled.Sync
                            "PENDING" -> Icons.Outlined.Schedule
                            else -> Icons.Filled.Error
                        },
                        contentDescription = null,
                        tint = opColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = log.commandExecuted,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = opColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = opColor,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${log.bytesTransferred} • ${log.progressPercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp
                )
                Text(
                    text = formattedTimestamp,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            if (!log.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = log.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = CrimsonError,
                    fontSize = 10.5.sp
                )
            }
        }
    }
}

private fun formatDriveTimestamp(isoString: String): String? {
    if (isoString.isBlank()) return null
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val parsed = parser.parse(isoString.take(19)) ?: return null
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(parsed)
    } catch (_: Exception) {
        null
    }
}

private fun formatDurationCompact(durationSeconds: Int): String {
    val mins = durationSeconds / 60
    val secs = durationSeconds % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}
