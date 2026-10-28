package com.vinyl.launcher.ui.film

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.data.CassetteShell
import com.vinyl.launcher.data.FilmCassette
import com.vinyl.launcher.data.FilmRules
import com.vinyl.launcher.data.UrlProblem
import com.vinyl.launcher.data.UrlResult
import com.vinyl.launcher.ui.cassette.CASSETTE_ASPECT
import com.vinyl.launcher.ui.cassette.CassetteArt
import com.vinyl.launcher.ui.cassette.CassetteLabel
import com.vinyl.launcher.ui.components.blockTouchesBehind
import com.vinyl.launcher.ui.theme.OsdFamily
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.theme.Tokens

/*
 * Isi laci kaset di meja, ditampilkan sebagai baki di bagian bawah ruang TV: kain gelap berisi kartu-kartu kaset
 * berwarna, bibir kayu dengan pegangan kuningan di tepi atasnya (muka laci yang ditarik). Ketuk kaset = masukkan ke TV;
 * ketuk kaset yang sudah di TV (bertanda DI TV) = keluarkan dan kaset musik kembali; tahan = ubah kaset film.
 */

/**
 * Satu kaset di baki. [film] terisi untuk kaset film (yang bisa diubah & dihapus); kaset bawaan aplikasi (game) null.
 * [resumeMs] = menit terakhir kaset film ini (null = mulai dari awal), ditandai di kartunya.
 */
@Immutable
internal class TrayItem(
    val tape: Tape,
    val title: String,
    val subtitle: String,
    val color: Color,
    val film: FilmCassette?,
    val resumeMs: Long? = null,
)

private val FeltTop = Color(Tokens.Film.FELT_TOP)
private val FeltBottom = Color(Tokens.Film.FELT_BOTTOM)
private val CardWidth = 148.dp

/**
 * Baki kaset. Selimut gelap di belakangnya menutup baki bila diketuk. [current] = kaset yang ada di deck sekarang.
 */
@Composable
internal fun FilmTray(
    items: List<TrayItem>,
    current: Tape,
    canAdd: Boolean,
    onPress: (Tape) -> Unit,
    onAdd: () -> Unit,
    onEdit: (FilmCassette) -> Unit,
    onRestoreDefaults: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Dipasang pada baki saja (mis. ruang taskbar & inset sistem); selimut tetap memenuhi layar. */
    sheetModifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        // Selimut: ketuk di luar baki = tutup laci
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.40f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Tutup laci", onClick = onClose),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .then(sheetModifier)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .blockTouchesBehind(),
        ) {
            DrawerLip()
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(FeltTop, FeltBottom)))) {
                // Judul bergaya OSD memperbesar sampai 1,3× saja (jendelanya sempit); petunjuk di bawahnya ikut skala penuh
                val fontScale = LocalDensity.current.fontScale
                val titleSize = (24f * fontScale.coerceAtMost(1.3f) / fontScale).sp
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "LACI KASET",
                        color = RetroColors.CreamText,
                        fontFamily = OsdFamily,
                        fontSize = titleSize,
                        lineHeight = titleSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp).semantics { heading() },
                    )
                    if (canAdd) {
                        // Pada huruf besar tombol hanya berisi ikon (label tetap dibacakan pembaca layar): teksnya tidak muat di baris judul
                        if (fontScale > 1.3f) {
                            Box(
                                Modifier
                                    .size(Tokens.A11y.MIN_TOUCH_DP.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, RetroColors.CreamTextSecondary.copy(alpha = 0.6f), CircleShape)
                                    .clickable(onClickLabel = "Tambah kaset film", role = Role.Button, onClick = onAdd),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Tambah kaset film", tint = RetroColors.CreamText, modifier = Modifier.size(22.dp))
                            }
                        } else {
                            Row(
                                Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(50))
                                    .border(1.dp, RetroColors.CreamTextSecondary.copy(alpha = 0.6f), RoundedCornerShape(50))
                                    .clickable(onClickLabel = "Tambah kaset film", role = Role.Button, onClick = onAdd)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Add, null, tint = RetroColors.CreamText, modifier = Modifier.size(18.dp))
                                Text("TAMBAH", color = RetroColors.CreamText, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            }
                        }
                    }
                    Box(
                        Modifier
                            .size(Tokens.A11y.MIN_TOUCH_DP.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = "Tutup laci", role = Role.Button, onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup laci", tint = RetroColors.CreamText)
                    }
                }
                // Petunjuk "geser" hanya muncul bila memang ada kaset di luar layar
                val listState = rememberLazyListState()
                val scrollable = listState.canScrollForward || listState.canScrollBackward
                Text(
                    TrayText.hint(current, scrollable),
                    style = MaterialTheme.typography.bodySmall,
                    color = RetroColors.CreamTextSecondary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 2.dp),
                )
                if (items.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Laci masih kosong.", color = RetroColors.CreamText, style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = onRestoreDefaults) { Text("Pulihkan kaset bawaan", color = RetroColors.Amber) }
                    }
                } else {
                    LazyRow(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items, key = { it.tape.toString() }) { item ->
                            TrayCard(
                                item = item,
                                active = item.tape == current,
                                onPress = { onPress(item.tape) },
                                onEdit = item.film?.let { f -> { onEdit(f) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Muka laci yang ditarik: kayu dengan tepi atas terang dan pegangan kuningan di tengah. */
@Composable
private fun DrawerLip() {
    Canvas(Modifier.fillMaxWidth().height(20.dp).clearAndSetSemantics { }) {
        val d = density
        drawRect(
            Brush.verticalGradient(listOf(Color(Tokens.Vhs.WOOD_LIGHT), Color(Tokens.Vhs.WOOD_MID), Color(Tokens.Vhs.WOOD_DARK))),
        )
        drawRect(Color.White.copy(alpha = 0.18f), Offset.Zero, Size(size.width, 1.5f * d))
        drawRect(Color.Black.copy(alpha = 0.55f), Offset(0f, size.height - 2f * d), Size(size.width, 2f * d))
        val hw = 64f * d
        val hh = 7f * d
        val hx = (size.width - hw) / 2f
        val hy = (size.height - hh) / 2f
        drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(hx, hy + 1.8f * d), Size(hw, hh), CornerRadius(hh / 2f))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFEBD28A), Color(0xFFB58E3A), Color(0xFF6E5420)), startY = hy, endY = hy + hh),
            Offset(hx, hy), Size(hw, hh), CornerRadius(hh / 2f),
        )
        drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(hx + hh / 2f, hy + 0.8f * d), Size(hw - hh, 1.1f * d), CornerRadius(0.5f * d))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrayCard(item: TrayItem, active: Boolean, onPress: () -> Unit, onEdit: (() -> Unit)?) {
    val label = remember(item) { CassetteLabel("tray\u0000${item.tape}", item.title, item.subtitle, null, CassetteShell.COLORED, item.color) }
    Column(
        Modifier
            .width(CardWidth)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClickLabel = if (active) "Keluarkan dari TV" else "Masukkan ke TV",
                onClick = onPress,
                onLongClickLabel = if (onEdit != null) "Ubah kaset" else null,
                onLongClick = onEdit,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = TrayText.cardDescription(item.title, item.subtitle, item.resumeMs)
                if (active) stateDescription = "Ada di TV"
                // Jalur langsung untuk pembaca layar, tanpa harus menemukan tekan-lama
                if (onEdit != null) customActions = listOf(CustomAccessibilityAction("Ubah kaset") { onEdit(); true })
            },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(CASSETTE_ASPECT)) {
            CassetteArt(label, CassetteShell.COLORED, item.color, Modifier.fillMaxSize())
            if (active) {
                Text(
                    "DI TV",
                    color = Color(Tokens.Retro.INK),
                    fontFamily = OsdFamily,
                    fontSize = 16.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(RetroColors.Amber)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            TrayText.resumeBadge(item.resumeMs)?.let { badge ->
                // Skala huruf sistem hanya memperbesar tanda ini sampai 1,3× (kartunya sempit, 148 dp)
                val fontScale = LocalDensity.current.fontScale
                val size = (14f * fontScale.coerceAtMost(1.3f) / fontScale).sp
                Text(
                    badge,
                    color = Color(Tokens.Retro.INK),
                    fontFamily = OsdFamily,
                    fontSize = size,
                    lineHeight = size,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(RetroColors.Amber)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            item.subtitle,
            color = RetroColors.CreamTextSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
    }
}

/**
 * Dialog kaset film baru ([initial] null) atau ubah kaset. Alamat diperiksa dengan [FilmRules.parseUrl] sebelum bisa
 * disimpan; yang tersimpan adalah alamat yang sudah dinormalkan (http dinaikkan ke https).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilmCassetteEditor(
    initial: FilmCassette?,
    onSave: (name: String, url: String, color: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(initial?.url.orEmpty()) }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var color by rememberSaveable { mutableIntStateOf(initial?.color ?: FilmRules.COLORS[0]) }
    var touched by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val problem: UrlProblem? = (FilmRules.parseUrl(url) as? UrlResult.Bad)?.problem
    val suggested = remember(url) { (FilmRules.parseUrl(url) as? UrlResult.Ok)?.let { FilmRules.suggestName(it.url) }.orEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Kaset film baru" else "Ubah kaset film") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.take(FilmRules.MAX_URL + 1); touched = true },
                    singleLine = true,
                    label = { Text("Alamat situs film") },
                    placeholder = { Text("archive.org") },
                    isError = touched && problem != null,
                    supportingText = { Text(if (touched && problem != null) problem.message else "Hanya alamat https. Isi situs sepenuhnya milik situs itu.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(FilmRules.MAX_NAME) },
                    singleLine = true,
                    label = { Text("Nama di label kaset") },
                    placeholder = { if (suggested.isNotEmpty()) Text(suggested) },
                    supportingText = { Text("${name.length}/${FilmRules.MAX_NAME}") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Warna cangkang", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilmRules.COLORS.forEachIndexed { i, argb ->
                        val swatch = Color(argb)
                        val selected = argb == color
                        Box(
                            Modifier
                                .size(Tokens.A11y.MIN_TOUCH_DP.dp)
                                .clip(CircleShape)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { color = argb })
                                .semantics { contentDescription = FilmRules.COLOR_NAMES.getOrElse(i) { "Warna ${i + 1}" } }
                                .padding(6.dp)
                                .background(swatch, CircleShape)
                                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Black.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(Icons.Rounded.Check, null, tint = if (swatch.luminance() > 0.4f) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                        Text(
                            if (confirmDelete) "Ketuk lagi untuk menghapus kaset ini" else "Hapus kaset ini",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                touched = true
                if (problem == null) onSave(name, url, color)
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
