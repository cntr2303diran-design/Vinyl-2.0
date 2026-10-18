package com.vinyl.launcher.fx

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.vinyl.launcher.ui.theme.Tokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * Shader AGSL (Android Graphics Shading Language, Android 13+ / API 33).
 *
 * Aturan yang dipegang kedua shader:
 *  - Tidak pernah mencerahkan di luar anggaran kontras: cahaya memutihkan paling banyak Tokens.A11y.FX_PEAK (dihitung
 *    BackdropDim.withFx) dan distorsi mengalikan warna paling banyak (1 + Tokens.A11y.DIST_PEAK) kali (dihitung
 *    BackdropDim.withDist). Peredam latar memakai kedua anggaran itu agar teks putih tetap >= 4,5:1, jadi angka di sini
 *    harus berasal dari konstanta yang sama.
 *  - Semua unit dihitung relatif terhadap tinggi layar (bukan piksel mutlak) supaya terlihat sama di semua kepadatan.
 *  - Kegagalan kompilasi tidak boleh membuat aplikasi crash: factory mengembalikan null dan perangkat jatuh ke
 *    cadangan Canvas (cahaya) atau tanpa efek (distorsi). Kegagalan dicatat di [FxShaderStatus].
 */

/** Dibaca Pengaturan untuk menjelaskan kenapa efek AGSL tidak tampil walau Android 13+. */
object FxShaderStatus {
    private val _failed = MutableStateFlow(false)
    /** True bila perangkat gagal mengompilasi shader AGSL kita (driver/GPU menolak). */
    val failed: StateFlow<Boolean> = _failed.asStateFlow()
    internal fun markFailed() { _failed.value = true }
}

private const val TAG = "VinylFx"

/**
 * Distorsi kaca beralur ("fluted glass") untuk latar: bayangan latar membias melalui alur-alur vertikal yang bergelombang
 * mengikuti energi musik, plus riak refraksi dari titik sentuh terakhir. Dipasang sebagai RenderEffect pada lapisan
 * latar saja — bukan pada teks atau kontrol — sehingga tidak ada yang bergeser dari area sentuhnya.
 *
 * Pencerahan maksimum [Tokens.A11y.DIST_PEAK] sebagai PERKALIAN warna (lihat BackdropDim.withDist), jadi kontras teks
 * tetap terjamin; pada sisi gelap shader hanya menggelapkan, yang tidak mengurangi kontras.
 */
@RequiresApi(33)
class GlassDistortion private constructor(private val shader: RuntimeShader) {

    /**
     * RenderEffect dengan uniform terbaru. Uniform baru hanya terbaca bila efek dibuat ulang, jadi dipanggil tiap frame.
     * @param ripple kemajuan riak 0..1; >= 1 berarti tidak ada riak.
     */
    fun effect(width: Float, height: Float, time: Float, level: Float, touchX: Float, touchY: Float, ripple: Float): androidx.compose.ui.graphics.RenderEffect? =
        try {
            shader.setFloatUniform("size", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("level", level.coerceIn(0f, 1f))
            shader.setFloatUniform("touch", touchX, touchY)
            shader.setFloatUniform("ripple", ripple.coerceIn(0f, 1f))
            RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        } catch (e: Exception) {
            Log.w(TAG, "RenderEffect distorsi gagal: ${e.javaClass.simpleName}")
            FxShaderStatus.markFailed()
            null
        }

    companion object {
        /** Null bila shader ditolak perangkat. Pemanggil wajib sudah memastikan Android 13+ (lihat FxCompose / FxPlan.AGSL_MIN_API). */
        fun createOrNull(): GlassDistortion? {
            return try {
                val s = RuntimeShader(source())
                // Memaksa uniform diperiksa sekarang, bukan saat frame pertama
                s.setFloatUniform("size", 1f, 1f)
                s.setFloatUniform("time", 0f)
                s.setFloatUniform("level", 0f)
                s.setFloatUniform("touch", 0f, 0f)
                s.setFloatUniform("ripple", 1f)
                GlassDistortion(s)
            } catch (e: Exception) {
                Log.w(TAG, "Shader distorsi tidak bisa dikompilasi: ${e.javaClass.simpleName}")
                FxShaderStatus.markFailed()
                null
            }
        }

        // Nilai `shade` dibatasi ±DIST_PEAK; hasil dipotong ke alfa agar warna premultiplied tidak melebihi 1
        internal fun source(): String = """
            uniform shader content;
            uniform float2 size;
            uniform float time;
            uniform float level;
            uniform float2 touch;
            uniform float ripple;

            half4 main(float2 fragCoord) {
                float unit = size.y / 1000.0;
                float2 uv = fragCoord / size;
                float phase = uv.x * 14.0 * 6.2831853 + time * 0.6;
                float wave = sin(phase);
                float slope = cos(phase);
                float amp = (3.0 + 14.0 * level) * unit;
                float2 offset = float2(wave * amp, sin(uv.y * 7.0 + time * 0.5 + wave * 0.6) * amp * 0.35);
                float rim = 0.0;
                if (ripple < 1.0) {
                    float2 d = fragCoord - touch;
                    float dist = length(d) + 0.001;
                    float radius = ripple * 0.55 * size.y;
                    float width = 0.07 * size.y;
                    float ring = exp(-pow((dist - radius) / width, 2.0)) * (1.0 - ripple);
                    offset += (d / dist) * ring * 26.0 * unit;
                    rim = ring;
                }
                float2 p = clamp(fragCoord + offset, float2(0.0), size - 1.0);
                half4 c = content.eval(p);
                float shade = 1.0 + ${Tokens.A11y.DIST_PEAK} * clamp(slope * (0.4 + 0.6 * level) + rim, -1.0, 1.0);
                half3 rgb = min(c.rgb * half(shade), half3(c.a));
                return half4(rgb, c.a);
            }
        """.trimIndent()
    }
}

/**
 * Cahaya reaktif: inti lembut dan cincin yang mengembang mengikuti energi (bass). Digambar sebagai ShaderBrush di
 * Canvas, di atas peredam latar dan di bawah konten. Alfa puncak = [Tokens.A11y.FX_PEAK] (dikirim sebagai uniform,
 * satu sumber kebenaran dengan jaminan kontras).
 */
@RequiresApi(33)
class ReactiveGlow private constructor(private val shader: RuntimeShader) {
    val brush = ShaderBrush(shader)

    /** Perbarui uniform; panggil sebelum menggambar `brush` pada tiap frame. [tintArgb] = warna cahaya (ARGB). */
    fun update(width: Float, height: Float, time: Float, level: Float, tintArgb: Int, centerX: Float, centerY: Float) {
        try {
            shader.setFloatUniform("size", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("level", level.coerceIn(0f, 1f))
            shader.setFloatUniform("center", centerX, centerY)
            shader.setColorUniform("tint", tintArgb)
        } catch (e: Exception) {
            Log.w(TAG, "Uniform cahaya gagal: ${e.javaClass.simpleName}")
            FxShaderStatus.markFailed()
        }
    }

    companion object {
        fun createOrNull(): ReactiveGlow? {
            return try {
                val s = RuntimeShader(source())
                s.setFloatUniform("peak", Tokens.A11y.FX_PEAK)
                s.setFloatUniform("size", 1f, 1f)
                s.setFloatUniform("time", 0f)
                s.setFloatUniform("level", 0f)
                s.setFloatUniform("center", 0f, 0f)
                s.setColorUniform("tint", 0xFFFFFFFF.toInt())
                ReactiveGlow(s)
            } catch (e: Exception) {
                Log.w(TAG, "Shader cahaya tidak bisa dikompilasi: ${e.javaClass.simpleName}")
                FxShaderStatus.markFailed()
                null
            }
        }

        // Keluaran premultiplied: rgb = tint * a, alfa = a, dengan a <= peak
        internal fun source(): String = """
            uniform float2 size;
            uniform float time;
            uniform float level;
            uniform float2 center;
            uniform float peak;
            layout(color) uniform half4 tint;

            half4 main(float2 fragCoord) {
                float s = min(size.x, size.y);
                float2 p = (fragCoord - center) / s;
                float r = length(p);
                float breathe = 0.85 + 0.15 * sin(time * 1.7);
                float core = exp(-r * r * 6.0);
                float ringR = 0.30 + 0.10 * level;
                float ring = exp(-pow((r - ringR) / 0.07, 2.0));
                float k = (0.55 * core + 0.45 * ring * (0.4 + 0.6 * level)) * breathe * (0.35 + 0.65 * level);
                float a = clamp(k, 0.0, 1.0) * peak;
                return half4(tint.rgb * half(a), half(a));
            }
        """.trimIndent()
    }
}
