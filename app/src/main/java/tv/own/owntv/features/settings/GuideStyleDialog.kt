package tv.own.owntv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.R
import tv.own.owntv.core.settings.SettingsRepository.GuideStyle

internal fun guideStyleLabelRes(style: GuideStyle): Int =
    if (style == GuideStyle.FULL) R.string.settings_guide_style_full else R.string.settings_guide_style_standard

/** Layout › TV Guide style: Standard (the Stage guide) or Full (the guide fills the screen). */
@Composable
internal fun GuideStyleDialog(settingsVm: SettingsViewModel, onClose: () -> Unit) {
    val current by settingsVm.guideStyle.collectAsStateWithLifecycle()
    PickerDialog(
        title = stringResource(R.string.settings_guide_style),
        subtitle = stringResource(R.string.settings_guide_style_description),
        options = GuideStyle.entries.map { it.name to stringResource(guideStyleLabelRes(it)) },
        descriptions = GuideStyle.entries.associate {
            it.name to stringResource(
                if (it == GuideStyle.FULL) R.string.settings_guide_style_full_description else R.string.settings_guide_style_standard_description,
            )
        },
        selected = current.name,
        onSelect = { settingsVm.setGuideStyle(GuideStyle.valueOf(it)); onClose() },
        onDismiss = onClose,
    )
}
