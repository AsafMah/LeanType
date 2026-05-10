// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.Manifest
import android.provider.Settings as AndroidSettings
import helium314.keyboard.latin.settings.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.LoadEmojiLibPreference
import helium314.keyboard.settings.preferences.LoadGestureLibPreference
import helium314.keyboard.settings.preferences.MultiSliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.preferences.TextInputPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WelcomeWizard(
    close: () -> Unit,
    finish: () -> Unit
) {
    val ctx = LocalContext.current
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    fun determineStep(): Int = when {
        !UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm) -> 0
        !UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm) -> 2
        else -> 3
    }
    var step by rememberSaveable { mutableIntStateOf(determineStep()) }
    var requiresRestart by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(step) {
        if (step == 2)
            scope.launch {
                while (step == 2 && !UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)) {
                    delay(50)
                }
                step = 3
            }
    }
    val useWideLayout = isWideScreen()
    val appName = stringResource(ctx.applicationInfo.labelRes)
    @Composable fun bigText() {
        val resource = if (step == 0) R.string.setup_welcome_title else R.string.setup_steps_title
        Column(Modifier.padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(resource, appName),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (JniUtils.sHaveGestureLib && step == 0) {
                Text(
                    stringResource(R.string.setup_welcome_additional_description),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
    @Composable
    fun ColumnScope.Step(currentStep: Int, title: String, instruction: String, actionText: String, icon: Painter, action: () -> Unit, onSkip: (() -> Unit)? = null, onBack: (() -> Unit)? = null, content: @Composable () -> Unit = {}) {
        // Progress indicator
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (i in 1..8) {
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .background(
                            if (i <= currentStep) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant,
                            androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
        }
        
        androidx.compose.material3.ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(instruction, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(Modifier.height(16.dp))
                content()
                
                Spacer(Modifier.height(24.dp))
                
                // Action Buttons
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (onBack != null) {
                        androidx.compose.material3.TextButton(onClick = onBack) {
                            Text("Back")
                        }
                    } else {
                        Spacer(Modifier.weight(0.1f)) // Placeholder to maintain spacing
                    }
                    
                    if (onSkip != null) {
                        androidx.compose.material3.TextButton(onClick = onSkip) {
                            Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    Spacer(Modifier.weight(1f))
                    
                    androidx.compose.material3.FilledTonalButton(onClick = action) {
                        Icon(icon, null, Modifier.padding(end = 8.dp).size(20.dp))
                        Text(actionText)
                    }
                }
            }
        }
    }
    @Composable fun steps() {
        if (step == 0)
            Step0 { step = 1 }
        else
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    step = determineStep()
                }
                if (step == 1) {
                    Step(
                        1,
                        stringResource(R.string.setup_step1_title, appName),
                        stringResource(R.string.setup_step1_instruction, appName),
                        stringResource(R.string.setup_step1_action),
                        painterResource(R.drawable.ic_setup_key),
                        {
                            val intent = Intent()
                            intent.action = AndroidSettings.ACTION_INPUT_METHOD_SETTINGS
                            intent.addCategory(Intent.CATEGORY_DEFAULT)
                            launcher.launch(intent)
                        },
                        null,
                        null
                    )
                } else if (step == 2) {
                    Step(
                        2,
                        stringResource(R.string.setup_step2_title, appName),
                        stringResource(R.string.setup_step2_instruction, appName),
                        stringResource(R.string.setup_step2_action),
                        painterResource(R.drawable.ic_setup_select),
                        { imm.showInputMethodPicker() },
                        null,
                        null
                    )
                } else if (step == 3) {
                    Step(
                        3,
                        "Libraries",
                        "Download emoji and gesture libraries to improve typing and suggestions.",
                        "Next",
                        painterResource(R.drawable.sym_keyboard_language_switch),
                        { step++ },
                        { step++ },
                        null
                    ) {
                        val locale = helium314.keyboard.latin.RichInputMethodManager.getInstance().currentSubtype.locale
                        val emojiLibInstalled = java.io.File(helium314.keyboard.latin.utils.DictionaryInfoUtils.getCacheDirectoryForLocale(locale, ctx), "emoji_${locale.language}.dict").exists()
                        val gestureLibInstalled = java.io.File(ctx.filesDir, "libjni_latinime.so").exists() || helium314.keyboard.latin.utils.JniUtils.sHaveGestureLib

                        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)) {
                            LoadEmojiLibPreference("Emoji Dictionary")
                            if (emojiLibInstalled) {
                                Icon(painterResource(R.drawable.ic_setup_check), null, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)) {
                            LoadGestureLibPreference(
                                title = "Gesture Typing Library",
                                restartOnSuccess = false,
                                onSuccess = { requiresRestart = true }
                            )
                            if (gestureLibInstalled) {
                                Icon(painterResource(R.drawable.ic_setup_check), null, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else if (step == 4) {
                    Step(
                        4,
                        "AI Integration",
                        "Select an AI service and provide your API key for advanced proofreading features.",
                        "Next",
                        painterResource(R.drawable.sym_keyboard_language_switch),
                        { step++ },
                        { step++ },
                        { step-- }
                    ) {
                        if (BuildConfig.FLAVOR == "standard") {
                            val provider = ctx.prefs().getString(helium314.keyboard.settings.SettingsWithoutKey.AI_PROVIDER, "") ?: ""
                            val apiKey = ctx.prefs().getString(helium314.keyboard.settings.SettingsWithoutKey.GEMINI_API_KEY, "") ?: ""
                            val aiConfigured = provider.isNotEmpty() && apiKey.isNotEmpty()

                            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)) {
                                Column {
                                    helium314.keyboard.settings.Setting(ctx, helium314.keyboard.settings.SettingsWithoutKey.AI_PROVIDER, R.string.ai_provider_title, R.string.ai_provider_summary) { setting ->
                                        ListPreference(setting, listOf(
                                            ctx.getString(R.string.ai_provider_huggingface) to helium314.keyboard.latin.utils.ProofreadService.AIProvider.GROQ.name,
                                            ctx.getString(R.string.ai_provider_gemini) to helium314.keyboard.latin.utils.ProofreadService.AIProvider.GEMINI.name,
                                            ctx.getString(R.string.ai_provider_openai) to helium314.keyboard.latin.utils.ProofreadService.AIProvider.OPENAI.name
                                        ), helium314.keyboard.latin.utils.ProofreadService.AIProvider.GEMINI.name)
                                    }.Preference()
                                    helium314.keyboard.settings.Setting(ctx, helium314.keyboard.settings.SettingsWithoutKey.GEMINI_API_KEY, R.string.gemini_api_key_title, R.string.gemini_api_key_summary) { setting ->
                                        TextInputPreference(setting, "")
                                    }.Preference()
                                }
                                if (aiConfigured) {
                                    Icon(painterResource(R.drawable.ic_setup_check), null, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else {
                            Text("AI features are not available in this build flavor.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (step == 5) {
                    Step(
                        5,
                        "Floating Keyboard",
                        "Enable floating keyboard by granting the 'Display over other apps' permission.",
                        "Next",
                        painterResource(R.drawable.sym_keyboard_language_switch),
                        { step++ },
                        { step++ },
                        { step-- }
                    ) {
                        val canDrawOverlays = AndroidSettings.canDrawOverlays(ctx)
                        val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium).clickable {
                            if (!canDrawOverlays) {
                                val intent = Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                                overlayLauncher.launch(intent)
                            }
                        }.padding(16.dp)) {
                            if (!canDrawOverlays) {
                                Text("Permission required. Tap here to grant.", color = MaterialTheme.colorScheme.primary)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painterResource(R.drawable.ic_setup_check), null, Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("Permission granted.", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                } else if (step == 6) {
                    Step(
                        6,
                        "Screenshot Suggestions",
                        "Suggest recently taken screenshots in the suggestion strip. Note: This permission also allows saving screenshots to the clipboard.",
                        "Next",
                        painterResource(R.drawable.sym_keyboard_language_switch),
                        { step++ },
                        { step++ },
                        { step-- }
                    ) {
                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_IMAGES
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        val granted = PermissionsUtil.checkAllPermissionsGranted(ctx.getActivity(), permission)
                        val screenshotsEnabled = ctx.prefs().getBoolean(Settings.PREF_SUGGEST_SCREENSHOTS, Defaults.PREF_SUGGEST_SCREENSHOTS)

                        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)) {
                            helium314.keyboard.settings.Setting(ctx, Settings.PREF_SUGGEST_SCREENSHOTS, R.string.suggest_screenshots, R.string.suggest_screenshots_summary) { setting ->
                                val activity = ctx.getActivity() ?: return@Setting
                                var isGranted by remember { mutableStateOf(PermissionsUtil.checkAllPermissionsGranted(activity, permission)) }
                                val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                                    isGranted = it
                                    if (isGranted)
                                        activity.prefs().edit { putBoolean(setting.key, true) }
                                }
                                SwitchPreference(setting, Defaults.PREF_SUGGEST_SCREENSHOTS, allowCheckedChange = {
                                    if (it && !isGranted) {
                                        permLauncher.launch(permission)
                                        false
                                    } else true
                                })
                            }.Preference()
                            
                            if (granted && screenshotsEnabled) {
                                Icon(painterResource(R.drawable.ic_setup_check), null, Modifier.align(Alignment.CenterEnd).padding(end = 64.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else if (step == 7) {
                    Step(
                        7,
                        "Keyboard Height",
                        "Adjust the height of the keyboard. Recommended: 77% for more square keys, 100% for taller keys.",
                        "Next",
                        painterResource(R.drawable.sym_keyboard_language_switch),
                        { step++ },
                        { step++ },
                        { step-- }
                    ) {
                        val heightSet = ctx.prefs().contains(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX + "0")

                        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)) {
                            helium314.keyboard.settings.Setting(ctx, Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, R.string.prefs_keyboard_height_scale) { setting ->
                                MultiSliderPreference(
                                    name = setting.title,
                                    baseKey = setting.key,
                                    dimensions = listOf(stringResource(R.string.landscape)),
                                    defaults = Defaults.PREF_KEYBOARD_HEIGHT_SCALE,
                                    range = 0.3f..1.5f,
                                    description = { "${(100 * it).toInt()}%" }
                                ) {}
                            }.Preference()
                            
                            if (heightSet) {
                                Icon(painterResource(R.drawable.ic_setup_check), null, Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else { // step 8
                    Step(
                        8,
                        stringResource(R.string.setup_step3_title),
                        stringResource(R.string.setup_step3_instruction, appName),
                        stringResource(R.string.setup_finish_action),
                        painterResource(R.drawable.ic_setup_check),
                        {
                            finish()
                            if (requiresRestart) {
                                Runtime.getRuntime().exit(0)
                            }
                        },
                        null,
                        { step-- }
                    )
                }
            }
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (useWideLayout)
                Row {
                    Box(Modifier.weight(0.4f)) {
                        bigText()
                    }
                    Box(Modifier.weight(0.6f)) {
                        steps()
                    }
                }
            else
                Column {
                    bigText()
                    steps()
                }
        }
    }
}

@Composable
fun Step0(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.setup_welcome_image), null)
        Row(Modifier.clickable { onClick() }
            .padding(top = 4.dp, start = 4.dp, end = 4.dp)
            //.background(color = MaterialTheme.colorScheme.primary)
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.setup_start_action),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Preview
@Composable
private fun Preview() {
    Theme(previewDark) {
        Surface {
            WelcomeWizard({}) {  }
        }
    }
}

@Preview(
    // content cut off on real device, but not here... great?
    device = "spec:orientation=landscape,width=400dp,height=780dp"
)
@Composable
private fun WidePreview() {
    Theme(previewDark) {
        Surface {
            WelcomeWizard({}) {  }
        }
    }
}
