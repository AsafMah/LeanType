// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.R
import helium314.keyboard.settings.screens.AIIntegrationScreen
import helium314.keyboard.settings.screens.LibrariesHubScreen
import helium314.keyboard.settings.screens.OcrSettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsAvailabilityUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun liteHubDoesNotOfferAnAiDestination() {
        compose.setContent {
            MaterialTheme {
                LibrariesHubScreen({}, availability = SettingsAvailability(flavor = "offlinelite"))
            }
        }
        compose.onAllNodesWithText(context.getString(R.string.settings_screen_ai_integration))
            .assertCountEquals(0)
    }

    @Test
    fun noUserlibHubDoesNotOfferAnOcrDestination() {
        compose.setContent {
            MaterialTheme {
                LibrariesHubScreen({}, availability = SettingsAvailability(buildType = "nouserlib"))
            }
        }
        compose.onAllNodesWithText(context.getString(R.string.ocr_title)).assertCountEquals(0)
    }

    @Test
    fun oldAndroidHubDoesNotOfferAnOcrDestination() {
        compose.setContent {
            MaterialTheme {
                LibrariesHubScreen({}, availability = SettingsAvailability(sdk = 25))
            }
        }
        compose.onAllNodesWithText(context.getString(R.string.ocr_title)).assertCountEquals(0)
    }

    @Test
    fun supportedHubStillNavigatesToAiAndOcr() {
        var aiClicks = 0
        var ocrClicks = 0
        compose.setContent {
            MaterialTheme {
                LibrariesHubScreen(
                    {}, onClickAIIntegration = { aiClicks++ }, onClickOcr = { ocrClicks++ },
                    availability = SettingsAvailability(flavor = "standard"),
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.settings_screen_ai_integration)).performClick()
        compose.onNodeWithText(context.getString(R.string.ocr_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.ai_provider_summary)).assertExists()
        compose.runOnIdle {
            assertEquals(1, aiClicks)
            assertEquals(1, ocrClicks)
        }
    }

    @Test
    fun offlineHubDescribesBundledModelConfigurationNotPluginInstallation() {
        compose.setContent {
            MaterialTheme {
                LibrariesHubScreen({}, availability = SettingsAvailability(flavor = "offline"))
            }
        }
        compose.onNodeWithText(context.getString(R.string.offline_model_summary)).assertExists()
    }

    @Test
    fun liteAiRouteReturnsWithoutRenderingConfiguration() {
        var backCalls = 0
        compose.setContent {
            MaterialTheme {
                AIIntegrationScreen(
                    { backCalls++ }, availability = SettingsAvailability(flavor = "offlinelite"),
                )
            }
        }
        compose.onAllNodesWithText(context.getString(R.string.settings_screen_ai_integration))
            .assertCountEquals(0)
        compose.runOnIdle { assertEquals(1, backCalls) }
    }

    @Test
    fun unsupportedOcrRouteNeverOffersCameraPermission() {
        var backCalls = 0
        compose.setContent {
            MaterialTheme {
                OcrSettingsScreen(
                    { backCalls++ }, availability = SettingsAvailability(buildType = "nouserlib"),
                )
            }
        }
        compose.onAllNodesWithText(context.getString(R.string.ocr_camera_permission)).assertCountEquals(0)
        compose.runOnIdle { assertEquals(1, backCalls) }
    }

    @Test
    fun liteSearchDoesNotExposeCloudTokenControl() {
        SettingsActivity.settingsContainer = SettingsContainer(
            context, SettingsAvailability(flavor = "offlinelite"),
        )
        compose.setContent {
            MaterialTheme { SearchSettingsScreen({}, "Settings", emptyList()) }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.label_search_key)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("token")
        compose.onAllNodesWithText(context.getString(R.string.cloud_ai_max_tokens_title)).assertCountEquals(0)
    }
}
