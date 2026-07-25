package helium314.keyboard.compat

import android.app.Dialog
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Binder
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodSubtype
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.latin.RichInputMethodManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], shadows = [ShadowInputMethodManager2::class])
class ImeCompatTest {
    @BeforeTest
    fun setUp() {
        ShadowInputMethodManager2.reset()
        RichInputMethodManager.init(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun preAndroidPDirectImeSwitchUsesInputMethodManager() {
        val service = serviceWithWindowToken()

        ImeCompat.run { service.switchInputMethodCompat(EXTERNAL_IME.id) }

        assertEquals(EXTERNAL_IME.id, ShadowInputMethodManager2.switchedImeId)
        assertNull(ShadowInputMethodManager2.switchedSubtype)
    }

    @Test
    fun preAndroidPNextImeWithoutWindowTokenIsNoOp() {
        val service = serviceWithWindowToken(null)

        val switched = ImeCompat.run { service.switchInputMethod() }

        assertEquals(false, switched)
        assertEquals(false, ShadowInputMethodManager2.switchedToNextInputMethod)
    }

    @Test
    fun preAndroidPDirectImeWithoutWindowTokenIsNoOp() {
        val service = serviceWithWindowToken(null)

        ImeCompat.run { service.switchInputMethodCompat(EXTERNAL_IME.id) }
        ImeCompat.run { service.switchInputMethodAndSubtypeCompat(EXTERNAL_IME, EXTERNAL_SUBTYPE) }

        assertNull(ShadowInputMethodManager2.switchedImeId)
        assertNull(ShadowInputMethodManager2.switchedSubtype)
    }

    @Test
    fun preAndroidPDirectImeSubtypeSwitchUsesInputMethodManager() {
        val service = serviceWithWindowToken()

        ImeCompat.run { service.switchInputMethodAndSubtypeCompat(EXTERNAL_IME, EXTERNAL_SUBTYPE) }

        assertEquals(EXTERNAL_IME.id, ShadowInputMethodManager2.switchedImeId)
        assertEquals(EXTERNAL_SUBTYPE, ShadowInputMethodManager2.switchedSubtype)
    }

    private fun serviceWithWindowToken(token: android.os.IBinder? = Binder()): InputMethodService {
        val service = mock(InputMethodService::class.java)
        val dialog = mock(Dialog::class.java)
        val window = mock(Window::class.java)
        val attributes = WindowManager.LayoutParams().apply { this.token = token }
        `when`(service.window).thenReturn(dialog)
        `when`(dialog.window).thenReturn(window)
        `when`(window.attributes).thenReturn(attributes)
        return service
    }

    companion object {
        private val EXTERNAL_IME = InputMethodInfo(
            "example.ime",
            "example.ime.Service",
            "Example IME",
            null,
        )
        private val EXTERNAL_SUBTYPE: InputMethodSubtype = InputMethodSubtype.InputMethodSubtypeBuilder()
            .setSubtypeId(202)
            .setLanguageTag("fr-FR")
            .setSubtypeLocale("fr_FR")
            .setSubtypeMode("keyboard")
            .build()
    }
}
