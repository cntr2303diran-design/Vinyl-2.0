package com.vinyl.launcher.ui.film

/*
 * Membaca & mengendalikan <video> di halaman film lewat skrip TETAP milik kita (evaluateJavascript). Tidak ada jembatan
 * addJavascriptInterface — halaman tidak bisa memanggil kode aplikasi — dan tidak ada teks dari halaman maupun pengguna
 * yang disisipkan ke skrip (hanya bilangan bulat). Jawaban dari halaman tidak dipercaya: [parse] menolak segala yang bukan
 * empat bilangan bulat yang masuk akal.
 *
 * Murni (tanpa Android) agar diuji.
 */

/** Keadaan video yang sedang tampil di halaman. [durationMs] 0 = tidak diketahui (siaran langsung / belum dimuat). */
data class VideoStatus(val playing: Boolean, val positionMs: Long, val durationMs: Long, val ended: Boolean)

/**
 * Cuplikan [status] beserta waktu pengambilannya ([atMs], jam monotonik): posisi di antara dua pembacaan diperkirakan,
 * sehingga layar tidak perlu disusun ulang tiap detik.
 */
data class FilmSample(val status: VideoStatus, val atMs: Long) {
    fun positionAt(nowMs: Long): Long {
        val p = if (status.playing && !status.ended) status.positionMs + (nowMs - atMs).coerceAtLeast(0L) else status.positionMs
        return if (status.durationMs > 0) p.coerceIn(0L, status.durationMs) else p.coerceAtLeast(0L)
    }
}

object FilmProbe {
    /** Selisih posisi (ms) antara perkiraan dan pembacaan baru yang dianggap lompatan (geser/seek), bukan detak biasa. */
    private const val DRIFT_MS = 1_500L

    /** Batas wajar (±100 jam); angka di luar itu dianggap jawaban palsu. */
    private const val MAX_MS = 360_000_000L
    private const val MAX_REPLY = 64

    /** Potongan skrip: pilih <video> terbesar di dokumen (yang sedang main didahulukan) ke variabel `b`. */
    private const val PICK =
        "var vs=document.getElementsByTagName('video'),b=null,ba=-1;" +
            "for(var i=0;i<vs.length;i++){var v=vs[i],r=v.getBoundingClientRect(),a=r.width*r.height+(v.paused?0:1e9);if(a>ba){ba=a;b=v}}"

    /** Jawaban "putar,posisiMs,durasiMs,selesai" (mis. "1,12000,5400000,0"), atau teks kosong bila tidak ada video. */
    const val STATUS_JS =
        "(function(){try{" + PICK + "if(!b)return '';var d=b.duration;" +
            "return (b.paused?0:1)+','+Math.round(b.currentTime*1000)+','+((isFinite(d)&&d>0)?Math.round(d*1000):-1)+','+(b.ended?1:0)" +
            "}catch(e){return ''}})()"

    /** Putar bila dijeda, jeda bila main. Jawaban 1 = diputar, 2 = dijeda, 0 = tidak ada video. */
    const val TOGGLE_JS =
        "(function(){try{" + PICK + "if(!b)return 0;" +
            "if(b.paused){var p=b.play();if(p&&p.catch)p.catch(function(){});return 1}b.pause();return 2" +
            "}catch(e){return 0}})()"

    /** Jeda semua video (saat meninggalkan ruang TV). */
    const val PAUSE_JS =
        "(function(){try{var vs=document.getElementsByTagName('video');for(var i=0;i<vs.length;i++){vs[i].pause()}return 1}catch(e){return 0}})()"

    /** Lompat ke [positionMs] (dijepit ke 0..durasi). */
    fun seekToJs(positionMs: Long): String =
        "(function(ms){try{" + PICK + "if(!b)return 0;var t=ms/1000;" +
            "if(isFinite(b.duration)&&b.duration>0)t=Math.min(t,Math.max(0,b.duration-0.1));b.currentTime=Math.max(0,t);return 1" +
            "}catch(e){return 0}})(${positionMs.coerceIn(0L, MAX_MS)})"

    /** Geser [deltaMs] dari posisi sekarang (negatif = mundur). */
    fun seekByJs(deltaMs: Long): String =
        "(function(d){try{" + PICK + "if(!b)return 0;var t=b.currentTime+d/1000;" +
            "if(isFinite(b.duration)&&b.duration>0)t=Math.min(t,Math.max(0,b.duration-0.1));b.currentTime=Math.max(0,t);return 1" +
            "}catch(e){return 0}})(${deltaMs.coerceIn(-MAX_MS, MAX_MS)})"

    /**
     * Jawaban [STATUS_JS] dari evaluateJavascript (string JSON: `"1,12000,5400000,0"`; `null` bila halaman tidak menjawab).
     * Hasil null = tidak ada video yang bisa dibaca, atau jawabannya tidak masuk akal.
     */
    fun parse(raw: String?): VideoStatus? {
        if (raw == null) return null
        var s = raw.trim()
        if (s.length > MAX_REPLY + 2) return null
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') s = s.substring(1, s.length - 1)
        if (s.isEmpty() || s.length > MAX_REPLY) return null
        val parts = s.split(',')
        if (parts.size != 4) return null
        val playing = flag(parts[0]) ?: return null
        val pos = digits(parts[1]) ?: return null
        val dur = if (parts[2] == "-1") 0L else digits(parts[2]) ?: return null
        val ended = flag(parts[3]) ?: return null
        if (pos > MAX_MS || dur > MAX_MS) return null
        return VideoStatus(playing, if (dur > 0) pos.coerceAtMost(dur) else pos, dur, ended)
    }

    /**
     * Gabungkan pembacaan baru [next] ke [prev]. Mengembalikan [prev] (instance yang sama) bila tidak ada perubahan berarti
     * — pemanggil hanya memublikasikan bila hasilnya BERBEDA — dan null bila halaman tidak punya video yang terbaca.
     */
    fun update(prev: FilmSample?, next: VideoStatus?, nowMs: Long): FilmSample? {
        if (next == null) return null
        if (prev == null) return FilmSample(next, nowMs)
        val p = prev.status
        val changed = p.playing != next.playing || p.durationMs != next.durationMs || p.ended != next.ended ||
            (!next.playing && p.positionMs != next.positionMs) ||
            kotlin.math.abs(prev.positionAt(nowMs) - next.positionMs) > DRIFT_MS
        return if (changed) FilmSample(next, nowMs) else prev
    }

    private fun flag(p: String): Boolean? = when (p) { "0" -> false; "1" -> true; else -> null }

    /** Bilangan bulat tanpa tanda, 1–10 angka. */
    private fun digits(p: String): Long? =
        if (p.isNotEmpty() && p.length <= 10 && p.all { it in '0'..'9' }) p.toLongOrNull() else null
}
