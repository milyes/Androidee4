package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.EmeraldSynced

private val SamsungBlue = Color(0xFF0381FE)
private val GoldAccent = Color(0xFFD97706)
private val CyanAccent = Color(0xFF06B6D4)
private val DarkSlate = Color(0xFF0F172A)

private enum class ConfigDialogTab {
    STRUCTURED,
    HTML_VIEW,
    CONNECTION_LOGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConfigurationDataDialog(
    isFr: Boolean = true,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(ConfigDialogTab.STRUCTURED) }
    val scrollState = rememberScrollState()

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, if (isFr) "$label copié !" else "$label copied!", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .testTag("device_configuration_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            border = BorderStroke(1.5.dp, SamsungBlue.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header: Samsung Galaxy S22 (SM-S901W) Data
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SamsungBlue.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, SamsungBlue.copy(alpha = 0.4f)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.PhoneAndroid,
                                    contentDescription = null,
                                    tint = SamsungBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Galaxy S22 (SM-S901W)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = EmeraldSynced.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "QUALCOMM",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSynced,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Données Service Configuration Android (Google Play)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag("close_device_dialog_btn")
                    ) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == ConfigDialogTab.STRUCTURED,
                        onClick = { selectedTab = ConfigDialogTab.STRUCTURED },
                        text = {
                            Text(
                                text = if (isFr) "Fiche Matériel" else "Hardware Spec",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == ConfigDialogTab.HTML_VIEW,
                        onClick = { selectedTab = ConfigDialogTab.HTML_VIEW },
                        text = {
                            Text(
                                text = if (isFr) "Document Google" else "Google HTML",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == ConfigDialogTab.CONNECTION_LOGS,
                        onClick = { selectedTab = ConfigDialogTab.CONNECTION_LOGS },
                        text = {
                            Text(
                                text = if (isFr) "Historique Sync" else "Sync History",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Content
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        ConfigDialogTab.STRUCTURED -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 1. Device Identifiers Section
                                ConfigSectionCard(
                                    title = if (isFr) "Identifiants de l'Appareil & Compte" else "Device & Account Identifiers",
                                    icon = Icons.Outlined.Fingerprint,
                                    accentColor = SamsungBlue
                                ) {
                                    ConfigDataRow(
                                        label = if (isFr) "Identifiant Android" else "Android ID",
                                        value = "3867290799904350142",
                                        onCopy = { copyToClipboard("Android ID", "3867290799904350142") }
                                    )
                                    ConfigDataRow(
                                        label = if (isFr) "Code IIEM (IMEI)" else "IMEI",
                                        value = "354818254529107",
                                        onCopy = { copyToClipboard("IMEI", "354818254529107") }
                                    )
                                    ConfigDataRow(
                                        label = if (isFr) "Numéro de série" else "Serial Number",
                                        value = "r0q:R3CT60MHGWL",
                                        onCopy = { copyToClipboard("Numéro de série", "r0q:R3CT60MHGWL") }
                                    )
                                    ConfigDataRow(
                                        label = if (isFr) "Nom sympathique" else "Friendly Name",
                                        value = "Galaxy S22",
                                        onCopy = null
                                    )
                                    ConfigDataRow(
                                        label = if (isFr) "Région & Fuseau" else "Region & Timezone",
                                        value = "fr-CA • America/Toronto (Montreal)",
                                        onCopy = null
                                    )
                                }

                                // 2. Hardware & Device Attributes Section
                                ConfigSectionCard(
                                    title = if (isFr) "Attributs Matériels & Écran" else "Device Attributes & Display",
                                    icon = Icons.Outlined.Memory,
                                    accentColor = CyanAccent
                                ) {
                                    ConfigDataRow(label = "SoC / Matériel", value = "Qualcomm Snapdragon (qcom) • 8 cœurs", onCopy = null)
                                    ConfigDataRow(label = "Modèle / Produit", value = "SM-S901W • r0q / r0qcsx", onCopy = null)
                                    ConfigDataRow(label = "Fabricant & Marque", value = "samsung (android-samsung-ss)", onCopy = null)
                                    ConfigDataRow(label = "Mémoire Totale (RAM)", value = "7 580 258 304 Octets (8 Go LPDDR5)", onCopy = null)
                                    ConfigDataRow(label = "Résolution Écran", value = "1080 x 2340 pixels • 480 DPI", onCopy = null)
                                    ConfigDataRow(label = "Plus petite largeur", value = "360 dp (Normal layout)", onCopy = null)
                                    ConfigDataRow(label = "Graphismes 3D", value = "GLES 196610 (3.2) • Vulkan Level 1 (v4198400)", onCopy = null)
                                    ConfigDataRow(label = "Clé OEM Google Play", value = "FMC (Fast Mobile Config)", onCopy = null)
                                    ConfigDataRow(label = "Plateformes Natives", value = "arm64-v8a, armeabi-v7a, armeabi", onCopy = null)
                                }

                                // 3. Network, SIM Fido & Radio Section
                                ConfigSectionCard(
                                    title = if (isFr) "Connectivité Réseau & SIM Fido" else "Network Connectivity & SIM Fido",
                                    icon = Icons.Outlined.CellTower,
                                    accentColor = GoldAccent
                                ) {
                                    ConfigDataRow(label = "Opérateur SIM Actuel", value = "Fido (MCC 302, MNC 370)", onCopy = null)
                                    ConfigDataRow(label = "Fournisseur Cellulaire", value = "Fido / Rogers (MCC 302, MNC 720)", onCopy = null)
                                    ConfigDataRow(
                                        label = "IMSI Tronqué",
                                        value = "302370507900000 (DD)",
                                        onCopy = { copyToClipboard("IMSI", "302370507900000") }
                                    )
                                    ConfigDataRow(label = "Rôles Actifs SIM", value = "Voice, Data, SMS (Data Roaming: Disabled)", onCopy = null)
                                    ConfigDataRow(label = "Téléphonie Matérielle", value = "GSM, IMS, SingleReg, eUICC, LTE/5G", onCopy = null)
                                    ConfigDataRow(label = "Micrologiciel Radio", value = "S901WVLU3CWGI,S901WVLU3CWGI", onCopy = null)
                                    ConfigDataRow(label = "Bootloader Firmware", value = "S901WVLU3CWGI", onCopy = null)
                                    ConfigDataRow(label = "Heure d'inscription SIM", value = "21 août 2022, 20 h 26 min 05 s UTC", onCopy = null)
                                    ConfigDataRow(label = "Dernière connexion données", value = "27 sept. 2023, 01 h 42 min 53 s UTC (Internal Trigger)", onCopy = null)
                                }

                                // 4. Audio Pro, Sensors & Keystore Security
                                ConfigSectionCard(
                                    title = if (isFr) "Audio Professionnel, Capteurs & Sécurité" else "Pro Audio, Sensors & Keystore",
                                    icon = Icons.Outlined.Security,
                                    accentColor = EmeraldSynced
                                ) {
                                    ConfigDataRow(label = "Audio Matériel", value = "android.hardware.audio.pro (Low-Latency Output)", onCopy = null)
                                    ConfigDataRow(label = "Microphone", value = "Studio Microphone PCM 44.1/48 kHz", onCopy = null)
                                    ConfigDataRow(label = "Capteurs & Biométrie", value = "Fingerprint, Face, Accel, Barometer, Gyro, SensorHub (v29)", onCopy = null)
                                    ConfigDataRow(label = "Keystore Matériel", value = "StrongBox Keystore (v4) • Hardware Level 100", onCopy = null)
                                    ConfigDataRow(label = "Knox SDK Supporté", value = "Samsung Knox API Levels 33, 34, 35, 36", onCopy = null)
                                    ConfigDataRow(label = "SDK Android & Services", value = "API 33 (Android 13) • Play Services 233515045", onCopy = null)
                                    ConfigDataRow(label = "Niveau Patch Sécurité", value = "2023-08-01 (Verified Boot & FBE)", onCopy = null)
                                    ConfigDataRow(
                                        label = "Certificats Sans Fil",
                                        value = "pQCiqOznWXJsMOtUFQOoAlW1zig=, Pcsad29a5jlLVFIZ9U5JX6bj0ao=",
                                        onCopy = { copyToClipboard("Certificats Sans Fil", "pQCiqOznWXJsMOtUFQOoAlW1zig=, Pcsad29a5jlLVFIZ9U5JX6bj0ao=") }
                                    )
                                    ConfigDataRow(
                                        label = "Empreinte SE (Build)",
                                        value = "samsung/r0qcsx/r0q:13/TP1A.220624.014/S901WVLU3CWGI:user/release-keys",
                                        onCopy = { copyToClipboard("Build Fingerprint", "samsung/r0qcsx/r0q:13/TP1A.220624.014/S901WVLU3CWGI:user/release-keys") }
                                    )
                                }
                            }
                        }

                        ConfigDialogTab.HTML_VIEW -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.javaScriptEnabled = false
                                            loadUrl("file:///android_asset/device_configuration_data.html")
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        ConfigDialogTab.CONNECTION_LOGS -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = SamsungBlue.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, SamsungBlue.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (isFr) "Historique des synchronisations réussies au Service de Configuration Google Play :" else "Successful Sync Sessions with Google Play Configuration Service:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SamsungBlue,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }

                                val syncEntries = listOf(
                                    Triple("27 sept. 2023, 01 h 42 min 53 s UTC", "S901WVLU3CWGI", "233515045"),
                                    Triple("21 sept. 2023, 08 h 56 min 41 s UTC", "S901WVLU3CWGI", "233515045"),
                                    Triple("13 sept. 2023, 21 h 37 min 44 s UTC", "S901WVLU3CWGI", "233414045"),
                                    Triple("2 sept. 2023, 10 h 03 min 28 s UTC", "S901WVLU3CWGI", "233316045"),
                                    Triple("1 sept. 2023, 04 h 24 min 40 s UTC", "S901WVLS3CWF2", "233316045"),
                                    Triple("29 août 2023, 03 h 20 min 49 s UTC", "S901WVLS3CWF2", "233217045"),
                                    Triple("19 août 2023, 09 h 05 min 05 s UTC", "S901WVLS3CWF2", "233116045"),
                                    Triple("5 août 2023, 08 h 53 min 51 s UTC", "S901WVLS3CWF2", "233013045"),
                                    Triple("29 juill. 2023, 21 h 54 min 15 s UTC", "S901WVLS3CWF2", "232617045"),
                                    Triple("25 juill. 2023, 10 h 03 min 10 s UTC", "S901WVLS3CWF2", "232518045"),
                                    Triple("23 juill. 2023, 19 h 27 min 35 s UTC", "S901WVLS3CWF2", "232516045")
                                )

                                syncEntries.forEach { (date, radio, gmsVer) ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = date, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    text = "Radio: $radio • GMS: $gmsVer",
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Surface(
                                                color = EmeraldSynced.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "200 OK",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EmeraldSynced,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val allData = """
                                === DONNÉES DU SERVICE DE CONFIGURATION ANDROID ===
                                Appareil: Samsung Galaxy S22 (SM-S901W)
                                Identifiant Android: 3867290799904350142
                                Code IIEM: 354818254529107
                                Numéro de série: r0q:R3CT60MHGWL
                                Nom sympathique: Galaxy S22
                                Région: fr-CA (America/Toronto)
                                Opérateur SIM: Fido (302370 / 302720)
                                IMSI: 302370507900000 (DD)
                                Matériel: Qualcomm Snapdragon (8 cœurs, 8 Go RAM - 7 580 258 304 Octets)
                                Écran: 1080x2340 px, 480 DPI, Smallest Width 360 px
                                Micrologiciel: S901WVLU3CWGI
                                GLES: 196610 (3.2) • Vulkan: Level 1 (4198400)
                                Android SDK: 33 (Android 13) • Play Services: 233515045
                                Empreinte: samsung/r0qcsx/r0q:13/TP1A.220624.014/S901WVLU3CWGI:user/release-keys
                                Certificats sans fil: pQCiqOznWXJsMOtUFQOoAlW1zig=, Pcsad29a5jlLVFIZ9U5JX6bj0ao=
                            """.trimIndent()
                            copyToClipboard("Profil Complet Galaxy S22", allData)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isFr) "Copier Tout" else "Copy All", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isFr) "Fermer" else "Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            content()
        }
    }
}

@Composable
private fun ConfigDataRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onCopy != null) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copier",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
