package com.vinyl.launcher.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.AppInfo
import com.vinyl.launcher.data.CustomGesture
import com.vinyl.launcher.data.GestureAction
import com.vinyl.launcher.data.GestureBinding
import com.vinyl.launcher.util.GestureRecognizer
import java.util.UUID

private const val MAX_CUSTOM = 12

/** Daftar gestur gambar buatan pengguna + tombol membuat yang baru. */
@Composable
fun CustomGesturesSection(
    gestures: List<CustomGesture>,
    apps: List<AppInfo>,
    onChange: (List<CustomGesture>) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

    Text(
        "Gambar bentuk sendiri (huruf, lingkaran, centang, zig-zag…) lalu pilih aksinya. " +
            "Di layar utama, gambar bentuk itu dengan satu jari di area kosong.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    gestures.forEachIndexed { index, g ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GesturePreview(g.points, Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.name, style = MaterialTheme.typography.bodyLarge)
                    val target = if (g.binding.action == GestureAction.OPEN_APP)
                        "Buka: " + (apps.firstOrNull { it.packageName == g.binding.packageName }?.label ?: "?")
                    else g.binding.action.label()
                    Text(target, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onChange(gestures.filterIndexed { i, _ -> i != index }) }) { Text("Hapus") }
            }
            GestureRow("Aksi", g.binding, apps) { b ->
                onChange(gestures.mapIndexed { i, old -> if (i == index) old.copy(binding = b) else old })
            }
        }
    }
    if (gestures.size < MAX_CUSTOM) {
        TextButton(onClick = { creating = true }) { Text("+ Buat gestur baru (${gestures.size}/$MAX_CUSTOM)") }
    }

    if (creating) GestureEditorDialog(
        existing = gestures,
        apps = apps,
        onDismiss = { creating = false },
        onSave = { g -> onChange(gestures + g); creating = false },
    )
}

@Composable
private fun GestureEditorDialog(
    existing: List<CustomGesture>,
    apps: List<AppInfo>,
    onDismiss: () -> Unit,
    onSave: (CustomGesture) -> Unit,
) {
    val stroke = remember { mutableStateListOf<Offset>() }
    var name by remember { mutableStateOf("") }
    var binding by remember { mutableStateOf(GestureBinding(GestureAction.PLAY_PAUSE)) }

    val normalized = remember(stroke.size) { GestureRecognizer.normalize(stroke.toList()) }
    val tooShort = stroke.isNotEmpty() && normalized == null
    val twin = normalized?.let { n -> existing.firstOrNull { GestureRecognizer.score(n, it.points) > 0.86f } }
    val canSave = normalized != null && name.isNotBlank() && twin == null &&
        binding.action != GestureAction.NONE &&
        !(binding.action == GestureAction.OPEN_APP && binding.packageName == null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gestur baru") },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(CustomGesture(UUID.randomUUID().toString(), name.trim(), normalized!!, binding))
                },
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Gambar gestur dengan satu goresan jari", style = MaterialTheme.typography.labelLarge)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { stroke.clear(); stroke.add(it) },
                                onDrag = { change, _ -> change.consume(); stroke.add(change.position) },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val lineColor = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.matchParentSize()) {
                        if (stroke.size > 1) {
                            val path = Path().apply {
                                moveTo(stroke[0].x, stroke[0].y)
                                for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y)
                            }
                            drawPath(path, lineColor, style = Stroke(width = 9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                    if (stroke.isEmpty()) Text("Gambar di sini", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            tooShort -> "Goresan terlalu pendek, coba lagi."
                            twin != null -> "Terlalu mirip dengan \"${twin.name}\". Gambar bentuk yang lebih berbeda."
                            normalized != null -> "Goresan terekam. Gambar ulang bila kurang pas."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (twin != null || tooShort) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { stroke.clear() }, enabled = stroke.isNotEmpty()) { Text("Hapus gambar") }
                }
                Text("2. Beri nama", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(name, { name = it.take(24) }, singleLine = true, placeholder = { Text("mis. Huruf M") }, modifier = Modifier.fillMaxWidth())
                Text("3. Pilih aksi", style = MaterialTheme.typography.labelLarge)
                GestureRow("Aksi", binding, apps) { binding = it }
            }
        },
    )
}

/** Gambar mini goresan tersimpan. */
@Composable
fun GesturePreview(points: List<Float>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        val n = points.size / 2
        if (n < 2) return@Canvas
        val pad = size.minDimension * 0.15f
        val box = size.minDimension - pad * 2
        fun pt(i: Int) = Offset(size.width / 2f + points[2 * i] * box, size.height / 2f + points[2 * i + 1] * box)
        val path = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until n) lineTo(pt(i).x, pt(i).y)
        }
        drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Color(0xFF4CAF50), radius = 5f, center = pt(0))   // titik awal
    }
}
