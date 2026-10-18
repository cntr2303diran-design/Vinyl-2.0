package com.vinyl.launcher.music

import kotlin.math.abs

/**
 * Tampilan satu sampul pada tumpukan. Semua jarak dalam satuan tinggi sampul agar tidak bergantung ukuran layar.
 */
data class CardLook(
    /** Posisi vertikal sampul relatif terhadap slot sampul fokus; negatif = di atas. */
    val slotY: Float,
    val scale: Float,
    /** Kemiringan ke belakang (derajat) di sumbu X; memberi kesan menumpuk di kedalaman. */
    val rotationXDeg: Float,
    val alpha: Float,
    /** Urutan tumpuk: lebih besar = lebih depan. */
    val z: Float,
)

/**
 * Mode "Tumpukan" rak piringan: sampul fokus di depan, yang berikutnya mengintip dari belakang dan sedikit ke atas;
 * sampul yang sudah lewat terlempar ke atas lalu memudar. Pager vertikal sudah meletakkan tiap halaman di
 * `offset` × tinggi dari slot fokus, jadi UI memakai `translationY = (slotY - offset) × tinggi` untuk menumpuknya.
 *
 * `offset` = indeksHalaman − posisiPagerPecahan: 0 = fokus, > 0 = berikutnya (di belakang), < 0 = sudah lewat.
 */
object StackTransform {
    /** Jumlah sampul di belakang yang masih terlihat. */
    const val VISIBLE_BEHIND = 3
    private const val PEEK = 0.09f              // seberapa jauh tiap lapis mengintip ke atas
    private const val SCALE_STEP = 0.07f
    private const val TILT_STEP_DEG = 6f
    private const val ALPHA_STEP = 0.22f
    private const val EXIT_DISTANCE = 1.15f     // sampul yang lewat terlempar sejauh ini (x tinggi)

    fun of(offset: Float): CardLook {
        val o = offset
        return if (o >= 0f) {
            val d = o.coerceAtMost(VISIBLE_BEHIND + 1f)
            // Lapis terjauh memudar mulus (VISIBLE_BEHIND → VISIBLE_BEHIND + 1) agar sampul tidak "muncul tiba-tiba"
            val entering = 1f - (o - VISIBLE_BEHIND).coerceIn(0f, 1f)
            CardLook(
                slotY = -PEEK * d,
                scale = 1f - SCALE_STEP * d,
                rotationXDeg = TILT_STEP_DEG * d,
                alpha = (1f - ALPHA_STEP * d).coerceIn(0f, 1f) * entering,
                z = -o,
            )
        } else {
            val p = (-o).coerceAtMost(1f)       // 0..1 sepanjang perjalanan keluar
            CardLook(
                slotY = -EXIT_DISTANCE * p,
                scale = 1f + 0.04f * p,
                rotationXDeg = -10f * p,
                alpha = 1f - p,
                z = p + 1f,                      // di depan sampul fokus saat keluar
            )
        }
    }

    /** True bila sampul ini tidak perlu digambar sama sekali (di luar jangkauan atau sudah transparan). */
    fun isHidden(offset: Float): Boolean = abs(offset) > VISIBLE_BEHIND + 1f || of(offset).alpha <= 0.01f
}
