package com.vinyl.launcher.ui.game

/** Tata letak papan di dalam layar TV, dalam satuan yang sama dengan lebar/tinggi layar (dp atau px). */
data class BoardLayout(
    /** Sisi satu petak (persegi). */
    val cell: Float,
    val left: Float,
    val top: Float,
    val boardWidth: Float,
    val boardHeight: Float,
    /** Tinggi pita skor di atas papan. */
    val hudHeight: Float,
)

object SnakeLayout {
    /** Tinggi pita skor sebagai pecahan tinggi layar. */
    const val HUD_FRACTION = 0.12f

    /**
     * Pita skor di atas, papan dengan petak persegi di bawahnya, di tengah. Pita skor tidak pernah menutupi petak, jadi
     * makanan tidak bisa muncul di bawah tulisan.
     */
    fun compute(width: Float, height: Float, cols: Int, rows: Int): BoardLayout {
        val hud = height * HUD_FRACTION
        val availableHeight = (height - hud).coerceAtLeast(0f)
        val cell = minOf(width / cols, availableHeight / rows).coerceAtLeast(0f)
        val boardWidth = cell * cols
        val boardHeight = cell * rows
        return BoardLayout(
            cell = cell,
            left = (width - boardWidth) / 2f,
            top = hud + (availableHeight - boardHeight) / 2f,
            boardWidth = boardWidth,
            boardHeight = boardHeight,
            hudHeight = hud,
        )
    }
}
