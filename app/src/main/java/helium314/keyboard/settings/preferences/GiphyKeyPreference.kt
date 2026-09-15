// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import helium314.keyboard.keyboard.media.GiphyService
import helium314.keyboard.keyboard.media.MediaError
import helium314.keyboard.keyboard.media.MediaException
import helium314.keyboard.latin.R
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.InfoDialog
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GiphyKeyPreference(setting: Setting) {
    val context = LocalContext.current
    val service = remember(context) { GiphyService(context) }
    val scope = rememberCoroutineScope()
    var configured by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<MediaError?>(null) }
    // Neither an existing key nor the replacement input belongs in saved instance state.
    var input by remember { mutableStateOf("") }
    LaunchedEffect(service) {
        try {
            configured = withContext(Dispatchers.IO) { service.hasApiKey() }
        } catch (failure: MediaException) {
            error = failure.reason
        } finally {
            loading = false
        }
    }
    fun save(key: String?) {
        loading = true
        input = ""
        editing = false
        scope.launch {
            try {
                withContext(Dispatchers.IO) { service.setApiKey(key) }
                configured = key != null
            } catch (failure: MediaException) {
                error = failure.reason
            } finally {
                loading = false
            }
        }
    }
    Preference(
        name = setting.title,
        enabled = !loading,
        description = stringResource(if (configured) R.string.giphy_key_configured else R.string.giphy_key_description),
        onClick = { if (!loading) { input = ""; editing = true } }
    )
    if (editing) {
        ThreeButtonAlertDialog(
            onDismissRequest = { editing = false; input = "" },
            onConfirmed = { save(input.trim()) },
            title = { Text(setting.title) },
            confirmButtonText = stringResource(if (configured) R.string.giphy_key_replace else R.string.giphy_key_set),
            checkOk = { input.isNotBlank() },
            neutralButtonText = if (configured) stringResource(R.string.giphy_key_clear) else null,
            onNeutral = { save(null) },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            content = {
                Column {
                    Text(stringResource(R.string.giphy_key_privacy))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.giphy_key_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false
                        )
                    )
                }
            }
        )
    }
    error?.let {
        InfoDialog(stringResource(if (it == MediaError.LOCKED)
            R.string.giphy_key_locked else R.string.giphy_key_storage_error)) { error = null }
    }
}
