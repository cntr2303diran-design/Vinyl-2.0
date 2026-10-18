package com.vinyl.launcher.audio

/*
 * Pengurai laporan diagnostik sistem (dumpsys) untuk Akses lanjutan. Murni (tanpa Android) dan toleran: baris yang
 * tidak dikenali dilewati, field yang tidak ditemukan menjadi null → "Tidak tersedia", bukan tebakan atau crash.
 *
 * Format diambil dari kode sumber AOSP (cabang main, diperiksa Oktober 2026):
 *  - dumpsys audio → PlaybackActivityMonitor mencetak tiap pemutar lewat AudioPlaybackConfiguration.dump():
 *      " AudioPlaybackConfiguration piid:<n> deviceIds:[..] type:<t> u/pid:<uid>/<pid> state:<started|paused|..>
 *        attr:AudioAttributes: usage=<USAGE_..> content=<..> ... sessionId:<n> mutedState:<..>  FormatInfo{isSpatialized=..,
 *        channelMask=0x.., sampleRate=<hz>}"
 *  - dumpsys bluetooth_manager → A2dpStateMachine.dump():
 *      "=== A2dpStateMachine for <alamat> (Active) ===" lalu " mCodecConfig: {codecName:<nama>,mCodecType:..,
 *        mCodecPriority:..,mSampleRate:0x..(<hz|hz>),mBitsPerSample:0x..(<bit>),mChannelMode:0x..(<mode>),...}"
 *  - dumpsys media.audio_flinger → ThreadBase::dump/dumpBase_l:
 *      "Output thread <ptr>, name <nama>, tid <n>, type <n> (<MIXER|DIRECT|..>):" lalu baris " Sample rate: <hz> Hz",
 *      " HAL format: 0x<x> (<AUDIO_FORMAT_..>)", " Output devices: <..> (<..>)", " Standby: <yes|no>"
 * OEM (mis. Samsung) bisa mengubah format ini; yang tidak cocok dilaporkan tidak tersedia.
 */
object DumpParsers {

    /** Satu pemutar audio dari dumpsys audio. [sampleRate] 0 = tidak dilaporkan. */
    data class Player(val uid: Int, val sessionId: Int, val state: String, val usage: String, val sampleRate: Int)

    private val APC = Regex("""AudioPlaybackConfiguration\s+piid:""")
    private val UID = Regex("""u/pid:(\d+)/(-?\d+)""")
    private val STATE = Regex("""\bstate:(\w+)""")
    private val USAGE = Regex("""\busage=(\w+)""")
    private val SESSION = Regex("""\bsessionId:(-?\d+)""")
    private val FORMAT_RATE = Regex("""FormatInfo\{[^}]*\bsampleRate=(\d+)""")

    fun players(text: String): List<Player> = text.lineSequence()
        .filter { APC.containsMatchIn(it) }
        .mapNotNull { line ->
            val uid = UID.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val session = SESSION.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            Player(
                uid = uid,
                sessionId = session,
                state = STATE.find(line)?.groupValues?.get(1).orEmpty(),
                usage = USAGE.find(line)?.groupValues?.get(1).orEmpty(),
                sampleRate = FORMAT_RATE.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            )
        }
        .toList()

    /**
     * Sesi musik yang bisa diberi EQ: pemutar media yang belum dilepas, dengan nomor sesi sah. Satu sesi per nomor.
     */
    fun musicSessions(players: List<Player>): List<Player> = players
        .filter { it.usage == "USAGE_MEDIA" && it.sessionId > 0 && it.state != "released" }
        .distinctBy { it.sessionId }

    /** Codec Bluetooth A2DP yang sedang dipakai. Daftar berisi lebih dari satu nilai bila sistem mencetak kemampuan gabungan. */
    data class Codec(val name: String, val sampleRates: List<Int>, val bitsPerSample: List<Int>)

    private val A2DP_HEADER = Regex("""===\s*A2dpStateMachine for .*?(\(Active\))?\s*===""")
    private val CODEC_LINE = Regex("""mCodecConfig:\s*\{(.*)\}""")
    private val CODEC_NAME = Regex("""codecName:([^,}]+)""")
    private val CODEC_RATE = Regex("""mSampleRate:0x[0-9a-fA-F]+\(([^)]*)\)""")
    private val CODEC_BITS = Regex("""mBitsPerSample:0x[0-9a-fA-F]+\(([^)]*)\)""")

    /**
     * Codec dari bagian A2DP perangkat yang bertanda "(Active)". Bila tidak ada tanda aktif, hanya dipakai bila tepat satu
     * perangkat punya codec (tidak menebak di antara beberapa perangkat).
     */
    fun activeA2dpCodec(text: String): Codec? {
        data class Section(val active: Boolean, val codec: Codec?)
        val sections = mutableListOf<Section>()
        var active = false
        var codec: Codec? = null
        var inSection = false
        for (line in text.lineSequence()) {
            val h = A2DP_HEADER.find(line)
            if (h != null) {
                if (inSection) sections += Section(active, codec)
                inSection = true
                active = h.groupValues[1].isNotEmpty()
                codec = null
                continue
            }
            if (inSection && codec == null) CODEC_LINE.find(line)?.let { codec = codec(it.groupValues[1]) }
        }
        if (inSection) sections += Section(active, codec)
        sections.firstOrNull { it.active && it.codec != null }?.let { return it.codec }
        val withCodec = sections.filter { it.codec != null }
        return if (sections.none { it.active } && withCodec.size == 1) withCodec[0].codec else null
    }

    private fun codec(body: String): Codec? {
        val name = CODEC_NAME.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
        if (name.startsWith("INVALID")) return null
        fun ints(r: Regex) = r.find(body)?.groupValues?.get(1)?.split('|')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        return Codec(name, ints(CODEC_RATE), ints(CODEC_BITS))
    }

    /** Satu utas keluaran AudioFlinger. */
    data class OutputThread(
        val name: String,
        val type: String,
        val sampleRate: Int?,
        val halFormat: String?,
        val devices: String,
        val standby: Boolean?,
    )

    private val THREAD_HEADER = Regex("""^\s*Output thread\b(.*)$""")
    private val THREAD_NAME = Regex("""name\s+([^,\s]+)""")
    private val THREAD_TYPE = Regex("""type\s+\d+\s+\((\w+)\)""")
    private val ANY_HEADER = Regex("""^\s*(Input|Output) thread\b""")
    private val RATE = Regex("""^\s*Sample rate:\s*(\d+)\s*Hz""")
    private val HAL_FORMAT = Regex("""^\s*HAL format:\s*0x[0-9a-fA-F]+\s*\((\w+)\)""")
    private val DEVICES = Regex("""^\s*Output devices?:\s*(.*)$""")
    private val STANDBY = Regex("""^\s*Standby:\s*(\w+)""")

    fun outputThreads(text: String): List<OutputThread> {
        val out = mutableListOf<OutputThread>()
        var cur: OutputThread? = null
        for (line in text.lineSequence()) {
            val header = THREAD_HEADER.find(line)
            if (header != null || ANY_HEADER.containsMatchIn(line)) {
                cur?.let(out::add)
                cur = header?.let {
                    val rest = it.groupValues[1]
                    OutputThread(
                        name = THREAD_NAME.find(rest)?.groupValues?.get(1).orEmpty(),
                        type = THREAD_TYPE.find(rest)?.groupValues?.get(1).orEmpty(),
                        sampleRate = null, halFormat = null, devices = "", standby = null,
                    )
                }
                continue
            }
            val c = cur ?: continue
            cur = when {
                c.sampleRate == null && RATE.containsMatchIn(line) -> c.copy(sampleRate = RATE.find(line)!!.groupValues[1].toIntOrNull())
                c.halFormat == null && HAL_FORMAT.containsMatchIn(line) -> c.copy(halFormat = HAL_FORMAT.find(line)!!.groupValues[1])
                c.devices.isEmpty() && DEVICES.containsMatchIn(line) -> c.copy(devices = DEVICES.find(line)!!.groupValues[1])
                c.standby == null && STANDBY.containsMatchIn(line) -> c.copy(standby = STANDBY.find(line)!!.groupValues[1].lowercase() == "yes")
                else -> c
            }
        }
        cur?.let(out::add)
        return out
    }

    /** Token jenis perangkat AudioFlinger untuk tiap jenis keluaran. */
    fun deviceTokens(kind: DeviceKind): List<String> = when (kind) {
        DeviceKind.BLUETOOTH -> listOf("BLUETOOTH_A2DP")
        DeviceKind.BLE_AUDIO -> listOf("BLE_HEADSET", "BLE_SPEAKER", "BLE_BROADCAST")
        DeviceKind.USB -> listOf("USB_DEVICE", "USB_HEADSET", "USB_ACCESSORY")
        DeviceKind.WIRED -> listOf("WIRED_HEADSET", "WIRED_HEADPHONE", "LINE")
        DeviceKind.SPEAKER -> listOf("OUT_SPEAKER")
        DeviceKind.HDMI -> listOf("HDMI")
        DeviceKind.HEARING_AID -> listOf("HEARING_AID")
        DeviceKind.EARPIECE, DeviceKind.BLUETOOTH_CALL, DeviceKind.OTHER -> emptyList()
    }

    /**
     * Utas keluaran yang sedang berjalan (bukan standby) untuk jenis perangkat [kind]. Bila tidak ada yang cocok dengan jenis
     * itu, null (tidak memilih utas lain secara acak).
     */
    fun activeThread(threads: List<OutputThread>, kind: DeviceKind?): OutputThread? {
        val tokens = kind?.let(::deviceTokens).orEmpty()
        if (tokens.isEmpty()) return null
        return threads.firstOrNull { t -> t.standby == false && t.sampleRate != null && tokens.any { tok -> t.devices.contains(tok) } }
    }

    /** "AUDIO_FORMAT_PCM_16_BIT" → "PCM 16-bit"; format lain → nama tanpa awalan. */
    fun formatName(halFormat: String): String = when (halFormat) {
        "AUDIO_FORMAT_PCM_16_BIT" -> "PCM 16-bit"
        "AUDIO_FORMAT_PCM_24_BIT_PACKED" -> "PCM 24-bit"
        "AUDIO_FORMAT_PCM_8_24_BIT" -> "PCM 24-bit (dalam 32-bit)"
        "AUDIO_FORMAT_PCM_32_BIT" -> "PCM 32-bit"
        "AUDIO_FORMAT_PCM_FLOAT" -> "PCM float 32-bit"
        "AUDIO_FORMAT_PCM_8_BIT" -> "PCM 8-bit"
        else -> halFormat.removePrefix("AUDIO_FORMAT_").replace('_', ' ')
    }
}

/** Menyusun [AdvancedReadings] dari hasil pengurai. Murni agar teks & labelnya diuji. */
object AdvancedReport {
    private const val NOTE = "Dari laporan diagnostik sistem"

    fun build(
        active: OutputDevice?,
        codec: DumpParsers.Codec?,
        thread: DumpParsers.OutputThread?,
        players: List<DumpParsers.Player>,
        musicUid: Int?,
    ): AdvancedReadings {
        val bt = active?.kind == DeviceKind.BLUETOOTH
        val codecReading = when {
            !bt -> Reading.unavailable("Keluaran aktif bukan Bluetooth A2DP")
            codec == null -> Reading.unavailable("Laporan Bluetooth tidak memuat codec perangkat aktif (format OEM berbeda atau tidak tersambung)")
            else -> Reading.reported(
                buildString {
                    append(codec.name)
                    if (codec.sampleRates.size == 1) append(" · ").append(AudioRoutes.khz(codec.sampleRates[0]))
                    if (codec.bitsPerSample.size == 1) append(" · ").append(codec.bitsPerSample[0]).append("-bit")
                },
                NOTE + " (Bluetooth)",
            )
        }
        val formatReading = when {
            active == null -> Reading.unavailable("Keluaran aktif tidak diketahui")
            thread == null -> Reading.unavailable("Tidak ada keluaran yang sedang berjalan untuk perangkat ini (musik dijeda?)")
            else -> Reading.reported(
                listOfNotNull(thread.sampleRate?.let(AudioRoutes::khz), thread.halFormat?.let(DumpParsers::formatName)).joinToString(" · ")
                    .ifEmpty { "?" },
                "$NOTE (utas ${thread.name.ifBlank { "keluaran" }})",
            )
        }
        val playing = DumpParsers.musicSessions(players).filter { it.state == "started" && (musicUid == null || it.uid == musicUid) }
        val rates = playing.map { it.sampleRate }.filter { it > 0 }.distinct()
        val outRate = thread?.sampleRate
        val resampling = when {
            thread == null || outRate == null -> Reading.unavailable("Tidak ada keluaran yang sedang berjalan")
            playing.isEmpty() -> Reading.unavailable("Tidak ada pemutar musik yang sedang berjalan")
            rates.isEmpty() -> Reading.unavailable("Sistem tidak melaporkan sample rate pemutar (butuh Android 14+)")
            rates.size > 1 -> Reading.reported(
                "Beberapa pemutar: " + rates.sorted().joinToString(", ") { AudioRoutes.khz(it) } + " → " + AudioRoutes.khz(outRate), NOTE,
            )
            rates[0] == outRate -> Reading.reported("Tidak · ${AudioRoutes.khz(outRate)}", "$NOTE: sample rate pemutar sama dengan keluaran")
            else -> Reading.reported("Ya · ${AudioRoutes.khz(rates[0])} → ${AudioRoutes.khz(outRate)}", "$NOTE: pemutar dan keluaran berbeda sample rate")
        }
        return AdvancedReadings(
            codec = codecReading,
            outputFormat = formatReading,
            resampling = resampling,
            // Laporan Bluetooth AOSP tidak memuat level baterai perangkat; tidak ditebak
            battery = Reading.unavailable("Laporan diagnostik sistem tidak memuat baterai perangkat Bluetooth"),
        )
    }
}
