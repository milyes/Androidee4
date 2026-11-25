package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * AudioWaveformVisualizer avec Auto-Scroll & Fixed Playhead (Mode Réel)
 * 
 * Supporte le défilement automatique continu avec tête de lecture fixe (auto-scroll show fixed)
 * pour éviter la compression des barres d'amplitude lors des enregistrements longs.
 * 
 * Conforme :
 * • LICENSE NETSECUREPRO.CA
 * • GEMIN CORE V7 PRO FINAL
 * • IA_LOGIC_SINGNATURE:ZOUBIROU-IA-2025
 * • VERSION FR 🇫🇷
 */
@Composable
fun AudioWaveformVisualizer(
    amplitudes: List<Int>,
    progressRatio: Float = 0f,
    isLive: Boolean = false,
    autoScrollShowFixed: Boolean = true,
    showFixedPlayhead: Boolean = true,
    onSeekRatio: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    activeBarColor: Color = MaterialTheme.colorScheme.secondary,
    inactiveBarColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_pulse")
    val pulseAnim by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(280, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val playheadGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "playhead_glow"
    )

    val displayAmps = if (amplitudes.isEmpty()) {
        listOf(20, 35, 50, 75, 90, 60, 40, 80, 95, 70, 40, 65, 85, 50, 30, 60, 75, 40, 20, 50)
    } else amplitudes

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .pointerInput(onSeekRatio) {
                if (onSeekRatio != null) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekRatio(ratio)
                    }
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        // Draw center baseline for studio oscilloscope feel
        drawLine(
            color = inactiveBarColor.copy(alpha = 0.25f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.5f
        )

        val fixedBarWidth = 12f
        val fixedBarGap = 8f
        val step = fixedBarWidth + fixedBarGap
        val maxVisibleBars = maxOf(12, (width / step).toInt())

        // Logic Auto-Scroll Show Fixed :
        // Lorsque l'enregistrement est en direct ou dépasse la largeur de l'écran,
        // on fait défiler automatiquement les barres en conservant une taille fixe.
        val (visibleAmps, startIndexOffset) = if (autoScrollShowFixed && displayAmps.size > maxVisibleBars) {
            val visible = displayAmps.takeLast(maxVisibleBars)
            Pair(visible, displayAmps.size - maxVisibleBars)
        } else {
            Pair(displayAmps, 0)
        }

        val count = visibleAmps.size
        val effectiveBarWidth = if (autoScrollShowFixed) fixedBarWidth else maxOf(4f, (width - (count - 1) * 4f) / count)
        val effectiveBarGap = if (autoScrollShowFixed) fixedBarGap else 4f

        visibleAmps.forEachIndexed { index, amp ->
            val globalIndex = startIndexOffset + index
            val isLatestLiveBar = isLive && (index >= count - 3)
            val scale = if (isLatestLiveBar) pulseAnim else 1.0f
            val rawNormalized = (amp.coerceIn(8, 100) / 100f)
            val barHeight = (height * rawNormalized * scale).coerceIn(6f, height)

            val x = index * (effectiveBarWidth + effectiveBarGap)
            val y = (height - barHeight) / 2f

            val isPlayed = if (displayAmps.isNotEmpty()) {
                (globalIndex.toFloat() / displayAmps.size) <= progressRatio
            } else false

            val currentColor = when {
                isLive -> if (isLatestLiveBar) Color(0xFFEF4444) else barColor.copy(alpha = 0.85f)
                isPlayed -> activeBarColor
                else -> inactiveBarColor
            }

            drawRoundRect(
                color = currentColor,
                topLeft = Offset(x, y),
                size = Size(effectiveBarWidth, barHeight),
                cornerRadius = CornerRadius(effectiveBarWidth / 2f, effectiveBarWidth / 2f)
            )

            // Draw peak indicator on active live bars
            if (isLive && isLatestLiveBar) {
                drawCircle(
                    color = Color(0xFFEF4444),
                    radius = effectiveBarWidth / 1.6f,
                    center = Offset(x + effectiveBarWidth / 2f, y - 3f)
                )
            }
        }

        // Ligne de tête de lecture fixe (Fixed Playhead Cursor)
        if (showFixedPlayhead) {
            val playheadX = when {
                isLive -> {
                    // En direct : playhead fixé sur la dernière barre générée à droite
                    val lastX = (count - 1) * (effectiveBarWidth + effectiveBarGap) + effectiveBarWidth / 2f
                    lastX.coerceAtMost(width - 2f)
                }
                progressRatio > 0f -> {
                    // En lecture : playhead fixé selon le progressRatio
                    (width * progressRatio).coerceIn(0f, width)
                }
                else -> -1f
            }

            if (playheadX >= 0f) {
                // Glow vertical line
                drawLine(
                    color = if (isLive) Color(0xFFEF4444).copy(alpha = playheadGlow * 0.4f) else Color(0xFF00E5FF).copy(alpha = playheadGlow * 0.4f),
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, height),
                    strokeWidth = 6f
                )
                // Solid line
                drawLine(
                    color = if (isLive) Color(0xFFEF4444) else Color(0xFF00E5FF),
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, height),
                    strokeWidth = 2f
                )
                // Top playhead marker triangle/circle
                drawCircle(
                    color = if (isLive) Color(0xFFEF4444) else Color(0xFF00E5FF),
                    radius = 4f,
                    center = Offset(playheadX, 4f)
                )
                drawCircle(
                    color = if (isLive) Color(0xFFEF4444) else Color(0xFF00E5FF),
                    radius = 4f,
                    center = Offset(playheadX, height - 4f)
                )
            }
        }
    }
}
