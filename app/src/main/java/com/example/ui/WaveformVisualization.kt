package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun WaveformVisualization(
    amplitude: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val barCount = 24
    val primaryColor = Color(0xFF00FFFF)
    val secondaryColor = Color(0xFFFF00FF)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barWidth = canvasWidth / (barCount * 1.8f)
        val spacing = barWidth * 0.8f
        val startX = (canvasWidth - (barCount * (barWidth + spacing))) / 2f

        for (i in 0 until barCount) {
            val x = startX + i * (barWidth + spacing)
            // Calculate dynamic bar height based on amplitude and sine wave phase
            val waveFactor = if (isActive) {
                (sin(phase + (i * 0.4f)) + 1f) / 2f
            } else {
                0.15f
            }
            val dynamicAmplitude = if (isActive) amplitude.coerceAtLeast(0.2f) else 0.1f
            val barHeight = canvasHeight * waveFactor * dynamicAmplitude * 0.9f + (canvasHeight * 0.1f)

            val y = (canvasHeight - barHeight) / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(primaryColor, secondaryColor)
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
