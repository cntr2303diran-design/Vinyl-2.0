package com.vinyl.launcher.ui.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.data.AppInfo
import com.vinyl.launcher.data.AppShortcut
import com.vinyl.launcher.data.ContactHit
import com.vinyl.launcher.data.PlayRecord
import com.vinyl.launcher.drawer.AlphaIndex
import com.vinyl.launcher.drawer.AppCategory
import com.vinyl.launcher.drawer.BadgeCounter
import com.vinyl.launcher.drawer.SystemSetting
import com.vinyl.launcher.ui.theme.Accent
import com.vinyl.launcher.ui.theme.AccentOn
import com.vinyl.launcher.ui.theme.Corner
import com.vinyl.launcher.ui.theme.Dimen
import com.vinyl.launcher.ui.theme.Hairline
import com.vinyl.launcher.ui.theme.TextPrimary
import com.vinyl.launcher.ui.theme.TextSecondary
import kotlin.math.roundToInt

/*
 * Komponen app drawer. Semua teks di atas latar cover memakai TextPrimary/TextSecondary saja (tersier tidak dijamin
 * kontrasnya di atas cover; lihat BackdropDim) dan semua permukaan "kaca" berupa lapisan GELAP tembus pandang —
 * lapisan putih akan mengangkat kecerahan latar dan merusak jaminan kontras itu.
 */

private val DarkGlassFill = Color.Black.copy(alpha = 0.28f)

private fun Modifier.darkGlass(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.clip(shape).background(DarkGlassFill, shape).border(0.8.dp, Hairline, shape)

// ------------------------------------------------------------------------------------------ pencarian

@Composable
internal fun SearchField(value: String, onChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = Dimen.minTouch).darkGlass(Corner.pill).padding(start = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(SEARCH_HINT, color = TextSecondary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= MAX_QUERY) onChange(it) },
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(TextPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = SEARCH_HINT },
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier
                    .size(Dimen.minTouch)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Hapus pencarian", role = Role.Button) { onChange("") }
                    .semantics { contentDescription = "Hapus pencarian" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp)) }
        } else Spacer(Modifier.width(18.dp))
    }
}

internal const val MAX_QUERY = 80
private const val SEARCH_HINT = "Cari aplikasi, lagu, dan pengaturan"

// ------------------------------------------------------------------------------------------ kategori

@Composable
internal fun CategoryChips(
    categories: List<Pair<AppCategory, Int>>,
    total: Int,
    selected: AppCategory?,
    onSelect: (AppCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = Dimen.xxl),
        horizontalArrangement = Arrangement.spacedBy(Dimen.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "all") { CategoryChip("Semua", total, selected == null) { onSelect(null) } }
        items(categories, key = { it.first.name }) { (category, count) ->
            CategoryChip(category.title, count, selected == category) { onSelect(category) }
        }
    }
}

@Composable
private fun CategoryChip(title: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    // Area sentuh 48dp; pil yang terlihat lebih ramping di tengahnya
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $count aplikasi" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .clip(Corner.pill)
                .background(if (selected) Accent else DarkGlassFill)
                .border(0.8.dp, if (selected) Accent else Hairline, Corner.pill)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                "$title  $count",
                color = if (selected) AccentOn else TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------ judul bagian

@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = TextSecondary,
        modifier = modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp).semantics { heading() },
    )
}

// ------------------------------------------------------------------------------------------ ikon aplikasi

private enum class TileMenu { NONE, MAIN, CATEGORY }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppTile(
    app: AppInfo,
    badge: Int,
    canAddToDock: Boolean,
    actions: DrawerActions,
    onClick: () -> Unit,
) {
    var menu by remember { mutableStateOf(TileMenu.NONE) }
    var shortcuts by remember { mutableStateOf<List<AppShortcut>?>(null) }
    val haptics = LocalHapticFeedback.current
    val twoLines = LocalDensity.current.fontScale >= 1.3f

    // Dimuat ulang setiap menu dibuka: pintasan dinamis berubah-ubah (daftar lama tetap tampil selagi memuat)
    LaunchedEffect(menu == TileMenu.MAIN) {
        if (menu == TileMenu.MAIN) shortcuts = actions.shortcuts(app)
    }

    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Corner.m)
                .combinedClickable(
                    onClickLabel = "Buka",
                    onClick = onClick,
                    onLongClickLabel = "Menu aplikasi",
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = TileMenu.MAIN },
                )
                .semantics(mergeDescendants = true) {
                    if (badge > 0) stateDescription = "$badge notifikasi"
                    // Jalur langsung untuk pembaca layar, tanpa harus menemukan tekan-lama
                    customActions = buildList {
                        add(CustomAccessibilityAction("Info aplikasi") { actions.info(app); true })
                        add(CustomAccessibilityAction("Uninstall") { actions.uninstall(app); true })
                        if (canAddToDock) add(CustomAccessibilityAction("Tambahkan ke taskbar") { actions.addToDock(app); true })
                        add(CustomAccessibilityAction("Sembunyikan aplikasi") { actions.hide(app); true })
                        add(CustomAccessibilityAction("Pindahkan ke kategori lain") { menu = TileMenu.CATEGORY; true })
                    }
                }
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(64.dp))
                if (badge > 0) {
                    BadgeDot(badge, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp))
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(
                app.label,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = if (twoLines) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        DropdownMenu(expanded = menu != TileMenu.NONE, onDismissRequest = { menu = TileMenu.NONE }) {
            when (menu) {
                TileMenu.MAIN -> {
                    val list = shortcuts.orEmpty()
                    list.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = s.icon?.let { icon -> { Image(icon, contentDescription = null, modifier = Modifier.size(24.dp)) } },
                            onClick = { menu = TileMenu.NONE; actions.startShortcut(s) },
                        )
                    }
                    if (list.isNotEmpty()) HorizontalDivider()
                    DropdownMenuItem(text = { Text("Info aplikasi") }, onClick = { menu = TileMenu.NONE; actions.info(app) })
                    DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = TileMenu.NONE; actions.uninstall(app) })
                    if (canAddToDock) DropdownMenuItem(text = { Text("Tambahkan ke taskbar") }, onClick = { menu = TileMenu.NONE; actions.addToDock(app) })
                    DropdownMenuItem(text = { Text("Sembunyikan aplikasi") }, onClick = { menu = TileMenu.NONE; actions.hide(app) })
                    DropdownMenuItem(text = { Text("Pindahkan ke kategori…") }, onClick = { menu = TileMenu.CATEGORY })
                }
                TileMenu.CATEGORY -> {
                    DropdownMenuItem(
                        text = { Text("Otomatis (${app.category.title})") },
                        onClick = { menu = TileMenu.NONE; actions.setCategory(app, null) },
                    )
                    HorizontalDivider()
                    AppCategory.entries.forEach { c ->
                        DropdownMenuItem(text = { Text(c.title) }, onClick = { menu = TileMenu.NONE; actions.setCategory(app, c) })
                    }
                }
                TileMenu.NONE -> Unit
            }
        }
    }
}

/** Lencana jumlah notifikasi. Teks di atas aksen memakai pasangan ACCENT/ACCENT_ON yang kontrasnya dibuktikan ContrastTest. */
@Composable
private fun BadgeDot(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(CircleShape)
            .background(Accent)
            .padding(horizontal = 6.dp)
            // Hitungan dibacakan oleh ikon induknya ("N notifikasi"), bukan sebagai angka lepas
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(BadgeCounter.format(count), color = AccentOn, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ------------------------------------------------------------------------------------------ baris hasil

@Composable
private fun ResultRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Corner.s)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(Corner.s).background(DarkGlassFill), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun SongRow(record: PlayRecord, onClick: () -> Unit) =
    ResultRow(Icons.Rounded.MusicNote, record.title, record.artist, onClick)

@Composable
internal fun ContactRow(hit: ContactHit, onClick: () -> Unit) =
    ResultRow(Icons.Rounded.Person, hit.name, null, onClick)

@Composable
internal fun SettingRow(setting: SystemSetting, onClick: () -> Unit) =
    ResultRow(Icons.Rounded.Settings, setting.title, "Pengaturan sistem", onClick)

// ------------------------------------------------------------------------------------------ jump bar A–Z

/**
 * Indeks abjad di tepi layar: geser atau ketuk huruf untuk melompat. Huruf individual terlalu kecil untuk target
 * sentuh 48dp (27 huruf), maka bagi pembaca layar seluruh bilah dijadikan SATU kontrol yang bisa disesuaikan
 * (geser naik/turun) dan bagian lain dari layar (kolom cari, chip kategori) tetap menjadi jalur utama.
 */
@Composable
internal fun JumpBar(
    present: Set<Char>,
    current: Char?,
    onJump: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    // pointerInput hanya dimulai ulang bila kuncinya berubah; tanpa ini lambda lama (dengan tata letak lama) tertangkap
    val currentOnJump by rememberUpdatedState(onJump)
    val letters = AlphaIndex.LETTERS
    val presentList = remember(present) { letters.filter { it in present } }
    // Ukuran huruf tidak ikut skala font: bilah ini dekoratif dan 27 huruf tidak muat bila ikut membesar
    val letterSize = with(LocalDensity.current) { 11.dp.toSp() }
    var last by remember { mutableStateOf<Char?>(null) }

    fun jumpAt(y: Float, height: Float) {
        if (height <= 0f) return
        val letter = letters[(y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)]
        if (letter != last) {
            last = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnJump(letter)
        }
    }

    Column(
        modifier
            .width(32.dp)
            .fillMaxHeight()
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    jumpAt(down.position.y, size.height.toFloat())
                    drag(down.id) { change ->
                        jumpAt(change.position.y, size.height.toFloat())
                        change.consume()
                    }
                    last = null
                }
            }
            .clearAndSetSemantics {
                contentDescription = "Indeks abjad"
                stateDescription = current?.let { "Huruf $it" } ?: "Belum dipilih"
                if (presentList.size >= 3) {
                    val idx = presentList.indexOf(current).coerceAtLeast(0)
                    progressBarRangeInfo = ProgressBarRangeInfo(idx.toFloat(), 0f..presentList.lastIndex.toFloat(), presentList.size - 2)
                    setProgress("Lompat ke huruf") { value ->
                        presentList.getOrNull(value.roundToInt())?.let { currentOnJump(it); true } ?: false
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { c ->
            val isCurrent = c == current
            Text(
                c.toString(),
                fontSize = letterSize,
                fontWeight = if (c in present) FontWeight.Bold else FontWeight.Normal,
                // Huruf aktif = pil aksen dengan teks AccentOn (pasangan yang kontrasnya dibuktikan ContrastTest);
                // teks aksen langsung di atas latar cover tidak dijamin terbaca
                color = if (isCurrent) AccentOn else if (c in present) TextPrimary else TextSecondary,
                modifier = if (isCurrent) Modifier.background(Accent, CircleShape).padding(horizontal = 4.dp) else Modifier,
            )
        }
    }
}
