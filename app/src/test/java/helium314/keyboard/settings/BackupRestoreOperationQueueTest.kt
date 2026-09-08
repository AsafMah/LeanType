// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import helium314.keyboard.settings.preferences.BackupRestoreOperationQueue
import helium314.keyboard.settings.preferences.BackupRestoreCallbacks
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class BackupRestoreOperationQueueTest {
    @Test fun submittingSlowProviderReturnsWithoutWaitingOrEarlyCompletion() {
        val worker = QueuedExecutor()
        val main = QueuedExecutor()
        val queue = BackupRestoreOperationQueue(worker, main)
        val returned = CountDownLatch(1)
        var completed = 0
        var opened = false
        val caller = Thread {
            queue.submit({ opened = true }, { completed++ }, { throw AssertionError(it) })
            returned.countDown()
        }
        caller.start()
        val task = worker.take()
        try {
            assertTrue("ActivityResult callback must return while provider is pending", returned.await(1, TimeUnit.SECONDS))
            assertFalse(opened)
            assertEquals(0, completed)
            assertTrue(main.tasks.isEmpty())
        } finally {
            task.run()
            caller.join(5000)
        }
        main.take().run()
        assertTrue(opened)
        assertEquals(1, completed)
    }

    @Test fun operationsStaySerializedThroughMainThreadFinalization() {
        val worker = QueuedExecutor()
        val main = QueuedExecutor()
        val queue = BackupRestoreOperationQueue(worker, main)
        val events = mutableListOf<String>()
        queue.submit({ events.add("work1") }, { events.add("complete1") }, { throw AssertionError(it) })
        queue.submit({ events.add("work2") }, { events.add("complete2") }, { throw AssertionError(it) })
        assertEquals(1, worker.tasks.size)
        worker.take().run()
        assertTrue(worker.tasks.isEmpty())
        assertEquals(listOf("work1"), events)
        main.take().run()
        assertEquals(listOf("work1", "complete1"), events)
        worker.take().run()
        main.take().run()
        assertEquals(listOf("work1", "complete1", "work2", "complete2"), events)
        assertTrue(main.tasks.isEmpty())
    }

    @Test fun failureCallsOnlyErrorOnMainAndAllowsNextOperation() {
        val worker = QueuedExecutor()
        val main = QueuedExecutor()
        val queue = BackupRestoreOperationQueue(worker, main)
        var errors = 0
        var successes = 0
        queue.submit({ throw IllegalStateException("provider failed") }, { successes++ }, {
            assertEquals("provider failed", it.message)
            errors++
        })
        queue.submit({}, { successes++ }, { throw AssertionError(it) })
        worker.take().run()
        assertEquals(0, errors)
        assertEquals(0, successes)
        main.take().run()
        assertEquals(1, errors)
        assertEquals(0, successes)
        worker.take().run()
        main.take().run()
        assertEquals(1, successes)
    }

    @Test fun detachingUiDoesNotCancelWorkOrRuntimeFinalization() {
        val worker = QueuedExecutor()
        val main = QueuedExecutor()
        val queue = BackupRestoreOperationQueue(worker, main)
        var runtimeReloads = 0
        var uiCallbacks = 0
        val callbacks = BackupRestoreCallbacks({ uiCallbacks++ }, { uiCallbacks++ })
        queue.submit({}, { runtimeReloads++; callbacks.success() }, { callbacks.error("error") })
        callbacks.detach()
        worker.take().run()
        main.take().run()
        assertEquals(1, runtimeReloads)
        assertEquals(0, uiCallbacks)
        callbacks.error("late failure")
        assertEquals(0, uiCallbacks)
    }

    @Test fun finalizationFailureIsReportedOnceAndReleasesQueue() {
        val worker = QueuedExecutor()
        val main = QueuedExecutor()
        val queue = BackupRestoreOperationQueue(worker, main)
        var errors = 0
        queue.submit({}, { error("reload failed") }, { errors++ })
        worker.take().run()
        main.take().run()
        assertEquals(1, errors)
        queue.submit({}, {}, { throw AssertionError(it) })
        worker.take().run()
        main.take().run()
        assertTrue(worker.tasks.isEmpty())
    }

    internal class QueuedExecutor : Executor {
        val tasks = LinkedBlockingQueue<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun take(): Runnable = checkNotNull(tasks.poll(5, TimeUnit.SECONDS)) { "No scheduled task" }
    }
}
