package helium314.keyboard.keyboard.internal

import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.common.InputPointers
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class])
class BatchInputArbiterTest {
    @Before
    fun setUp() {
        Robolectric.setupService(LatinIME::class.java)
    }

    @Test
    fun `raw pointer one anchors track zero through update and end`() {
        val arbiter = BatchInputArbiter(1, GestureStrokeRecognitionParams.DEFAULT)
        arbiter.setKeyboardGeometry(100, 500)
        val points = recognitionPoints(arbiter)
        val listener = RecordingListener()

        points.addEventPoint(0, 0, 0, true)
        points.addEventPoint(200, 0, 10, true)
        points.addEventPoint(400, 0, 40, true)
        assertTrue(arbiter.mayStartBatchInput(listener))

        points.addEventPoint(420, 0, 140, true)
        arbiter.updateBatchInput(140, listener)

        assertEquals(4, listener.updatedSize)
        assertContentEquals(intArrayOf(0, 0, 0, 0), listener.updatedPointerIds)

        points.addEventPoint(440, 0, 200, true)
        assertTrue(arbiter.mayEndBatchInput(200, 1, listener))

        assertEquals(5, listener.endedSize)
        assertContentEquals(intArrayOf(0, 0, 0, 0, 0), listener.endedPointerIds)
    }

    @Test
    fun `only the final finger completes the normalized aggregate`() {
        val first = BatchInputArbiter(5, GestureStrokeRecognitionParams.DEFAULT)
        val second = BatchInputArbiter(7, GestureStrokeRecognitionParams.DEFAULT)
        first.setKeyboardGeometry(100, 500)
        second.setKeyboardGeometry(100, 500)
        val points = recognitionPoints(first)
        val listener = RecordingListener()
        points.addEventPoint(0, 0, 0, true)
        points.addEventPoint(200, 0, 10, true)
        points.addEventPoint(400, 0, 40, true)
        assertTrue(first.mayStartBatchInput(listener))
        recognitionPoints(second).apply {
            addEventPoint(100, 100, 45, true)
            addEventPoint(300, 100, 90, true)
        }

        assertFalse(first.mayEndBatchInput(100, 2, listener))
        assertEquals(0, listener.endCount)
        assertTrue(second.mayEndBatchInput(120, 1, listener))
        assertEquals(1, listener.endCount)
        assertContentEquals(intArrayOf(0, 0, 0, 1, 1), listener.endedPointerIds)
    }

    @Test
    fun `fresh gesture restarts elapsed time while overlapping fingers share it`() {
        val first = BatchInputArbiter(0, GestureStrokeRecognitionParams.DEFAULT)
        val second = BatchInputArbiter(1, GestureStrokeRecognitionParams.DEFAULT)
        first.setKeyboardGeometry(100, 500)
        second.setKeyboardGeometry(100, 500)
        first.addDownEventPoint(0, 0, 100, 0, 1)
        second.addDownEventPoint(100, 0, 150, 0, 2)
        assertEquals(70, second.getElapsedTimeSinceFirstDown(170))

        first.addDownEventPoint(0, 0, 500, 0, 1)
        assertEquals(30, first.getElapsedTimeSinceFirstDown(530))
    }

    private fun recognitionPoints(arbiter: BatchInputArbiter) =
        BatchInputArbiter::class.java.getDeclaredField("mRecognitionPoints").run {
            isAccessible = true
            get(arbiter) as GestureStrokeRecognitionPoints
        }

    private class RecordingListener : BatchInputArbiter.BatchInputArbiterListener {
        var endCount = 0
        var updatedSize = 0
        var updatedPointerIds = intArrayOf()
        var endedSize = 0
        var endedPointerIds = intArrayOf()

        override fun onStartBatchInput() = Unit

        override fun onUpdateBatchInput(
            aggregatedPointers: InputPointers,
            moveEventTime: Long,
        ) {
            updatedSize = aggregatedPointers.pointerSize
            updatedPointerIds = aggregatedPointers.pointerIds.copyOf(updatedSize)
        }

        override fun onStartUpdateBatchInputTimer() = Unit

        override fun onEndBatchInput(
            aggregatedPointers: InputPointers,
            upEventTime: Long,
        ) {
            endCount++
            endedSize = aggregatedPointers.pointerSize
            endedPointerIds = aggregatedPointers.pointerIds.copyOf(endedSize)
        }
    }
}
