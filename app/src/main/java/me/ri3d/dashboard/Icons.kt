package me.ri3d.dashboard

/**
 * Icon inventory: 24 x 24 outlined paths taken from the reference artboards (stroke 1.75,
 * round caps and joins unless noted). Drawn at runtime by [IconView] in any colour, so
 * normal / active (accent) / disabled (#2C2F34 or #7B7F87) variants need no extra assets.
 */
object Icons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}" +
            "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

    const val RADIO = "M5 8h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2zM7 8 17 3M6 14a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0M14 12h4M14 16h4"
    const val AA = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0M12 7l4 9-4-2-4 2z"
    const val PHONE = "M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L8.1 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2z"
    const val FOLDER = "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
    const val CAR = "M5 11l1.5-4.5A2 2 0 0 1 8.4 5h7.2a2 2 0 0 1 1.9 1.5L19 11M5 11h14a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2 2 0 0 1 2-2zM6 17v2M18 17v2"
    val SLIDERS = "M4 6h10M18 6h2M4 12h2M10 12h10M4 18h8M16 18h4" + circle(16f, 6f, 2f) + circle(8f, 12f, 2f) + circle(14f, 18f, 2f)
    val GRID = rect(3f, 3f, 7f, 7f, 1.5f) + rect(14f, 3f, 7f, 7f, 1.5f) + rect(3f, 14f, 7f, 7f, 1.5f) + rect(14f, 14f, 7f, 7f, 1.5f)
    val CLOCK = circle(12f, 12f, 9f) + "M12 7v5l3 2"
    const val BACK = "m15 18-6-6 6-6"
    const val NEXT = "m9 18 6-6-6-6"
    const val PLUS = "M12 5v14M5 12h14"
    const val SWAP = "M4 7h14l-3-3M20 17H6l3 3"
    val REMOVE = circle(12f, 12f, 9f) + "M8 12h8"
    const val EDIT = "M4 20h4L19 9l-4-4L4 16zm9.5-13.5 4 4"
    val SEARCH = circle(11f, 11f, 7f) + "M20 20l-3.5-3.5"
    const val MIC = "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3zM19 10v2a7 7 0 0 1-14 0v-2M12 19v3"
    val PERSON = circle(12f, 8f, 4f) + "M4 21a8 8 0 0 1 16 0"
    val CAR_OUTLINE = "M5 11l1.5-4.5A2 2 0 0 1 8.4 5h7.2a2 2 0 0 1 1.9 1.5L19 11" + rect(3f, 11f, 18f, 6f, 2f) + "M6 17v2M18 17v2"
    const val ARROW = "M5 12h14M13 6l6 6-6 6"
    const val APP = "M7 3h10a4 4 0 0 1 4 4v10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4z"

    // Filled media controls (fill + stroke 2 in the reference, pause/play are fill only).
    const val PREV = "M19 6v12l-9-6zM6 6v12"
    const val SKIP = "M5 6v12l9-6zM18 6v12"
    const val PLAY = "M8 5v14l11-7z"
    val PAUSE = rect(6f, 5f, 4f, 14f, 1f) + rect(14f, 5f, 4f, 14f, 1f)
    /** Play state unknown (WELLE does not report it on Android 4.x). */
    val PLAY_PAUSE = "M3 6v12l8.5-6z" + rect(14f, 6f, 3f, 12f, 0.8f) + rect(19f, 6f, 3f, 12f, 0.8f)
}
