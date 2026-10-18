package com.vinyl.launcher.ui.crate

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.CassetteShell
import com.vinyl.launcher.ui.cassette.CassetteArt
import com.vinyl.launcher.ui.cassette.CassetteLabel
import com.vinyl.launcher.ui.cassette.CASSETTE_ASPECT
import com.vinyl.launcher.ui.theme.RetroColors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.vinyl.launcher.music.StackNavigation
import com.vinyl.launcher.music.StackTransform
import com.vinyl.launcher.ui.theme.rememberReduceMotion
import com.vinyl.launcher.util.rememberImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Rak "Tumpukan": sampul bertumpuk di kedalaman. Sampul di depan terbesar; yang berikutnya mengintip dari belakang
 * ke arah atas, sedikit lebih kecil dan miring ke belakang. Geser ke atas untuk membuang sampul depan ke atas dan
 * memajukan yang di belakangnya; geser ke bawah untuk memanggilnya kembali. Ketuk sampul di depan untuk memutarnya,
 * ketuk sampul di belakang untuk memajukannya.
 *
 * Perhitungan bentuk tumpukan ada di [StackTransform] (murni dan teruji); berkas ini hanya menggambarnya. Hanya
 * sampul yang terlihat yang digambar, jadi antrean ratusan lagu tetap ringan.
 *
 * Pembaca layar dan switch access mendapat aksi "Sampul berikutnya/sebelumnya" sebagai pengganti gestur geser.
 *
 * @param startPage sampul yang diletakkan di depan saat pertama tampil dan setiap kali lagu yang diputar berganti.
 * @param onFocusChange dipanggil dengan indeks sampul yang paling dekat ke depan, termasuk saat pertama tampil.
 */
@Composable
internal fun StackShelf(
    items: List<ShelfItem>,
    startPage: Int,
    cover: Dp,
    onFocusChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val last = items.lastIndex
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val coverPx = with(LocalDensity.current) { cover.toPx() }
    // Posisi pecahan: 2,0 = sampul ke-3 tepat di depan; antara dua bilangan bulat = sedang bergeser
    val position = remember(items.size) { Animatable(startPage.coerceIn(0, last).toFloat()) }

    fun settle(target: Int) {
        val to = target.coerceIn(0, last).toFloat()
        scope.launch {
            if (reduceMotion) position.snapTo(to) else position.animateTo(to, spring(dampingRatio = 0.82f, stiffness = 320f))
        }
    }

    LaunchedEffect(startPage, items.size) { settle(startPage) }
    // Kunci = items (bukan hanya ukurannya): saat daftar dibangun ulang, kolektor mulai lagi dan melaporkan ulang sampul di depan,
    // jadi keterangan di bawah tumpukan tidak pernah berbeda dari sampul yang terlihat
    LaunchedEffect(items) {
        snapshotFlow { StackNavigation.focusOf(position.value, items.size) }.distinctUntilChanged().collect(onFocusChange)
    }

    val drag = rememberDraggableState { delta ->
        // Ke atas (delta negatif) = maju; aritmetika dan batasnya ada di StackNavigation (teruji)
        scope.launch { position.snapTo(StackNavigation.dragTo(position.value, delta, coverPx, items.size)) }
    }

    val p = position.value
    val focus = StackNavigation.focusOf(p, items.size)
    Box(
        modifier
            .draggable(
                state = drag,
                orientation = Orientation.Vertical,
                onDragStopped = { velocityPxPerSec ->
                    // Lemparan cepat melewati beberapa sampul; yang pelan mendarat di sampul terdekat
                    settle(StackNavigation.settleTarget(position.value, velocityPxPerSec, coverPx, items.size))
                },
            )
            .semantics {
                stateDescription = "Sampul ${focus + 1} dari ${items.size}"
                customActions = listOf(
                    CustomAccessibilityAction("Sampul berikutnya") { settle(focus + 1); true },
                    CustomAccessibilityAction("Sampul sebelumnya") { settle(focus - 1); true },
                )
            },
    ) {
        val from = (floor(p).toInt() - 1).coerceAtLeast(0)
        val to = (ceil(p).toInt() + StackTransform.VISIBLE_BEHIND + 1).coerceAtMost(last)
        for (i in from..to) {
            val offset = i - p
            if (StackTransform.isHidden(offset)) continue
            key(i) {
                StackCard(
                    item = items[i],
                    look = StackTransform.of(offset),
                    cover = cover,
                    coverPx = coverPx,
                    isFocus = i == focus,
                    onClick = { if (i == focus) items[i].play?.invoke() else settle(i) },
                )
            }
        }
    }
}

@Composable
private fun BoxScope.StackCard(
    item: ShelfItem,
    look: com.vinyl.launcher.music.CardLook,
    cover: Dp,
    coverPx: Float,
    isFocus: Boolean,
    onClick: () -> Unit,
) {
    val img = item.art ?: rememberImage(item.bitmap, item.source)
    val description = buildString {
        append(item.title)
        if (item.subtitle.isNotBlank()) append(", ").append(item.subtitle)
        if (item.relation == 0) append(", sedang diputar")
    }
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .size(cover)
            .zIndex(look.z)
            .graphicsLayer {
                cameraDistance = 14f * density
                translationY = look.slotY * coverPx
                scaleX = look.scale
                scaleY = look.scale
                // Rotasi X positif Android mengangkat sisi atas ke arah penonton; tanda dibalik agar sampul miring KE BELAKANG
                rotationX = -look.rotationXDeg
                alpha = look.alpha
                transformOrigin = TransformOrigin(0.5f, 1f)   // berdiri di dasar slot, seperti sampul di rak
            }
            .clickable(
                onClickLabel = if (!isFocus) "Majukan sampul ini" else if (item.play != null) "Putar" else null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
    ) {
        // Kaset bersandar di rak tumpukan (tema TV tabung): sampul menjadi label kaset
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .size(cover, cover / CASSETTE_ASPECT)
                .then(if (item.relation == 0) Modifier.border(2.dp, RetroColors.Amber, RoundedCornerShape(10.dp)) else Modifier),
        ) {
            CassetteArt(
                CassetteLabel(item.key, item.title, item.subtitle, img),
                shell = if (img != null) CassetteShell.PICTURE else CassetteShell.BLACK,
                accent = RetroColors.Amber,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
