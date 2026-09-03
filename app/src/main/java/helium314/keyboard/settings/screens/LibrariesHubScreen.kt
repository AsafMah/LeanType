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
import helium314.keyboard.latin.ocr.OcrPluginLoader
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
    onClickOcr: () -> Unit = {},
    onClickAIIntegration: () -> Unit = {},
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
                // Section 1: Active Plugin Engines
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory("Active Engines & Capabilities")

                        // AI Integration Screen (Available in standard, standardfull, offline)
                        val isSupported = BuildConfig.FLAVOR != "classic"
                        val aiPluginInstalled = helium314.keyboard.latin.ai.OfflineAiLoader.hasPlugin(context)
                        if (isSupported) {
                            val aiSummary = when {
                                aiPluginInstalled -> stringResource(R.string.libraries_status_active)
                                else -> stringResource(R.string.libraries_status_not_installed)
                            }
                            Preference(
                                name = stringResource(R.string.settings_screen_ai_integration),
                                description = aiSummary,
                                onClick = onClickAIIntegration,
                                icon = R.drawable.ic_proofread
                            ) { NextScreenIcon() }
                        }

                        // Handwriting Input Plugin (ML Kit based)
                        val isHandwritingSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        val handwritingInstalled = isHandwritingSupported && HandwritingLoader.hasPlugin(context)
                        val summary = when {
                            !isHandwritingSupported -> "Requires Android 8.0+"
                            handwritingInstalled -> stringResource(R.string.libraries_status_active)
                            else -> stringResource(R.string.libraries_status_not_installed)
                        }
                        Preference(
                            name = stringResource(R.string.libraries_hub_handwriting_title),
                            description = summary,
                            onClick = if (isHandwritingSupported) onClickHandwriting else ({}),
                            enabled = isHandwritingSupported,
                            icon = R.drawable.ic_edit
                        ) { if (isHandwritingSupported) NextScreenIcon() }

                        // Text Recognition (OCR) Plugin (ML Kit based)
                        val isOcrSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        val ocrInstalled = isOcrSupported && OcrPluginLoader.hasPlugin(context)
                        val ocrSummary = when {
                            !isOcrSupported -> "Requires Android 8.0+"
                            ocrInstalled -> {
                                val script = OcrPluginLoader.getActiveScriptName(context)
                                if (!script.isNullOrBlank()) "${stringResource(R.string.libraries_status_active)} ($script)"
                                else stringResource(R.string.libraries_status_active)
                            }
                            else -> stringResource(R.string.libraries_status_not_installed)
                        }
                        Preference(
                            name = stringResource(R.string.ocr_title),
                            description = ocrSummary,
                            onClick = if (isOcrSupported) onClickOcr else ({}),
                            enabled = isOcrSupported,
                            icon = R.drawable.ic_ocr
                        ) { if (isOcrSupported) NextScreenIcon() }

                        // Offline Voice Input
                        val voicePluginManager = remember { helium314.keyboard.latin.voice.VoicePluginManager(context) }
                        val voiceInstalled = voicePluginManager.isPluginInstalled()
                        val voiceSummary = if (voiceInstalled) {
                            stringResource(R.string.libraries_status_active)
                        } else {
                            stringResource(R.string.libraries_status_not_installed)
                        }
                        Preference(
                            name = stringResource(R.string.offline_voice_title),
                            description = voiceSummary,
                            onClick = onClickOfflineVoice,
                            icon = R.drawable.sym_keyboard_voice_holo
                        ) { NextScreenIcon() }

                        // Translation Settings Screen (available for all flavors)
                        val isTranslationSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N
                        val translationInstalled = isTranslationSupported && TranslationLoader.hasPlugin(context)
                        val translationSummary = when {
                            !isTranslationSupported -> "Requires Android 7.0+"
                            translationInstalled -> stringResource(R.string.libraries_status_active)
                            else -> stringResource(R.string.libraries_status_not_installed)
                        }
                        Preference(
                            name = stringResource(R.string.translation_settings_title),
                            description = translationSummary,
                            onClick = if (isTranslationSupported) onClickTranslation else ({}),
                            enabled = isTranslationSupported,
                            icon = R.drawable.ic_translate
                        ) { if (isTranslationSupported) NextScreenIcon() }
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
