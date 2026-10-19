package tv.own.owntv.features.epg

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import tv.own.owntv.ui.theme.mpx

/**
 * The sizes that differ between the guide styles. [Standard] is the Stage guide; [Full] (Navigation =
 * Side panel) fills the screen as TiviMate does: a smaller top area, no control line, about seven
 * taller rows with a big logo, and one-line programme cells. Edges are in 1920 px units (`fx`).
 */
@Immutable
internal data class GuideLook(
    val full: Boolean,
    /** The top area: its distance from the screen top, its height, and the programme block's top. */
    val topPad: Dp,
    val topHeight: Dp,
    val blockTop: Dp,
    /** The grid's left and right edges, and its distance below the top area. */
    val startPx: Int,
    val endPx: Int,
    val gridTop: Dp,
    val rowHeight: Dp,
    val rowGap: Dp,
    /** The channel column when the guide widths are not customised. */
    val channelShare: Float,
    val numberSize: Int,
    /** Width per digit of the longest channel number, and the least the number column takes. */
    val digitWidth: Float,
    val minNumberWidth: Float,
    val logoWidth: Dp,
    val logoHeight: Dp,
    val nameSize: Int,
    val nameLines: Int,
    /** The channel number's and name's weight. */
    val labelWeight: Int,
    /**
     * False: TiviMate's cells: flat, one line, the title only (the times are on the ruler), and the
     * focused one light with dark text instead of an accent card.
     */
    val cellTimes: Boolean,
) {
    companion object {
        val Standard = GuideLook(
            full = false,
            topPad = 44.mpx, topHeight = 360.mpx, blockTop = 76.mpx,
            startPx = 48, endPx = 64, gridTop = 18.mpx,
            rowHeight = GuideGridDefaults.RowHeight, rowGap = GuideGridDefaults.RowGap,
            channelShare = GuideGridDefaults.ChannelColShare,
            numberSize = 17, digitWidth = 11f, minNumberWidth = 38f,
            logoWidth = 58.mpx, logoHeight = 40.mpx,
            nameSize = 17, nameLines = 1, labelWeight = 700,
            cellTimes = true,
        )

        /** Rows from 405 to 1080: seven 91 px rows, 4 apart, with no part-row below, the timeline running off the right edge. */
        val Full = GuideLook(
            full = true,
            topPad = 24.mpx, topHeight = 320.mpx, blockTop = 84.mpx,
            startPx = 24, endPx = 0, gridTop = 12.mpx,
            rowHeight = 91.mpx, rowGap = 4.mpx,
            channelShare = 440f / 1920f,
            numberSize = 22, digitWidth = 14f, minNumberWidth = 48f,
            logoWidth = 104.mpx, logoHeight = 66.mpx,
            nameSize = 21, nameLines = 2, labelWeight = 500,
            cellTimes = false,
        )

        fun of(full: Boolean) = if (full) Full else Standard
    }
}

/**
 * Where the Full guide starts its timeline for [time]: at the half hour it falls in, as TiviMate does,
 * so the programme before it is not left showing at the edge.
 */
internal fun fullGuideViewStart(time: Long): Long {
    val slotMs = GuideGridDefaults.SlotMin * 60_000L
    return time - Math.floorMod(time, slotMs)
}
