package helium314.keyboard.keyboard

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import helium314.keyboard.latin.R
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class TouchpadTapTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var surface: View
    private lateinit var listener: TouchpadView.TouchpadListener
    private var downTime = 0L

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        val touchpad = TouchpadView(controller.get())
        listener = mock(TouchpadView.TouchpadListener::class.java)
        touchpad.setTouchpadListener(listener)
        controller.get().setContentView(touchpad)
        surface = touchpad.findViewById(R.id.touchpad_surface)
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    @Test
    fun finalFingerUpKeepsPendingSingleTap() {
        twoFingerTap()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(251))
        verify(listener).onSingleTap()
        verify(listener, never()).onTwoFingerDoubleTap()
    }

    @Test
    fun secondTwoFingerTapWithinWindowProducesOnlyDoubleTap() {
        twoFingerTap()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
        twoFingerTap()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(251))
        verify(listener).onTwoFingerDoubleTap()
        verify(listener, never()).onSingleTap()
    }

    @Test
    fun cancelledSequenceDiscardsPendingTap() {
        twoFingerTap()
        send(MotionEvent.ACTION_CANCEL, 1)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        verifyNoInteractions(listener)
    }

    @Test
    fun followingTwoFingerDragDoesNotLeaveTapCountForNextTap() {
        twoFingerTap()
        send(MotionEvent.ACTION_DOWN, 1)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), 2)
        send(MotionEvent.ACTION_MOVE, 2, 20f)
        send(MotionEvent.ACTION_POINTER_UP or (1 shl 8), 2, 20f)
        send(MotionEvent.ACTION_UP, 1, 20f)
        twoFingerTap()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(251))
        verify(listener).onSingleTap()
        verify(listener, never()).onTwoFingerDoubleTap()
    }

    @Test
    fun thirdFingerDiscardsPendingTwoFingerTap() {
        twoFingerTap()
        send(MotionEvent.ACTION_DOWN, 1)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), 2)
        send(MotionEvent.ACTION_POINTER_DOWN or (2 shl 8), 3)
        send(MotionEvent.ACTION_POINTER_UP or (2 shl 8), 3)
        send(MotionEvent.ACTION_POINTER_UP or (1 shl 8), 2)
        send(MotionEvent.ACTION_UP, 1)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        verifyNoInteractions(listener)
    }

    private fun twoFingerTap() {
        send(MotionEvent.ACTION_DOWN, 1)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), 2)
        send(MotionEvent.ACTION_POINTER_UP or (1 shl 8), 2)
        send(MotionEvent.ACTION_UP, 1)
    }

    private fun send(action: Int, count: Int, offset: Float = 0f) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = now
        val properties = Array(count) { i ->
            MotionEvent.PointerProperties().apply {
                id = i
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(count) { i ->
            MotionEvent.PointerCoords().apply {
                x = 100f + i * 30 + offset
                y = 100f
                pressure = 1f
                size = 1f
            }
        }
        val event = MotionEvent.obtain(downTime, now, action, count, properties, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        surface.dispatchTouchEvent(event)
        event.recycle()
    }
}
