// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.ocr.OcrPluginLoader
import helium314.keyboard.latin.sound.RemoteSoundPack
import helium314.keyboard.latin.sound.SoundPackImporter
import helium314.keyboard.latin.sound.SoundPackUrls
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddonBoundaryTest {
    @Test
    fun offlineSoundDownloadIsBlockedBeforeCreatingFiles() {
        assumeTrue(BuildConfig.FLAVOR in listOf("offline", "offlinelite"))
        val context = mock(Context::class.java)
        val pack = RemoteSoundPack(id = "test", name = "Test", downloadUrl = "invalid-url")

        assertFalse(SoundPackImporter.downloadAndInstall(context, pack))
        verifyNoInteractions(context)
        assertEquals(SoundPackUrls.FALLBACK_CATALOG, SoundPackUrls.fetchRemoteIndex("invalid-url"))
    }

    @Test
    fun offlineOcrDownloadIsBlockedBeforeResolvingTheRemoteArtifact() {
        assumeTrue(BuildConfig.FLAVOR in listOf("offline", "offlinelite"))
        val context = mock(Context::class.java)
        val abis = Build.SUPPORTED_ABIS
        try {
            // Any attempt to construct the download URL fails before it can reach the network.
            ReflectionHelpers.setStaticField(Build::class.java, "SUPPORTED_ABIS", null)
            assertFalse(OcrPluginLoader.downloadPluginApk(context, tempFile = File("unused.apk")))
            verifyNoInteractions(context)
        } finally {
            ReflectionHelpers.setStaticField(Build::class.java, "SUPPORTED_ABIS", abis)
        }
    }

    @Test
    fun unsupportedOcrCannotImportOrDiscoverRestoredPlugins() {
        val context = mock(Context::class.java)
        val sdk = Build.VERSION.SDK_INT
        try {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 25)
            assertFalse(OcrPluginLoader.hasPlugin(context))
            assertFalse(OcrPluginLoader.importPluginFromTempFile(context, File("unused.apk")))
            verifyNoInteractions(context)
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
        }
    }

    @Test
    fun offlineLocalSoundImportStillWorks() {
        assumeTrue(BuildConfig.FLAVOR in listOf("offline", "offlinelite"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val zip = File(context.cacheDir, "local-sound-test.zip")
        val wav = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(38); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(2); putShort(0)
        }.array()
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("standard.wav"))
            output.write(wav)
            output.closeEntry()
        }
        try {
            val id = SoundPackImporter.importFromZipFile(context, zip)
            assertNotNull(id)
            assertTrue(SoundPackImporter.isPackInstalled(context, id!!))
            assertTrue(SoundPackImporter.getPackAudioFiles(context, id).isValid)
        } finally {
            zip.delete()
        }
    }
}
