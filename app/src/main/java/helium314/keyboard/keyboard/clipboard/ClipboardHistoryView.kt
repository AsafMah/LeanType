// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.isRepeatableToolbarKey
import helium314.keyboard.latin.utils.RepeatableKeyTouchListener
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
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val res = context.resources
        // The main keyboard expands to the entire this {@link KeyboardView}.
        val width = ResourceUtils.getKeyboardWidth(context, Settings.getValues()) + paddingLeft + paddingRight
        val height = ResourceUtils.getSecondaryKeyboardHeight(res, Settings.getValues()) + paddingTop + paddingBottom
        setMeasuredDimension(width, height)
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
        val clipboardStripScrollView = KeyboardSwitcher.getInstance().clipboardStripScrollView
        if (clipboardStripScrollView != null) {
            colors.setBackground(clipboardStripScrollView, ColorType.STRIP_BACKGROUND)
        }
        clipboardStrip.removeAllViews()
        toolbarKeys.forEach {
            clipboardStrip.addView(it)
            val tag = it.tag
            if (tag is ToolbarKey && isRepeatableToolbarKey(tag)) {
                it.setOnTouchListener(RepeatableKeyTouchListener { repeatCount ->
                    if (repeatCount == 0 || repeatCount % 4 == 0) {
                        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
                    }
                    val code = getCodeForToolbarKey(tag)
                    if (code != KeyCode.UNSPECIFIED) {
                        keyboardActionListener.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, repeatCount > 0)
                    }
                })
            } else {
                it.setOnClickListener(this@ClipboardHistoryView)
                it.setOnLongClickListener(this@ClipboardHistoryView)
            }
            colors.setColor(it, ColorType.TOOL_BAR_KEY)
            it.setBackgroundResource(R.drawable.toolbar_key_background)
            colors.setColor(it.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
        }
        applyClipboardToolbarKeyLayoutParams()
        clipboardStrip.post { applyClipboardToolbarKeyLayoutParams() }
    }

    private lateinit var searchBarTextView: TextView
    private var searchQuery = StringBuilder()
    private var backButton: ImageButton? = null
    
    // We reuse searchWatcher logic but applied manually or to a hidden text view if needed.
    // Actually we just filter manually now.
    
    // ... initialize ...
    // Note: XML elements for search overlay are now unused, we should eventually remove them from XML.
    // For now, we just ignore them.
    
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
        applyClipboardToolbarKeyLayoutParams()
        clipboardStrip.post { applyClipboardToolbarKeyLayoutParams() }
        
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

    // --- Edit Mode ---
    private var editEntry: ClipboardHistoryEntry? = null
    private var editText = StringBuilder()
    private var editCursorPos = 0
    private var deleteSwipeStartPos = -1
    private var currentDeleteSwipePos = -1
    private lateinit var editTextView: TextView

    val inEditMode: Boolean
        get() = editEntry != null

    fun startEditMode(entry: ClipboardHistoryEntry) {
        // Stop search mode first if active
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip
        if (inSearchMode) {
            stopSearchMode()
        }

        editEntry = entry
        editText = StringBuilder(entry.text)
        editCursorPos = editText.length

        // Replace toolbar with: [Text display] [Save] [✕]
        clipboardStrip.removeAllViews()

        val colors = Settings.getValues().mColors
        val btnWidth = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width)

        editTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            textSize = 16f
            setTextColor(colors.get(ColorType.KEY_TEXT))
            setPadding(32, 0, 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    val layout = layout
                    if (layout != null) {
                        val x = event.x - totalPaddingLeft + scrollX
                        val y = event.y - totalPaddingTop + scrollY
                        val line = layout.getLineForVertical(y.toInt().coerceIn(0, (layout.height - 1).coerceAtLeast(0)))
                        var offset = layout.getOffsetForHorizontal(line, x)
                        if (offset > editCursorPos) {
                            offset = (offset - 1).coerceAtLeast(0)
                        }
                        editCursorPos = offset.coerceIn(0, editText.length)
                        updateEditDisplay()
                    }
                }
                true
            }
        }
        clipboardStrip.addView(editTextView)

        val saveButton = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnWidth, LinearLayout.LayoutParams.MATCH_PARENT)
            setImageResource(R.drawable.ic_setup_check)
            setBackgroundResource(R.drawable.toolbar_key_background)
            setColorFilter(colors.get(ColorType.KEY_ICON))
            colors.setColor(background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
            setOnClickListener { stopEditMode(save = true) }
        }
        clipboardStrip.addView(saveButton)

        val cancelButton = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnWidth, LinearLayout.LayoutParams.MATCH_PARENT)
            setImageResource(R.drawable.ic_close)
            setBackgroundResource(R.drawable.toolbar_key_background)
            setColorFilter(colors.get(ColorType.KEY_ICON))
            colors.setColor(background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
            setOnClickListener { stopEditMode(save = false) }
        }
        clipboardStrip.addView(cancelButton)

        // Hide list and empty view
        clipboardRecyclerView.visibility = View.GONE
        emptyViewContainer.visibility = View.GONE

        updateEditDisplay()

        // Switch bottom row to alphabet keyboard
        setBottomRowLayout(KeyboardId.ELEMENT_ALPHABET)
    }

    private fun stopEditMode(save: Boolean) {
        val entry = editEntry ?: return

        if (save) {
            val newText = editText.toString().trim()
            if (newText.isNotEmpty() && newText != entry.text) {
                clipboardHistoryManager.updateClipText(entry.id, newText)
            }
        }

        editEntry = null
        deleteSwipeStartPos = -1
        currentDeleteSwipePos = -1

        // Restore toolbar
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        clipboardStrip.removeAllViews()
        toolbarKeys.forEach { clipboardStrip.addView(it) }
        applyClipboardToolbarKeyLayoutParams()
        clipboardStrip.post { applyClipboardToolbarKeyLayoutParams() }

        // Switch back to clipboard bottom row
        setBottomRowLayout(KeyboardId.ELEMENT_CLIPBOARD_BOTTOM_ROW)

        // Show list again
        clipboardRecyclerView.visibility = View.VISIBLE
        updateEmptyView(clipboardAdapter.isFiltering)
    }

    private fun updateEditDisplay() {
        updateEditDisplayWithSelection(-1, -1)
    }

    private fun updateEditDisplayWithSelection(selStart: Int, selEnd: Int) {
        if (!this::editTextView.isInitialized) return
        val colors = Settings.getValues().mColors
        val textColor = colors.get(ColorType.KEY_TEXT)

        if (selStart >= 0 && selEnd > selStart && selEnd <= editText.length) {
            val sb = android.text.SpannableStringBuilder(editText)
            val highlightColor = (textColor and 0x00FFFFFF) or 0x50000000
            sb.setSpan(
                android.text.style.BackgroundColorSpan(highlightColor),
                selStart,
                selEnd,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            editTextView.text = sb
        } else {
            val sb = android.text.SpannableStringBuilder(editText)
            val pos = editCursorPos.coerceIn(0, sb.length)
            sb.insert(pos, "|")
            sb.setSpan(
                android.text.style.ForegroundColorSpan(textColor),
                pos,
                pos + 1,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            editTextView.text = sb
        }
    }

    // Intercept Input - Implements KeyboardActionListener
    override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip
        
        // Edit mode intercept
        if (inEditMode) {
            val char = if (primaryCode > 0) primaryCode.toChar() else null

            if (primaryCode == KeyCode.DELETE) {
                if (editCursorPos > 0) {
                    editText.deleteCharAt(editCursorPos - 1)
                    editCursorPos--
                    updateEditDisplay()
                }
            } else if (primaryCode == Constants.CODE_ENTER) {
                editText.insert(editCursorPos, "\n")
                editCursorPos++
                updateEditDisplay()
            } else if (primaryCode == Constants.CODE_SPACE) {
                editText.insert(editCursorPos, " ")
                editCursorPos++
                updateEditDisplay()
            } else if (primaryCode == KeyCode.ARROW_LEFT) {
                if (editCursorPos > 0) {
                    editCursorPos--
                    updateEditDisplay()
                }
            } else if (primaryCode == KeyCode.ARROW_RIGHT) {
                if (editCursorPos < editText.length) {
                    editCursorPos++
                    updateEditDisplay()
                }
            } else if (char != null) {
                editText.insert(editCursorPos, char.toString())
                editCursorPos++
                updateEditDisplay()
            }
            // Block sending to app
            return
        }

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
         // Edit mode intercept
         if (inEditMode) {
              editText.insert(editCursorPos, text)
              editCursorPos += text.length
              updateEditDisplay()
              return
         }

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
        if (inEditMode) {
            val newPos = (editCursorPos + steps).coerceIn(0, editText.length)
            if (newPos != editCursorPos) {
                editCursorPos = newPos
                updateEditDisplay()
            }
            return true
        }
        return keyboardActionListener.onHorizontalSpaceSwipe(steps)
    }
    override fun onVerticalSpaceSwipe(steps: Int): Boolean { return keyboardActionListener.onVerticalSpaceSwipe(steps) }
    override fun onEndSpaceSwipe() { keyboardActionListener.onEndSpaceSwipe() }
    override fun toggleNumpad(w: Boolean, f: Boolean): Boolean { return keyboardActionListener.toggleNumpad(w, f) }
    override fun onMoveDeletePointer(steps: Int) {
        if (inEditMode) {
            if (deleteSwipeStartPos == -1) {
                deleteSwipeStartPos = editCursorPos
                currentDeleteSwipePos = editCursorPos
            }
            currentDeleteSwipePos = (currentDeleteSwipePos + steps).coerceIn(0, editText.length)
            updateEditDisplayWithSelection(
                minOf(deleteSwipeStartPos, currentDeleteSwipePos),
                maxOf(deleteSwipeStartPos, currentDeleteSwipePos)
            )
            return
        }
        keyboardActionListener.onMoveDeletePointer(steps)
    }
    override fun onUpWithDeletePointerActive() {
        if (inEditMode) {
            if (deleteSwipeStartPos != -1 && currentDeleteSwipePos != -1) {
                val start = minOf(deleteSwipeStartPos, currentDeleteSwipePos)
                val end = maxOf(deleteSwipeStartPos, currentDeleteSwipePos)
                if (start < end) {
                    editText.delete(start, end)
                    editCursorPos = start
                }
            }
            deleteSwipeStartPos = -1
            currentDeleteSwipePos = -1
            updateEditDisplay()
            return
        }
        keyboardActionListener.onUpWithDeletePointerActive()
    }
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
        applyClipboardToolbarKeyLayoutParams()
        KeyboardSwitcher.getInstance().clipboardStrip?.post { applyClipboardToolbarKeyLayoutParams() }
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
        keyboardView.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)
        keyboardView.setKeyboardActionListener(this)  // Set 'this' as listener to intercept
        PointerTracker.switchTo(keyboardView)
        val kls = KeyboardLayoutSet.Builder.buildEmojiClipBottomRow(context, editorInfo)
        val keyboard = kls.getKeyboard(elementId)
        keyboardView.setKeyboard(keyboard)
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        if (!enabled) return
        // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        setLayerType(LAYER_TYPE_HARDWARE, null)
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
        historyManager.sortHistoryEntries()
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
 


    fun stopClipboardHistory() {
        if (!this::clipboardAdapter.isInitialized) return

        // Stop edit mode if active
        if (inEditMode && isAttachedToWindow) {
            stopEditMode(save = false)
        }
        
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
            val code = getCodeForToolbarKey(tag)
            if (code == KeyCode.CLIPBOARD_CLEAR_HISTORY) {
                dismissConfirmationBar()
                clipboardHistoryManager.clearHistory()
                return true
            }
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

    override fun onClipChanged(position: Int) {
        confirmationHandler.post {
            clipboardAdapter.refresh()
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        setToolbarButtonsActivatedStateOnPrefChange(KeyboardSwitcher.getInstance().clipboardStrip, key)

        if (key == Settings.PREF_AUTO_SPAN_TOOLBAR_KEYS || key == Settings.PREF_CLIPBOARD_TOOLBAR_KEYS) {
            applyClipboardToolbarKeyLayoutParams()
            KeyboardSwitcher.getInstance().clipboardStrip?.post { applyClipboardToolbarKeyLayoutParams() }
        }

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

    private fun applyClipboardToolbarKeyLayoutParams() {
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip ?: return
        val count = clipboardStrip.childCount
        if (count == 0) return

        val parentView = (clipboardStrip.parent as? View)
        val containerWidth = parentView?.width?.takeIf { it > 0 }
            ?: parentView?.measuredWidth?.takeIf { it > 0 }
            ?: clipboardStrip.width.takeIf { it > 0 }
            ?: clipboardStrip.measuredWidth.takeIf { it > 0 }
            ?: context.resources.displayMetrics.widthPixels

        val singleKeyWidth = kotlin.math.min(
            context.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width),
            context.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        )
        val totalKeysWidth = count * singleKeyWidth

        val isAutoSpan = Settings.getValues().mAutoSpanToolbarKeys
        val useEqualSpacing = isAutoSpan && containerWidth > 0 && totalKeysWidth <= containerWidth

        clipboardStrip.gravity = if (useEqualSpacing) Gravity.NO_GRAVITY else (Gravity.START or Gravity.CENTER_VERTICAL)

        val toolbarKeyLayoutParams = LinearLayout.LayoutParams(
            singleKeyWidth,
            singleKeyWidth
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        for (i in 0 until count) {
            val child = clipboardStrip.getChildAt(i) ?: continue
            child.layoutParams = if (useEqualSpacing) {
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            } else {
                toolbarKeyLayoutParams
            }
        }
    }
}
