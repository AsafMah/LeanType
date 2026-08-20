// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.leanbitlab.leantype.voice.ModelImportRequest
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object VoiceDownloadDispatcher {
    private const val TAG = "VoiceDownloadDispatcher"
    private const val PREFS_NAME = "voice_download_tracker"

    fun hasInternetPermission(context: Context): Boolean {
        return context.packageManager.checkPermission(
            "android.permission.INTERNET",
            context.packageName
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun download(context: Context, model: VoiceModelItem) {
        if (!hasInternetPermission(context)) {
            Log.i(TAG, "Offline build: delegating download to browser for ${model.id}")
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(model.browserUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Opening browser for ${model.displayName}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open browser", e)
                Toast.makeText(context, "Failed to open browser: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm == null) {
                fallbackToBrowser(context, model)
                return
            }

            val request = DownloadManager.Request(Uri.parse(model.downloadUrl)).apply {
                setTitle(model.displayName)
                setDescription("Downloading ${model.sizeMb} speech model")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
            }

            val downloadId = dm.enqueue(request)
            Log.i(TAG, "Enqueued downloadId=$downloadId for model=${model.id}")

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("download_$downloadId", model.id)
                .apply()

            Toast.makeText(context, "Downloading ${model.displayName} (${model.sizeMb})...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "DownloadManager error, falling back to browser", e)
            fallbackToBrowser(context, model)
        }
    }

    private fun fallbackToBrowser(context: Context, model: VoiceModelItem) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(model.browserUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "DownloadManager unavailable, opening browser", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch browser", e)
        }
    }
}

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("voice_download_tracker", Context.MODE_PRIVATE)
        val modelId = prefs.getString("download_$downloadId", null) ?: return
        prefs.edit().remove("download_$downloadId").apply()

        val model = VoiceModelRegistry.findById(modelId) ?: return

        android.util.Log.i("DownloadCompleteReceiver", "Download complete for model: ${model.displayName} (id=$downloadId)")

        val pendingResult = goAsync()

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return@launch
                val query = DownloadManager.Query().setFilterById(downloadId)

                dm.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = if (statusIndex != -1) cursor.getInt(statusIndex) else -1

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val uri = dm.getUriForDownloadedFile(downloadId)
                            if (uri != null) {
                                val pfd = appContext.contentResolver.openFileDescriptor(uri, "r")
                                if (pfd != null) {
                                    val pluginManager = VoicePluginManager(appContext)
                                    val request = ModelImportRequest(
                                        engineType = model.engineType,
                                        language = model.language,
                                        sha256 = null,
                                        sizeBytes = pfd.statSize,
                                        file = pfd
                                    )

                                    val success = kotlinx.coroutines.withTimeoutOrNull(9000L) {
                                        pluginManager.bindAndImport(request)
                                    } ?: false

                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        if (success) {
                                            appContext.prefs().edit().putString("installed_model_${model.engineType}", model.id).apply()
                                            Toast.makeText(appContext, "${model.displayName} installed successfully!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(appContext, "Failed to install ${model.displayName}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        } else {
                            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = if (reasonIndex != -1) cursor.getInt(reasonIndex) else -1
                            android.util.Log.e("DownloadCompleteReceiver", "Download failed with status=$status, reason=$reason")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadCompleteReceiver", "Error processing downloaded model", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
