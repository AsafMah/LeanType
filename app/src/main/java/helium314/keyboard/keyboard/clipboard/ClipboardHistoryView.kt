// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.keyboard.internal.KeyDrawParams
import helium314.keyboard.keyboard.internal.KeyVisualAttributes
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKeyLongClick
import helium314.keyboard.latin.utils.getEnabledClipboardToolbarKeys
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.setToolbarButtonsActivatedStateOnPrefChange

@SuppressLint("CustomViewStyleable")
class ClipboardHistoryView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int = R.attr.clipboardHistoryViewStyle
) : LinearLayout(context, attrs, defStyle), View.OnClickListener,
    ClipboardDao.Listener, OnKeyEventListener, KeyboardActionListener,
    View.OnLongClickListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private val clipboardLayoutParams = ClipboardLayoutParams(context)
    private val pinIconId: Int
    private val keyBackgroundId: Int

    private lateinit var clipboardRecyclerView: ClipboardHistoryRecyclerView
    private lateinit var placeholderView: TextView
    private val toolbarKeys = mutableListOf<ImageButton>()
    private lateinit var clipboardAdapter: ClipboardAdapter

    lateinit var keyboardActionListener: KeyboardActionListener
    private lateinit var clipboardHistoryManager: ClipboardHistoryManager

    init {
        val clipboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.ClipboardHistoryView, defStyle, R.style.ClipboardHistoryView)
        pinIconId = clipboardViewAttr.getResourceId(R.styleable.ClipboardHistoryView_iconPinnedClip, 0)
        clipboardViewAttr.recycle()
        @SuppressLint("UseKtx") // suggestion does not work
        val keyboardViewAttr = context.obtainStyledAttributes(attrs, R.styleable.KeyboardView, defStyle, R.style.KeyboardView)
        keyBackgroundId = keyboardViewAttr.getResourceId(R.styleable.KeyboardView_keyBackground, 0)
        keyboardViewAttr.recycle()
        if (Settings.getValues().mSecondaryStripVisible) {
            getEnabledClipboardToolbarKeys(context.prefs())
                .forEach { toolbarKeys.add(createToolbarKey(context, it)) }
        }
        fitsSystemWindows = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val res = context.resources
        val width = ResourceUtils.getKeyboardWidth(context, Settings.getValues()) + paddingLeft + paddingRight
        val baseHeight = ResourceUtils.getSecondaryKeyboardHeight(res, Settings.getValues())
        val finalHeight = baseHeight + paddingTop + paddingBottom
        // Measure children against the dimensions we'll actually report. If we just call
        // super with the parent's spec (which may be much larger), children get measured
        // against that larger size and overflow our final height -- pushing the
        // bottom-row keyboard off-screen below the editor panel.
        val exactW = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val exactH = MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY)
        super.onMeasure(exactW, exactH)
        setMeasuredDimension(width, finalHeight)
    }

    private lateinit var searchBar: android.widget.EditText
    private lateinit var clearSearch: android.widget.ImageButton
    private lateinit var backSearch: android.widget.ImageButton
    private lateinit var searchOverlay: android.view.View
    private lateinit var emptyViewIcon: android.widget.ImageView
    private lateinit var emptyViewText: android.widget.TextView
    private lateinit var emptyViewContainer: View
    private lateinit var listContainer: View

    private var confirmationBar: View? = null
    private val confirmationDismissRunnable = Runnable { dismissConfirmationBar() }
    private val confirmationHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var editorInfo: EditorInfo? = null
    // We already have keyboardActionListener property

    private val searchWatcher = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            val query = s?.toString() ?: ""
            clearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            clipboardAdapter.filter(query)
            // Empty view logic likely needs adjustment for "search mode" vs "list mode"? 
            // Actually, if we filter, the list updates. 
            // In Search Mode, do we see the list? 
            // "Type query -> Hit Enter -> View returns to filtered list"
            // So while typing, maybe we DON'T see list?
            // "redirected to keyboard key page ... indicator showing we are typing"
            // Let's assume while typing, we just see the input. 
            // BUT live filtering is nice. I will keep list visible if overlay allows (it sits on top?).
            // If overlay covers everything, then we don't see it.
            // Let's follow "redirected to keyboard key page" -> Overlay covers list.
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initialize() { // needs to be delayed for access to ClipboardStrip, which is not a child of this view
        if (this::clipboardAdapter.isInitialized) return
        val colors = Settings.getValues().mColors
        clipboardAdapter = ClipboardAdapter(clipboardLayoutParams, this).apply {
            itemBackgroundId = keyBackgroundId
            pinnedIconResId = pinIconId
        }
        
        // Search & Empty View init
        searchOverlay = findViewById(R.id.clipboard_search_overlay)
        searchBar = findViewById(R.id.clipboard_search_bar)
        clearSearch = findViewById(R.id.clipboard_clear_search)
        backSearch = findViewById(R.id.clipboard_search_back)
        emptyViewContainer = findViewById(R.id.clipboard_empty_view)
        emptyViewIcon = findViewById(R.id.clipboard_empty_icon)
        emptyViewText = findViewById(R.id.clipboard_empty_text)
        
        // Locate the list container if possible, or just the RecyclerView
        // Our XML has FrameLayout around list/empty. 
        // We might want to toggle visibility of that FrameLayout vs Overlay?
        // Let's assume RecyclerView is enough if Overlay is "match_parent" and on top.

        searchBar.addTextChangedListener(searchWatcher)
        clearSearch.setOnClickListener { searchBar.setText("") }
        backSearch.setOnClickListener { stopSearchMode() }
        
        // Make sure Enter key submits search
        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                stopSearchMode()
                return@setOnEditorActionListener true
            }
            false
        }
        
        // Coloring
        colors.setBackground(searchOverlay, ColorType.MAIN_BACKGROUND) // Overlay background
        searchBar.setTextColor(colors.get(ColorType.KEY_TEXT))
        val hintColor = colors.get(ColorType.KEY_TEXT)
        searchBar.setHintTextColor((hintColor and 0x00FFFFFF) or 0x80000000.toInt()) // semi-transparent
        colors.setColor(clearSearch, ColorType.KEY_ICON)
        colors.setColor(backSearch, ColorType.KEY_ICON)
        // Tint empty icon
        val iconColor = colors.get(ColorType.KEY_ICON)
        emptyViewIcon.setColorFilter(iconColor)
        emptyViewText.setTextColor(iconColor)

        // removed placeholderView logic as it was unused/confusing

        clipboardRecyclerView = findViewById<ClipboardHistoryRecyclerView>(R.id.clipboard_list).apply {
            val colCount = resources.getInteger(R.integer.config_clipboard_keyboard_col_count)
            layoutManager = StaggeredGridLayoutManager(colCount, StaggeredGridLayoutManager.VERTICAL)
            @Suppress("deprecation") // "no cache" should be fine according to warning in https://developer.android.com/reference/android/view/ViewGroup#setPersistentDrawingCache(int)
            persistentDrawingCache = PERSISTENT_NO_CACHE
            clipboardLayoutParams.setListProperties(this)
        }

        confirmationBar = findViewById(R.id.clipboard_confirmation_bar)
        confirmationBar?.let { bar ->
            try {
                colors.setBackground(bar, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)
                bar.findViewById<TextView>(R.id.clipboard_confirmation_text)?.setTextColor(colors.get(ColorType.KEY_TEXT))
                bar.findViewById<TextView>(R.id.clipboard_confirmation_button)?.setTextColor(colors.get(ColorType.KEY_TEXT))
            } catch (_: Exception) {}
            bar.findViewById<View>(R.id.clipboard_confirmation_button)?.setOnClickListener {
                clipboardHistoryManager.clearHistory()
                dismissConfirmationBar()
            }
        }

        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        toolbarKeys.forEach {
            clipboardStrip.addView(it)
            it.setOnClickListener(this@ClipboardHistoryView)
            it.setOnLongClickListener(this@ClipboardHistoryView)
            colors.setColor(it, ColorType.TOOL_BAR_KEY)
            it.setBackgroundResource(R.drawable.toolbar_key_background)
            colors.setColor(it.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
        }
    }

    private lateinit var searchBarTextView: TextView
    private var searchQuery = StringBuilder()
    private var backButton: ImageButton? = null

    private fun startSearchMode() {
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        clipboardStrip.removeAllViews()
        
        // 1. Add Search Text View
        searchBarTextView = TextView(context).apply {
             layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
             gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
             textSize = 16f
             setTextColor(Settings.getValues().mColors.get(ColorType.KEY_TEXT))
             hint = context.getString(R.string.clipboard_search_hint)
             setHintTextColor(Settings.getValues().mColors.get(ColorType.KEY_TEXT) and 0x00FFFFFF or 0x80000000.toInt())
             setPadding(32, 0, 0, 0)
        }
        clipboardStrip.addView(searchBarTextView)
        
        // 2. Add Close Button
        backButton = ImageButton(context).apply {
             layoutParams = LinearLayout.LayoutParams(
                 resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width), 
                 LinearLayout.LayoutParams.MATCH_PARENT
             )
             setImageResource(R.drawable.ic_close)
             setBackgroundResource(R.drawable.toolbar_key_background)
             setColorFilter(Settings.getValues().mColors.get(ColorType.KEY_ICON))
             Settings.getValues().mColors.setColor(background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
             setOnClickListener { stopSearchMode() }
        }
        clipboardStrip.addView(backButton)
        
        searchQuery.clear()
        searchBarTextView.text = ""

        // Switch to Alphabet Keyboard
        setBottomRowLayout(KeyboardId.ELEMENT_ALPHABET)
        
        // HIDE Clipboard List while searching
        clipboardRecyclerView.visibility = View.GONE
        emptyViewContainer.visibility = View.GONE
    }

    private fun stopSearchMode() {
        // Restore toolbar
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        clipboardStrip.removeAllViews()
        toolbarKeys.forEach { 
             clipboardStrip.addView(it) 
             // Restore state (enabled/disabled handling if needed)
        }
        
        // Keep the filter if we have query?
        // User said: "redirected to the clipboard with filtered items"
        // So we keep the filter!
        // If searchQuery is empty, we clear filter.
        
        if (searchQuery.isNotEmpty()) {
             clipboardAdapter.filter(searchQuery.toString())
        } else {
             clipboardAdapter.filter("")
        }
        
        // Switch back to Clipboard Keyboard
        setBottomRowLayout(KeyboardId.ELEMENT_CLIPBOARD_BOTTOM_ROW)
        
        // SHOW Clipboard List
        clipboardRecyclerView.visibility = View.VISIBLE
        updateEmptyView(clipboardAdapter.isFiltering)
    }

    private fun updateEmptyView(isSearch: Boolean) {
        val isEmpty = clipboardAdapter.itemCount == 0 // Since we removed header, 0 is truly empty
        emptyViewContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE
        if (isEmpty) {
            emptyViewText.setText(if (isSearch) R.string.clipboard_no_search_results else R.string.clipboard_empty_text)
        }
    }

    // Intercept Input - Implements KeyboardActionListener
    override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip

        if (inSearchMode) {
            val char = if (primaryCode > 0) primaryCode.toChar() else null
            
            if (primaryCode == KeyCode.DELETE) {
                if (searchQuery.isNotEmpty()) {
                    searchQuery.deleteCharAt(searchQuery.length - 1)
                    searchBarTextView.text = searchQuery.toString()
                    // Filter live or deferred? User requested deferred but also "auto complete". 
                    // Since list is HIDDEN, live filtering is useless visually but keeps state correct.
                    // Doing it live is safer.
                    clipboardAdapter.filter(searchQuery.toString()) 
                }
            } else if (primaryCode == Constants.CODE_ENTER) {
                stopSearchMode()
            } else if (primaryCode == Constants.CODE_SPACE) {
                 searchQuery.append(" ")
                 searchBarTextView.text = searchQuery.toString()
                 clipboardAdapter.filter(searchQuery.toString())
            } else if (char != null) {
                 searchQuery.append(char)
                 searchBarTextView.text = searchQuery.toString()
                 clipboardAdapter.filter(searchQuery.toString())
            } else {
                 // Any other key (like Symbols ?123 or Settings) should close search 
                 // and pass through to original listener
                 stopSearchMode()
                 keyboardActionListener.onCodeInput(primaryCode, x, y, isKeyRepeat)
            }
            // Block sending to app
            return 
        }
        
        // Pass through if not search mode
        if (Settings.getValues().mAlphaAfterClipHistoryEntry)
            keyboardActionListener.onCodeInput(primaryCode, x, y, isKeyRepeat)
        else
            keyboardActionListener.onCodeInput(primaryCode, x, y, isKeyRepeat)
    }
    
    override fun onTextInput(text: String) {
         val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
         val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip

         if (inSearchMode) {
             searchQuery.append(text)
             searchBarTextView.text = searchQuery.toString()
             clipboardAdapter.filter(searchQuery.toString())
             updateEmptyView(true)
             return
         }
         
         keyboardActionListener.onTextInput(text)
    }

    override fun onImageSelected(imageUri: String) {
         keyboardActionListener.onImageSelected(imageUri)
    }

    // Delegate other KeyboardActionListener methods
    override fun onPressKey(primaryCode: Int, repeatCount: Int, isSinglePointer: Boolean, hapticEvent: HapticEvent?) {
        keyboardActionListener.onPressKey(primaryCode, repeatCount, isSinglePointer, hapticEvent)
    }
    override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) {
        keyboardActionListener.onReleaseKey(primaryCode, withSliding)
    }
    override fun onLongPressKey(primaryCode: Int) {
        keyboardActionListener.onLongPressKey(primaryCode)
    }
    override fun onKeyDown(keyCode: Int, keyEvent: android.view.KeyEvent?): Boolean {
        return keyboardActionListener.onKeyDown(keyCode, keyEvent)
    }
    override fun onKeyUp(keyCode: Int, keyEvent: android.view.KeyEvent?): Boolean {
        return keyboardActionListener.onKeyUp(keyCode, keyEvent)
    }
    override fun onStartBatchInput() { keyboardActionListener.onStartBatchInput() }
    override fun onUpdateBatchInput(p: helium314.keyboard.latin.common.InputPointers?) { keyboardActionListener.onUpdateBatchInput(p) }
    override fun onEndBatchInput(p: helium314.keyboard.latin.common.InputPointers?) { keyboardActionListener.onEndBatchInput(p) }
    override fun onCancelBatchInput() { keyboardActionListener.onCancelBatchInput() }
    override fun onCancelInput() { keyboardActionListener.onCancelInput() }
    override fun onFinishSlidingInput() { keyboardActionListener.onFinishSlidingInput() }
    override fun onCustomRequest(requestCode: Int): Boolean { return keyboardActionListener.onCustomRequest(requestCode) }
    override fun onHorizontalSpaceSwipe(steps: Int): Boolean {
        return keyboardActionListener.onHorizontalSpaceSwipe(steps)
    }
    override fun onVerticalSpaceSwipe(steps: Int): Boolean {
        return keyboardActionListener.onVerticalSpaceSwipe(steps)
    }
    override fun onEndSpaceSwipe() {
        keyboardActionListener.onEndSpaceSwipe()
    }
    override fun toggleNumpad(w: Boolean, f: Boolean): Boolean { return keyboardActionListener.toggleNumpad(w, f) }
    override fun onMoveDeletePointer(steps: Int) { keyboardActionListener.onMoveDeletePointer(steps) }
    override fun onUpWithDeletePointerActive() { keyboardActionListener.onUpWithDeletePointerActive() }
    override fun resetMetaState() { keyboardActionListener.resetMetaState() }
    
    private fun setupClipKey(params: KeyDrawParams) {
        clipboardAdapter.apply {
            itemBackgroundId = keyBackgroundId
            itemTypeFace = params.mTypeface
            itemTextColor = params.mTextColor
            itemTextSize = params.mLabelSize.toFloat()
        }
    }

    private fun setupToolbarKeys() {
        // set layout params
        val toolbarKeyLayoutParams = LayoutParams(
            resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width), 
            resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width)
        ).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        toolbarKeys.forEach { it.layoutParams = toolbarKeyLayoutParams }
    }

    private fun setupBottomRowKeyboard(editorInfo: EditorInfo, listener: KeyboardActionListener) {
        // Initial setup only
        this.editorInfo = editorInfo
        this.keyboardActionListener = listener
        setBottomRowLayout(KeyboardId.ELEMENT_CLIPBOARD_BOTTOM_ROW)
    }

    private fun setBottomRowLayout(elementId: Int) {
        val editorInfo = this.editorInfo ?: return
        val keyboardView = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
        keyboardView.setKeyboardActionListener(this)  // Set 'this' as listener to intercept
        PointerTracker.switchTo(keyboardView)
        // Use Builder to get correct layout. Match EmojiPalettesView's search-mode setup
        // exactly so we get the same theming the user already accepts as "their"
        // keyboard there: pass secondary-keyboard-height for the geometry instead of the
        // full keyboard height. The full-height geometry made the inner keys mismatch
        // the user's main keyboard visually.
        val isAlphaOrSymbols = elementId == KeyboardId.ELEMENT_ALPHABET ||
                elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED ||
                elementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED ||
                elementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED ||
                elementId == KeyboardId.ELEMENT_SYMBOLS ||
                elementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED
        val geometryHeight = if (isAlphaOrSymbols) {
            ResourceUtils.getSecondaryKeyboardHeight(context.resources, Settings.getValues())
        } else {
            ResourceUtils.getKeyboardHeight(context.resources, Settings.getValues())
        }
        val builder = KeyboardLayoutSet.Builder(context, editorInfo)
            .setSubtype(RichInputMethodManager.getInstance().currentSubtype)
            .setKeyboardGeometry(ResourceUtils.getKeyboardWidth(context, Settings.getValues()), geometryHeight)

        val kls = builder.build()
        val keyboard = kls.getKeyboard(elementId)
        keyboardView.setKeyboard(keyboard)
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        if (!enabled) return
        // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        setLayerType(LAYER_TYPE_HARDWARE, null)
        // Propagate to the bottom-row keyboard so it matches the main keyboard's
        // hardware-accelerated drawing (otherwise key rendering can look different).
        findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)?.setHardwareAcceleratedDrawingEnabled(true)
    }

    fun startClipboardHistory(
            historyManager: ClipboardHistoryManager,
            keyVisualAttr: KeyVisualAttributes?,
            editorInfo: EditorInfo,
            keyboardActionListener: KeyboardActionListener
    ) {
        clipboardHistoryManager = historyManager
        initialize()
        setupToolbarKeys()
        historyManager.prepareClipboardHistory()
        historyManager.setHistoryChangeListener(this)
        clipboardAdapter.clipboardHistoryManager = historyManager
        
        // Clear search on start
        searchQuery.clear() // Explicitly clear query builder
        searchBar.setText("")
        searchBar.clearFocus() // ensure focus is lost
        clipboardAdapter.filter("")
        updateEmptyView(false)
        stopSearchMode()

        val params = KeyDrawParams()
        params.updateParams(clipboardLayoutParams.bottomRowKeyboardHeight, keyVisualAttr)
        val settings = Settings.getInstance()
        settings.getCustomTypeface()?.let { params.mTypeface = it }
        setupClipKey(params)
        setupBottomRowKeyboard(editorInfo, keyboardActionListener)

        // Typeface for search and empty text
        params.mTypeface?.let { tf ->
            searchBar.typeface = tf
            emptyViewText.typeface = tf
        }
        searchBar.setTextColor(params.mTextColor)

        val keyboardWidth = ResourceUtils.getKeyboardWidth(context, settings.current)
        val keyboardAttr = context.obtainStyledAttributes(
            null, R.styleable.Keyboard, R.attr.keyboardStyle, R.style.Keyboard)
        val leftPadding = (keyboardAttr.getFraction(R.styleable.Keyboard_keyboardLeftPadding,
            keyboardWidth, keyboardWidth, 0f)
                * settings.current.mSidePaddingScale).toInt()
        val rightPadding =  (keyboardAttr.getFraction(R.styleable.Keyboard_keyboardRightPadding,
            keyboardWidth, keyboardWidth, 0f)
                * settings.current.mSidePaddingScale).toInt()
        keyboardAttr.recycle()

        clipboardRecyclerView.apply {
            adapter = clipboardAdapter
            layoutParams.width = keyboardWidth
            setPadding(leftPadding, paddingTop, rightPadding, paddingBottom)
        }
        
        // absurd workaround so Android sets the correct color from stateList (depending on "activated")
        toolbarKeys.forEach { it.isEnabled = false; it.isEnabled = true }
    }

    override fun onKeyDown(clipId: Long) {
        keyboardActionListener.onPressKey(KeyCode.NOT_SPECIFIED, 0, true, HapticEvent.KEY_PRESS)
    }

    override fun onKeyUp(clipId: Long) {
        val clipContent = clipboardHistoryManager.getHistoryEntryContent(clipId) ?: return
        if (clipContent.imageUri != null) {
            keyboardActionListener.onImageSelected(clipContent.imageUri)
        } else {
            val text = clipContent.text
            if (text.length > 1000) {
                clipboardHistoryManager.pasteLargeText(text)
            } else {
                keyboardActionListener.onTextInput(text)
            }
        }
        keyboardActionListener.onReleaseKey(KeyCode.NOT_SPECIFIED, false)
        if (Settings.getValues().mAlphaAfterClipHistoryEntry)
            keyboardActionListener.onCodeInput(KeyCode.ALPHA, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
    }

    override fun onKeyLongPress(clipId: Long, anchor: View): Boolean {
        val entry = clipboardHistoryManager.getHistoryEntryContent(clipId) ?: return false
        AudioAndHapticFeedbackManager.getInstance()
            .performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_LONG_PRESS)
        showClipContextMenu(entry, anchor)
        return true
    }

    private fun showClipContextMenu(entry: helium314.keyboard.latin.ClipboardHistoryEntry, anchor: View) {
        val isImage = entry.imageUri != null
        val pinLabelRes = if (entry.isPinned) R.string.clipboard_action_unpin else R.string.clipboard_action_pin

        // Build option list dynamically; Edit is omitted for image clips.
        data class Option(val labelRes: Int, val action: () -> Unit)
        val options = mutableListOf<Option>()
        if (!isImage) {
            options += Option(R.string.clipboard_action_edit) { showEditActivity(entry) }
        }
        options += Option(pinLabelRes) { clipboardHistoryManager.toggleClipPinned(entry.id) }
        if (!isImage) {
            options += Option(R.string.clipboard_action_copy) { copyEntryToSystemClipboard(entry) }
            options += Option(R.string.clipboard_action_share) { shareEntry(entry) }
        }
        options += Option(R.string.clipboard_action_delete) { deleteEntry(entry) }

        val labels = options.map { context.getString(it.labelRes) }.toTypedArray()
        val dialog = android.app.AlertDialog.Builder(context)
            .setItems(labels) { d, which ->
                d.dismiss()
                options[which].action()
            }
            .create()
        attachDialogToKeyboard(dialog, anchor)
        dialog.show()
    }

    private fun showEditActivity(entry: helium314.keyboard.latin.ClipboardHistoryEntry) {
        try {
            KeyboardSwitcher.getInstance().setAlphabetKeyboard()
            context.startActivity(ClipboardClipEditActivity.createIntent(context, entry.id))
        } catch (e: android.content.ActivityNotFoundException) {
            helium314.keyboard.latin.utils.Log.w("ClipboardHistoryView", "No activity to edit clip: ${e.message}")
        }
    }

    private fun copyEntryToSystemClipboard(entry: helium314.keyboard.latin.ClipboardHistoryEntry) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("clipboard", entry.text))
        } catch (e: SecurityException) {
            helium314.keyboard.latin.utils.Log.w("ClipboardHistoryView", "Failed to set primary clip: ${e.message}")
        }
    }

    private fun shareEntry(entry: helium314.keyboard.latin.ClipboardHistoryEntry) {
        try {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, entry.text)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(send, null).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: android.content.ActivityNotFoundException) {
            helium314.keyboard.latin.utils.Log.w("ClipboardHistoryView", "No activity to handle share: ${e.message}")
        }
    }

    private fun deleteEntry(entry: helium314.keyboard.latin.ClipboardHistoryEntry) {
        // Pinned items can't be removed; unpin first so they behave like any other entry.
        if (entry.isPinned) clipboardHistoryManager.toggleClipPinned(entry.id)
        val index = clipboardHistoryManager.getClips().indexOfFirst { it.id == entry.id }
        if (index < 0) return
        val deleted = clipboardHistoryManager.removeEntry(index) ?: return
        clipboardAdapter.notifyItemRemoved(index)
        clipboardRecyclerView.showUndoForDeletedEntry(deleted)
    }

    private fun attachDialogToKeyboard(dialog: android.app.AlertDialog, anchor: View) {
        // Same pattern used in EmojiPalettesView so the dialog appears above the IME window.
        val window = dialog.window ?: return
        val lp = window.attributes
        lp.token = anchor.windowToken
        lp.type = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        window.attributes = lp
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }
 


    fun stopClipboardHistory() {
        if (!this::clipboardAdapter.isInitialized) return

        // Also ensure search mode is stopped if we explicitly leave clipboard history
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip
        if (inSearchMode && isAttachedToWindow) {
            stopSearchMode()
        }

        // Dismiss any active undo bar
        clipboardRecyclerView.dismissUndoBar()
        dismissConfirmationBar()
        
        clipboardRecyclerView.adapter = null
        clipboardHistoryManager.setHistoryChangeListener(null)
        clipboardAdapter.clipboardHistoryManager = null
    }

    fun showClearAllConfirmationBar() {
        clipboardRecyclerView.dismissUndoBar()
        val bar = confirmationBar ?: return
        confirmationHandler.removeCallbacks(confirmationDismissRunnable)
        bar.visibility = View.VISIBLE
        confirmationHandler.postDelayed(confirmationDismissRunnable, 5000)
    }

    fun dismissConfirmationBar() {
        confirmationHandler.removeCallbacks(confirmationDismissRunnable)
        confirmationBar?.visibility = View.GONE
    }

    override fun onClick(view: View) {
        val tag = view.tag
        if (tag is ToolbarKey) {
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_PRESS)
            val code = getCodeForToolbarKey(tag)
            if (code == KeyCode.CLIPBOARD_SEARCH) {
                 startSearchMode()
                 return
            }
            if (code == KeyCode.CLIPBOARD_CLEAR_HISTORY) {
                showClearAllConfirmationBar()
                return
            }
            if (code != KeyCode.UNSPECIFIED) {
                keyboardActionListener.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                return
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        val tag = view.tag
        if (tag is ToolbarKey) {
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_LONG_PRESS)
            val longClickCode = getCodeForToolbarKeyLongClick(tag)
            if (longClickCode != KeyCode.UNSPECIFIED) {
                keyboardActionListener.onCodeInput(
                    longClickCode,
                    Constants.NOT_A_COORDINATE,
                    Constants.NOT_A_COORDINATE,
                    false
                )
            }
            return true
        }
        return false
    }



    override fun onClipInserted(position: Int) {
        confirmationHandler.post {
            clipboardAdapter.refresh()
            if (!clipboardAdapter.isFiltering) {
                 clipboardRecyclerView.smoothScrollToPosition(0)
            }
            updateEmptyView(clipboardAdapter.isFiltering)
        }
    }

    override fun onClipsRemoved(position: Int, count: Int) {
        confirmationHandler.post {
            clipboardAdapter.refresh()
            updateEmptyView(clipboardAdapter.isFiltering)
        }
    }

    override fun onClipMoved(oldPosition: Int, newPosition: Int) {
        confirmationHandler.post {
            clipboardAdapter.refresh()
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        setToolbarButtonsActivatedStateOnPrefChange(KeyboardSwitcher.getInstance().clipboardStrip, key)

        if (::clipboardHistoryManager.isInitialized &&
            (key == Settings.PREF_CLIPBOARD_HISTORY_PINNED_FIRST || key == Settings.PREF_CLIPBOARD_FOLD_PINNED)) {
            // Ensure settings are reloaded first
            Settings.getInstance().onSharedPreferenceChanged(prefs, key)
            clipboardHistoryManager.sortHistoryEntries()
            confirmationHandler.post {
                clipboardAdapter.refresh()
            }
        }
    }
}
