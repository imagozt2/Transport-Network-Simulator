package com.rmm.app.ui.screen.journeys

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmm.app.R
import com.rmm.app.core.networkcatalog.NetworkCatalog
import kotlin.math.min

@Composable
internal fun NetworkMapView(
    catalog: NetworkCatalog,
    modifier: Modifier = Modifier,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var translation by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val stationOutline = MaterialTheme.colorScheme.onSurface
    val stationFill = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurface
    val fallbackLineColor = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.journeys_map_content_description)
    val stationByCode = remember(catalog.stations) { catalog.stations.associateBy { it.code } }
    val lineByCode = remember(catalog.lines) { catalog.lines.associateBy { it.code } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    val nextZoom = (zoom * gestureZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    if (nextZoom == MIN_ZOOM) {
                        translation = Offset.Zero
                    } else {
                        val maxX = size.width * (nextZoom - 1f) / 2f
                        val maxY = size.height * (nextZoom - 1f) / 2f
                        translation = Offset(
                            x = (translation.x + pan.x).coerceIn(-maxX, maxX),
                            y = (translation.y + pan.y).coerceIn(-maxY, maxY),
                        )
                    }
                    zoom = nextZoom
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = translation.x
                    translationY = translation.y
                },
        ) {
            val mapScale = min(
                size.width / NetworkMapGeometry.WIDTH,
                size.height / NetworkMapGeometry.HEIGHT,
            )
            val origin = Offset(
                x = (size.width - NetworkMapGeometry.WIDTH * mapScale) / 2f,
                y = (size.height - NetworkMapGeometry.HEIGHT * mapScale) / 2f,
            )
            fun point(code: String): Offset? = NetworkMapGeometry.stations[code]?.let {
                Offset(origin.x + it.x * mapScale, origin.y + it.y * mapScale)
            }

            NetworkMapGeometry.lines.forEach { lineGeometry ->
                val apiLine = lineByCode[lineGeometry.code] ?: return@forEach
                val pathPoints = lineGeometry.stationCodes
                    .filter(stationByCode::containsKey)
                    .mapNotNull(::point)
                if (pathPoints.size < 2) return@forEach

                val path = Path().apply {
                    moveTo(pathPoints.first().x, pathPoints.first().y)
                    pathPoints.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = path,
                    color = apiLine.color.toMapColorOr(fallbackLineColor),
                    style = Stroke(
                        width = 5.dp.toPx() / zoom,
                        cap = StrokeCap.Round,
                    ),
                )
            }

            catalog.stations.forEach { station ->
                val center = point(station.code) ?: return@forEach
                val transfer = station.lineCodes.size > 1
                val outerRadius = (if (transfer) 7.dp else 5.dp).toPx() / zoom
                drawCircle(stationOutline, radius = outerRadius, center = center)
                drawCircle(
                    stationFill,
                    radius = outerRadius - 2.dp.toPx() / zoom,
                    center = center,
                )

                if (zoom >= LABEL_ZOOM) {
                    drawContext.canvas.nativeCanvas.drawText(
                        station.name,
                        center.x + 8.dp.toPx() / zoom,
                        center.y - 7.dp.toPx() / zoom,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = labelColor.toArgb()
                            textSize = with(density) { 11.sp.toPx() } / zoom
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        },
                    )
                }
            }

            NetworkMapGeometry.lines.forEach { lineGeometry ->
                val apiLine = lineByCode[lineGeometry.code] ?: return@forEach
                val lineColor = apiLine.color.toMapColorOr(fallbackLineColor)
                listOf(lineGeometry.startLabel, lineGeometry.endLabel).forEach { label ->
                    val center = Offset(
                        origin.x + label.x * mapScale,
                        origin.y + label.y * mapScale,
                    )
                    val halfWidth = 16.dp.toPx() / zoom
                    val halfHeight = 11.dp.toPx() / zoom
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        RectF(
                            center.x - halfWidth,
                            center.y - halfHeight,
                            center.x + halfWidth,
                            center.y + halfHeight,
                        ),
                        6.dp.toPx() / zoom,
                        6.dp.toPx() / zoom,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = lineColor.toArgb()
                            style = Paint.Style.FILL
                        },
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        lineGeometry.code,
                        center.x,
                        center.y + 4.dp.toPx() / zoom,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = lineColor.contrastingTextColor().toArgb()
                            textSize = with(density) { 11.sp.toPx() } / zoom
                            textAlign = Paint.Align.CENTER
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        },
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.journeys_map_gesture_hint),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    MaterialTheme.shapes.small,
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
        if (zoom > MIN_ZOOM) {
            TextButton(
                onClick = { zoom = MIN_ZOOM; translation = Offset.Zero },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Text(stringResource(R.string.journeys_map_reset))
            }
        }
    }
}

private fun String.toMapColorOr(fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(this))
} catch (_: IllegalArgumentException) {
    fallback
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

private fun Color.contrastingTextColor(): Color {
    val perceivedBrightness = red * 0.299f + green * 0.587f + blue * 0.114f
    return if (perceivedBrightness > 0.65f) Color.Black else Color.White
}

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private const val LABEL_ZOOM = 1.45f
