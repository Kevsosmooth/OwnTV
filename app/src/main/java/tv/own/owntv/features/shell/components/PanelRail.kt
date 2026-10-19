package tv.own.owntv.features.shell.components

import android.os.SystemClock
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.theme.LocalStagePalette
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.ownTvTween

/** Whether the Full height menu is open: gone, or the icons with their names. */
enum class PanelStep { CLOSED, NAMES }

/** The closed menu's strip, kept just past the edge so the app can still hand it focus. */
private val PanelParkWidth: Dp = 112.mpx

/** The menu with names. */
val PanelNamesWidth: Dp = 390.mpx

/** As the shell's rail: focus that reaches the hidden menu this soon after ◀ / ▶ was sent there. */
private const val PANEL_KEY_WINDOW_MS = 400L

/**
 * How long, and how often, a picked section is tried for its way in while it loads. Long enough for a
 * cold start's TV Guide, whose rows come a few seconds after the screen.
 */
private const val PANEL_ENTER_RETRY_MS = 8000L
private const val PANEL_ENTER_STEP_MS = 100L

/**
 * Navigation = Full height, as the shell drives it: whether it is the chosen style ([enabled]) and shown
 * ([shown], on ◀ or Back out of the content or a key pressed on it, not when the app merely parks focus
 * there). It resets as focus leaves the menu.
 */
@Stable
class PanelNav internal constructor(private val scope: CoroutineScope, private val contentFocus: FocusRequester) {
    var enabled = false
        internal set
    internal var entry: (() -> Boolean)? = null
    var shown by mutableStateOf(false)
    /** When ◀ / ▶ was last pressed. */
    var keyAt = 0L
    private var railFocused = false
    private var picking: Job? = null
    /** The way back into the open screen's categories sheet (Live TV, Movies, Series) while it is open. */
    private var sheetEntry: (() -> Boolean)? = null

    fun step(railHasFocus: Boolean): PanelStep =
        if (enabled && shown && railHasFocus) PanelStep.NAMES else PanelStep.CLOSED

    /** A [PanelSheetHook]. */
    fun sheet(enter: (() -> Boolean)?) {
        sheetEntry = enter
    }

    /** Up, Down or OK on the menu is the user using it. */
    fun onRailKey(key: Key) {
        if (key == Key.DirectionUp || key == Key.DirectionDown || key == Key.DirectionCenter || key == Key.Enter) shown = true
    }

    /** Into the open screen: its open sheet, else its own way in, else wherever its content had focus. */
    fun enterContent() {
        if (sheetEntry?.invoke() == true) return
        if (entry?.invoke() != true) runCatching { contentFocus.requestFocus() }
    }

    /**
     * A section picked from the menu is entered, as TiviMate's is, once it has drawn its way in. A screen
     * still loading its items (Movies) has nothing to focus yet, so its own way in is tried again until
     * focus has left the menu; only then is the content's last focus (often its search field) used.
     */
    fun enterPicked() {
        picking?.cancel()
        // The pick closes the menu; opening it again (or Back) is what stops the tries.
        shown = false
        picking = scope.launch {
            withFrameNanos { }
            withFrameNanos { }
            val until = SystemClock.uptimeMillis() + PANEL_ENTER_RETRY_MS
            // Until the user opens the menu themselves.
            while (entry != null && !shown) {
                val claimed = entry?.invoke() == true
                // Lets the focus change reach [railFocused].
                delay(PANEL_ENTER_STEP_MS)
                if (claimed && !railFocused) return@launch
                if (SystemClock.uptimeMillis() >= until) break
            }
            if (!shown) runCatching { contentFocus.requestFocus() }
        }
    }

    /** Where the app puts focus by itself (start-up, leaving the player): the rail, or the content here. */
    fun park(focusRail: () -> Unit) {
        if (enabled) enterPicked() else focusRail()
    }

    /**
     * Resets the menu as focus leaves it, sends focus that lands on it by itself (the style read after
     * start-up parked it there, a screen swap) back into the content, and returns how far the content
     * moves aside: the menu pushes the screen over rather than covering it.
     */
    @Composable
    fun track(railHasFocus: Boolean): State<Dp> {
        SideEffect { railFocused = railHasFocus }
        LaunchedEffect(railHasFocus) { if (!railHasFocus) shown = false }
        LaunchedEffect(railHasFocus, enabled) {
            if (!enabled || !railHasFocus || shown) return@LaunchedEffect
            if (SystemClock.uptimeMillis() - keyAt <= PANEL_KEY_WINDOW_MS) shown = true else enterContent()
        }
        return animatePanelPush(step(railHasFocus))
    }
}

/**
 * How a screen with a categories sheet tells the Full height menu about it: the way back into the sheet
 * while it is open, else null. With the hook set the sheet stays open while the menu has focus and
 * closes as its own list takes focus instead, so ◀ ◀ from the list goes sheet, then menu, and ▶ ▶ back.
 */
typealias PanelSheetHook = ((() -> Boolean)?) -> Unit

/** Reports a screen's categories sheet through [hook] while it is composed. */
@Composable
fun ReportPanelSheet(hook: PanelSheetHook?, open: Boolean, focus: FocusRequester) {
    DisposableEffect(hook, open) {
        hook?.invoke(if (open) { { runCatching { focus.requestFocus() }.getOrDefault(false) } } else null)
        onDispose { hook?.invoke(null) }
    }
}

/** The shell's [PanelNav], kept up to date with the chosen style and the open screen's way in. */
@Composable
fun rememberPanelNav(enabled: Boolean, entry: (() -> Boolean)?, contentFocus: FocusRequester): PanelNav {
    val scope = rememberCoroutineScope()
    return remember(contentFocus) { PanelNav(scope, contentFocus) }.apply {
        this.enabled = enabled
        this.entry = entry
    }
}

/** A screen's categories column beside the Full height menu, in 1920-wide pixels (the Full TV Guide's too). */
const val PANEL_SHEET_PX = 390

/** A screen's categories as the Full TV Guide draws them: a flat column [width] wide, the height of the screen. */
@Composable
fun Modifier.panelSheetColumn(width: Dp): Modifier =
    width(width).fillMaxHeight().background(LocalStagePalette.current.page).padding(top = 28.mpx)

/** How far the content moves aside for the menu at [step], animated (a snap with animations off). */
@Composable
internal fun animatePanelPush(step: PanelStep): State<Dp> = animateDpAsState(
    when (step) {
        PanelStep.CLOSED -> 0.dp
        PanelStep.NAMES -> PanelNamesWidth
    },
    ownTvTween(320),
)

/**
 * The Full height menu's items, from the top of the screen: [leading] (Now Playing), the [sections], then
 * [extra] (the Full guide's own controls). [names] shows each icon's name; without it the active
 * section is boxed. ◀ into it lands on the open section ([sectionFocus]), then on whatever had focus last.
 */
@Composable
fun PanelMenu(
    sections: List<MainSection>,
    selected: MainSection,
    names: Boolean,
    onSelect: (MainSection) -> Unit,
    onActive: () -> Unit,
    sectionFocus: FocusRequester,
    modifier: Modifier = Modifier,
    leading: @Composable ColumnScope.() -> Unit = {},
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    fun isActive(section: MainSection) = section == selected || (section == MainSection.MORE && selected == MainSection.SETTINGS)
    val hasActive = sections.any(::isActive)
    BoxWithConstraints(modifier.fillMaxHeight().background(LocalStagePalette.current.page)) {
        Column(
            Modifier
                .focusRestorer(if (hasActive) sectionFocus else FocusRequester.Default)
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 16.mpx, vertical = 28.mpx),
            // Packed at the top, as TiviMate's, on every screen whatever else the menu lists.
            verticalArrangement = Arrangement.spacedBy(10.mpx),
        ) {
            leading()
            sections.forEach { section ->
                val active = isActive(section)
                StageTool(
                    text = null, icon = section.stageIcon,
                    value = if (names) stringResource(section.labelRes) else null,
                    valueAccent = active,
                    boxed = active && !names,
                    onClick = { if (active) onActive() else onSelect(section) },
                    modifier = Modifier.fillMaxWidth().then(if (active) Modifier.focusRequester(sectionFocus) else Modifier),
                )
            }
            extra()
        }
    }
}

/**
 * Navigation = Full height: the menu the height of the screen at its edge, as TiviMate's. It is gone
 * while focus is in the content; ◀ (or Back) opens it ([onShow]) with its names, and ▶ goes back into
 * the content ([enterContent]: an open sheet, then the screen's own way in). The shell moves the content
 * aside by [animatePanelPush] of the same [step].
 */
@Composable
fun PanelRail(
    step: PanelStep,
    selected: MainSection,
    visibleSections: Set<MainSection>,
    onSelect: (MainSection) -> Unit,
    onShow: () -> Unit,
    enterContent: () -> Unit,
    selectedItemFocusRequester: FocusRequester,
    onFocused: () -> Unit,
    nowPlaying: NowPlayingRail?,
    onNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val push by animatePanelPush(step)
    var hasFocus by remember { mutableStateOf(false) }
    val width = maxOf(push, PanelParkWidth)
    PanelMenu(
        sections = stageRailSections(visibleSections),
        selected = selected,
        names = step == PanelStep.NAMES,
        onSelect = onSelect,
        onActive = enterContent,
        sectionFocus = selectedItemFocusRequester,
        leading = {
            if (nowPlaying != null) {
                StageTool(
                    text = null,
                    icon = if (nowPlaying.audioMode) OwnTVIcon.EQ else OwnTVIcon.PLAY,
                    value = if (step == PanelStep.NAMES) stringResource(R.string.shell_now_playing) else null,
                    onClick = onNowPlaying,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        modifier = modifier
            .width(width)
            // Closed, the strip waits just past the edge, so the app can still hand it focus.
            .graphicsLayer { translationX = (push - PanelParkWidth).coerceAtMost(0.dp).toPx() * (if (rtl) -1f else 1f) }
            .clipToBounds()
            .onFocusChanged {
                // Every entry from the content lands on the open section, not on whatever item lines up.
                val entered = it.hasFocus && !hasFocus
                hasFocus = it.hasFocus
                if (it.hasFocus) onFocused()
                if (entered) scope.launch { runCatching { selectedItemFocusRequester.requestFocus() } }
            }
            .onKeyEvent { e ->
                val toEdge = if (rtl) Key.DirectionRight else Key.DirectionLeft
                val out = if (rtl) Key.DirectionLeft else Key.DirectionRight
                if (e.key != toEdge && e.key != out) return@onKeyEvent false
                if (e.type == KeyEventType.KeyDown) if (e.key == toEdge) onShow() else enterContent()
                true
            },
    )
}
