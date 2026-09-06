// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.sound

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SoundPackImporterTest {
    private lateinit var fixture: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        fixture = File("build/import-fixtures/sound-${UUID.randomUUID()}").absoluteFile
        context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getFilesDir() = File(fixture, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(fixture, "no_backup").apply { mkdirs() }
            override fun getCacheDir() = File(fixture, "cache").apply { mkdirs() }
            override fun getApplicationContext(): Context = this
        }
    }

    @After
    fun tearDown() {
        fixture.deleteRecursively()
    }

    @Test
    fun dotManifestCannotDeletePackRoot() = assertRejectedZip(".")

    @Test
    fun dotDotManifestCannotDeleteAppDirectory() = assertRejectedZip("..")

    @Test
    fun invalidManifestIdIsAnErrorRatherThanLegacyFallback() = assertRejectedZip("nested/pack")

    private fun assertRejectedZip(id: String) {
        val outside = File(context.noBackupFilesDir, "outside-sentinel").apply { writeText("outside") }
        val installed = File(SoundPackImporter.getSoundPacksDir(context), "keep/standard.ogg")
            .apply { parentFile!!.mkdirs(); writeText("installed") }
        val zip = zipOf("pack.json" to manifest(id), "standard.ogg" to "new audio")
        val result = SoundPackImporter.importFromZipFile(context, zip)

        assertTrue("sentinel outside sound_packs must survive id=$id", outside.exists())
        assertEquals("outside", outside.readText())
        assertTrue("an installed pack must survive id=$id", installed.exists())
        assertEquals("installed", installed.readText())
        assertNull("a present but invalid manifest must not be imported as a legacy pack", result)
        assertEquals(listOf("keep"), SoundPackImporter.getSoundPacksDir(context).list()!!.sorted())
    }

    @Test
    fun packDirectoryRejectsInvalidIdsWithoutSanitizationCollisions() {
        for (id in listOf(".", "..", "", "a/b", "a\\b", "pack ", "a:b")) {
            assertThrows("id=$id", IllegalArgumentException::class.java) {
                SoundPackImporter.getPackDir(context, id)
            }
        }
        assertNotEquals(
            SoundPackImporter.getPackDir(context, "a_b"),
            SoundPackImporter.getPackDir(context, "a-b")
        )
    }

    @Test
    fun deleteRejectsAncestorIdsAndKeepsUnrelatedFiles() {
        val sentinel = File(context.noBackupFilesDir, "outside-sentinel").apply { writeText("outside") }
        File(SoundPackImporter.getSoundPacksDir(context), "keep").mkdirs()
        for (id in listOf(".", "..", "a/b")) {
            assertFalse("id=$id", SoundPackImporter.deletePack(context, id))
            assertTrue(sentinel.exists())
            assertTrue(File(SoundPackImporter.getSoundPacksDir(context), "keep").exists())
        }
    }

    @Test
    fun rejectedPackAccessLogsIdAndCause() {
        ShadowLog.clear()
        assertNull(SoundPackImporter.getManifest(context, "bad/id"))
        assertFalse(SoundPackImporter.getPackAudioFiles(context, "bad/id").isValid)
        assertFalse(SoundPackImporter.deletePack(context, "bad/id"))
        val errors = ShadowLog.getLogsForTag("SoundPackImporter").filter { it.type == Log.ERROR }
        assertEquals("every rejected access must retain its diagnostic", 3, errors.size)
        assertTrue(errors.all { it.msg.contains("bad/id") && it.throwable is IllegalArgumentException })
    }

    @Test
    fun audioResolutionDoesNotSwallowFatalErrors() {
        val directory = object : File(fixture, "pack") {
            override fun getCanonicalFile(): File = throw AssertionError("fatal filesystem error")
        }
        assertThrows(AssertionError::class.java) {
            SoundPackImporter.resolveAudioFile(directory, "standard.ogg")
        }
    }

    @Test
    fun invalidInstalledManifestDoesNotFallBackToLegacyAudio() {
        val dir = SoundPackImporter.getPackDir(context, "valid").apply { mkdirs() }
        File(dir, "pack.json").writeText(manifest(".."))
        File(dir, "standard.ogg").writeText("audio")
        assertNull(SoundPackImporter.getManifest(context, "valid"))
        assertFalse(SoundPackImporter.isPackInstalled(context, "valid"))
    }

    @Test
    fun generatedManifestKeepsNestedAudioPaths() {
        val zip = zipOf(
            "wrapper/standard.ogg" to "default",
            "wrapper/variants/space.ogg" to "space",
            "wrapper/actions/delete.wav" to "delete",
            "wrapper/actions/enter.wav" to "enter"
        )
        val id = SoundPackImporter.importFromZipFile(context, zip)
        assertNotNull(id)
        val manifest = SoundPackImporter.getManifest(context, id!!)!!
        val root = SoundPackImporter.getPackDir(context, id)
        val expected = mapOf(
            "keypress.default" to "standard.ogg",
            "keypress.space" to "variants/space.ogg",
            "keypress.delete" to "actions/delete.wav",
            "keypress.return" to "actions/enter.wav"
        )
        expected.forEach { (event, path) ->
            assertEquals(event, listOf(path), manifest.sounds.getValue(event).files)
            assertTrue("runtime reference $path must exist", File(root, path).isFile)
            assertEquals(File(root, path).canonicalFile, SoundPackImporter.resolveAudioFile(root, path))
        }
    }

    @Test
    fun generatedManifestRetainsAudioInSiblingDirectories() {
        val zip = zipOf(
            "wrapper/variants/default/standard.ogg" to "default",
            "wrapper/variants/space/space.ogg" to "space",
            "wrapper/actions/enter.wav" to "enter"
        )
        val id = SoundPackImporter.importFromZipFile(context, zip)!!
        val manifest = SoundPackImporter.getManifest(context, id)!!
        assertEquals(listOf("variants/default/standard.ogg"), manifest.sounds.getValue("keypress.default").files)
        assertEquals(listOf("variants/space/space.ogg"), manifest.sounds.getValue("keypress.space").files)
        assertEquals(listOf("actions/enter.wav"), manifest.sounds.getValue("keypress.return").files)
        manifest.sounds.values.flatMap { it.files }.forEach { path ->
            assertNotNull(SoundPackImporter.resolveAudioFile(SoundPackImporter.getPackDir(context, id), path))
        }
    }

    @Test
    fun runtimeAudioResolverRejectsPathsOutsidePack() {
        val root = SoundPackImporter.getPackDir(context, "valid").apply { mkdirs() }
        val outside = File(context.noBackupFilesDir, "outside.ogg").apply { writeText("private") }
        assertNull(SoundPackImporter.resolveAudioFile(root, "../../outside.ogg"))
        assertNull(SoundPackImporter.resolveAudioFile(root, "..\\..\\outside.ogg"))
        assertNull(SoundPackImporter.resolveAudioFile(root, outside.absolutePath))
    }

    @Test
    fun manifestWithEscapingAudioCannotReplaceInstalledPack() {
        val root = SoundPackImporter.getPackDir(context, "valid").apply { mkdirs() }
        File(root, "standard.ogg").writeText("old")
        val zip = zipOf(
            "pack.json" to manifest("valid").replace("standard.ogg", "../../outside.ogg"),
            "standard.ogg" to "new"
        )
        assertNull(SoundPackImporter.importFromZipFile(context, zip))
        assertEquals("old", File(root, "standard.ogg").readText())
    }

    @Test
    fun legacyManifestKeepsNestedAudioPaths() {
        val dir = SoundPackImporter.getPackDir(context, "legacy").apply { mkdirs() }
        File(dir, "audio/standard.ogg").apply { parentFile!!.mkdirs(); writeText("audio") }
        val manifest = SoundPackImporter.getManifest(context, "legacy")!!
        manifest.sounds.values.flatMap { it.files }.forEach { path ->
            assertEquals("audio/standard.ogg", path)
            assertTrue(File(dir, path).isFile)
        }
    }

    private fun manifest(id: String) =
        """{"schemaVersion":1,"id":"$id","name":"fixture","sounds":{"keypress.default":{"files":["standard.ogg"]}}}"""

    private fun zipOf(vararg entries: Pair<String, String>): File {
        val zip = File(context.cacheDir, "fixture.zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            entries.forEach { (name, content) ->
                output.putNextEntry(ZipEntry(name))
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
        return zip
    }
}
