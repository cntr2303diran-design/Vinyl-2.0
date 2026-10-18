package com.vinyl.launcher.ui.console

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.audio.Reading
import com.vinyl.launcher.audio.dsp.AutoEqImport
import com.vinyl.launcher.audio.dsp.CrossfeedLevel
import com.vinyl.launcher.audio.dsp.EqBand
import com.vinyl.launcher.audio.dsp.EqEditor
import com.vinyl.launcher.audio.dsp.EqMath
import com.vinyl.launcher.audio.dsp.FilterType
import com.vinyl.launcher.audio.dsp.ParametricProfile
import com.vinyl.launcher.audio.dsp.ReplayGainMode
import com.vinyl.launcher.data.PlayerRules
import com.vinyl.launcher.player.LocalFormat
import com.vinyl.launcher.ui.theme.MonoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Panel "Pemutar bawaan" di Audio Console: jalur suara lagu yang sedang diputar (format sumber sampai keluaran ke
 * Android, semuanya diketahui pasti karena pemutarnya milik launcher) dan rantai suaranya — ReplayGain, EQ parametrik
 * (sunting pita atau impor AutoEQ), crossfeed, keluaran float, dan laju sampel USB.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerPanel(ui: ConsoleUi, actions: ConsoleActions) {
    val p = ui.player
    val path = ui.path
    val set = actions.onPlayer
    val context = LocalContext.current
    var importNote by rememberSaveable { mutableStateOf<String?>(null) }
    val importer = rememberEqImporter { result ->
        if (result == null) {
            importNote = "Berkas bukan ParametricEQ.txt (AutoEQ / Equalizer APO) yang dikenali."
        } else {
            set { PlayerRules.saveEq(it, result.profile).copy(eq = result.profile, eqEnabled = true) }
            importNote = "Profil \"${result.profile.name}\" dipakai: ${result.profile.bands.size} pita" +
                if (result.skipped.isNotEmpty()) "; ${result.skipped.size} baris dilewati" else ""
        }
    }

    ConsolePanel("Pemutar bawaan", lit = path.active) {
        // ---------- Jalur suara
        if (!path.active) {
            ReadingRow("Jalur suara", Reading.unavailable("Sedang tidak memutar. Buka Perpustakaan kaset → tab Lokal untuk memutar berkas musik di HP."))
        } else {
            val src = buildString {
                append(path.codec.ifBlank { "?" })
                if (path.sourceBits > 0 && path.lossless) append(" ${path.sourceBits}-bit")
                if (path.sourceRate > 0) append(" ${LocalFormat.khz(path.sourceRate)} kHz")
                if (path.channels > 0) append(" · ${path.channels} kanal")
            }
            ReadingRow("Sumber", Reading.known(src, if (path.lossless) "Lossless, dibaca dari berkas" else "Dibaca dari berkas"))
            if (path.decoded.isNotBlank()) ReadingRow("Hasil dekoder", Reading.known(path.decoded, "Masuk ke rantai suara"))
            val chain = listOfNotNull(
                path.replayGainDb?.let { "ReplayGain " + EqEditor.gainText(it) },
                path.eqName?.let { "EQ \"$it\"" },
                path.crossfeed?.let { "crossfeed" },
                if (path.limiter) "pembatas" else null,
            )
            ReadingRow(
                "Rantai suara",
                if (path.transparent) Reading.known("Tanpa olahan", "Sampel diteruskan apa adanya")
                else Reading.known(chain.joinToString(" → ").ifBlank { "Diolah" }, "Diproses float 32-bit"),
            )
            if (path.output.isNotBlank()) ReadingRow("Ke Android", Reading.known(path.output, "Format AudioTrack; mixer Android mengatur volume & mencampur suara lain"))
            if (path.usbRate.isNotBlank()) ReadingRow("DAC USB", Reading.reported(path.usbRate, "Hasil permintaan ke sistem"))
        }
        ConsoleDivider()

        // ---------- ReplayGain
        Text("ReplayGain (tag kenyaringan di berkas)", color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReplayGainMode.entries.forEach { m ->
                ConsoleButton(PlayerRules.label(m), { set { it.copy(replayGain = it.replayGain.copy(mode = m)) } }, selected = p.replayGain.mode == m)
            }
        }
        if (p.replayGain.mode != ReplayGainMode.OFF) {
            LineSlider(
                "Preamp ReplayGain", p.replayGain.preampDb, -15f..15f, EqEditor.gainText(p.replayGain.preampDb), "−15", "+15",
                { v -> set { it.copy(replayGain = it.replayGain.copy(preampDb = v)) } }, step = 0.5f,
            )
            LineSlider(
                "Berkas tanpa tag", p.replayGain.untaggedDb, -15f..6f, EqEditor.gainText(p.replayGain.untaggedDb), "−15", "+6",
                { v -> set { it.copy(replayGain = it.replayGain.copy(untaggedDb = v)) } }, step = 0.5f,
            )
            ConsoleSwitch(
                "Cegah clipping", p.replayGain.preventClipping, { on -> set { it.copy(replayGain = it.replayGain.copy(preventClipping = on)) } },
                detail = "Penguatan dibatasi oleh puncak yang tercatat di tag",
            )
        }
        ConsoleDivider()

        // ---------- EQ parametrik
        ConsoleSwitch(
            "EQ parametrik", p.eqEnabled, { on -> set { it.copy(eqEnabled = on) } },
            detail = "Biquad presisi 64-bit di dalam pemutar (bukan efek sistem): selalu bekerja, termasuk untuk berkas 24-bit",
        )
        EqCurve(p.eq.bands, Modifier.fillMaxWidth().height(120.dp))
        Text(
            "${p.eq.name} · preamp ${EqEditor.gainText(EqMath.effectivePreampDb(p.eq, p.autoHeadroom))}",
            color = ConsoleColors.Text, style = MaterialTheme.typography.bodyMedium, fontFamily = MonoFamily,
        )
        ProfileChips(
            saved = p.savedEqs, current = p.eq.name,
            onPick = { prof -> set { it.copy(eq = prof, eqEnabled = true) } },
            onImport = { importer() },
            onSave = { set { PlayerRules.saveEq(it, it.eq) } },
            onDelete = { name -> set { PlayerRules.deleteEq(it, name) } },
            onFlat = { set { it.copy(eq = ParametricProfile()) } },
        )
        importNote?.let { ConsoleNote(it) }
        BandEditor(p.eq) { prof -> set { it.copy(eq = ParametricProfile.clamp(prof)) } }
        ConsoleSwitch(
            "Headroom otomatis", p.autoHeadroom, { on -> set { it.copy(autoHeadroom = on) } },
            detail = "Preamp diturunkan sebesar penguatan EQ terbesar: tidak pernah clipping, tanpa pembatas",
        )
        LineSlider(
            "Preamp EQ", p.eq.preampDb, ParametricProfile.MIN_PREAMP..ParametricProfile.MAX_PREAMP, EqEditor.gainText(p.eq.preampDb), "−30", "+12",
            { v -> set { it.copy(eq = it.eq.copy(preampDb = v)) } }, step = 0.1f,
        )
        ConsoleDivider()

        // ---------- Crossfeed
        Text("Crossfeed headphone", color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsoleButton("Mati", { set { it.copy(crossfeed = null) } }, selected = p.crossfeed == null)
            CrossfeedLevel.entries.forEach { c ->
                ConsoleButton(c.label, { set { it.copy(crossfeed = c) } }, selected = p.crossfeed == c)
            }
        }
        ConsoleNote("Mencampur sedikit bass ke kanal seberang seperti mendengar speaker; untuk headphone, bukan speaker.")
        ConsoleDivider()

        // ---------- Keluaran
        ConsoleSwitch(
            "Keluaran resolusi tinggi (float 32-bit)", p.hiResOutput, { on -> set { it.copy(hiResOutput = on) } },
            detail = "Berkas 24-bit sampai ke mixer Android utuh. Mati: 16-bit dengan dither (berlaku di lagu berikutnya)",
        )
        val usbSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ConsoleSwitch(
            "Laju sampel cocok untuk DAC USB", p.usbRateMatch && usbSupported, { on -> set { it.copy(usbRateMatch = on) } },
            enabled = usbSupported,
            detail = if (usbSupported) "Android 14+: mixer USB mengikuti laju sampel lagu (44,1/96/192 kHz), tanpa resampling Android. " +
                "Bukan bit-perfect: volume & suara lain tetap dicampur."
            else "Butuh Android 14 atau lebih baru",
        )
    }
}

/** Grafik respons EQ 20 Hz–20 kHz (±15 dB), sumbu frekuensi logaritmik, selalu kiri→kanan. */
@Composable
internal fun EqCurve(bands: List<EqBand>, modifier: Modifier = Modifier) {
    val points = remember(bands) {
        FloatArray(96) { i ->
            val f = 20.0 * Math.pow(1000.0, i / 95.0)
            EqMath.responseDb(bands.filter { it.enabled }, f).toFloat()
        }
    }
    val desc = remember(bands) {
        if (bands.none { it.enabled }) "Respons EQ datar" else "Respons EQ, ${bands.count { it.enabled }} pita aktif, puncak " +
            EqEditor.gainText(points.maxOrNull() ?: 0f) + ", lembah " + EqEditor.gainText(points.minOrNull() ?: 0f)
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(modifier.semantics { contentDescription = desc }) {
            val range = 15f
            fun y(db: Float) = size.height / 2f - (db.coerceIn(-range, range) / range) * (size.height / 2f - 4f)
            // Kisi: 0 dB, ±6, ±12; garis frekuensi 100 Hz, 1 kHz, 10 kHz
            for (db in listOf(-12f, -6f, 6f, 12f)) drawLine(ConsoleColors.Line, Offset(0f, y(db)), Offset(size.width, y(db)), 1f)
            drawLine(ConsoleColors.TextSecondary.copy(alpha = 0.6f), Offset(0f, y(0f)), Offset(size.width, y(0f)), 1.5f)
            for (f in listOf(100.0, 1000.0, 10_000.0)) {
                val x = (Math.log(f / 20.0) / Math.log(1000.0)).toFloat() * size.width
                drawLine(ConsoleColors.Line, Offset(x, 0f), Offset(x, size.height), 1f)
            }
            val path = Path()
            points.forEachIndexed { i, db ->
                val x = i / (points.size - 1f) * size.width
                if (i == 0) path.moveTo(x, y(db)) else path.lineTo(x, y(db))
            }
            drawPath(path, ConsoleColors.Amber, style = Stroke(width = 2.5f))
        }
    }
}

/** Pilihan profil EQ tersimpan + impor AutoEQ + simpan/hapus. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileChips(
    saved: List<ParametricProfile>,
    current: String,
    onPick: (ParametricProfile) -> Unit,
    onImport: () -> Unit,
    onSave: (() -> Unit)?,
    onDelete: ((String) -> Unit)?,
    onFlat: (() -> Unit)?,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onFlat != null) ConsoleButton("Datar", onFlat, selected = current == "Datar")
        saved.forEach { prof -> ConsoleButton(prof.name, { onPick(prof) }, selected = prof.name == current, description = "Profil EQ ${prof.name}") }
        ConsoleButton("Impor AutoEQ…", onImport, description = "Impor berkas ParametricEQ.txt dari AutoEQ atau Equalizer APO")
        if (onSave != null && current != "Datar" && saved.none { it.name == current }) ConsoleButton("Simpan profil", onSave)
        if (onDelete != null && saved.any { it.name == current }) ConsoleButton("Hapus \"$current\"", { onDelete(current) })
    }
}

/**
 * Penyunting pita EQ parametrik: setiap pita satu baris berlabel gaya Equalizer APO; ketuk untuk menyunting jenis,
 * frekuensi (skala logaritmik), penguatan, dan Q.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandEditor(profile: ParametricProfile, onChange: (ParametricProfile) -> Unit) {
    var open by rememberSaveable { mutableIntStateOf(-1) }
    val bands = profile.bands
    fun update(i: Int, b: EqBand) = onChange(profile.copy(name = edited(profile.name), bands = bands.mapIndexed { j, x -> if (j == i) b else x }))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        bands.forEachIndexed { i, b ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = if (open == i) "Tutup" else "Sunting") { open = if (open == i) -1 else i },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${i + 1}. " + EqEditor.label(b) + if (!b.enabled) " (mati)" else "",
                    color = if (b.enabled) ConsoleColors.Text else ConsoleColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium, fontFamily = MonoFamily,
                )
            }
            if (open == i) {
                Column(Modifier.padding(start = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterType.entries.forEach { t ->
                            ConsoleButton(EqEditor.short(t), { update(i, b.copy(type = t)) }, selected = b.type == t, description = typeName(t))
                        }
                    }
                    LineSlider(
                        "Frekuensi", EqEditor.freqToSlider(b.freq), 0f..1f, EqEditor.freqText(b.freq), "20 Hz", "20 kHz",
                        { v -> update(i, b.copy(freq = EqEditor.sliderToFreq(v))) }, step = 0.002f,
                    )
                    if (b.type != FilterType.LOW_PASS && b.type != FilterType.HIGH_PASS) {
                        LineSlider("Penguatan", b.gainDb, -15f..15f, EqEditor.gainText(b.gainDb), "−15", "+15", { v -> update(i, b.copy(gainDb = v)) }, step = 0.1f)
                    }
                    LineSlider(
                        "Q", b.q, 0.1f..10f, "%.2f".format(java.util.Locale.ROOT, b.q).replace('.', ','), "lebar", "sempit",
                        { v -> update(i, b.copy(q = v)) }, step = 0.01f,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConsoleButton(if (b.enabled) "Matikan pita" else "Nyalakan pita", { update(i, b.copy(enabled = !b.enabled)) })
                        ConsoleButton("Hapus pita", {
                            onChange(profile.copy(name = edited(profile.name), bands = bands.filterIndexed { j, _ -> j != i }))
                            open = -1
                        })
                    }
                }
            }
        }
        if (bands.size < ParametricProfile.MAX_BANDS) {
            ConsoleButton("Tambah pita", {
                onChange(profile.copy(name = edited(profile.name), bands = bands + EqEditor.newBand(bands)))
                open = bands.size
            })
        }
    }
}

/** Profil yang disunting bukan lagi profil impor aslinya. */
private fun edited(name: String): String = when {
    name == "Datar" -> "Kustom"
    name.endsWith(" (ubah)") || name == "Kustom" -> name
    else -> "$name (ubah)".take(60)
}

private fun typeName(t: FilterType) = when (t) {
    FilterType.PEAK -> "Peaking"
    FilterType.LOW_SHELF -> "Low shelf"
    FilterType.HIGH_SHELF -> "High shelf"
    FilterType.LOW_PASS -> "Low pass"
    FilterType.HIGH_PASS -> "High pass"
}

/**
 * Pemilih berkas ParametricEQ.txt (AutoEQ / Equalizer APO) lewat pemilih dokumen Android. Berkas dibaca sekali
 * (maks. 32 KB), tidak disimpan; hasilnya profil EQ. Nama profil dari nama berkas tanpa " ParametricEQ.txt".
 */
@Composable
internal fun rememberEqImporter(onResult: (AutoEqImport.Result?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { readProfile(context, uri) }
            onResult(result)
        }
    }
    return { launcher.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) }
}

private fun readProfile(context: Context, uri: Uri): AutoEqImport.Result? = runCatching {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }.orEmpty()
    val text = context.contentResolver.openInputStream(uri)?.use { input ->
        val buf = ByteArray(AutoEqImport.MAX_CHARS + 1)
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        if (n > AutoEqImport.MAX_CHARS) return null
        String(buf, 0, n, Charsets.UTF_8)
    } ?: return null
    val profileName = name.substringBeforeLast('.').removeSuffix(" ParametricEQ").removeSuffix("ParametricEQ").trim().ifBlank { "AutoEQ" }
    AutoEqImport.parse(text, profileName)
}.getOrNull()
