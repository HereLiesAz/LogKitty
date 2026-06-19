package com.hereliesaz.logkitty.feature.appmonitor

import androidx.compose.runtime.Composable
import com.hereliesaz.logkitty.core.feature.AppPickerFeature

/**
 * Entry point the base loads reflectively once this module is installed. Delegates to the moved
 * [AppPickerDialog]. Bundling the picker here lets the QUERY_ALL_PACKAGES-driven app list — and the
 * accessibility service that ships alongside it — stay out of the base until the user opts in.
 */
class AppPickerFeatureImpl : AppPickerFeature {
    @Composable
    override fun AppPicker(onAppSelected: (String) -> Unit, onDismiss: () -> Unit) {
        AppPickerDialog(onDismiss = onDismiss, onAppSelected = onAppSelected)
    }
}
