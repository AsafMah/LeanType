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

        val prefs = context.getSharedPreferences("voice_download_tracker", Context.MODE_PRIVATE)
        val modelId = prefs.getString("download_$downloadId", null) ?: return
        prefs.edit().remove("download_$downloadId").apply()

        val model = VoiceModelRegistry.findById(modelId) ?: return

        android.util.Log.i("DownloadCompleteReceiver", "Download complete for model: ${model.displayName} (id=$downloadId)")

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        val query = DownloadManager.Query().setFilterById(downloadId)

        try {
            dm.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = if (statusIndex != -1) cursor.getInt(statusIndex) else -1

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        val uri = dm.getUriForDownloadedFile(downloadId)
                        if (uri != null) {
                            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                            if (pfd != null) {
                                val pluginManager = VoicePluginManager(context)
                                val request = ModelImportRequest(
                                    engineType = model.engineType,
                                    language = model.language,
                                    sha256 = null,
                                    sizeBytes = pfd.statSize,
                                    file = pfd
                                )
                                pluginManager.bindIfNeeded()
                                pluginManager.importModelSafely(request)
                                Toast.makeText(context, "${model.displayName} installed successfully!", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = if (reasonIndex != -1) cursor.getInt(reasonIndex) else -1
                        android.util.Log.e("DownloadCompleteReceiver", "Download failed with status=$status, reason=$reason")
                        Toast.makeText(context, "Download failed for ${model.displayName}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadCompleteReceiver", "Error processing downloaded model", e)
        }
    }
}
