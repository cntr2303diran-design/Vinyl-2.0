package com.vinyl.launcher.ui.home

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.data.AppInfo
import com.vinyl.launcher.data.ThemePreset
import com.vinyl.launcher.ui.components.sheet
import com.vinyl.launcher.ui.settings.ControlCard
import com.vinyl.launcher.ui.settings.GestureControls
import com.vinyl.launcher.ui.settings.HomeSection
import com.vinyl.launcher.ui.settings.HomeSectionControls
import com.vinyl.launcher.ui.settings.SectionTitle
import com.vinyl.launcher.ui.settings.SettingsActivity
import com.vinyl.launcher.ui.theme.Accent
import com.vinyl.launcher.ui.theme.AccentOn
import com.vinyl.launcher.ui.theme.Corner
import com.vinyl.launcher.ui.theme.MonoFamily
import com.vinyl.launcher.ui.theme.RetroColors

/** Tab panel editor: bagian gaya + gestur + preset. */
private val TABS = HomeSection.entries.map { it.title } + listOf("Gestur", "Preset")

/**
 * Panel editor home screen bergaya "service menu" TV: judul dengan baris status mono amber, tab menu huruf kapital
 * mono (yang terpilih bertanda ▸ di atas amber), isi tetap kontrol standar agar mudah dipakai.
 * Semua perubahan gaya langsung tampil di layar karena HomeScreen menggambar dari draft.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeEditorPanel(vm: HomeViewModel, landscape: Boolean, apps: List<AppInfo>) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var naming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ThemePreset?>(null) }
    val home = draft ?: return

    val shape = if (landscape) RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)
    else RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    Column(
        (if (landscape) Modifier.width(EDITOR_WIDTH).fillMaxHeight() else Modifier.fillMaxWidth().height(EDITOR_HEIGHT_PORTRAIT))
            .sheet(shape)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    if (landscape) WindowInsetsSides.Vertical + WindowInsetsSides.End
                    else WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
    ) {
        if (!landscape) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(40.dp, 5.dp)
                    .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))
            )
        }
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // Baris status service menu (hiasan; judul di bawahnya yang dibacakan)
                Text(
                    "SERVICE MENU · ${TABS[tab].uppercase()}",
                    color = RetroColors.Amber,
                    fontFamily = MonoFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics { },
                )
                Text("Edit beranda", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            }
            TextButton(onClick = vm::cancelEdit) { Text("Batal") }
            Button(onClick = vm::saveEdit) { Text("Simpan") }
        }
        // Tab pil yang bisa digeser horizontal
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TABS.forEachIndexed { i, t ->
                val sel = i == tab
                // Teks di atas amber memakai AccentOn (pasangan yang kontrasnya dibuktikan ContrastTest)
                Text(
                    (if (sel) "▸ " else "") + t.uppercase(),
                    color = if (sel) AccentOn else MaterialTheme.colorScheme.onSurface,
                    fontFamily = MonoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (sel) Accent else Color.White.copy(alpha = 0.07f))
                        .selectable(selected = sel, role = Role.Tab) { tab = i }
                        .semantics { contentDescription = t }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            when {
                tab < HomeSection.entries.size ->
                    HomeSectionControls(
                        HomeSection.entries[tab], home, settings.retro,
                        onChange = { new -> vm.updateDraft { new } },
                        // Gaya TV langsung tersimpan (bukan bagian draf beranda), seperti gestur
                        onRetro = { r -> vm.updateSettings { it.copy(retro = r) } },
                    )

                TABS[tab] == "Gestur" -> {
                    SectionTitle("Gestur layar utama")
                    ControlCard {
                        GestureControls(settings.gestures, apps) { g -> vm.updateSettings { it.copy(gestures = g) } }
                    }
                    Text(
                        "Gestur langsung tersimpan (tidak perlu menekan Simpan).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 18.dp, top = 8.dp),
                    )
                }

                else -> {
                    SectionTitle("Preset tema")
                    ControlCard {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            settings.presets.forEach { p ->
                                Text(
                                    p.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .combinedClickable(
                                            onClickLabel = "Terapkan", onClick = { vm.applyPreset(p) },
                                            onLongClickLabel = "Hapus preset", onLongClick = { deleting = p },
                                        )
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                )
                            }
                            Text(
                                "+ Simpan gaya ini",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                    .clickable(role = Role.Button) { naming = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        }
                        Text(
                            if (settings.presets.isEmpty()) "Belum ada preset. Simpan gaya saat ini untuk dipakai lagi nanti."
                            else "Ketuk untuk menerapkan, tekan lama untuk menghapus.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Pengaturan lainnya",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Corner.l)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { context.startActivity(Intent(context, SettingsActivity::class.java)) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    if (naming) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Simpan preset") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Nama preset") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.savePreset(name); naming = false }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Batal") } },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Hapus preset \"${p.name}\"?") },
            confirmButton = { TextButton(onClick = { vm.deletePreset(p.id); deleting = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } },
        )
    }
}
