// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.handwriting.HandwritingLoader
import helium314.keyboard.latin.translation.TranslationLoader
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory

@Composable
fun LibrariesHubScreen(
    onClickBack: () -> Unit,
    onClickOfflineVoice: () -> Unit = {},
    onClickTranslation: () -> Unit = {},
    onClickHandwriting: () -> Unit = {},
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.plugins_title),
        settings = emptyList(),
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
        ) { innerPadding ->
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(vertical = 8.dp)
            ) {
                // Section 1: Plugins
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory(stringResource(R.string.plugins_title))

                        // Handwriting Input Plugin (ML Kit based, standardfull only)
                        if (BuildConfig.FLAVOR == "standardfull") {
                            val handwritingInstalled = HandwritingLoader.hasPlugin(context)
                            val summary = if (handwritingInstalled) stringResource(R.string.libraries_status_active) else stringResource(R.string.libraries_status_not_installed)
                            Preference(
                                name = stringResource(R.string.libraries_hub_handwriting_title),
                                description = summary,
                                onClick = onClickHandwriting,
                                icon = R.drawable.ic_edit
                            ) { NextScreenIcon() }
                        }

                        // Offline Voice Input
                        Preference(
                            name = stringResource(R.string.offline_voice_title),
                            description = stringResource(R.string.pref_offline_voice_summary),
                            onClick = onClickOfflineVoice,
                            icon = R.drawable.sym_keyboard_voice_holo
                        ) { NextScreenIcon() }

                        // Translation Settings Screen (available on standard and standardfull)
                        if (BuildConfig.FLAVOR == "standard" || BuildConfig.FLAVOR == "standardfull") {
                            val translationInstalled = TranslationLoader.hasPlugin(context)
                            val summary = if (translationInstalled) {
                                if (BuildConfig.FLAVOR == "standardfull") "Offline ML Kit & Online engine" else "Online Translation Plugin"
                            } else {
                                "Configure plugin & translation backend"
                            }
                            Preference(
                                name = stringResource(R.string.translation_settings_title),
                                description = summary,
                                onClick = onClickTranslation,
                                icon = R.drawable.ic_translate
                            ) { NextScreenIcon() }
                        }
                    }
                }

                // Section 2: Documentation
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory("Documentation")

                        Preference(
                            name = "Features Guide",
                            description = "View the detailed features.md guide on GitHub",
                            onClick = { uriHandler.openUri("https://github.com/LeanBitLab/HeliboardL/blob/main/docs/FEATURES.md") },
                            icon = R.drawable.ic_settings_about_wiki
                        ) { NextScreenIcon() }
                    }
                }
            }
        }
    }
}
