// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat

object MediaContent {
    fun supports(editorInfo: EditorInfo, mimeType: String): Boolean =
        concreteMime(mimeType) && EditorInfoCompat.getContentMimeTypes(editorInfo).any {
            ClipDescription.compareMimeTypes(mimeType, it)
        }

    /**
     * False means rejection, not insertion. Receiver exceptions propagate to the caller.
     * The advertisement opt-out preserves legacy clipboard transport; picker calls keep the default.
     */
    fun commit(
        context: Context,
        connection: InputConnection,
        editorInfo: EditorInfo,
        media: StagedMedia,
        requireAdvertisedMimeType: Boolean = true
    ): Boolean {
        if (requireAdvertisedMimeType && !supports(editorInfo, media.mimeType)) return false
        requireContent(media)
        val content = InputContentInfoCompat(
            media.uri, ClipDescription(media.description, arrayOf(media.mimeType)), null
        )
        val flags = if (Build.VERSION.SDK_INT >= 25) {
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        } else {
            val target = editorInfo.packageName
            if (target.isNullOrBlank()) {
                if (requireAdvertisedMimeType) return false
            } else {
                try {
                    context.grantUriPermission(target, media.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (error: SecurityException) {
                    // Legacy clipboard URIs may already be independently readable by the receiver.
                    // Only that caller can opt out; picker transfers require a successful grant.
                    if (requireAdvertisedMimeType) throw error
                }
            }
            0
        }
        // On pre-25 Android there is no package-specific revoke API. Keep the scoped grant
        // for the staging lease rather than revoking other receivers' access on rejection.
        return InputConnectionCompat.commitContent(connection, editorInfo, content, flags, null)
    }

    /** Only an explicit user Share action may call this. The system chooses the recipient. */
    fun share(context: Context, media: StagedMedia) {
        requireContent(media)
        val clip = ClipData(ClipDescription(media.description, arrayOf(media.mimeType)), ClipData.Item(media.uri))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = media.mimeType
            putExtra(Intent.EXTRA_STREAM, media.uri)
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).apply {
            clipData = clip
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }

    private fun requireContent(media: StagedMedia) {
        require(media.uri.scheme == "content" && concreteMime(media.mimeType)) { "Expected typed content URI" }
    }

    private fun concreteMime(mime: String): Boolean =
        mime.count { it == '/' } == 1 && !mime.startsWith('/') && !mime.endsWith('/') &&
            '*' !in mime && mime.none { it.isWhitespace() }
}
