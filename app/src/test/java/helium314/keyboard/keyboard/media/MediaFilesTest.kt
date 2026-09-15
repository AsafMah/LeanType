// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.util.ReflectionHelpers
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28], shadows = [MediaFilesTest.HostFileProviderPaths::class])
class MediaFilesTest {
    private lateinit var context: Context
    private lateinit var source: FixtureSource
    private var now = 1_000L
    private val policy = MediaRetentionPolicy(receiverReadMillis = 10_000, maxBytes = 1024 * 1024, maxFiles = 4)
    private val directory get() = File(context.cacheDir, MediaFiles.DIRECTORY)

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        source = FixtureSource(OwnedMediaFixtures.gif())
        directory.deleteRecursively()
        // Robolectric starts its MIME database empty rather than loading Android's defaults.
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("gif", "image/gif")
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("webp", "image/webp")
        val authority = "${context.packageName}.fileprovider"
        val paths = context.resources.getIdentifier("provider_paths", "xml", context.packageName)
        assertTrue(paths != 0)
        val provider = FileProvider()
        provider.attachInfo(context, ProviderInfo().apply {
            this.authority = authority
            exported = false
            grantUriPermissions = true
            metaData = Bundle().apply { putInt("android.support.FILE_PROVIDER_PATHS", paths) }
        })
        ShadowContentResolver.registerProviderInternal(authority, provider)
    }

    @After fun tearDown() {
        directory.deleteRecursively()
    }

    @Test fun defaultPolicyRefusesBeforeDownloadOrDiskWrite() = runBlocking {
        val files = MediaFiles(context, source)
        assertEquals(MediaError.UNAVAILABLE, assertFailsWith<MediaException> {
            files.stage(OwnedMediaFixtures.rendition(), "Owned")
        }.reason)
        assertEquals(0, source.downloads)
        assertFalse(directory.exists())
    }

    @Test fun providerExposesOnlyTheNamedStagingRoot() {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", File(directory, "owned.gif")
        )
        assertTrue(uri.path!!.startsWith("/media_staging/"))
        assertFailsWith<IllegalArgumentException> {
            FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", File(context.cacheDir, "not-in-provider-path.gif")
            )
        }
        assertFailsWith<SecurityException> {
            context.contentResolver.openInputStream(uri.buildUpon().encodedPath("/media_staging/../outside.gif").build())
        }
    }

    /**
     * FileProvider hardcodes Android's '/' in its private containment check; Robolectric's java.io.File
     * still uses host paths. Adapt only separators, keeping real root selection, traversal rejection,
     * URI creation, MIME resolution, file descriptors, and bytes. This is not on-device grant evidence.
     */
    @Implements(className = "androidx.core.content.FileProvider\$SimplePathStrategy", isInAndroidSdk = false)
    class HostFileProviderPaths {
        @Implementation
        protected fun belongsToRoot(filePath: String, rootPath: String): Boolean =
            filePath.replace(File.separatorChar, '/').trimEnd('/')
                .startsWith(rootPath.replace(File.separatorChar, '/').trimEnd('/') + '/')
    }

    @Test fun stagesExactAnimationBytesWithTypedNarrowUniqueUris() = runBlocking {
        val files = files()
        val first = files.stage(OwnedMediaFixtures.rendition(), "Owned transparent animation")
        val second = files.stage(OwnedMediaFixtures.rendition(), "Owned second copy")
        assertNotEquals(first.uri, second.uri)
        assertEquals("content", first.uri.scheme)
        assertTrue(first.uri.path!!.startsWith("/media_staging/"))
        assertTrue(first.uri.path!!.endsWith(".gif"))
        assertEquals("image/gif", first.mimeType)
        assertEquals(first.mimeType, context.contentResolver.getType(first.uri))
        assertEquals("Owned transparent animation", first.description)
        assertContentEquals(source.bytes, context.contentResolver.openInputStream(first.uri)!!.use { it.readBytes() })
        assertEquals(MediaLimits.MEDIA_BYTES, source.lastLimit)
        assertEquals(OwnedMediaFixtures.rendition().copy(preview = false), source.lastRendition)
    }

    @Test fun closeAndCleanupPreserveDelayedReadsUntilLeaseExpires() = runBlocking {
        val files = files()
        val media = files.stage(OwnedMediaFixtures.rendition(), "Delayed read")
        files.close()
        now = 10_999
        val nextSession = files()
        nextSession.cleanup()
        assertContentEquals(source.bytes, context.contentResolver.openInputStream(media.uri)!!.use { it.readBytes() })
        now = 11_000
        nextSession.cleanup()
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun refusesFullStoreWithoutEvictingProtectedReceivers() = runBlocking {
        val files = files(policy.copy(maxFiles = 1))
        val media = files.stage(OwnedMediaFixtures.rendition(), "Protected")
        assertEquals(MediaError.STORAGE, assertFailsWith<MediaException> {
            files.stage(OwnedMediaFixtures.rendition(), "Too soon")
        }.reason)
        assertContentEquals(source.bytes, context.contentResolver.openInputStream(media.uri)!!.use { it.readBytes() })
        now += policy.receiverReadMillis
        assertNotEquals(media.uri, files.stage(OwnedMediaFixtures.rendition(), "Expired replaced").uri)
        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test fun boundsAggregateDiskBytesAndLeavesUnrelatedCacheFilesAlone() = runBlocking {
        val unrelated = File(context.cacheDir, "owned-unrelated-media-test").apply { writeText("keep") }
        try {
            val files = files(policy.copy(maxBytes = source.bytes.size.toLong()))
            files.stage(OwnedMediaFixtures.rendition(), "Fits")
            assertEquals(MediaError.STORAGE, assertFailsWith<MediaException> {
                files.stage(OwnedMediaFixtures.rendition(), "Does not fit")
            }.reason)
            now += policy.receiverReadMillis
            files.cleanup()
            assertEquals("keep", unrelated.readText())
        } finally {
            unrelated.delete()
        }
    }

    @Test fun rejectsCorruptOversizedChangedCredentialAndClosedTransfers() = runBlocking {
        val files = files()
        assertEquals(MediaError.TOO_LARGE, assertFailsWith<MediaException> {
            files.stage(OwnedMediaFixtures.rendition().copy(byteSize = MediaLimits.MEDIA_BYTES + 1L), "Too big")
        }.reason)
        assertEquals(0, source.downloads)
        source.bytes = byteArrayOf(0, 1)
        assertEquals(MediaError.UNSUPPORTED, assertFailsWith<MediaException> {
            files.stage(OwnedMediaFixtures.rendition(), "Invalid")
        }.reason)
        source.bytes = OwnedMediaFixtures.gif()
        source.changeVersionDuringDownload = true
        assertEquals(MediaError.UNAVAILABLE, assertFailsWith<MediaException> {
            files.stage(OwnedMediaFixtures.rendition(), "Credentials changed")
        }.reason)
        files.close()
        assertEquals(MediaError.UNAVAILABLE, assertFailsWith<MediaException> {
            files.stage(OwnedMediaFixtures.rendition(), "Closed")
        }.reason)
        assertFalse(directory.exists())
    }

    @Test fun cleanupReportsBrokenProviderRootsWithoutPretendingFilesWereDeleted() = runBlocking {
        val files = files()
        files.stage(OwnedMediaFixtures.rendition(), "Protected")
        now += policy.receiverReadMillis
        val cache = ReflectionHelpers.getStaticField<MutableMap<String, Any>>(FileProvider::class.java, "sCache")
        val strategy = cache.getValue("${context.packageName}.fileprovider")
        val roots = ReflectionHelpers.getField<MutableMap<String, File>>(strategy, "mRoots")
        val staging = roots.remove("media_staging")!!
        try {
            assertEquals(MediaError.STORAGE, assertFailsWith<MediaException> { files.cleanup() }.reason)
            assertEquals(1, directory.listFiles()!!.size)
        } finally {
            roots["media_staging"] = staging
        }
    }

    @Test fun cleanupReportsDeniedRevocationWithoutDeletingReceiverFile() = runBlocking {
        val denied = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun revokeUriPermission(uri: Uri, modeFlags: Int) {
                throw SecurityException("Owned permission failure")
            }
        }
        val files = MediaFiles(denied, source, policy) { now }
        files.stage(OwnedMediaFixtures.rendition(), "Protected")
        now += policy.receiverReadMillis
        assertEquals(MediaError.STORAGE, assertFailsWith<MediaException> { files.cleanup() }.reason)
        assertEquals(1, directory.listFiles()!!.size)
    }

    private fun files(retention: MediaRetentionPolicy = policy) = MediaFiles(context, source, retention) { now }

    private class FixtureSource(var bytes: ByteArray) : MediaSource {
        override val available = true
        override var credentialVersion = 0L
        var downloads = 0
        var lastLimit = 0
        var lastRendition: MediaRendition? = null
        var changeVersionDuringDownload = false
        override fun hasApiKey() = true
        override suspend fun search(kind: MediaKind, query: String, language: String, offset: Int): MediaPage =
            error("Search must not be called")
        override suspend fun download(rendition: MediaRendition, maxBytes: Int): ByteArray {
            downloads++
            lastLimit = maxBytes
            lastRendition = rendition
            if (changeVersionDuringDownload) credentialVersion++
            return bytes
        }
    }
}
