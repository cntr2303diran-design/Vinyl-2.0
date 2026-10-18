package com.vinyl.launcher.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/*
 * Token desain dalam satuan Compose. NILAI-nya hanya ada di satu tempat ([Tokens], Kotlin murni agar bisa diuji);
 * berkas ini cuma mengubahnya menjadi Dp / Shape / Easing. Mengubah angka di Tokens mengubah seluruh pemakainya.
 * Pemakai saat ini: app drawer dan animasi buka/tutupnya; layar lain dimigrasikan bertahap.
 */

/** Spasi ([Tokens.Space]) dan ukuran minimum sentuh ([Tokens.A11y]). */
object Dimen {
    val xs = Tokens.Space.XS.dp
    val s = Tokens.Space.S.dp
    val m = Tokens.Space.M.dp
    val l = Tokens.Space.L.dp
    val xl = Tokens.Space.XL.dp
    val xxl = Tokens.Space.XXL.dp
    val minTouch = Tokens.A11y.MIN_TOUCH_DP.dp
}

/** Sudut membulat ([Tokens.Radius]). */
object Corner {
    val s = RoundedCornerShape(Tokens.Radius.S.dp)
    val m = RoundedCornerShape(Tokens.Radius.M.dp)
    val l = RoundedCornerShape(Tokens.Radius.L.dp)
    val xl = RoundedCornerShape(Tokens.Radius.XL.dp)
    /** Pil penuh: persen, bukan dp, supaya selalu setengah tinggi komponen. */
    val pill = RoundedCornerShape(Tokens.Radius.PILL)
}

/** Kurva easing ([Tokens.Motion]). Durasi dipakai langsung dari `Tokens.Motion.*_MS`. */
object Ease {
    private fun bezier(p: FloatArray): Easing = CubicBezierEasing(p[0], p[1], p[2], p[3])
    val standard: Easing = bezier(Tokens.Motion.EASE_STANDARD)
    val decelerate: Easing = bezier(Tokens.Motion.EASE_DECELERATE)
    val accelerate: Easing = bezier(Tokens.Motion.EASE_ACCELERATE)
}
