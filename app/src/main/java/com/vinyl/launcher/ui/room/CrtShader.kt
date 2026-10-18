package com.vinyl.launcher.ui.room

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Lengkung tabung (barrel) dan aberasi warna tipis untuk isi layar CRT, dengan AGSL (Android 13+). Dipasang sebagai
 * RenderEffect pada lapisan isi layar saja (cover, video, judul), bukan pada bodi TV atau kontrol. Kekuatannya mengikuti
 * pengaturan "Efek tabung"; pada 0 tidak dipasang sama sekali. Di Android 10–12 (atau bila GPU menolak shader) layar tetap
 * memakai efek Canvas (scanline & tepi gelap) saja.
 *
 * Kontras: shader hanya menggeser sampel (tidak mencerahkan); judul putih tetap di atas peredam yang sama, dengan
 * pergeseran kanal maksimum ±0,25% lebar layar di tepi — tidak cukup untuk menurunkan kontras di bagian tengah huruf.
 */
@RequiresApi(33)
class CrtShader private constructor(private val shader: RuntimeShader) {

    fun effect(width: Float, height: Float, strength: Float): androidx.compose.ui.graphics.RenderEffect? = try {
        shader.setFloatUniform("size", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
        shader.setFloatUniform("k", strength.coerceIn(0f, 1f))
        RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    } catch (e: Exception) {
        Log.w(TAG, "RenderEffect CRT gagal: ${e.javaClass.simpleName}")
        null
    }

    companion object {
        private const val TAG = "VinylCrt"

        private const val SOURCE = """
            uniform shader content;
            uniform float2 size;
            uniform float k;
            half4 main(float2 p) {
                float2 uv = p / size;
                float2 c = uv - 0.5;
                float r2 = dot(c, c);
                float2 q = c * (1.0 + 0.10 * k * r2) + 0.5;
                if (q.x < 0.0 || q.x > 1.0 || q.y < 0.0 || q.y > 1.0) {
                    return half4(0.0, 0.0, 0.0, 1.0);
                }
                float2 off = c * 0.005 * k;
                half4 mid = content.eval(q * size);
                half r = content.eval((q + off) * size).r;
                half b = content.eval((q - off) * size).b;
                return half4(r, mid.g, b, mid.a);
            }
        """

        /** Null bila perangkat menolak shader (pemanggil wajib memastikan Android 13+). */
        fun createOrNull(): CrtShader? = try {
            val s = RuntimeShader(SOURCE)
            s.setFloatUniform("size", 1f, 1f)
            s.setFloatUniform("k", 0f)
            CrtShader(s)
        } catch (e: Throwable) {
            Log.w(TAG, "Shader CRT tidak bisa dikompilasi: ${e.javaClass.simpleName}")
            null
        }
    }
}
