// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutType.Companion.displayNameId
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.LayoutPickerDialog
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.previewDark

@Composable
fun SecondaryLayoutScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "recomposition trigger")

    val customCount = prefs.getInt("custom_layouts_count", 0)

    val settingsList = remember(customCount, b?.value) {
        val list = mutableListOf<Any?>()
        // Add non-main and non-custom layouts
        LayoutType.entries.filter { it != LayoutType.MAIN && !it.name.startsWith("CUSTOM") }.forEach {
            list.add(Settings.PREF_LAYOUT_PREFIX + it.name)
        }
        // Add configured custom layouts
        for (i in 1..customCount) {
            list.add(Settings.PREF_LAYOUT_PREFIX + "CUSTOM$i")
        }
        if (customCount < 5) {
            list.add("add_custom_layout")
        }
        list
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_secondary_layouts),
        settings = settingsList
    )
}

fun createLayoutSettings(context: Context): List<Setting> {
    val list = LayoutType.entries.filter { it != LayoutType.MAIN }.map { layoutType ->
        Setting(context, Settings.PREF_LAYOUT_PREFIX + layoutType.name, layoutType.displayNameId) { setting ->
            val ctx = LocalContext.current
            val prefs = ctx.prefs()
            val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
            if ((b?.value ?: 0) < 0)
                Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
            var showDialog by rememberSaveable { mutableStateOf(false) }
            val currentLayout = Settings.readDefaultLayoutName(layoutType, prefs)
            val displayName = if (LayoutUtilsCustom.isCustomLayout(currentLayout)) LayoutUtilsCustom.getDisplayName(currentLayout)
                else currentLayout.getStringResourceOrName("layout_", ctx)
            val isCustom = layoutType.name.startsWith("CUSTOM")
            Preference(
                name = setting.title,
                description = displayName,
                onClick = { showDialog = true },
                value = if (isCustom) {
                    {
                        IconButton(
                            onClick = {
                                val index = layoutType.name.removePrefix("CUSTOM").toIntOrNull() ?: 0
                                val count = prefs.getInt("custom_layouts_count", 0)
                                if (index in 1..count) {
                                    val edit = prefs.edit()
                                    for (i in index until count) {
                                        val nextVal = prefs.getString(Settings.PREF_LAYOUT_PREFIX + "CUSTOM${i + 1}", null)
                                        if (nextVal != null) {
                                            edit.putString(Settings.PREF_LAYOUT_PREFIX + "CUSTOM$i", nextVal)
                                        } else {
                                            edit.remove(Settings.PREF_LAYOUT_PREFIX + "CUSTOM$i")
                                        }
                                    }
                                    edit.remove(Settings.PREF_LAYOUT_PREFIX + "CUSTOM$count")
                                    edit.putInt("custom_layouts_count", count - 1)
                                    edit.apply()
                                    // Trigger recomposition
                                    (ctx.getActivity() as? SettingsActivity)?.let {
                                        it.prefChanged.value = it.prefChanged.value + 1
                                    }
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bin),
                                contentDescription = "delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else null
            )
            if (showDialog)
                LayoutPickerDialog(
                    onDismissRequest = { showDialog = false },
                    setting = setting,
                    layoutType = layoutType
                )
        }
    }.toMutableList()

    // Add the "add_custom_layout" Setting
    list.add(
        Setting(context, "add_custom_layout", R.string.add_custom_layout) { setting ->
            val ctx = LocalContext.current
            val prefs = ctx.prefs()
            Preference(
                name = setting.title,
                icon = R.drawable.ic_plus,
                onClick = {
                    val count = prefs.getInt("custom_layouts_count", 0)
                    if (count < 5) {
                        prefs.edit().putInt("custom_layouts_count", count + 1).apply()
                        // Trigger preference update so settings screen recomposes
                        (ctx.getActivity() as? SettingsActivity)?.let {
                            it.prefChanged.value = it.prefChanged.value + 1
                        }
                    }
                }
            )
        }
    )

    return list
}

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            SecondaryLayoutScreen { }
        }
    }
}
