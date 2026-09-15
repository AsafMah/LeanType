// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.settings.SettingsActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

internal interface MediaPickerHost {
    val editorVersion: Long
    val privateMode: Boolean
    val typingListener: KeyboardActionListener
    fun supports(mimeType: String): Boolean
    fun insert(media: StagedMedia, editorVersion: Long): Boolean
    fun returnToTyping()
}

internal class MediaPickerView(
    context: Context,
    private val host: MediaPickerHost,
    private val source: MediaSource = GiphyService(context)
) : LinearLayout(context) {
    private val state = MediaSearchState()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var requestJob: Job? = null
    private var sendJob: Job? = null
    private var previews: MediaPreviewLoader? = null
    private var mediaFiles: MediaFiles? = null
    private var editorVersion = -1L
    private var keyVersion = -1L
    private var active = false
    private var editing = false
    private var alphabet = KeyboardId.ELEMENT_ALPHABET
    private var layoutSet: KeyboardLayoutSet? = null
    private var configuredKeyboardHeight = 0
    private var pendingShare: StagedMedia? = null

    private val field = EditText(context).apply {
        id = R.id.media_query
        hint = context.getString(R.string.media_search_hint)
        setSingleLine()
        showSoftInputOnFocus = false
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        doAfterTextChanged { state.tab.draft = it.toString() }
        setOnClickListener { showQueryKeyboard() }
        setOnEditorActionListener { _, _, _ -> search(false); true }
    }
    private val status = TextView(context).apply {
        id = R.id.media_status
        gravity = Gravity.CENTER
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val keyboard = MainKeyboardView(context).apply { id = R.id.media_keyboard }
    private val attribution = ImageView(context).apply {
        contentDescription = context.getString(R.string.media_giphy_attribution)
        scaleType = ImageView.ScaleType.FIT_END
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }
    private val controls = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val grid = RecyclerView(context).apply {
        id = R.id.media_results
        layoutManager = GridLayoutManager(context, 3)
    }
    private val share = Button(context).apply {
        id = R.id.media_share
        setText(R.string.media_share_action)
        visibility = GONE
        setOnClickListener { shareSelected() }
    }
    private val more = Button(context).apply {
        id = R.id.media_more
        setText(R.string.media_more_action)
        visibility = GONE
        setOnClickListener { search(true) }
    }
    private val setup = Button(context).apply {
        setText(R.string.media_key_settings)
        visibility = GONE
        setOnClickListener {
            stop()
            context.startActivity(Intent(context, SettingsActivity::class.java)
                .putExtra("screen", "advanced").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    private val adapter = MediaAdapter()
    private val queryInput = MediaQueryInput(field, { search(false) }, ::layoutKey) {
        RichInputMethodManager.getInstance().currentSubtype.isRtlSubtype
    }
    private val queryListener = object : KeyboardActionListener by queryInput {
        override fun onPressKey(primaryCode: Int, repeatCount: Int, isSinglePointer: Boolean, hapticEvent: HapticEvent) {
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(primaryCode, keyboard, hapticEvent)
        }
    }

    init {
        orientation = VERTICAL
        controls.addView(field, LayoutParams(0, dp(48), 1f))
        controls.addView(Button(context).apply {
            id = R.id.media_clear
            text = "\u00d7"
            contentDescription = context.getString(R.string.media_clear_query)
            setOnClickListener {
                requestJob?.cancel()
                sendJob?.cancel()
                state.clearQuery()
                field.setText("")
                pendingShare = null
                share.visibility = GONE
                refreshResults()
                showQueryKeyboard()
            }
        }, LayoutParams(dp(48), dp(48)))
        controls.addView(Button(context).apply {
            id = R.id.media_submit
            setText(R.string.media_search_action)
            setOnClickListener { search(false) }
        }, LayoutParams(WRAP_CONTENT, dp(48)))
        addView(controls)
        addView(status, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(setup, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(share, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        grid.adapter = adapter
        grid.setItemViewCacheSize(0)
        addView(grid, LayoutParams(MATCH_PARENT, 0, 1f))
        addView(more, LayoutParams(MATCH_PARENT, dp(40)))
        addView(keyboard, LayoutParams(MATCH_PARENT, 0, 1f))
        addView(footer.apply {
            addView(Button(context).apply {
                setText(R.string.media_typing_action)
                setOnClickListener { host.returnToTyping() }
            }, LayoutParams(WRAP_CONTENT, dp(40)))
            addView(attribution, LayoutParams(0, dp(40), 1f))
        })
        updateColors()
    }

    fun updateColors() {
        val colors = Settings.getValues().mColors
        colors.setBackground(this, ColorType.MAIN_BACKGROUND)
        fun apply(view: View) {
            if (view is TextView) view.setTextColor(colors.get(ColorType.KEY_TEXT))
            if (view is Button) {
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND))
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) apply(view.getChildAt(index))
        }
        apply(this)
        field.setHintTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        attribution.setImageResource(
            if (ColorUtils.calculateLuminance(colors.get(ColorType.MAIN_BACKGROUND)) < 0.5)
                R.drawable.giphy_attribution_dark else R.drawable.giphy_attribution_light
        )
    }

    fun open(kind: MediaKind) {
        requestJob?.cancel()
        sendJob?.cancel()
        pendingShare = null
        share.visibility = GONE
        state.select(kind)
        if (!active) {
            active = true
            editorVersion = host.editorVersion
            alphabet = KeyboardSwitcher.getInstance().activeAlphabetKeyboardId
            previews = MediaPreviewLoader(context, source)
            val files = MediaFiles(context, source, STAGING_RETENTION)
            mediaFiles = files
            scope.launch {
                try {
                    files.cleanup()
                } catch (e: MediaException) {
                    if (active) showError(e.reason)
                }
            }
        }
        try {
            val version = source.credentialVersion
            if (keyVersion != version) {
                state.reset()
                keyVersion = version
            }
            field.setText(state.tab.draft)
            setup.visibility = GONE
            refreshResults()
            if (allowed() && !source.hasApiKey()) {
                showError(MediaError.MISSING_KEY)
                setup.visibility = VISIBLE
            }
        } catch (e: MediaException) {
            showError(e.reason)
            setup.visibility = VISIBLE
        }
        showResults()
        requestLayout()
    }

    private fun allowed(): Boolean {
        if (!active || host.editorVersion != editorVersion) return false
        if (!source.available) { showError(MediaError.UNAVAILABLE); return false }
        if (host.privateMode) {
            requestJob?.cancel()
            sendJob?.cancel()
            state.reset()
            refreshResults()
            showError(MediaError.UNAVAILABLE)
            return false
        }
        return true
    }

    private fun fresh(generation: Long): Boolean = try {
        allowed() && state.generation == generation && source.credentialVersion == keyVersion
    } catch (e: MediaException) {
        showError(e.reason)
        false
    }

    private fun search(more: Boolean) {
        if (!allowed() || sendJob?.isActive == true) return
        requestJob?.cancel()
        pendingShare = null
        share.visibility = GONE
        try {
            if (!source.hasApiKey()) {
                showError(MediaError.MISSING_KEY)
                setup.visibility = VISIBLE
                return
            }
            if (source.credentialVersion != keyVersion) {
                state.reset()
                keyVersion = source.credentialVersion
                state.tab.draft = field.text.toString()
            }
            val language = RichInputMethodManager.getInstance().currentSubtype.locale.toLanguageTag()
            val request = state.request(language, more) ?: run {
                status.setText(R.string.media_enter_query)
                return
            }
            showResults()
            adapter.notifyDataSetChanged()
            status.setText(R.string.media_loading)
            this.more.visibility = GONE
            requestJob = scope.launch {
                try {
                    val page = source.search(request.kind, request.query, request.language, request.offset)
                    if (fresh(request.generation) && state.accept(request, page)) refreshResults()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: MediaException) {
                    if (fresh(request.generation)) showError(e.reason)
                }
            }
        } catch (e: MediaException) {
            showError(e.reason)
        }
    }

    private fun refreshResults() {
        adapter.notifyDataSetChanged()
        status.text = when {
            state.tab.submitted.isEmpty() -> context.getString(R.string.media_enter_query)
            state.tab.items.isEmpty() -> context.getString(R.string.media_no_results)
            else -> context.getString(R.string.media_results_for, state.tab.submitted)
        }
        more.visibility = if (!editing && state.tab.nextOffset != null) VISIBLE else GONE
        status.visibility = if (editing) GONE else VISIBLE
    }

    private fun showQueryKeyboard() {
        if (!active || !allowed()) return
        if (editing) return
        editing = true
        clearVisiblePreviews()
        grid.visibility = GONE
        more.visibility = GONE
        footer.visibility = GONE
        status.visibility = GONE
        keyboard.visibility = VISIBLE
        val settings = Settings.getValues()
        val viewportHeight = height.takeIf { it > 0 } ?: (ResourceUtils.getSecondaryKeyboardHeight(resources, settings) - dp(40))
        configureQueryKeyboard((viewportHeight - dp(48) -
            if (setup.visibility == VISIBLE) setup.measuredHeight else 0).coerceAtLeast(1), alphabet)
        PointerTracker.switchTo(keyboard)
        keyboard.setKeyboardActionListener(queryListener)
        field.requestFocus()
        field.setSelection(field.length())
    }

    private fun configureQueryKeyboard(height: Int, element: Int) {
        val settings = Settings.getValues()
        configuredKeyboardHeight = height
        val layouts = KeyboardLayoutSet.Builder(context, null)
            .setSubtype(RichInputMethodManager.getInstance().currentSubtype)
            .setKeyboardOptions(settings)
            .setInternalAction(KeyboardLayoutSet.InternalAction(
                helium314.keyboard.latin.common.Constants.CODE_ENTER, context.getString(R.string.media_search_action)
            ))
            .setKeyboardGeometry(ResourceUtils.getKeyboardWidth(context, settings), height)
            .build()
        layoutSet = layouts
        keyboard.setKeyboard(layouts.getKeyboard(element))
        keyboard.setKeyPreviewPopupEnabled(settings.mKeyPreviewPopupOn)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!editing || !active || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) return
        val otherHeight = (0 until childCount).map { getChildAt(it) }
            .filter { it !== keyboard && it.visibility != GONE }.sumOf { it.measuredHeight }
        val available = (MeasureSpec.getSize(heightMeasureSpec) - otherHeight - paddingTop - paddingBottom).coerceAtLeast(1)
        if (available != configuredKeyboardHeight) {
            configureQueryKeyboard(available, keyboard.keyboard?.mId?.mElementId ?: alphabet)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private fun showResults() {
        editing = false
        keyboard.visibility = GONE
        releaseQueryKeyboard()
        grid.visibility = VISIBLE
        footer.visibility = VISIBLE
        status.visibility = VISIBLE
        adapter.notifyDataSetChanged()
        more.visibility = if (state.tab.nextOffset != null) VISIBLE else GONE
        field.clearFocus()
    }

    private fun layoutKey(code: Int) {
        val layouts = layoutSet ?: return
        val current = keyboard.keyboard?.mId?.mElementId ?: alphabet
        val symbols = current == KeyboardId.ELEMENT_SYMBOLS || current == KeyboardId.ELEMENT_SYMBOLS_SHIFTED
        val next = if (code == KeyCode.SHIFT || code == KeyCode.CAPS_LOCK) {
            when (current) {
                KeyboardId.ELEMENT_SYMBOLS -> KeyboardId.ELEMENT_SYMBOLS_SHIFTED
                KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> KeyboardId.ELEMENT_SYMBOLS
                KeyboardId.ELEMENT_ALPHABET -> KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED
                else -> alphabet
            }
        } else if (symbols) alphabet else KeyboardId.ELEMENT_SYMBOLS
        keyboard.setKeyboard(layouts.getKeyboard(next))
    }

    fun onBack(): Boolean {
        if (!active) return false
        if (editing) showResults() else host.returnToTyping()
        return true
    }

    fun onHardwareKey(keyCode: Int, event: KeyEvent): Boolean {
        if (!active) return false
        if (keyCode == KeyEvent.KEYCODE_BACK) return if (event.repeatCount == 0) onBack() else true
        if (!editing) showQueryKeyboard()
        return queryInput.onKeyDown(keyCode, event)
    }

    fun stop() {
        active = false
        editing = false
        state.reset()
        scope.coroutineContext.cancelChildren()
        requestJob = null
        sendJob = null
        pendingShare = null
        releaseQueryKeyboard()
        previews?.close()
        previews = null
        mediaFiles?.close()
        mediaFiles = null
        layoutSet = null
        field.setText("")
        field.clearFocus()
        status.setText(R.string.media_enter_query)
        share.visibility = GONE
        more.visibility = GONE
        adapter.notifyDataSetChanged()
    }

    private fun clearVisiblePreviews() {
        for (index in 0 until grid.childCount) {
            (grid.getChildViewHolder(grid.getChildAt(index)) as? MediaHolder)?.let { previews?.clear(it.image) }
        }
    }

    private fun releaseQueryKeyboard() {
        if (PointerTracker.replaceKeyboardActionListener(queryListener, host.typingListener)) {
            keyboard.cancelAllOngoingEvents()
        }
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun select(item: MediaItem) {
        if (!allowed() || sendJob?.isActive == true || requestJob?.isActive == true) return
        pendingShare = null
        val candidates = item.renditions.filter {
            it.mimeType in setOf("image/gif", "image/webp") &&
                (it.byteSize == null || it.byteSize <= MediaLimits.MEDIA_BYTES)
        }.sortedWith(compareBy<MediaRendition> { it.preview }
            .thenByDescending { it.width.toLong() * it.height })
        val selected = candidates.firstOrNull { host.supports(it.mimeType) } ?: candidates.firstOrNull()
        if (selected == null) { showError(MediaError.UNSUPPORTED); return }
        val generation = state.generation
        val files = mediaFiles ?: return
        status.setText(R.string.media_downloading)
        share.visibility = GONE
        sendJob = scope.launch {
            try {
                val media = files.stage(selected, item.title)
                if (!fresh(generation)) return@launch
                insertStagedMedia(media)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MediaException) {
                if (fresh(generation)) {
                    showError(e.reason)
                    share.visibility = if (pendingShare != null) VISIBLE else GONE
                }
            }
        }
    }

    internal fun insertStagedMedia(media: StagedMedia) {
        val generation = state.generation
        if (!fresh(generation)) return
        pendingShare = media
        val inserted = try {
            host.supports(media.mimeType) && host.insert(media, editorVersion)
        } catch (e: RuntimeException) {
            android.util.Log.w("MediaPicker", "Content handoff failed (${e.javaClass.simpleName})")
            false
        } catch (_: android.os.RemoteException) {
            android.util.Log.w("MediaPicker", "Content handoff failed (RemoteException)")
            false
        }
        if (!fresh(generation)) {
            pendingShare = null
            return
        }
        if (inserted) {
            pendingShare = null
            host.returnToTyping()
        } else {
            status.visibility = VISIBLE
            status.setText(R.string.media_unsupported)
            share.visibility = VISIBLE
        }
    }

    private fun shareSelected() {
        val media = pendingShare ?: return
        try {
            if (!fresh(state.generation)) return
            MediaContent.share(context, media)
        } catch (e: MediaException) {
            showError(e.reason)
        } catch (e: RuntimeException) {
            android.util.Log.w("MediaPicker", "Share chooser failed (${e.javaClass.simpleName})")
            status.setText(R.string.media_share_error)
        }
    }

    private fun showError(error: MediaError) {
        status.visibility = VISIBLE
        status.setText(when (error) {
            MediaError.UNAVAILABLE -> R.string.media_unavailable
            MediaError.MISSING_KEY -> R.string.media_missing_key
            MediaError.LOCKED -> R.string.media_key_locked
            MediaError.INVALID_KEY -> R.string.media_invalid_key
            MediaError.QUOTA -> R.string.media_quota
            MediaError.QUERY_TOO_LONG -> R.string.media_query_long
            MediaError.TOO_LARGE -> R.string.media_too_large
            MediaError.UNSUPPORTED -> R.string.media_format_unsupported
            MediaError.STORAGE -> R.string.media_storage_error
            MediaError.INVALID_RESPONSE -> R.string.media_invalid_response
            MediaError.NETWORK -> R.string.media_network_error
        })
    }

    private inner class MediaAdapter : RecyclerView.Adapter<MediaHolder>() {
        override fun getItemCount() = state.tab.items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaHolder {
            val container = LinearLayout(context).apply { orientation = VERTICAL }
            val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            val label = TextView(context).apply {
                maxLines = 2
                textSize = 10f
                setTextColor(Settings.getValues().mColors.get(ColorType.KEY_TEXT))
            }
            container.addView(image, LayoutParams(MATCH_PARENT, dp(72)))
            container.addView(label, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            return MediaHolder(container, image, label)
        }
        override fun onBindViewHolder(holder: MediaHolder, position: Int) {
            previews?.clear(holder.image)
            val item = state.tab.items[position]
            holder.item = item
            holder.image.contentDescription = item.title
            holder.label.text = item.sourceName.ifBlank {
                item.sourceUrl.ifBlank { context.getString(R.string.media_giphy_attribution) }
            }
            holder.itemView.setOnClickListener { select(item) }
            if (holder.itemView.isAttachedToWindow) bindPreview(holder)
        }
        private fun bindPreview(holder: MediaHolder) {
            val item = holder.item ?: return
            if (!active || editing || host.privateMode) return
            previews?.bind(holder.image, item) {
                if (holder.item === item) {
                    holder.image.contentDescription = "${item.title}: ${context.getString(R.string.media_preview_failed)}"
                }
            }
        }
        override fun onViewAttachedToWindow(holder: MediaHolder) = bindPreview(holder)
        override fun onViewDetachedFromWindow(holder: MediaHolder) { previews?.clear(holder.image) }
        override fun onViewRecycled(holder: MediaHolder) {
            previews?.clear(holder.image)
            holder.item = null
        }
    }

    private class MediaHolder(view: View, val image: ImageView, val label: TextView) : RecyclerView.ViewHolder(view) {
        var item: MediaItem? = null
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
        private val STAGING_RETENTION = MediaRetentionPolicy(
            receiverReadMillis = 60 * 60 * 1000L,
            maxBytes = 64L * 1024 * 1024,
            maxFiles = 64
        )
    }
}
