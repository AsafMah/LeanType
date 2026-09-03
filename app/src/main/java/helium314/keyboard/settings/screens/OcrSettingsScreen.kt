// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.R
import helium314.keyboard.latin.ocr.OcrPluginLoader
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.preferences.LoadOcrPluginPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.preferences.SwitchPreference

@Composable
fun OcrSettingsScreen(
    onClickBack: () -> Unit,
) {
    val context = LocalContext.current
    var ocrInstalled by remember { mutableStateOf(OcrPluginLoader.hasPlugin(context)) }

    var isCameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isCameraPermissionGranted = granted
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.ocr_settings_title),
        settings = emptyList()
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
                // Plugin Management Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory("Plugin Management")

                        LoadOcrPluginPreference(
                            title = "OCR Plugin APK",
                            summary = if (ocrInstalled) stringResource(R.string.libraries_status_active) else stringResource(R.string.libraries_status_not_installed),
                            icon = R.drawable.ic_ocr,
                            onSuccess = { ocrInstalled = OcrPluginLoader.hasPlugin(context) }
                        )
                    }
                }

                // Permissions Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory("Permissions")

                        Preference(
                            name = "Camera Permission",
                            description = if (isCameraPermissionGranted) "Permission granted" else "Tap to grant camera permission for in-keyboard viewfinder",
                            onClick = {
                                if (!isCameraPermissionGranted) {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            icon = R.drawable.ic_ocr
                        )
                    }
                }

                // Configuration Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory("Text Extraction Options")

                        SwitchPreference(
                            name = stringResource(R.string.ocr_keep_line_breaks),
                            description = stringResource(R.string.ocr_keep_line_breaks_summary),
                            key = OcrPluginLoader.PREF_OCR_KEEP_LINE_BREAKS,
                            default = true
                        )

                        SwitchPreference(
                            name = stringResource(R.string.ocr_suggest_screenshot_text),
                            description = stringResource(R.string.ocr_suggest_screenshot_text_summary),
                            key = OcrPluginLoader.PREF_OCR_SUGGEST_SCREENSHOT_TEXT,
                            default = true
                        )
                    }
                }
            }
        }
    }
}
