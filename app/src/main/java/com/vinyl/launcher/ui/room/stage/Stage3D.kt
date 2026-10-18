package com.vinyl.launcher.ui.room.stage

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Matematika 3D kecil untuk "panggung" beranda (TV, meja, deck, kaset). Murni (tanpa Android/Compose) agar diuji.
 *
 * Satuan dunia: sentimeter. Sumbu: x ke kanan, y ke atas, z menjauh dari kamera (ke dalam layar).
 * Satuan layar: dp, y ke bawah.
 */

/** Titik atau vektor 3D. */
data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = V3(x * k, y * k, z * k)
    infix fun dot(o: V3): Float = x * o.x + y * o.y + z * o.z
    fun length(): Float = sqrt(this dot this)
    fun normalized(): V3 { val l = length(); return if (l > 1e-6f) this * (1f / l) else this }

    /** Putar di sekitar sumbu x (radian). Positif = bagian atas condong menjauh dari kamera. */
    fun rotateX(rad: Float): V3 {
        val c = cos(rad); val s = sin(rad)
        return V3(x, y * c - z * s, y * s + z * c)
    }

    /** Putar di sekitar sumbu y (radian). Positif = sisi kanan benda maju mendekati kamera (berlawanan jarum jam dari atas). */
    fun rotateY(rad: Float): V3 {
        val c = cos(rad); val s = sin(rad)
        return V3(x * c + z * s, y, -x * s + z * c)
    }

    companion object {
        val ZERO = V3(0f, 0f, 0f)
    }
}

/** Titik layar (dp). */
data class P2(val x: Float, val y: Float)

/** Persegi panjang layar (dp). */
data class SRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun overlaps(o: SRect, eps: Float = 0.01f): Boolean =
        left < o.right - eps && o.left < right - eps && top < o.bottom - eps && o.top < bottom - eps

    fun inside(w: Float, h: Float, eps: Float = 0.5f): Boolean =
        left >= -eps && top >= -eps && right <= w + eps && bottom <= h + eps

    fun contains(o: SRect, eps: Float = 0.5f): Boolean =
        o.left >= left - eps && o.top >= top - eps && o.right <= right + eps && o.bottom <= bottom + eps

    fun inset(dx: Float, dy: Float) = SRect(left + dx, top + dy, right - dx, bottom - dy)

    companion object {
        fun around(points: List<P2>): SRect = SRect(
            points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y },
        )
    }
}

/**
 * Kamera lurus (tanpa rotasi) dengan geser lensa: memandang searah +z dari [eye]. Bidang yang sejajar layar (z tetap)
 * — muka depan TV, panel deck, dinding — tergambar sebagai persegi panjang sejajar sumbu, sehingga isinya bisa berupa
 * composable biasa. Atap & sisi benda menyempit ke titik hilang (cx, cy), seperti fotografi arsitektur.
 *
 * @param focal panjang fokus dalam dp (makin besar, makin besar gambar)
 * @param cx titik hilang / titik utama di layar (dp)
 */
data class StageCamera(val eye: V3, val focal: Float, val cx: Float, val cy: Float) {
    fun project(p: V3): P2 {
        val d = (p.z - eye.z).coerceAtLeast(1e-3f)
        return P2(cx + focal * (p.x - eye.x) / d, cy - focal * (p.y - eye.y) / d)
    }

    /** Kebalikan [project] pada kedalaman [z]: x dunia untuk x layar [sx] (dan [unprojectY] untuk y). */
    fun unprojectX(sx: Float, z: Float): Float = eye.x + (sx - cx) * (z - eye.z).coerceAtLeast(1e-3f) / focal

    fun unprojectY(sy: Float, z: Float): Float = eye.y - (sy - cy) * (z - eye.z).coerceAtLeast(1e-3f) / focal

    /** Skala (dp per cm) pada bidang sejajar layar di kedalaman [z]. */
    fun scaleAt(z: Float): Float = focal / (z - eye.z).coerceAtLeast(1e-3f)

    /** Persegi panjang dunia di bidang z tetap → persegi panjang layar. */
    fun rectAt(x0: Float, x1: Float, y0: Float, y1: Float, z: Float): SRect {
        val a = project(V3(x0, y1, z))
        val b = project(V3(x1, y0, z))
        return SRect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
    }
}

/** Muka sebuah balok. [n] normal ke luar di ruang lokal. */
enum class Face(val n: V3) {
    FRONT(V3(0f, 0f, -1f)), BACK(V3(0f, 0f, 1f)), LEFT(V3(-1f, 0f, 0f)),
    RIGHT(V3(1f, 0f, 0f)), TOP(V3(0f, 1f, 0f)), BOTTOM(V3(0f, -1f, 0f)),
}

/**
 * Balok berukuran [w]×[h]×[d] dengan titik asal di tengah alasnya (x ∈ ±w/2, y ∈ 0..h, z ∈ ±d/2), dimiringkan
 * [tilt] di sumbu x lalu diputar [yaw] di sumbu y, kemudian digeser ke [origin]. Balok sejajar sumbu = yaw/tilt 0.
 */
data class Solid(val w: Float, val h: Float, val d: Float, val origin: V3, val yaw: Float = 0f, val tilt: Float = 0f) {

    fun toWorld(local: V3): V3 = local.rotateX(tilt).rotateY(yaw) + origin

    fun normal(face: Face): V3 = face.n.rotateX(tilt).rotateY(yaw)

    /**
     * Empat sudut muka di dunia, berurutan searah jarum jam bila dilihat dari luar muka itu (untuk muka depan: kiri-atas,
     * kanan-atas, kanan-bawah, kiri-bawah), sehingga sudut ke-0 selalu "kiri-atas" tekstur muka tersebut.
     */
    fun quad(face: Face): List<V3> {
        val x0 = -w / 2f; val x1 = w / 2f; val y0 = 0f; val y1 = h; val z0 = -d / 2f; val z1 = d / 2f
        val local = when (face) {
            Face.FRONT -> listOf(V3(x0, y1, z0), V3(x1, y1, z0), V3(x1, y0, z0), V3(x0, y0, z0))
            Face.BACK -> listOf(V3(x1, y1, z1), V3(x0, y1, z1), V3(x0, y0, z1), V3(x1, y0, z1))
            Face.LEFT -> listOf(V3(x0, y1, z1), V3(x0, y1, z0), V3(x0, y0, z0), V3(x0, y0, z1))
            Face.RIGHT -> listOf(V3(x1, y1, z0), V3(x1, y1, z1), V3(x1, y0, z1), V3(x1, y0, z0))
            Face.TOP -> listOf(V3(x0, y1, z1), V3(x1, y1, z1), V3(x1, y1, z0), V3(x0, y1, z0))
            Face.BOTTOM -> listOf(V3(x0, y0, z0), V3(x1, y0, z0), V3(x1, y0, z1), V3(x0, y0, z1))
        }
        return local.map(::toWorld)
    }

    fun center(face: Face): V3 {
        val q = quad(face)
        return V3(q.sumOf { it.x.toDouble() }.toFloat() / 4f, q.sumOf { it.y.toDouble() }.toFloat() / 4f, q.sumOf { it.z.toDouble() }.toFloat() / 4f)
    }

    /** Muka terlihat dari [eye] (backface culling). */
    fun visible(face: Face, eye: V3): Boolean = ((eye - center(face)) dot normal(face)) > 0f

    fun corners(): List<V3> {
        val xs = listOf(-w / 2f, w / 2f); val ys = listOf(0f, h); val zs = listOf(-d / 2f, d / 2f)
        return xs.flatMap { x -> ys.flatMap { y -> zs.map { z -> toWorld(V3(x, y, z)) } } }
    }

    /** Muka yang terlihat, dari yang paling jauh ke yang paling dekat (urutan gambar painter). */
    fun visibleFaces(eye: V3): List<Face> =
        Face.entries.filter { visible(it, eye) }.sortedByDescending { (center(it) - eye).length() }

    companion object {
        /** Balok sejajar sumbu dari batas-batasnya. */
        fun axisAligned(x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float): Solid =
            Solid(x1 - x0, y1 - y0, z1 - z0, V3((x0 + x1) / 2f, y0, (z0 + z1) / 2f))
    }
}

/** Pencahayaan sederhana: cahaya ambien + Lambert dari arah [toLight] (dinormalkan), hasil 0..1. */
fun lambert(normal: V3, toLight: V3, ambient: Float = 0.35f): Float {
    val l = (normal.normalized() dot toLight.normalized()).coerceAtLeast(0f)
    return (ambient + (1f - ambient) * l).coerceIn(0f, 1f)
}
