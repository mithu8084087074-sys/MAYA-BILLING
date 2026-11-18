package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

object SignatureSerializer {
    /**
     * Serializes a list of normalized strokes (coordinates in 0f..1f) as:
     * "0.120,0.450;0.145,0.460|0.200,0.500;0.230,0.520"
     */
    fun encode(strokes: List<List<Pair<Float, Float>>>): String {
        return strokes
            .filter { it.isNotEmpty() }
            .joinToString("|") { stroke ->
                stroke.take(120).joinToString(";") { (x, y) ->
                    "%.3f,%.3f".format(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
                }
            }
            .take(11500)
    }

    fun decode(raw: String): List<List<Pair<Float, Float>>> {
        if (raw.isBlank()) return emptyList()
        return raw.split("|").mapNotNull { strokeStr ->
            val pts = strokeStr.split(";").mapNotNull { ptStr ->
                val parts = ptStr.split(",")
                if (parts.size == 2) {
                    val x = parts[0].toFloatOrNull()
                    val y = parts[1].toFloatOrNull()
                    if (x != null && y != null) x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f) else null
                } else {
                    null
                }
            }
            pts.takeIf { it.isNotEmpty() }
        }
    }
}

@Composable
fun SignaturePadCard(
    encodedStrokes: String,
    onStrokesChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Business Owner Signature / मालिक के हस्ताक्षर"
) {
    val strokes = remember(encodedStrokes) {
        mutableStateListOf<List<Pair<Float, Float>>>().apply {
            addAll(SignatureSerializer.decode(encodedStrokes))
        }
    }
    var activeStroke by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Draw,
                    contentDescription = "Signature Pad",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedButton(
                onClick = {
                    strokes.clear()
                    activeStroke = emptyList()
                    onStrokesChanged("")
                },
                modifier = Modifier.testTag("signature_clear_button")
            ) {
                Icon(Icons.Rounded.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp)
                )
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val w = canvasSize.width.coerceAtLeast(1).toFloat()
                        val h = canvasSize.height.coerceAtLeast(1).toFloat()
                        val pt = (offset.x / w).coerceIn(0f, 1f) to (offset.y / h).coerceIn(0f, 1f)
                        strokes.add(listOf(pt, (pt.first + 0.004f).coerceAtMost(1f) to pt.second))
                        onStrokesChanged(SignatureSerializer.encode(strokes))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val w = canvasSize.width.coerceAtLeast(1).toFloat()
                            val h = canvasSize.height.coerceAtLeast(1).toFloat()
                            activeStroke = listOf((start.x / w).coerceIn(0f, 1f) to (start.y / h).coerceIn(0f, 1f))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val w = canvasSize.width.coerceAtLeast(1).toFloat()
                            val h = canvasSize.height.coerceAtLeast(1).toFloat()
                            val pt = (change.position.x / w).coerceIn(0f, 1f) to (change.position.y / h).coerceIn(0f, 1f)
                            activeStroke = activeStroke + pt
                        },
                        onDragEnd = {
                            if (activeStroke.isNotEmpty()) {
                                strokes.add(activeStroke)
                                activeStroke = emptyList()
                                onStrokesChanged(SignatureSerializer.encode(strokes))
                            }
                        },
                        onDragCancel = {
                            activeStroke = emptyList()
                        }
                    )
                }
                .testTag("owner_signature_canvas")
        ) {
            if (strokes.isEmpty() && activeStroke.isEmpty()) {
                Text(
                    text = "Draw your signature here (उंगली से साइन करें)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Baseline guide
                val baselineY = size.height * 0.78f
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(24f, baselineY),
                    end = Offset(size.width - 24f, baselineY),
                    strokeWidth = 2f
                )

                val strokeStyle = Stroke(
                    width = 5f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )

                val allStrokes = if (activeStroke.isNotEmpty()) strokes + listOf(activeStroke) else strokes
                allStrokes.forEach { pts ->
                    if (pts.isNotEmpty()) {
                        val path = Path()
                        pts.forEachIndexed { idx, (nx, ny) ->
                            val px = nx * size.width
                            val py = ny * size.height
                            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF0C2B47),
                            style = strokeStyle
                        )
                    }
                }
            }
        }
    }
}
