package com.vinyl.launcher.ui.room.stage

/** Benda tambahan di ruangan yang punya tempat sendiri di [StageLayout.Frame.props]. Urutan = prioritas tempat. */
enum class Prop { CLOCK, RADIO, PHONE }

/** Tempat satu benda: kotak gambar dan area sentuh (≥ 48 × 48 dp, menutupi kotak gambar), keduanya dalam dp layar. */
class PropSpot(val bounds: SRect, val hit: SRect)

/**
 * Penempatan benda-benda tambahan (jam dinding, radio, telepon putar) di ruangan (murni, teruji). Tiap benda diberi
 * beberapa kandidat tempat; yang pertama muat dipakai, dan bila tidak ada yang muat benda itu TIDAK ditempatkan (pengguna
 * tetap bisa memakai fungsinya dari Pengaturan → Benda ruangan). Sebuah tempat dianggap muat bila:
 *  - seluruh area sentuhnya ada di dalam layar (jarak [StageLayout.SIDE] dari tepi), di bawah teks OSD atas
 *    ([wallTop]) dan di atas pita OSD bawah ([floorLimit]);
 *  - jaraknya ke benda lain yang sudah ada (TV, kaset, laci, deck, lampu, dan benda tambahan yang lebih dulu ditempatkan)
 *    minimal [GAP] dp;
 *  - untuk benda meja: berdiri di atas papan meja, tidak menggantung di luar tepi kiri-kanannya.
 * Benda meja digambar tegak menghadap pemirsa seperti TV dan kaset, dengan dasarnya di garis papan meja.
 */
object StageProps {
    const val MIN_TOUCH = 48f
    const val GAP = 8f
    /** Kedalaman (cm) tempat benda meja berdiri: sejajar bagian tengah TV. */
    private const val TABLE_Z = StageLayout.TV_FRONT_Z + 14f

    const val CLOCK_CM = 26f
    const val RADIO_CM = 22f
    const val PHONE_CM = 22f

    /**
     * @param wallTop batas atas benda (di bawah teks OSD sudut atas)
     * @param floorLimit batas bawah benda (atas pita OSD bawah)
     * @param tv kotak TV termasuk atapnya; [cassette] area kaset; [avoid] benda lain yang tidak boleh dekat
     */
    fun place(
        w: Float,
        wallTop: Float,
        floorLimit: Float,
        cam: StageCamera,
        standHalfW: Float,
        tv: SRect,
        cassette: SRect,
        avoid: List<SRect>,
    ): Map<Prop, PropSpot> {
        val placed = LinkedHashMap<Prop, PropSpot>()
        val taken = ArrayList<SRect>(avoid)
        taken += tv
        taken += cassette

        fun fits(hit: SRect): Boolean {
            if (hit.left < StageLayout.SIDE || hit.right > w - StageLayout.SIDE || hit.top < wallTop || hit.bottom > floorLimit) return false
            val padded = SRect(hit.left - GAP, hit.top - GAP, hit.right + GAP, hit.bottom + GAP)
            return taken.none { padded.overlaps(it) }
        }

        fun accept(prop: Prop, bounds: SRect, hit: SRect): Boolean {
            if (!fits(hit)) return false
            placed[prop] = PropSpot(bounds, hit)
            taken += hit
            return true
        }

        // ---------- Jam dinding: kanan atas dinding, kembaran lampu di kiri atas
        val sw = cam.scaleAt(StageLayout.WALL_Z)
        val clock = (CLOCK_CM * sw).coerceIn(MIN_TOUCH + 4f, 84f)
        val clockLeft = w - StageLayout.LAMP_LEFT - clock
        for (dy in listOf(0f, clock * 0.4f)) {
            val b = SRect(clockLeft, wallTop + dy, clockLeft + clock, wallTop + dy + clock)
            if (accept(Prop.CLOCK, b, b)) break
        }

        // ---------- Benda meja: dasar di garis papan meja, kandidat kiri-kanan TV dan kaset
        val st = cam.scaleAt(StageLayout.TV_FRONT_Z)
        val tableY = cam.project(V3(0f, StageLayout.STAND_H, TABLE_Z)).y
        val slabLeft = cam.project(V3(-(standHalfW - 6f), StageLayout.STAND_H, TABLE_Z)).x
        val slabRight = cam.project(V3(standHalfW - 6f, StageLayout.STAND_H, TABLE_Z)).x

        fun table(prop: Prop, width: Float, height: Float, order: List<Slot>) {
            val gap = GAP * 1.5f
            val leftNear = tv.left - gap - width
            val rightNear = cassette.right + gap
            for (slot in order) {
                val x = when (slot) {
                    Slot.LEFT_NEAR -> leftNear
                    Slot.LEFT_FAR -> leftNear - width - gap
                    Slot.RIGHT_NEAR -> rightNear
                    Slot.RIGHT_FAR -> rightNear + width + gap
                }
                val b = SRect(x, tableY - height, x + width, tableY)
                if (b.left < slabLeft || b.right > slabRight) continue
                if (accept(prop, b, growTouch(b))) return
            }
        }

        val radioW = (RADIO_CM * st).coerceIn(64f, 120f)
        table(Prop.RADIO, radioW, radioW * 0.6f, listOf(Slot.LEFT_NEAR, Slot.LEFT_FAR, Slot.RIGHT_NEAR, Slot.RIGHT_FAR))
        val phoneW = (PHONE_CM * st).coerceIn(72f, 120f)
        table(Prop.PHONE, phoneW, phoneW * 0.8f, listOf(Slot.LEFT_FAR, Slot.LEFT_NEAR, Slot.RIGHT_NEAR, Slot.RIGHT_FAR))
        return placed
    }

    private enum class Slot { LEFT_NEAR, LEFT_FAR, RIGHT_NEAR, RIGHT_FAR }

    /** Area sentuh ≥ 48 × 48 dp: kotak yang lebih kecil diperbesar ke samping dan ke ATAS (dasarnya menyentuh papan meja). */
    private fun growTouch(r: SRect): SRect {
        val gw = (MIN_TOUCH - r.width).coerceAtLeast(0f)
        val gh = (MIN_TOUCH - r.height).coerceAtLeast(0f)
        return SRect(r.left - gw / 2f, r.top - gh, r.right + gw / 2f, r.bottom)
    }
}
