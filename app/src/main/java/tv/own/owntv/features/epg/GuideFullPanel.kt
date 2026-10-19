package tv.own.owntv.features.epg

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.features.shell.components.PanelMenu
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.stage.StageSearchField
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.theme.LocalStagePalette
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * What the Full guide's left menu needs from the shell, which has no rail on that guide: the sections
 * in rail order, the way to one, and what Back from the menu does (the rail's exit prompt).
 */
@Immutable
class GuideFullNav(
    val sections: List<MainSection>,
    val selected: MainSection,
    val onSelect: (MainSection) -> Unit,
    val onBack: () -> Unit,
)

/** The guide's own controls as the menu lists them (the Standard guide has them on its control line). */
internal class GuideFullTools(
    val dayLabel: String,
    val onDay: () -> Unit,
    val onNow: () -> Unit,
    val sortLabel: String,
    val onOrder: () -> Unit,
    val query: String,
    val onQueryChange: (String) -> Unit,
    val matching: Boolean,
    val matchSummary: String?,
    val onAutoMatch: () -> Unit,
    /** Live TV's Guide view: back to the channel list. */
    val onListView: (() -> Unit)?,
)

/**
 * The Full guide's menu, the height of the screen at its left edge as in TiviMate: the sections (and,
 * with [names], the guide's controls) spread over its height, then the categories beside them. The
 * guide slides right to make room (EpgScreen), so the channels and the video stay in view. It opens on
 * the categories ([categoryFocus]) with the menu out of sight; ◀ or Back goes into the menu, which opens
 * with its names ([onMenuFocus]); ▶ goes back a column at a time. It closes as focus leaves it ([onClose]).
 */
@Composable
internal fun GuideFullPanel(
    nav: GuideFullNav,
    tools: GuideFullTools,
    menuWidth: Dp,
    names: Boolean,
    onMenuFocus: (Boolean) -> Unit,
    categoryWidth: Dp,
    categoryFocus: FocusRequester,
    onClose: () -> Unit,
    categories: @Composable (Modifier) -> Unit,
) {
    var hadFocus by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val sectionFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val width by animateDpAsState(menuWidth)
    val page = LocalStagePalette.current.page
    // Back from the categories opens the menu; from the menu, the rail's exit prompt.
    BackHandler {
        if (names) nav.onBack()
        else if (!runCatching { sectionFocus.requestFocus() }.getOrDefault(false)) focusManager.moveFocus(FocusDirection.Left)
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { categoryFocus.requestFocus() } }
    LaunchedEffect(searching) { if (searching) { withFrameNanos { }; runCatching { searchFocus.requestFocus() } } }
    Box(Modifier.fillMaxHeight()) {
        Row(
            Modifier
                .fillMaxHeight()
                .onFocusChanged {
                    // Not on the "unfocused" report every node gets when it attaches.
                    if (it.hasFocus) hadFocus = true else if (hadFocus) onClose()
                }
                // The closed menu has no width for ◀ to find, so ◀ from the categories goes to it here.
                .onKeyEvent { e ->
                    e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft && !names &&
                        runCatching { sectionFocus.requestFocus() }.getOrDefault(false)
                }
                .focusGroup(),
        ) {
            PanelMenu(
                sections = nav.sections,
                selected = nav.selected,
                names = names,
                onSelect = nav.onSelect,
                onActive = onClose,
                sectionFocus = sectionFocus,
                modifier = Modifier.width(width).clipToBounds().onFocusChanged { onMenuFocus(it.hasFocus) },
            ) {
                if (names) GuideTools(tools, searching, { searching = it }, searchFocus)
            }
            categories(Modifier.width(categoryWidth).fillMaxHeight().background(page).padding(top = 28.mpx))
        }
    }
}

/** The guide's controls under the sections, while the menu shows its names. */
@Composable
private fun GuideTools(tools: GuideFullTools, searching: Boolean, onSearching: (Boolean) -> Unit, searchFocus: FocusRequester) {
    PanelHeading(stringResource(R.string.content_epg_full_tools))
    StageTool(text = null, icon = OwnTVIcon.CALENDAR, value = tools.dayLabel, trailingIcon = OwnTVIcon.CHEVRON_DOWN, onClick = tools.onDay, modifier = Modifier.fillMaxWidth())
    StageTool(text = stringResource(R.string.content_epg_now_short), icon = OwnTVIcon.NOW, onClick = tools.onNow, modifier = Modifier.fillMaxWidth())
    StageTool(text = null, icon = OwnTVIcon.SORT, value = tools.sortLabel, trailingIcon = OwnTVIcon.CHEVRON_DOWN, onClick = tools.onOrder, modifier = Modifier.fillMaxWidth())
    if (searching || tools.query.isNotEmpty()) {
        StageSearchField(
            query = tools.query, onQueryChange = tools.onQueryChange,
            placeholder = stringResource(R.string.content_epg_search_hint).trimEnd('…'),
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus)
                .onFocusChanged { if (!it.hasFocus && tools.query.isEmpty()) onSearching(false) },
        )
    } else {
        StageTool(text = stringResource(R.string.common_search), icon = OwnTVIcon.SEARCH, onClick = { onSearching(true) }, modifier = Modifier.fillMaxWidth())
    }
    if (tools.matching) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.mpx), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.mpx)) {
            OwnTVSpinner(sizeDp = 18)
            Text(stringResource(R.string.content_epg_matching), style = stageText(18, 700), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        StageTool(text = stringResource(R.string.content_epg_match_button), icon = OwnTVIcon.SPARKLE, onClick = tools.onAutoMatch, modifier = Modifier.fillMaxWidth())
    }
    tools.matchSummary?.let {
        Text(it, style = stageText(16, 600), color = StageColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.mpx))
    }
    tools.onListView?.let { StageTool(text = stringResource(R.string.content_live_list_view), icon = OwnTVIcon.LIST, onClick = it, modifier = Modifier.fillMaxWidth()) }
}

@Composable
private fun PanelHeading(text: String) {
    Text(
        text, style = stageText(16, 800), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.mpx, top = 20.mpx, bottom = 6.mpx),
    )
}
