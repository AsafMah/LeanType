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
import android.widget.PopupMenu
import androidx.core.widget.doAfterTextChanged
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.Keyboard
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
import kotlinx.coroutines.CoroutineStart

internal interface MediaPickerHost {
    val editorVersion: Long
    val privateMode: Boolean
    val typingListener: KeyboardActionListener
    val networkAvailable: Boolean get() = true
    fun beginTyping() {}
    fun supports(mimeType: String): Boolean
    fun insert(media: StagedMedia, editorVersion: Long): Boolean
    fun returnToTyping()
}

internal class MediaPickerView(
    context: Context,
    private val host: MediaPickerHost,
    upstream: MediaSource = GiphyService(context),
    private val library: MediaLibrary? = if (upstream is GiphyService)
        MediaLibrary(context, upstream, { !host.privateMode && host.networkAvailable }) else null
) : LinearLayout(context) {
    private val source: MediaSource = library ?: upstream
    private val state = MediaSearchState()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
    private var restoring: Job? = null
    private var bindingQuery = false
    private val hydrated = mutableSetOf<MediaKind>()
    private val editedDrafts = mutableSetOf<MediaKind>()
    private var bookmarkItems = emptyList<MediaBookmark>()
    private var showingPins = false
    private var viewedPin: MediaBookmark? = null
    private val uiPrefs = context.getSharedPreferences("media_picker_ui", Context.MODE_PRIVATE)
    private var reduceMotion = uiPrefs.getBoolean("reduce_motion", false)
    private val previewPane = LinearLayout(context).apply { id = R.id.media_preview; orientation = VERTICAL; visibility = GONE }
    private val previewImage = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val previewLabel = TextView(context)
    private val pinAction = Button(context).apply { id = R.id.media_pin_toggle }
    private var previewItem: MediaItem? = null
    private var previewRequest = 0L
    private var privacyBlocked = false
    private var historyReady = library == null
    private val privacyObserver = android.view.ViewTreeObserver.OnPreDrawListener {
        if (active && isShown) {
            val locked = Build.VERSION.SDK_INT >= 24 &&
                context.getSystemService(android.os.UserManager::class.java)?.isUserUnlocked != true
            val restricted = host.privateMode || !host.networkAvailable || locked
            val changedKey = keyVersion != source.credentialVersion
            if ((restricted && !privacyBlocked) || changedKey) {
                requestJob?.cancel()
                sendJob?.cancel()
                forgetVisible()
                keyVersion = source.credentialVersion
                showError(if (locked) MediaError.LOCKED else MediaError.UNAVAILABLE)
            }
            privacyBlocked = restricted
        }
        true
    }
    private var expanded = false
    val isTypingInApp: Boolean get() = active && !editing
    private val panel = LinearLayout(context).apply { orientation = VERTICAL }
    private val focusMode = Button(context).apply {
        id = R.id.media_focus
        isAllCaps = false
        setText(R.string.media_focus_app)
        setOnClickListener { if (editing) showResults() else showQueryKeyboard() }
    }

    private val field = EditText(context).apply {
        id = R.id.media_query
        hint = context.getString(R.string.media_search_hint)
        setSingleLine()
        showSoftInputOnFocus = false
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        doAfterTextChanged {
            if (!bindingQuery) {
                state.tab.draft = it.toString()
                editedDrafts.add(state.kind)
            }
        }
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
    private val pinsButton = Button(context).apply {
        id = R.id.media_pins
        setText(R.string.media_pins)
        isAllCaps = false
        setOnClickListener { togglePins() }
    }
    private val refresh = Button(context).apply {
        id = R.id.media_refresh
        setText(R.string.media_refresh)
        isAllCaps = false
        setOnClickListener { search(false, refresh = true) }
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
        val toolbar = LinearLayout(context)
        toolbar.addView(focusMode, LayoutParams(0, dp(40), 1f))
        toolbar.addView(pinsButton, LayoutParams(WRAP_CONTENT, dp(40)))
        toolbar.addView(Button(context).apply {
            id = R.id.media_expand
            isAllCaps = false
            setText(R.string.media_expand)
            setOnClickListener {
                expanded = !expanded
                setText(if (expanded) R.string.media_collapse else R.string.media_expand)
                requestLayout()
                parent?.requestLayout()
            }
        }, LayoutParams(WRAP_CONTENT, dp(40)))
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
        panel.addView(toolbar)
        panel.addView(controls)
        panel.addView(status, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        panel.addView(setup, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        panel.addView(share, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        grid.adapter = adapter
        grid.setItemViewCacheSize(0)
        panel.addView(grid, LayoutParams(MATCH_PARENT, 0, 1f))
        previewPane.addView(previewImage, LayoutParams(MATCH_PARENT, 0, 1f))
        previewPane.addView(previewLabel, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        previewPane.addView(LinearLayout(context).apply {
            addView(Button(context).apply {
                id = R.id.media_preview_close
                setText(R.string.media_preview_close)
                setOnClickListener { closePreview() }
            }, LayoutParams(0, dp(40), 1f))
            addView(pinAction, LayoutParams(0, dp(40), 1f))
            addView(Button(context).apply {
                id = R.id.media_insert
                setText(R.string.media_insert)
                setOnClickListener { previewItem?.let { closePreview(); select(it) } }
            }, LayoutParams(0, dp(40), 1f))
        })
        panel.addView(previewPane, LayoutParams(MATCH_PARENT, 0, 1f))
        panel.addView(more, LayoutParams(MATCH_PARENT, dp(32)))
        panel.addView(footer.apply {
            addView(refresh, LayoutParams(WRAP_CONTENT, dp(40)))
            addView(Button(context).apply {
                id = R.id.media_options
                text = "\u22ee"
                contentDescription = context.getString(R.string.media_options)
                setOnClickListener { showOptions() }
            }, LayoutParams(dp(48), dp(40)))
            addView(attribution, LayoutParams(0, dp(40), 1f))
        })
        addView(panel, LayoutParams(MATCH_PARENT, 0))
        addView(keyboard, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) savePosition()
            }
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
        savePosition()
        restoring?.cancel()
        closePreview()
        requestJob?.cancel()
        sendJob?.cancel()
        pendingShare = null
        share.visibility = GONE
        showingPins = false
        pinsButton.setText(R.string.media_pins)
        state.select(kind)
        historyReady = library == null
        hydrated.remove(kind)
        if (!active) {
            active = true
            privacyBlocked = false
            editorVersion = host.editorVersion
            alphabet = KeyboardSwitcher.getInstance().activeAlphabetKeyboardId
            previews = MediaPreviewLoader(context, source)
            previews?.setAnimationsEnabled(!reduceMotion)
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
                historyReady = false
                keyVersion = version
            }
            if (!allowed()) {
                forgetVisible()
                showError(MediaError.UNAVAILABLE)
                showResults()
                return
            }
            previews?.resume()
            bindQuery()
            setup.visibility = GONE
            refreshResults()
            if (allowed() && !source.hasApiKey()) {
                forgetVisible()
                showError(MediaError.MISSING_KEY)
                setup.visibility = VISIBLE
            } else if (library != null) {
                val generation = state.generation
                restoring = scope.launch {
                    try {
                        val history = library.restore()
                        if (!fresh(generation) || field.text.toString() != state.tab.draft) return@launch
                        val draft = state.tab.draft
                        val preserveDraft = state.kind in editedDrafts
                        if (history != null) state.restore(history)
                        if (preserveDraft) state.tab.draft = draft
                        hydrated.add(state.kind)
                        bookmarkItems = library.pins()
                        if (!fresh(generation)) return@launch
                        historyReady = true
                        bindQuery()
                        refreshResults()
                        restoreScroll()
                    } catch (e: MediaException) {
                        if (active && state.generation == generation) {
                            forgetVisible()
                            showError(e.reason)
                        }
                    }
                }
            }
        } catch (e: MediaException) {
            forgetVisible()
            showError(e.reason)
            setup.visibility = VISIBLE
        }
        showResults()
        requestLayout()
    }

    private fun allowed(): Boolean {
        if (!active || host.editorVersion != editorVersion) return false
        if (!source.available || !host.networkAvailable) {
            forgetVisible()
            showError(if (!source.available) MediaError.UNAVAILABLE else MediaError.NETWORK)
            return false
        }
        if (host.privateMode) {
            requestJob?.cancel()
            sendJob?.cancel()
            forgetVisible()
            showError(MediaError.UNAVAILABLE)
            return false
        }
        return true
    }

    private fun bindQuery() {
        bindingQuery = true
        field.setText(state.tab.draft)
        bindingQuery = false
    }

    private fun forgetVisible() {
        restoring?.cancel()
        previews?.pause()
        state.reset()
        hydrated.clear()
        editedDrafts.clear()
        bookmarkItems = emptyList()
        previews?.clear(previewImage)
        previewItem = null
        previewPane.visibility = GONE
        grid.visibility = VISIBLE
        pendingShare = null
        share.visibility = GONE
        bindingQuery = true
        field.setText("")
        bindingQuery = false
        adapter.notifyDataSetChanged()
    }

    private fun fresh(generation: Long): Boolean = try {
        allowed() && state.generation == generation && source.credentialVersion == keyVersion
    } catch (e: MediaException) {
        showError(e.reason)
        false
    }

    private fun search(more: Boolean, refresh: Boolean = false) {
        if (!allowed() || sendJob?.isActive == true) return
        previews?.resume()
        requestJob?.cancel()
        restoring?.cancel()
        closePreview()
        showingPins = false
        pinsButton.setText(R.string.media_pins)
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
                    val page = if (refresh) source.refresh(request.kind, request.query, request.language, request.offset)
                        else source.search(request.kind, request.query, request.language, request.offset)
                    if (fresh(request.generation) && state.accept(request, page)) {
                        hydrated.add(state.kind)
                        library?.remember(state.snapshot())
                        if (fresh(request.generation)) {
                            historyReady = true
                            refreshResults()
                            restoreScroll()
                        }
                    }
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
            showingPins -> context.getString(R.string.media_pins_hint)
            !historyReady && library != null -> context.getString(R.string.media_loading_saved)
            state.tab.requiresRefresh -> context.getString(R.string.media_expired_results)
            state.tab.submitted.isEmpty() -> context.getString(R.string.media_enter_query)
            state.tab.items.isEmpty() -> context.getString(R.string.media_no_results)
            else -> context.getString(R.string.media_results_for, state.tab.submitted)
        }
        more.visibility = if (!showingPins && state.tab.nextOffset != null) VISIBLE else GONE
        status.visibility = VISIBLE
    }

    private fun togglePins() {
        if (!allowed()) return
        previews?.resume()
        closePreview()
        showingPins = !showingPins
        pinsButton.setText(if (showingPins) R.string.media_results else R.string.media_pins)
        if (!showingPins) { refreshResults(); return }
        val generation = state.generation
        scope.launch {
            try {
                val pins = library?.pins().orEmpty()
                if (fresh(generation) && showingPins) {
                    bookmarkItems = pins
                    refreshResults()
                }
            } catch (e: MediaException) { if (fresh(generation)) showError(e.reason) }
        }
    }

    private fun previewBookmark(bookmark: MediaBookmark) {
        if (!allowed()) return
        closePreview()
        val ticket = previewRequest
        requestJob?.cancel()
        state.invalidate()
        val generation = state.generation
        viewedPin = bookmark
        status.setText(R.string.media_loading)
        requestJob = scope.launch {
            try {
                val item = library?.resolve(bookmark)
                if (!fresh(generation) || ticket != previewRequest || !showingPins) return@launch
                if (item == null) {
                    status.setText(R.string.media_pin_missing)
                    showPreview(MediaItem(bookmark.id, bookmark.title, bookmark.pageUrl,
                        bookmark.sourceName, bookmark.sourceUrl, emptyList()))
                } else showPreview(item)
            } catch (e: MediaException) { if (fresh(generation)) showError(e.reason) }
        }
    }

    private fun showPreview(item: MediaItem) {
        if (!allowed()) return
        previewRequest++
        if (!showingPins) viewedPin = null
        clearVisiblePreviews()
        previewItem = item
        grid.visibility = GONE
        previewPane.visibility = VISIBLE
        more.visibility = GONE
        previewLabel.text = "${item.title}\n${item.sourceName.ifBlank { item.sourceUrl }}"
        previewImage.contentDescription = item.title
        val kind = viewedPin?.kind ?: state.kind
        val pinned = bookmarkItems.any { it.kind == kind && it.id == item.id }
        pinAction.setText(if (pinned) R.string.media_unpin else R.string.media_pin)
        pinAction.setOnClickListener {
            if (!allowed()) return@setOnClickListener
            val generation = state.generation
            scope.launch {
                try {
                    if (pinned) library?.unpin(kind, item.id) else library?.pin(kind, item)
                    if (fresh(generation)) {
                        val pins = library?.pins().orEmpty()
                        if (!fresh(generation)) return@launch
                        bookmarkItems = pins
                        if (previewItem === item && previewPane.visibility == VISIBLE) showPreview(item)
                    }
                } catch (e: MediaException) { if (fresh(generation)) showError(e.reason) }
            }
        }
        if (item.renditions.isNotEmpty()) previewImage.post {
            if (active && previewItem === item && !host.privateMode)
                previews?.bind(previewImage, item) { showError(it) }
        }
    }

    private fun closePreview() {
        previewRequest++
        previews?.clear(previewImage)
        previewItem = null
        previewPane.visibility = GONE
        grid.visibility = VISIBLE
        refreshResults()
    }

    private fun showOptions() {
        val popup = PopupMenu(context, findViewById(R.id.media_options))
        popup.menu.add(0, 1, 0, R.string.media_reduce_motion).apply { isCheckable = true; isChecked = reduceMotion }
        popup.menu.add(0, 2, 1, R.string.media_clear_history)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> {
                    reduceMotion = !reduceMotion
                    uiPrefs.edit().putBoolean("reduce_motion", reduceMotion).apply()
                    previews?.setAnimationsEnabled(!reduceMotion)
                }
                2 -> if (allowed()) {
                    requestJob?.cancel()
                    sendJob?.cancel()
                    restoring?.cancel()
                    state.invalidate()
                    scope.launch {
                        try {
                            library?.clearHistory()
                            forgetVisible()
                            historyReady = true
                            hydrated.add(state.kind)
                            bindQuery()
                            refreshResults()
                        } catch (e: MediaException) { showError(e.reason) }
                    }
                }
            }
            true
        }
        popup.show()
    }

    private fun savePosition() {
        if (!active || host.privateMode || !host.networkAvailable || host.editorVersion != editorVersion || showingPins ||
            state.kind !in hydrated) return
        val manager = grid.layoutManager as GridLayoutManager
        val position = manager.findFirstVisibleItemPosition().coerceAtLeast(0)
        val offset = -(manager.findViewByPosition(position)?.top ?: 0).coerceAtMost(0)
        state.tab.scrollPosition = position
        state.tab.scrollOffset = offset
        val kind = state.kind
        val draft = state.tab.draft
        if (draft.codePointCount(0, draft.length) > MediaLimits.QUERY_CHARACTERS) return
        val credential = source.credentialVersion
        persistenceScope.launch {
            try {
                if (credential == source.credentialVersion && !host.privateMode && host.networkAvailable)
                    library?.rememberPosition(kind, draft, position, offset)
            }
            catch (e: MediaException) { if (active && !host.privateMode) showError(e.reason) }
        }
    }

    private fun restoreScroll() {
        (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(
            state.tab.scrollPosition, -state.tab.scrollOffset)
    }

    private fun showQueryKeyboard() {
        if (!active || !allowed()) return
        if (editing) return
        editing = true
        focusMode.setText(R.string.media_focus_search)
        configureQueryKeyboard(retainedKeyboardHeight(), alphabet)
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
            .setInternalAction(if (editing) KeyboardLayoutSet.InternalAction(
                helium314.keyboard.latin.common.Constants.CODE_ENTER, context.getString(R.string.media_search_action)
            ) else null)
            .setKeyboardGeometry(ResourceUtils.getKeyboardWidth(context, settings), height)
            .build()
        layoutSet = layouts
        keyboard.setKeyboard(layouts.getKeyboard(element))
        keyboard.setKeyPreviewPopupEnabled(settings.mKeyPreviewPopupOn)
    }

    fun preferredHeight(): Int = retainedKeyboardHeight() +
        PickerPaneGeometry.panelHeight(context, retainedKeyboardHeight(), expanded) + dp(96)

    private fun retainedKeyboardHeight() = ResourceUtils.getKeyboardHeight(resources, Settings.getValues())

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val keyboardHeight = retainedKeyboardHeight()
        panel.layoutParams.height = preferredHeight() - keyboardHeight
        if (active && configuredKeyboardHeight != keyboardHeight) {
            configureQueryKeyboard(keyboardHeight, keyboard.keyboard?.mId?.mElementId ?: alphabet)
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(preferredHeight(), MeasureSpec.EXACTLY))
    }

    private fun showResults() {
        editing = false
        releaseQueryKeyboard()
        focusMode.setText(R.string.media_focus_app)
        configureQueryKeyboard(retainedKeyboardHeight(), alphabet)
        keyboard.visibility = VISIBLE
        PointerTracker.switchTo(keyboard)
        keyboard.setKeyboardActionListener(host.typingListener)
        if (active) host.beginTyping()
        grid.visibility = VISIBLE
        footer.visibility = VISIBLE
        status.visibility = VISIBLE
        adapter.notifyDataSetChanged()
        more.visibility = if (state.tab.nextOffset != null) VISIBLE else GONE
        field.clearFocus()
    }

    fun updateTypingKeyboard(value: Keyboard) {
        if (!isTypingInApp) return
        keyboard.setKeyboard(value)
        keyboard.setKeyboardActionListener(host.typingListener)
        PointerTracker.switchTo(keyboard)
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
        if (previewItem != null) closePreview()
        else if (editing) showResults() else host.returnToTyping()
        return true
    }

    fun onHardwareKey(keyCode: Int, event: KeyEvent): Boolean {
        if (!active) return false
        if (keyCode == KeyEvent.KEYCODE_BACK) return if (event.repeatCount == 0) onBack() else true
        return if (editing) queryInput.onKeyDown(keyCode, event) else false
    }

    fun stop() {
        savePosition()
        active = false
        editing = false
        state.invalidate()
        scope.coroutineContext.cancelChildren()
        requestJob = null
        sendJob = null
        pendingShare = null
        previewRequest++
        previews?.clear(previewImage)
        previewItem = null
        previewPane.visibility = GONE
        releaseQueryKeyboard()
        previews?.close()
        previews = null
        mediaFiles?.close()
        mediaFiles = null
        layoutSet = null
        if (host.privateMode) forgetVisible()
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
        viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(privacyObserver)
        stop()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(privacyObserver)
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
        override fun getItemCount() = if (showingPins) bookmarkItems.size else if (historyReady) state.tab.items.size else 0
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
            val item = if (showingPins) bookmarkItems[position].let {
                MediaItem(it.id, it.title.ifBlank { "${it.kind.name}: ${it.id}" },
                    it.pageUrl, it.sourceName, it.sourceUrl, emptyList())
            } else state.tab.items[position]
            holder.item = item
            holder.image.contentDescription = item.title
            holder.label.text = item.sourceName.ifBlank {
                item.sourceUrl.ifBlank { context.getString(R.string.media_giphy_attribution) }
            }
            holder.itemView.setOnClickListener {
                if (showingPins) previewBookmark(bookmarkItems[position]) else select(item)
            }
            holder.itemView.setOnLongClickListener {
                if (showingPins) previewBookmark(bookmarkItems[position]) else showPreview(item)
                true
            }
            if (showingPins) {
                holder.label.text = "${item.title}\n${context.getString(R.string.media_pin_open)}"
            } else if (holder.itemView.isAttachedToWindow) bindPreview(holder)
        }
        private fun bindPreview(holder: MediaHolder) {
            val item = holder.item ?: return
            if (!active || host.privateMode) return
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
