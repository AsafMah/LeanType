package helium314.keyboard.keyboard.internal

import helium314.keyboard.latin.common.InputPointers
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BatchInputArbiterTest {

    @Test
    fun `raw pointer one anchors track zero through update and end`() {
        val arbiter = BatchInputArbiter(1, GestureStrokeRecognitionParams.DEFAULT)
        arbiter.setKeyboardGeometry(100, 500)
        val points = BatchInputArbiter::class.java.getDeclaredField("mRecognitionPoints").run {
            isAccessible = true
            get(arbiter) as GestureStrokeRecognitionPoints
        }
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

    private class RecordingListener : BatchInputArbiter.BatchInputArbiterListener {
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
            endedSize = aggregatedPointers.pointerSize
            endedPointerIds = aggregatedPointers.pointerIds.copyOf(endedSize)
        }
    }
}
