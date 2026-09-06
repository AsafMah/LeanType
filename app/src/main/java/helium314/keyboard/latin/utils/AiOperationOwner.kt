package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import helium314.keyboard.latin.LatinIME
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal suspend fun postAiFeedback(action: () -> Unit) {
    coroutineContext[AiOperationOwner.FeedbackKey]?.post(action = action)
}

/** A worker finishing does not end ownership: its Main delivery may still be queued. */
internal class AiOperationOwner {
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0L
    var active = false
        private set

    fun invalidate(): Long {
        active = false
        return ++generation
    }

    fun begin(context: Context): Ticket {
        val id = invalidate()
        active = true
        val editor = (context as? LatinIME)?.let(AiEditorRequest::capture)
        return Ticket(id) { context !is LatinIME || editor?.isCurrent() == true }
    }

    fun postIfIdle(id: Long, action: () -> Unit) {
        handler.post { if (generation == id && !active) action() }
    }

    object FeedbackKey : CoroutineContext.Key<Ticket>

    inner class Ticket(private val id: Long, private val validEditor: () -> Boolean) :
        AbstractCoroutineContextElement(FeedbackKey) {
        fun post(complete: Boolean = false, action: () -> Unit) {
            handler.post {
                if (generation != id) return@post
                val valid = validEditor()
                if (complete) active = false
                if (valid) action()
            }
        }
    }
}
