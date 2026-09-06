package helium314.keyboard.keyboard.clipboard

import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.database.Database
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipboardClipEditActivityTest {
    private lateinit var context: Context
    private lateinit var dao: ClipboardDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        for (owner in listOf(ClipboardDao::class.java, Database::class.java)) {
            owner.declaredFields.first { it.name == "instance" }.apply {
                isAccessible = true
                (get(null) as? SQLiteOpenHelper)?.close()
                set(null, null)
            }
        }
        context.deleteDatabase(Database.NAME)
        dao = ClipboardDao.getInstance(context)!!
        dao.listener = null
        dao.addClip(100L, false, "original clip")
    }

    @Test
    fun recreationRestoresUnsavedDraftAndSelectionWithoutSavingClip() {
        val id = dao.getClips().single().id
        val controller = Robolectric.buildActivity(ClipboardClipEditActivity::class.java,
            ClipboardClipEditActivity.createIntent(context, id)).setup()
        val editor = findEditor(controller.get().window.decorView)!!
        editor.setText("unsaved replacement\nsecond line")
        editor.setSelection(3, 12)

        controller.recreate()

        val restored = findEditor(controller.get().window.decorView)!!
        assertEquals("unsaved replacement\nsecond line", restored.text.toString())
        assertEquals(3, restored.selectionStart)
        assertEquals(12, restored.selectionEnd)
        assertEquals("original clip", dao.get(id).text)
        controller.pause().stop().destroy()
    }

    @Test
    fun recreationPreservesEmptyDraftRatherThanReloadingDatabaseText() {
        val id = dao.getClips().single().id
        val controller = Robolectric.buildActivity(ClipboardClipEditActivity::class.java,
            ClipboardClipEditActivity.createIntent(context, id)).setup()
        findEditor(controller.get().window.decorView)!!.setText("")
        controller.recreate()
        assertEquals("", findEditor(controller.get().window.decorView)!!.text.toString())
        assertEquals("original clip", dao.get(id).text)
        controller.pause().stop().destroy()
    }

    private fun findEditor(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findEditor(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
