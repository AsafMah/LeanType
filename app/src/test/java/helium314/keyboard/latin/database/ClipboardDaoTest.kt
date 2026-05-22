// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.database

import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.ClipboardHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipboardDaoTest {
    private lateinit var dao: ClipboardDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        resetSingleton(ClipboardDao::class.java)
        resetSingleton(Database::class.java)
        context.deleteDatabase(Database.NAME)
        dao = ClipboardDao.getInstance(context)!!
        dao.listener = null
    }

    private fun resetSingleton(owner: Class<*>) {
        owner.declaredFields.firstOrNull { it.name == "instance" }?.let { field ->
            field.isAccessible = true
            (field.get(null) as? SQLiteOpenHelper)?.close()
            field.set(null, null)
        }
    }

    @Test
    fun updateTextChangesClipAndMovesItToTop() {
        dao.addClip(100L, false, "old")
        val id = dao.getClips().single().id

        val newPosition = dao.updateText(id, "new")

        assertEquals(0, newPosition)
        assertEquals("new", dao.get(id).text)
        assertTrue(dao.get(id).timeStamp >= 100L)
    }

    @Test
    fun updateTextWithEmptyTextDeletesClipAndReturnsDeletedEntry() {
        dao.addClip(100L, false, "old")
        val id = dao.getClips().single().id
        val deleted = arrayOfNulls<ClipboardHistoryEntry>(1)

        val newPosition = dao.updateText(id, "", deleted)

        assertEquals(-1, newPosition)
        assertEquals(0, dao.count())
        assertEquals(id, deleted[0]?.id)
        assertEquals("old", deleted[0]?.text)
    }

    @Test
    fun updateTextMergesDuplicateTextAndPreservesPinnedState() {
        dao.addClip(100L, false, "old")
        val editedId = dao.getClips().single().id
        dao.addClip(200L, true, "duplicate")

        val newPosition = dao.updateText(editedId, "duplicate")

        assertEquals(0, newPosition)
        assertEquals(1, dao.count())
        val remaining = dao.getClips().single()
        assertEquals(editedId, remaining.id)
        assertEquals("duplicate", remaining.text)
        assertTrue(remaining.isPinned)
    }

    @Test
    fun updateTextIgnoresImageClips() {
        dao.addClip(100L, false, "[Image]", "image.png")
        val id = dao.getClips().single().id

        val newPosition = dao.updateText(id, "new")

        assertEquals(-1, newPosition)
        assertEquals("[Image]", dao.get(id).text)
        assertEquals("image.png", dao.get(id).imageUri)
    }

    @Test
    fun updateTextReturnsMinusOneWhenClipDoesNotExist() {
        val deleted = arrayOfNulls<ClipboardHistoryEntry>(1)

        val newPosition = dao.updateText(123L, "new", deleted)

        assertEquals(-1, newPosition)
        assertEquals(0, dao.count())
        assertNull(deleted[0])
    }
}
