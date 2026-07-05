package com.local.mewgenicsradio

import android.graphics.Color.parseColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

object RadioVisualizerStyleParser {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun parse(raw: String): RadioVisualizerStyle = json.decodeFromString(raw)
}

@Composable
fun RadioVisualizer(
    style: RadioVisualizerStyle,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "radio-visualizer")
    val playbackMotionScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.35f,
        animationSpec = tween(durationMillis = 900),
        label = "playback-motion-scale",
    )
    val cycle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = ((6000 / style.normalizedMotionScale) / playbackMotionScale)
                    .toInt()
                    .coerceAtLeast(2000),
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cycle",
    )
    val playbackAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.32f,
        animationSpec = tween(durationMillis = 600),
        label = "playback-alpha",
    )

    Canvas(
        modifier = modifier.fillMaxSize(),
    ) {
        val palette = style.palette.toColors(style.normalizedContrast)
        val width = size.width
        val height = size.height
        val phase = cycle * (PI.toFloat() * 2f)
        val overlayAlpha = (0.45f + 0.55f * playbackAlpha).visualizerAlpha()

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(palette.skyTop, palette.skyBottom),
            ),
        )

        repeat(style.normalizedCloudCount) { index ->
            val baseX = (index + 0.7f) / style.normalizedCloudCount
            val orbit = ((sin(phase + index * 0.9f) + 1f) * 0.5f)
            val cloudWidth = width * (0.16f + index * 0.012f)
            val cloudHeight = height * (0.06f + (index % 3) * 0.012f)
            val x = (baseX * width + orbit * width * 0.18f - cloudWidth * 0.5f)
            val y = height * (0.12f + (index % 4) * 0.06f)
            drawRoundRect(
                color = palette.cloud.copy(alpha = (0.18f + 0.1f * playbackAlpha).visualizerAlpha()),
                topLeft = Offset(x, y),
                size = Size(cloudWidth, cloudHeight),
                cornerRadius = CornerRadius(cloudHeight, cloudHeight),
            )
            drawCircle(
                color = palette.cloud.copy(alpha = (0.15f + 0.08f * playbackAlpha).visualizerAlpha()),
                radius = cloudHeight * 0.58f,
                center = Offset(x + cloudWidth * 0.18f, y + cloudHeight * 0.42f),
            )
            drawCircle(
                color = palette.cloud.copy(alpha = (0.15f + 0.08f * playbackAlpha).visualizerAlpha()),
                radius = cloudHeight * 0.68f,
                center = Offset(x + cloudWidth * 0.52f, y + cloudHeight * 0.38f),
            )
        }

        val columns = style.normalizedCatprintColumns
        repeat(columns * 2) { index ->
            val row = index / columns
            val column = index % columns
            val drift = (cycle + index * 0.07f) % 1f
            val cx = (column + 0.5f) / columns * width + row * width * 0.08f
            val cy = height * (0.26f + row * 0.16f) + drift * height * 0.14f
            val pawRadius = width * 0.018f
            val color = palette.catprint.copy(alpha = (0.08f + 0.09f * playbackAlpha).visualizerAlpha())
            drawCircle(color = color, radius = pawRadius, center = Offset(cx, cy))
            drawCircle(color = color, radius = pawRadius * 0.42f, center = Offset(cx - pawRadius * 0.95f, cy - pawRadius * 0.95f))
            drawCircle(color = color, radius = pawRadius * 0.42f, center = Offset(cx, cy - pawRadius * 1.18f))
            drawCircle(color = color, radius = pawRadius * 0.42f, center = Offset(cx + pawRadius * 0.95f, cy - pawRadius * 0.95f))
        }

        repeat(style.normalizedAtomCount) { index ->
            val orbit = index.toFloat() / style.normalizedAtomCount
            val cx = width * (0.15f + orbit * 0.7f)
            val cy = height * (0.24f + ((index % 4) * 0.1f))
            val radius = width * (0.02f + (index % 3) * 0.007f)
            val atomAlpha = (0.24f + 0.22f * playbackAlpha).visualizerAlpha()
            drawCircle(
                color = palette.atom.copy(alpha = atomAlpha),
                radius = radius * 0.28f,
                center = Offset(
                    cx + sin(phase + index) * width * 0.01f,
                    cy + sin(phase * 1.2f + index) * height * 0.016f,
                ),
            )
            drawCircle(
                color = palette.atom.copy(alpha = atomAlpha * 0.7f),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = width * 0.003f, cap = StrokeCap.Round),
            )
            drawCircle(
                color = palette.atom.copy(alpha = atomAlpha * 0.65f),
                radius = radius * 0.72f,
                center = Offset(
                    cx + sin(phase + index * 1.3f) * radius,
                    cy + sin(phase + index * 0.8f + 1.4f) * radius,
                ),
            )
        }

        val pilePath = Path().apply {
            moveTo(0f, height)
            lineTo(0f, height * 0.72f)
            repeat(style.normalizedCatPilePeaks) { index ->
                val x = width * index / (style.normalizedCatPilePeaks - 1).coerceAtLeast(1)
                val crest = sin(phase * 0.7f + index * 0.8f)
                val y = height * (0.7f + crest * 0.05f)
                quadraticBezierTo(
                    x + width * 0.08f,
                    y - height * 0.08f,
                    x + width * 0.16f,
                    y,
                )
            }
            lineTo(width, height)
            close()
        }
        drawPath(color = palette.pile.copy(alpha = 0.95f), path = pilePath)

        repeat(style.normalizedCatPilePeaks + 2) { index ->
            val ridgeX = width * index / (style.normalizedCatPilePeaks + 1)
            val ridgeY = height * (0.74f + sin(phase + index * 0.6f) * 0.025f)
            drawLine(
                color = palette.pileAccent.copy(alpha = (0.24f + 0.18f * playbackAlpha).visualizerAlpha()),
                start = Offset(ridgeX - width * 0.05f, ridgeY),
                end = Offset(ridgeX + width * 0.06f, ridgeY - height * 0.07f),
                strokeWidth = width * 0.009f,
                cap = StrokeCap.Round,
            )
        }

        repeat(18) { index ->
            val y = height * index / 18f
            drawLine(
                color = palette.overlay.copy(alpha = (0.025f + 0.035f * playbackAlpha).visualizerAlpha()),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = height * 0.002f,
            )
        }

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    palette.skyBottom.copy(alpha = overlayAlpha * 0.65f),
                ),
                center = Offset(width * 0.5f, height * 0.5f),
                radius = width.coerceAtLeast(height) * 0.8f,
            ),
            blendMode = BlendMode.Multiply,
        )
    }
}

private fun RadioVisualizerPalette.toColors(contrast: Float): VisualizerPaletteColors =
    VisualizerPaletteColors(
        skyTop = skyTop.toComposeColor(contrast),
        skyBottom = skyBottom.toComposeColor(contrast),
        cloud = cloud.toComposeColor(contrast),
        catprint = catprint.toComposeColor(contrast),
        atom = atom.toComposeColor(contrast),
        pile = pile.toComposeColor(contrast),
        pileAccent = pileAccent.toComposeColor(contrast),
        overlay = overlay.toComposeColor(contrast),
    )

private fun String.toComposeColor(contrast: Float): Color {
    val androidColor = parseColor(this)
    val base = Color(androidColor)
    return Color(
        red = (base.red * contrast).coerceIn(0f, 1f),
        green = (base.green * contrast).coerceIn(0f, 1f),
        blue = (base.blue * contrast).coerceIn(0f, 1f),
        alpha = base.alpha,
    )
}

private data class VisualizerPaletteColors(
    val skyTop: Color,
    val skyBottom: Color,
    val cloud: Color,
    val catprint: Color,
    val atom: Color,
    val pile: Color,
    val pileAccent: Color,
    val overlay: Color,
)
