// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

sealed class ClipboardDisplayItem {
    data class Header(val count: Int, val isFolded: Boolean) : ClipboardDisplayItem()
    data class Clip(val entry: ClipboardHistoryEntry) : ClipboardDisplayItem()
}

class ClipboardAdapter(
       val clipboardLayoutParams: ClipboardLayoutParams,
       val keyEventListener: OnKeyEventListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var clipboardHistoryManager: ClipboardHistoryManager? = null

    var pinnedIconResId = 0
    var itemBackgroundId = 0
    var itemTypeFace: Typeface? = null
    var itemTextColor = 0
    var itemTextSize = 0f

    private var filteredList: List<ClipboardHistoryEntry>? = null
    private var searchQuery: String = ""
    
    private val displayList = mutableListOf<ClipboardDisplayItem>()
    private var isPinnedFolded = true

    val isFiltering: Boolean
        get() = filteredList != null

    fun filter(query: String) {
        searchQuery = query
        if (query.isEmpty()) {
            filteredList = null
        } else {
            val allClips = clipboardHistoryManager?.getClips() ?: emptyList()
            filteredList = allClips.filter { it.text.contains(query, ignoreCase = true) }
        }
        refresh()
    }

    fun rebuildDisplayList() {
        displayList.clear()
        val allClips = filteredList ?: clipboardHistoryManager?.getClips() ?: emptyList()
        
        if (isFiltering) {
            allClips.forEach { displayList.add(ClipboardDisplayItem.Clip(it)) }
            return
        }

        val isFoldEnabled = Settings.getValues().mClipboardFoldPinned
        if (isFoldEnabled) {
            val pinnedClips = allClips.filter { it.isPinned }
            val unpinnedClips = allClips.filter { !it.isPinned }

            if (pinnedClips.isNotEmpty()) {
                displayList.add(ClipboardDisplayItem.Header(pinnedClips.size, isPinnedFolded))
                if (!isPinnedFolded) {
                    pinnedClips.forEach { displayList.add(ClipboardDisplayItem.Clip(it)) }
                }
            }
            unpinnedClips.forEach { displayList.add(ClipboardDisplayItem.Clip(it)) }
        } else {
            allClips.forEach { displayList.add(ClipboardDisplayItem.Clip(it)) }
        }
    }

    fun refresh() {
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CLIP = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayList[position]) {
            is ClipboardDisplayItem.Header -> VIEW_TYPE_HEADER
            is ClipboardDisplayItem.Clip -> VIEW_TYPE_CLIP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val textView = TextView(parent.context).apply {
                val lp = StaggeredGridLayoutManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.isFullSpan = true
                layoutParams = lp
                gravity = android.view.Gravity.CENTER
                setPadding(32, 24, 32, 24)
            }
            HeaderViewHolder(textView)
        } else {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.clipboard_entry_key, parent, false)
            ClipViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayList[position]) {
            is ClipboardDisplayItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ClipboardDisplayItem.Clip -> (holder as ClipViewHolder).setContent(item.entry)
        }
    }

    private fun getItem(position: Int): ClipboardHistoryEntry? {
        val item = displayList.getOrNull(position)
        return if (item is ClipboardDisplayItem.Clip) item.entry else null
    }

    override fun getItemCount(): Int {
        return displayList.size
    }

    inner class HeaderViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(header: ClipboardDisplayItem.Header) {
            textView.apply {
                typeface = itemTypeFace
                setTextColor(itemTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, itemTextSize)
                setBackgroundResource(itemBackgroundId)
                
                text = buildString {
                    append(if (header.isFolded) "📁 " else "📂 ")
                    append(context.getString(R.string.clipboard_history_pinned_first))
                    append(" (")
                    append(header.count)
                    append(")")
                }
                
                setOnClickListener {
                    isPinnedFolded = !isPinnedFolded
                    refresh()
                }
            }
            Settings.getValues().mColors.setBackground(textView, ColorType.KEY_BACKGROUND)
        }
    }

    inner class ClipViewHolder(
            view: View
    ) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnTouchListener, View.OnLongClickListener {

        private val pinnedIconView: ImageView
        private val contentView: TextView
        private val imageContainer: View
        private val imageView: ImageView
        private val imageNameView: TextView

        init {
            view.apply {
                setOnClickListener(this@ClipViewHolder)
                setOnTouchListener(this@ClipViewHolder)
                setOnLongClickListener(this@ClipViewHolder)
                setBackgroundResource(itemBackgroundId)
                isHapticFeedbackEnabled = false
            }
            Settings.getValues().mColors.setBackground(view, ColorType.KEY_BACKGROUND)
            pinnedIconView = view.findViewById<ImageView>(R.id.clipboard_entry_pinned_icon).apply {
                visibility = View.GONE
                setImageResource(pinnedIconResId)
            }
            imageContainer = view.findViewById<View>(R.id.clipboard_entry_image_container).apply {
                visibility = View.GONE
            }
            imageView = view.findViewById(R.id.clipboard_entry_image)
            imageNameView = view.findViewById(R.id.clipboard_entry_image_name)
            contentView = view.findViewById<TextView>(R.id.clipboard_entry_content).apply {
                typeface = itemTypeFace
                setTextColor(itemTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, itemTextSize)
            }
            clipboardLayoutParams.setItemProperties(view)
            val colors = Settings.getValues().mColors
            colors.setColor(pinnedIconView, ColorType.CLIPBOARD_PIN)
        }

        fun setContent(historyEntry: ClipboardHistoryEntry?) {
            itemView.tag = historyEntry?.id
            
            if (historyEntry?.imageUri != null) {
                contentView.visibility = View.GONE
                imageContainer.visibility = View.VISIBLE
                val file = java.io.File(historyEntry.imageUri)
                if (file.exists()) {
                    imageView.setImageURI(android.net.Uri.fromFile(file))
                    imageNameView.text = file.name
                }
            } else {
                contentView.visibility = View.VISIBLE
                imageContainer.visibility = View.GONE
                contentView.text = historyEntry?.text?.take(1000) // truncate displayed text for performance reasons
            }
            pinnedIconView.visibility = if (historyEntry?.isPinned == true) View.VISIBLE else View.GONE
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                keyEventListener.onKeyDown(view.tag as Long)
            }
            return false
        }

        override fun onClick(view: View) {
            keyEventListener.onKeyUp(view.tag as Long)
        }

        override fun onLongClick(view: View): Boolean {
            val id = view.tag as? Long ?: return false
            // Delegate to host view which shows the context menu.
            // Fallback to legacy "toggle pin" if the host doesn't handle the event,
            // preserving behaviour for any caller that hasn't been updated.
            if (keyEventListener.onKeyLongPress(id, view)) return true
            clipboardHistoryManager?.toggleClipPinned(id)
            return true
        }
    }
}
