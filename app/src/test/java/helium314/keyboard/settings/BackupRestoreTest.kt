// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.DictionaryFacilitatorImpl
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.database.Database
import helium314.keyboard.latin.ShadowHandler
import helium314.keyboard.latin.ShadowKeyboardSwitcher
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.protectedPrefs
import helium314.keyboard.settings.preferences.BackupCategory
import helium314.keyboard.settings.preferences.BackupRestoreCallbacks
import helium314.keyboard.settings.preferences.BackupRestoreOperationQueue
import helium314.keyboard.settings.preferences.backupData
import helium314.keyboard.settings.preferences.completeRestore
import helium314.keyboard.settings.preferences.restoreBackupData
import helium314.keyboard.settings.preferences.settingsToJsonStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.android.controller.ServiceController
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledExecutorService
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.CRC32
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [
    ShadowLocaleManagerCompat::class, ShadowInputMethodManager2::class,
    ShadowKeyboardSwitcher::class,
    ShadowHandler::class, BackupRestoreTest.RestoreDictionaryShadow::class,
])
class BackupRestoreTest {
    private lateinit var context: Context
    private lateinit var service: ServiceController<LatinIME>

    @Before fun setUp() {
        // Do not race the service's unrelated native dictionary extraction against fixture files.
        ExecutorUtils.setExecutorServiceForTests(Mockito.mock(ScheduledExecutorService::class.java))
        service = Robolectric.buildService(LatinIME::class.java).create()
        context = service.get().applicationContext
        context.prefs().edit()
            .putInt(Settings.PREF_VERSION_CODE, BuildConfig.VERSION_CODE)
            .putInt(Settings.PREF_EMOJI_MAX_SDK, 33)
            .commit()
    }

    @Implements(DictionaryFacilitatorImpl::class)
    class RestoreDictionaryShadow {
        @Implementation
        fun getCurrentLocale(): Locale = Locale.US

        // Native dictionary mappings keep Windows fixture files open; they are outside this import test.
        @Implementation
        @Suppress("UNUSED_PARAMETER")
        fun resetDictionaries(
            context: Context,
            newLocale: Locale,
            useContactsDict: Boolean,
            useAppsDict: Boolean,
            usePersonalizedDicts: Boolean,
            forceReloadMainDictionary: Boolean,
            dictNamePrefix: String,
            listener: DictionaryFacilitator.DictionaryInitializationListener?
        ) = Unit
    }

    @After fun tearDown() {
        Settings.getInstance().startListener()
        service.destroy()
        ExecutorUtils.setExecutorServiceForTests(null)
    }

    @Test fun sameVersionRestoreRefreshesLoadedSettingsImmediately() {
        context.prefs().edit()
            .putInt(Settings.PREF_COMBINING_GRACE_MS, 0)
            .putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, false)
            .putBoolean(Settings.PREF_COMBINING_AUTOSPACE_ONLY_AFTER_GESTURE, false)
            .commit()
        val before = Settings.getValues()
        assertEquals(0, before.mCombiningGraceMs)
        val fixture = context.prefs().all.toMutableMap().apply {
            put(Settings.PREF_COMBINING_GRACE_MS, 725)
            put(Settings.PREF_GESTURE_MANUAL_SPACING, true)
            put(Settings.PREF_COMBINING_AUTOSPACE_ONLY_AFTER_GESTURE, true)
        }
        val json = ByteArrayOutputStream().also { settingsToJsonStream(fixture, it) }
        val input = archive("preferences.json" to json.toString(Charsets.UTF_8))
        restoreBackupData(context, setOf(BackupCategory.GENERAL_SETTINGS)) { input }
        assertSame("The Settings listener is suspended during import", before, Settings.getValues())
        completeRestore(context)
        val after = Settings.getValues()
        assertEquals(725, after.mCombiningGraceMs)
        assertTrue(after.mGestureManualSpacing)
        assertTrue(after.mCombiningAutospaceOnlyAfterGesture)
        assertEquals(before.mLocale, after.mLocale)
        assertSame(before.mInputAttributes, after.mInputAttributes)
        assertEquals(before.mCurrentKeyboardScript, after.mCurrentKeyboardScript)
    }

    @Test fun validFileThenTraversalRejectsAndPreservesOriginalFiles() {
        val original = File(context.filesDir, "blacklists/original.txt").apply {
            parentFile!!.mkdirs()
            writeText("original")
        }
        val protectedOriginal = File(DeviceProtectedUtils.getFilesDir(context), "blacklists/protected.txt").apply {
            parentFile!!.mkdirs()
            writeText("protected original")
        }
        val victim = File(context.filesDir.parentFile, "victim.txt").apply { writeText("sentinel") }
        val otherOriginals = listOf(
            File(context.filesDir, "dicts/en/main_user.dict"),
            File(context.filesDir, "layouts/main/custom.test.json"),
            File(context.filesDir, "custom_font"),
            File(DeviceProtectedUtils.getFilesDir(context), "custom_background_image"),
            context.getDatabasePath(Database.NAME)
        )
        otherOriginals.forEach { it.parentFile!!.mkdirs(); it.writeText("selected original") }
        val originalPrefs = context.prefs().all
        val input = archive(
            "blacklists/ok.txt" to "new",
            "blacklists/../../victim.txt" to "overwrite"
        )
        val result = runCatching {
            restoreBackupData(context, BackupCategory.entries.toSet()) { input }
        }
        assertTrue("An unsafe archive must fail visibly", result.isFailure)
        assertEquals("original", original.readText())
        assertEquals("protected original", protectedOriginal.readText())
        assertEquals("sentinel", victim.readText())
        otherOriginals.forEach { assertEquals("selected original", it.readText()) }
        assertEquals(originalPrefs, context.prefs().all)
        assertFalse(File(context.filesDir, "blacklists/ok.txt").exists())
    }

    @Test fun malformedPreferencesRejectBeforeSelectedDataIsDeleted() {
        val original = File(context.filesDir, "blacklists/original.txt").apply {
            parentFile!!.mkdirs()
            writeText("original")
        }
        context.prefs().edit().putInt(Settings.PREF_COMBINING_GRACE_MS, 725).commit()
        val input = archive("blacklists/ok.txt" to "new", "preferences.json" to "boolean settings\n{broken")
        val result = runCatching {
            restoreBackupData(context, setOf(BackupCategory.DICTIONARY_HISTORY, BackupCategory.GENERAL_SETTINGS)) { input }
        }
        assertTrue("Malformed preferences must not be swallowed", result.isFailure)
        assertEquals("original", original.readText())
        assertEquals(725, context.prefs().getInt(Settings.PREF_COMBINING_GRACE_MS, 0))
    }

    @Test fun nullProviderStreamIsAnError() {
        assertTrue(runCatching {
            restoreBackupData(context, setOf(BackupCategory.GENERAL_SETTINGS)) { null }
        }.isFailure)
    }

    @Test fun slowStreamKeepsUiResponsiveAndDefersMutationAndCompletion() {
        val original = File(context.filesDir, "blacklists/original.txt").apply {
            parentFile!!.mkdirs()
            writeText("original")
        }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stream = object : FilterInputStream(archive("blacklists/restored.txt" to "restored")) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "Test did not release provider" }
                return super.read(buffer, offset, length)
            }
        }
        val worker = BackupRestoreOperationQueueTest.QueuedExecutor()
        val main = BackupRestoreOperationQueueTest.QueuedExecutor()
        val queue = BackupRestoreOperationQueue(worker, main)
        var completions = 0
        var uiCalls = 0
        val callbacks = BackupRestoreCallbacks({ uiCalls++ }, { uiCalls++ })
        queue.submit({
            restoreBackupData(context, setOf(BackupCategory.DICTIONARY_HISTORY)) { stream }
        }, {
            completeRestore(context)
            completions++
            callbacks.success()
        }, { throw AssertionError(it) })
        val thread = Thread(worker.take())
        thread.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals("original", original.readText())
            assertTrue(main.tasks.isEmpty())
            assertEquals(0, completions)
            // Leaving the screen drops only the UI callbacks, not the application-owned import.
            callbacks.detach()
        } finally {
            release.countDown()
            thread.join(5000)
        }
        assertFalse(thread.isAlive)
        assertEquals(0, completions)
        main.take().run()
        assertEquals(1, completions)
        assertEquals(0, uiCalls)
        assertEquals("restored", File(context.filesDir, "blacklists/restored.txt").readText())
        assertFalse(original.exists())
        assertListenerStillWorks()
    }

    @Test fun providerReadFailurePreservesDataAndListener() {
        val original = File(context.filesDir, "blacklists/original.txt").apply {
            parentFile!!.mkdirs()
            writeText("original")
        }
        val broken = object : java.io.InputStream() {
            override fun read(): Int = throw IOException("provider disconnected")
        }
        assertTrue(runCatching {
            restoreBackupData(context, setOf(BackupCategory.DICTIONARY_HISTORY)) { broken }
        }.isFailure)
        assertEquals("original", original.readText())
        assertListenerStillWorks()
        assertFalse(context.cacheDir.listFiles().orEmpty().any { it.name.startsWith("restore-") })
    }

    @Test fun directoriesAndBothPrefStoresRestore() {
        val input = archive(
            "blacklists/" to "",
            "blacklists/normal.txt" to "normal",
            "unprotected/" to "",
            "unprotected/blacklists/" to "",
            "unprotected/blacklists/device.txt" to "device",
            "preferences.json" to preferences(ints = """{"combining_grace_ms":725}"""),
            "protected_preferences.json" to preferences(booleans = """{"gesture_manual_spacing":true}"""),
        )
        restoreBackupData(context, setOf(BackupCategory.DICTIONARY_HISTORY, BackupCategory.GENERAL_SETTINGS)) { input }
        completeRestore(context)
        assertEquals("normal", File(context.filesDir, "blacklists/normal.txt").readText())
        assertEquals("device", File(DeviceProtectedUtils.getFilesDir(context), "blacklists/device.txt").readText())
        assertEquals(725, context.prefs().getInt(Settings.PREF_COMBINING_GRACE_MS, 0))
        assertTrue(context.protectedPrefs().getBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, false))
        assertListenerStillWorks()
    }

    @Test fun malformedUnselectedPreferencesStillRejectBeforeMutation() {
        val original = File(context.filesDir, "blacklists/original.txt").apply {
            parentFile!!.mkdirs()
            writeText("original")
        }
        val input = archive(
            "blacklists/ok.txt" to "replacement",
            "floating_keyboard_preferences.json" to "boolean settings\nnot json"
        )
        assertTrue(runCatching {
            restoreBackupData(context, setOf(BackupCategory.DICTIONARY_HISTORY)) { input }
        }.isFailure)
        assertEquals("original", original.readText())
    }

    @Test fun backupRoundTripPreservesBothStorageLocationsAndUnselectedData() {
        val files = listOf(
            File(context.filesDir, "blacklists/normal.txt") to "normal",
            File(DeviceProtectedUtils.getFilesDir(context), "blacklists/device.txt") to "device"
        )
        files.forEach { (file, value) -> file.parentFile!!.mkdirs(); file.writeText(value) }
        val output = ByteArrayOutputStream()
        backupData(context, setOf(BackupCategory.DICTIONARY_HISTORY)) { output }
        files.forEach { (file, _) -> file.writeText("changed") }
        context.prefs().edit().putInt(Settings.PREF_COMBINING_GRACE_MS, 725).commit()
        restoreBackupData(context, setOf(BackupCategory.DICTIONARY_HISTORY)) {
            ByteArrayInputStream(output.toByteArray())
        }
        completeRestore(context)
        files.forEach { (file, value) -> assertEquals(value, file.readText()) }
        assertEquals(725, Settings.getValues().mCombiningGraceMs)
    }

    @Test fun backupNullOutputIsAnError() {
        assertTrue(runCatching { backupData(context, setOf(BackupCategory.GENERAL_SETTINGS)) { null } }.isFailure)
    }

    @Test fun failureDuringApplyRestoresListener() {
        val database = File(context.filesDir, "fake-db").apply { writeText("original") }
        val failingContext = object : ContextWrapper(context) {
            override fun getDatabasePath(name: String): File =
                if (name == Database.NAME) database else super.getDatabasePath(name)
            override fun deleteDatabase(name: String): Boolean = throw IOException("storage failure")
        }
        assertTrue(runCatching {
            restoreBackupData(failingContext, setOf(BackupCategory.CLIPBOARD)) {
                archive("preferences.json" to preferences())
            }
        }.isFailure)
        assertListenerStillWorks()
    }

    @Test fun destroyedLifecycleDropsCallbacks() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
        }
        var callbacks = 0
        val observer = BackupRestoreCallbacks({ callbacks++ }, { callbacks++ })
        owner.lifecycle.addObserver(observer)
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        observer.success()
        observer.error("late error")
        assertEquals(0, callbacks)
    }

    @Test fun corruptEntryPreservesOriginals() {
        val original = File(context.filesDir, "blacklists/original.txt").apply {
            parentFile!!.mkdirs()
            writeText("original")
        }
        val payload = "backup payload".toByteArray()
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("blacklists/ok.txt").apply {
                method = ZipEntry.STORED
                size = payload.size.toLong()
                compressedSize = size
                crc = CRC32().apply { update(payload) }.value
            })
            zip.write(payload)
            zip.closeEntry()
        }
        val damaged = bytes.toByteArray()
        val offset = damaged.toString(Charsets.ISO_8859_1).indexOf("backup payload")
        assertTrue(offset >= 0)
        damaged[offset] = 'X'.code.toByte()
        assertTrue(runCatching {
            restoreBackupData(context, setOf(BackupCategory.DICTIONARY_HISTORY)) {
                ByteArrayInputStream(damaged)
            }
        }.isFailure)
        assertEquals("original", original.readText())
    }

    @Test fun unrelatedZipDoesNotDeleteUserData() {
        val original = File(context.filesDir, "blacklists/original.txt").apply {
            parentFile!!.mkdirs()
            writeText("original")
        }
        assertTrue(runCatching {
            restoreBackupData(context, setOf(BackupCategory.DICTIONARY_HISTORY)) {
                archive("photo.txt" to "not a backup")
            }
        }.isFailure)
        assertEquals("original", original.readText())
    }

    private fun assertListenerStillWorks() {
        context.prefs().edit().putInt(Settings.PREF_COMBINING_GRACE_MS, 913).commit()
        assertEquals(913, Settings.getValues().mCombiningGraceMs)
    }

    private fun archive(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    private fun preferences(booleans: String = "{}", ints: String = "{}") =
        "boolean settings\n$booleans\nint settings\n$ints\nlong settings\n{}\n" +
            "float settings\n{}\nstring settings\n{}\nstring set settings\n{}"
}
