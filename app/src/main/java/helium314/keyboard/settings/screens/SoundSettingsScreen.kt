// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.media.AudioManager
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.sound.CustomSoundManager
import helium314.keyboard.latin.sound.SoundPackImporter
import helium314.keyboard.latin.sound.SoundPackUrls
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SettingsModule
import helium314.keyboard.settings.dialogs.SoundPackDownloadDialog
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference

// The dedicated screen owns playback previews and pack state; search links back to it.
fun createSoundSettings(context: Context) = listOf(
    Settings.PREF_KEYPRESS_SOUND_STYLE to R.string.prefs_keypress_sound_style_settings,
    Settings.PREF_KEYPRESS_SOUND_VOLUME to R.string.prefs_keypress_sound_volume_settings,
    Settings.PREF_SOUND_PITCH_SCALE to R.string.prefs_sound_pitch_scale,
    Settings.PREF_SOUND_RANDOM_PITCH to R.string.prefs_sound_random_pitch,
    Settings.PREF_SOUND_STEREO_PAN to R.string.prefs_sound_stereo_pan,
    Settings.PREF_SOUND_DYNAMIC_VELOCITY to R.string.prefs_sound_dynamic_velocity,
    Settings.PREF_SOUND_MUTE_IN_SILENT to R.string.prefs_sound_mute_in_silent,
    Settings.PREF_SOUND_MUTE_IN_DND to R.string.prefs_sound_mute_in_dnd,
    Settings.PREF_SOUND_VOL_SPACE to R.string.prefs_sound_vol_space,
    Settings.PREF_SOUND_VOL_DELETE to R.string.prefs_sound_vol_delete,
    Settings.PREF_SOUND_VOL_ENTER to R.string.prefs_sound_vol_enter,
    Settings.PREF_SOUND_VOL_MODIFIERS to R.string.prefs_sound_vol_modifiers,
).map { (key, title) ->
    SettingsModule(key, SettingsDestination.Sound, title, iconRes = R.drawable.ic_play_arrow)
        .createNavigationSetting(context)
}

@Composable
fun SoundSettingsScreen(
    onClickBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.prefs() }

    val b = (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0) {
        // Trigger recomposition on preference changes
    }

    val soundEnabled = prefs.getBoolean(Settings.PREF_SOUND_ON, Defaults.PREF_SOUND_ON)
    var showSoundPackDialog by remember { mutableStateOf(false) }
    val currentSoundStyle = prefs.getString(Settings.PREF_KEYPRESS_SOUND_STYLE, Defaults.PREF_KEYPRESS_SOUND_STYLE) ?: Defaults.PREF_KEYPRESS_SOUND_STYLE
    val soundManifest = if (currentSoundStyle != SoundPackUrls.SYSTEM_DEFAULT_ID) {
        SoundPackImporter.getManifest(context, currentSoundStyle)
    } else null
    val soundStyleName = when {
        currentSoundStyle == SoundPackUrls.SYSTEM_DEFAULT_ID -> stringResource(R.string.prefs_keypress_sound_style_system)
        soundManifest != null -> soundManifest.name
        else -> currentSoundStyle
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.sound_packs_title),
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
                // Section 1: Master Audio Configuration
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory(stringResource(R.string.sound_on_keypress))

                        SwitchPreference(
                            name = stringResource(R.string.sound_on_keypress),
                            key = Settings.PREF_SOUND_ON,
                            default = Defaults.PREF_SOUND_ON,
                            icon = R.drawable.ic_play_arrow
                        )

                        if (soundEnabled) {
                            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                            SliderPreference(
                                name = stringResource(R.string.prefs_keypress_sound_volume_settings),
                                key = Settings.PREF_KEYPRESS_SOUND_VOLUME,
                                default = Defaults.PREF_KEYPRESS_SOUND_VOLUME,
                                description = {
                                    if (it < 0) stringResource(R.string.settings_system_default)
                                    else "${(it * 100).toInt()}%"
                                },
                                range = -0.01f..1f,
                                onValueChanged = { it?.let { vol ->
                                    val played = CustomSoundManager.getInstance(context).playSound(0, vol)
                                    if (!played) {
                                        audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, vol)
                                    }
                                } }
                            )
                        }
                    }
                }

                if (soundEnabled) {
                    // Section 2: Sound Pack Selection & Details
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column {
                            PreferenceCategory("Sound Pack")

                            Preference(
                                name = stringResource(R.string.prefs_keypress_sound_style_settings),
                                description = soundStyleName,
                                onClick = { showSoundPackDialog = true },
                                icon = R.drawable.ic_play_arrow
                            ) { NextScreenIcon() }

                            if (soundManifest != null) {
                                val authorText = if (!soundManifest.author.isNullOrBlank()) "by ${soundManifest.author}" else null
                                val versionText = if (soundManifest.versionName.isNotBlank()) "v${soundManifest.versionName}" else null
                                val metaLine = listOfNotNull(versionText, authorText).joinToString(" • ")
                                val descText = listOfNotNull(soundManifest.summary, metaLine.takeIf { it.isNotBlank() }).joinToString("\n")
                                if (descText.isNotBlank()) {
                                    Preference(
                                        name = "Active Pack Info",
                                        description = descText,
                                        onClick = { showSoundPackDialog = true }
                                    )
                                }
                            }
                        }
                    }

                    // Section 3: Acoustics & Playback Modulation
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column {
                            PreferenceCategory(stringResource(R.string.prefs_sound_acoustics_category))

                            SliderPreference(
                                name = stringResource(R.string.prefs_sound_pitch_scale),
                                key = Settings.PREF_SOUND_PITCH_SCALE,
                                default = Defaults.PREF_SOUND_PITCH_SCALE,
                                description = { "${String.format(java.util.Locale.US, "%.2f", it)}x (${(it * 100).toInt()}%)" },
                                range = 0.5f..1.5f,
                                onValueChanged = { it?.let { pitch ->
                                    CustomSoundManager.getInstance(context).previewSound(currentSoundStyle, pitch = pitch)
                                } }
                            )

                            SwitchPreference(
                                name = stringResource(R.string.prefs_sound_random_pitch),
                                description = stringResource(R.string.prefs_sound_random_pitch_summary),
                                key = Settings.PREF_SOUND_RANDOM_PITCH,
                                default = Defaults.PREF_SOUND_RANDOM_PITCH
                            )

                            SwitchPreference(
                                name = stringResource(R.string.prefs_sound_stereo_pan),
                                description = stringResource(R.string.prefs_sound_stereo_pan_summary),
                                key = Settings.PREF_SOUND_STEREO_PAN,
                                default = Defaults.PREF_SOUND_STEREO_PAN
                            )

                            SwitchPreference(
                                name = stringResource(R.string.prefs_sound_dynamic_velocity),
                                description = stringResource(R.string.prefs_sound_dynamic_velocity_summary),
                                key = Settings.PREF_SOUND_DYNAMIC_VELOCITY,
                                default = Defaults.PREF_SOUND_DYNAMIC_VELOCITY
                            )
                        }
                    }

                    // Section 4: Per-Key Sound & Volume Balance
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column {
                            PreferenceCategory(stringResource(R.string.prefs_sound_balance_category))

                            SliderPreference(
                                name = stringResource(R.string.prefs_sound_vol_space),
                                key = Settings.PREF_SOUND_VOL_SPACE,
                                default = Defaults.PREF_SOUND_VOL_SPACE,
                                description = { "${(it * 100).toInt()}%" },
                                range = 0.0f..1.5f,
                                onValueChanged = {
                                    CustomSoundManager.getInstance(context).playSound(Constants.CODE_SPACE, 0.8f)
                                }
                            )

                            SliderPreference(
                                name = stringResource(R.string.prefs_sound_vol_delete),
                                key = Settings.PREF_SOUND_VOL_DELETE,
                                default = Defaults.PREF_SOUND_VOL_DELETE,
                                description = { "${(it * 100).toInt()}%" },
                                range = 0.0f..1.5f,
                                onValueChanged = {
                                    CustomSoundManager.getInstance(context).playSound(KeyCode.DELETE, 0.8f)
                                }
                            )

                            SliderPreference(
                                name = stringResource(R.string.prefs_sound_vol_enter),
                                key = Settings.PREF_SOUND_VOL_ENTER,
                                default = Defaults.PREF_SOUND_VOL_ENTER,
                                description = { "${(it * 100).toInt()}%" },
                                range = 0.0f..1.5f,
                                onValueChanged = {
                                    CustomSoundManager.getInstance(context).playSound(Constants.CODE_ENTER, 0.8f)
                                }
                            )

                            SliderPreference(
                                name = stringResource(R.string.prefs_sound_vol_modifiers),
                                key = Settings.PREF_SOUND_VOL_MODIFIERS,
                                default = Defaults.PREF_SOUND_VOL_MODIFIERS,
                                description = { "${(it * 100).toInt()}%" },
                                range = 0.0f..1.5f,
                                onValueChanged = {
                                    CustomSoundManager.getInstance(context).playSound(KeyCode.SHIFT, 0.8f)
                                }
                            )
                        }
                    }

                    // Section 5: System & Profiles
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column {
                            PreferenceCategory(stringResource(R.string.prefs_sound_system_category))

                            SwitchPreference(
                                name = stringResource(R.string.prefs_sound_mute_in_silent),
                                description = stringResource(R.string.prefs_sound_mute_in_silent_summary),
                                key = Settings.PREF_SOUND_MUTE_IN_SILENT,
                                default = Defaults.PREF_SOUND_MUTE_IN_SILENT
                            )

                            SwitchPreference(
                                name = stringResource(R.string.prefs_sound_mute_in_dnd),
                                description = stringResource(R.string.prefs_sound_mute_in_dnd_summary),
                                key = Settings.PREF_SOUND_MUTE_IN_DND,
                                default = Defaults.PREF_SOUND_MUTE_IN_DND
                            )
                        }
                    }
                }

                if (showSoundPackDialog) {
                    SoundPackDownloadDialog(
                        onDismissRequest = { showSoundPackDialog = false }
                    )
                }
            }
        }
    }
}
