// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 25, 28])
class MediaContentTest {
    private val context = RecordingContext(RuntimeEnvironment.getApplication())
    private val media = StagedMedia(Uri.parse("content://fixture.invalid/media_staging/owned.gif"), "image/gif", "Owned")

    @Test fun preflightsConcreteAndWildcardMimeAdvertisements() {
        for (accepted in listOf("image/gif", "image/*", "*/*")) {
            assertTrue(MediaContent.supports(editor(accepted), media.mimeType))
        }
        for (unsupported in listOf("image/jpeg", "video/*", "")) {
            assertFalse(MediaContent.supports(editor(unsupported), media.mimeType))
        }
        assertFalse(MediaContent.supports(editor(), media.mimeType))
        assertFalse(MediaContent.supports(editor("*/*"), "image/*"))
        assertFalse(MediaContent.supports(editor("*/*"), "broken"))
    }

    @Test fun referenceReceiverGetsTypedContentAndPlatformSpecificReadGrant() {
        val editor = editor("image/*")
        var received = false
        val receiver = InputConnectionCompat.createWrapper(Mockito.mock(InputConnection::class.java), editor) { content, flags, _ ->
            received = true
            assertEquals(media.uri, content.contentUri)
            assertEquals(media.description, content.description.label)
            assertEquals(media.mimeType, content.description.getMimeType(0))
            assertNull(content.linkUri)
            assertEquals(if (Build.VERSION.SDK_INT >= 25) 1 else 0, flags)
            true
        }
        assertTrue(MediaContent.commit(context, receiver, editor, media))
        assertTrue(received)
        if (Build.VERSION.SDK_INT < 25) {
            assertEquals(listOf(Grant("receiver.example", media.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)), context.grants)
        } else {
            assertTrue(context.grants.isEmpty())
        }
        assertTrue(context.activities.isEmpty())
    }

    @Test fun unsupportedMimeDoesNotCallOrGrantReceiverOrAutomaticallyShare() {
        val receiver = Mockito.mock(InputConnection::class.java)
        assertFalse(MediaContent.commit(context, receiver, editor("image/jpeg"), media))
        Mockito.verifyNoInteractions(receiver)
        assertTrue(context.grants.isEmpty())
        assertTrue(context.activities.isEmpty())
    }

    @Test fun rejectedAndThrowingCommitsNeverBecomeSuccessOrShare() {
        val editor = editor("image/gif")
        val rejected = InputConnectionCompat.createWrapper(Mockito.mock(InputConnection::class.java), editor) { _, _, _ -> false }
        assertFalse(MediaContent.commit(context, rejected, editor, media))
        val throwing = InputConnectionCompat.createWrapper(Mockito.mock(InputConnection::class.java), editor) { _, _, _ ->
            throw IllegalStateException("Owned receiver failure")
        }
        assertFailsWith<IllegalStateException> { MediaContent.commit(context, throwing, editor, media) }
        assertTrue(context.activities.isEmpty())
    }

    @Test @Config(sdk = [25, 28])
    fun legacyClipboardCanCommitWithoutMimeAdvertisementButPickerCannot() {
        val editor = editor()
        var calls = 0
        val receiver = InputConnectionCompat.createWrapper(Mockito.mock(InputConnection::class.java), editor) { content, flags, _ ->
            calls++
            assertEquals(media.uri, content.contentUri)
            assertEquals(media.mimeType, content.description.getMimeType(0))
            assertEquals(InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, flags)
            true
        }
        assertFalse(MediaContent.commit(context, receiver, editor, media))
        assertEquals(0, calls)
        assertTrue(MediaContent.commit(context, receiver, editor, media, requireAdvertisedMimeType = false))
        assertEquals(1, calls)
        assertTrue(context.grants.isEmpty())
        assertTrue(context.activities.isEmpty())
        assertFalse(MediaContent.commit(
            context, Mockito.mock(InputConnection::class.java), editor, media, requireAdvertisedMimeType = false
        ))
        assertFailsWith<IllegalArgumentException> {
            MediaContent.commit(
                context, receiver, editor, media.copy(uri = Uri.parse("file:///owned.gif")),
                requireAdvertisedMimeType = false
            )
        }
    }

    @Test @Config(sdk = [23])
    fun oldPlatformRequiresReceiverPackageAndSupportsLegacyCompatAdvertisement() {
        val receiver = Mockito.mock(InputConnection::class.java)
        val missingPackage = editor("image/gif").apply { packageName = null }
        assertFalse(MediaContent.commit(context, receiver, missingPackage, media))
        Mockito.verifyNoInteractions(receiver)
        assertTrue(context.grants.isEmpty())

        val legacy = editor("image/gif").apply {
            extras.remove("androidx.core.view.inputmethod.EditorInfoCompat.CONTENT_MIME_TYPES")
        }
        var received = false
        val wrapped = InputConnectionCompat.createWrapper(receiver, legacy) { _, _, _ -> received = true; true }
        assertTrue(MediaContent.commit(context, wrapped, legacy, media))
        assertTrue(received)
        assertEquals("receiver.example", context.grants.single().target)
    }

    @Test @Config(sdk = [23])
    fun legacyClipboardStillUsesPrivateCommandWhenGrantIsDeniedButPickerFailsClosed() {
        val editor = editor("image/gif")
        var calls = 0
        val receiver = InputConnectionCompat.createWrapper(Mockito.mock(InputConnection::class.java), editor) { content, flags, _ ->
            calls++
            assertEquals(media.uri, content.contentUri)
            assertEquals(0, flags)
            true
        }
        context.grantFailure = SecurityException("Owned denied-grant fixture")
        assertFailsWith<SecurityException> { MediaContent.commit(context, receiver, editor, media) }
        assertEquals(0, calls)
        assertTrue(MediaContent.commit(context, receiver, editor, media, requireAdvertisedMimeType = false))
        assertEquals(1, calls)
        assertFalse(MediaContent.commit(
            context, Mockito.mock(InputConnection::class.java), editor, media, requireAdvertisedMimeType = false
        ))
        assertTrue(context.activities.isEmpty())
    }

    @Test @Config(sdk = [23])
    fun legacyPrivateCommandDoesNotRequirePackageMetadataOrSwallowReceiverErrors() {
        val editor = editor("image/gif").apply { packageName = null }
        val receiver = InputConnectionCompat.createWrapper(Mockito.mock(InputConnection::class.java), editor) { _, _, _ -> true }
        assertFalse(MediaContent.commit(context, receiver, editor, media))
        assertTrue(MediaContent.commit(context, receiver, editor, media, requireAdvertisedMimeType = false))
        assertTrue(context.grants.isEmpty())
        val throwing = InputConnectionCompat.createWrapper(Mockito.mock(InputConnection::class.java), editor) { _, _, _ ->
            throw SecurityException("Owned receiver exception, not a grant failure")
        }
        assertFailsWith<SecurityException> {
            MediaContent.commit(context, throwing, editor, media, requireAdvertisedMimeType = false)
        }
    }

    @Test fun explicitShareUsesChooserStreamClipMimeAndReadOnlyGrant() {
        MediaContent.share(context, media)
        val chooser = context.activities.single()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(media.mimeType, send.type)
        assertEquals(media.uri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(media.uri, send.clipData!!.getItemAt(0).uri)
        assertEquals(media.mimeType, send.clipData!!.description.getMimeType(0))
        assertEquals(media.uri, chooser.clipData!!.getItemAt(0).uri)
        assertNull(send.`package`)
        assertNull(send.component)
        assertFalse(send.hasExtra(Intent.EXTRA_TEXT))
        assertEquals(0, send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertTrue(context.grants.isEmpty())
    }

    @Test fun invalidUriNeverEscapesAsFileOrUrlShare() {
        assertFailsWith<IllegalArgumentException> {
            MediaContent.share(context, media.copy(uri = Uri.parse("file:///owned.gif")))
        }
        assertTrue(context.activities.isEmpty())
    }

    private fun editor(vararg mime: String) = EditorInfo().apply {
        packageName = "receiver.example"
        EditorInfoCompat.setContentMimeTypes(this, mime)
    }

    private data class Grant(val target: String, val uri: Uri, val flags: Int)
    private class RecordingContext(context: Context) : ContextWrapper(context) {
        val grants = mutableListOf<Grant>()
        val activities = mutableListOf<Intent>()
        var grantFailure: SecurityException? = null
        override fun grantUriPermission(toPackage: String, uri: Uri, modeFlags: Int) {
            grants += Grant(toPackage, uri, modeFlags)
            grantFailure?.let { throw it }
        }
        override fun startActivity(intent: Intent) { activities += intent }
    }
}
