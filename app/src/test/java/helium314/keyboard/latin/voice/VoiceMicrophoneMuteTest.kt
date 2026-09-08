package helium314.keyboard.latin.voice

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Looper
import com.leanbitlab.leantype.voice.VoiceConstants
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, UnavailableAudioRecord::class])
class VoiceMicrophoneMuteTest {
    private lateinit var manager: VoiceInputManager
    private lateinit var audio: AudioManager
    private lateinit var plugin: VoicePluginManager
    private val errors = mutableListOf<String>()

    @Before
    fun setUp() {
        ShadowInputMethodManager2.reset()
        val ime = spy(Robolectric.setupService(LatinIME::class.java))
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        ime.prefs().edit().putBoolean(VoiceConstants.PREF_VOICE_OFFLINE_ENABLED, true).commit()
        audio = mock(AudioManager::class.java)
        doReturn(audio).`when`(ime).getSystemService(Context.AUDIO_SERVICE)
        plugin = mock(VoicePluginManager::class.java)
        `when`(plugin.isPluginConnected()).thenReturn(true)
        manager = VoiceInputManager(ime, plugin)
        manager.setListener(object : VoiceInputManager.VoiceInputListener {
            override fun onStateChanged(state: VoiceInputManager.VoiceState) = Unit
            override fun onError(message: String) { errors.add(message) }
        })
    }

    @After
    fun tearDown() {
        manager.release()
        ShadowInputMethodManager2.reset()
    }

    @Test
    fun mutedMicrophoneFailsStartupWithoutUnmutingOrStartingPlugin() {
        `when`(audio.isMicrophoneMute).thenReturn(true)
        manager.startVoice()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("must not change the user's global microphone setting",
            mockingDetails(audio).invocations.any { it.method.name == "setMicrophoneMute" })
        assertEquals(VoiceInputManager.VoiceState.ERROR, manager.getState())
        assertTrue(errors.any { it.contains("mut", ignoreCase = true) })
        assertFalse(mockingDetails(plugin).invocations.any { it.method.name == "startSession" })
        manager.cancelVoice()
        assertEquals(VoiceInputManager.VoiceState.IDLE, manager.getState())
        assertFalse(mockingDetails(audio).invocations.any { it.method.name == "setMicrophoneMute" })
    }

    @Test
    fun unavailableAudioCaptureDoesNotStartPluginOrLeaveHandshakePending() {
        `when`(audio.isMicrophoneMute).thenReturn(false)
        manager.startVoice()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(VoiceInputManager.VoiceState.ERROR, manager.getState())
        assertFalse(manager.isRecording())
        assertTrue(errors.isNotEmpty())
        assertFalse(mockingDetails(plugin).invocations.any { it.method.name == "startSession" })
        for (name in listOf("activeSessionId", "audioPipeReadSide", "audioPipeWriteSide", "handshakeTimeoutRunnable")) {
            val field = VoiceInputManager::class.java.getDeclaredField(name).apply { isAccessible = true }
            assertNull(name, field.get(manager))
        }
        assertFalse(mockingDetails(audio).invocations.any { it.method.name == "setMicrophoneMute" })
    }
}

@Implements(AudioRecord::class)
class UnavailableAudioRecord {
    companion object {
        @JvmStatic
        @Implementation
        fun getMinBufferSize(sampleRateInHz: Int, channelConfig: Int, audioFormat: Int) = -1
    }
}
