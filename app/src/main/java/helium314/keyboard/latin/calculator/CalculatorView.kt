// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.calculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.dpToPx
import java.math.BigDecimal

class CalculatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface CalculatorListener {
        fun onInsertText(text: String)
        fun onClose()
    }

    private var listener: CalculatorListener? = null
    private val historyManager by lazy { CalculatorHistoryManager.getInstance(context) }

    // Views
    private var expressionScroll: HorizontalScrollView? = null
    private var expressionText: TextView? = null
    private var previewText: TextView? = null
    private var displayCard: LinearLayout? = null
    private var historyOverlay: LinearLayout? = null
    private var historyListContainer: LinearLayout? = null
    private var btnHistory: ImageButton? = null
    private var btnCopy: ImageButton? = null
    private var btnInsert: TextView? = null
    private var btnClose: ImageButton? = null
    private var btnClearHistory: TextView? = null
    private var btnCloseHistory: ImageButton? = null

    // Calculator State
    private var currentExpression: String = "0"
    private var justEvaluated: Boolean = false
    private var lastAnswer: BigDecimal? = null
    private var lastComputedResultFormatted: String? = null
    private var currentColors: Colors? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        bindViews()
        setupListeners()
        updateDisplay()
    }

    fun setListener(listener: CalculatorListener) {
        this.listener = listener
    }

    private fun bindViews() {
        expressionScroll = findViewById(R.id.calc_expression_scroll)
        expressionText = findViewById(R.id.calc_expression_text)
        previewText = findViewById(R.id.calc_preview_text)
        displayCard = findViewById(R.id.calc_display_card)
        historyOverlay = findViewById(R.id.calc_history_overlay)
        historyListContainer = findViewById(R.id.calc_history_list_container)
        btnHistory = findViewById(R.id.calc_btn_history)
        btnCopy = findViewById(R.id.calc_btn_copy)
        btnInsert = findViewById(R.id.calc_btn_insert)
        btnClose = findViewById(R.id.calc_btn_close)
        btnClearHistory = findViewById(R.id.calc_btn_clear_history)
        btnCloseHistory = findViewById(R.id.calc_btn_close_history)

        val customTypeface = Settings.getInstance().customTypeface
        if (customTypeface != null) {
            expressionText?.typeface = customTypeface
            previewText?.typeface = customTypeface
        }
    }

    private fun setupListeners() {
        fun setKey(id: Int, action: () -> Unit) {
            findViewById<TextView>(id)?.setOnClickListener { v ->
                playFeedback(v)
                action()
            }
        }

        // Digits
        setKey(R.id.calc_key_0) { appendDigit("0") }
        setKey(R.id.calc_key_00) { appendDigit("00") }
        setKey(R.id.calc_key_1) { appendDigit("1") }
        setKey(R.id.calc_key_2) { appendDigit("2") }
        setKey(R.id.calc_key_3) { appendDigit("3") }
        setKey(R.id.calc_key_4) { appendDigit("4") }
        setKey(R.id.calc_key_5) { appendDigit("5") }
        setKey(R.id.calc_key_6) { appendDigit("6") }
        setKey(R.id.calc_key_7) { appendDigit("7") }
        setKey(R.id.calc_key_8) { appendDigit("8") }
        setKey(R.id.calc_key_9) { appendDigit("9") }
        setKey(R.id.calc_key_dot) { appendDot() }

        // Operators
        setKey(R.id.calc_key_add) { appendOperator("+") }
        setKey(R.id.calc_key_sub) { appendOperator("−") }
        setKey(R.id.calc_key_mul) { appendOperator("×") }
        setKey(R.id.calc_key_div) { appendOperator("÷") }
        setKey(R.id.calc_key_percent) { appendPercent() }
        setKey(R.id.calc_key_pow) { appendOperator("^") }
        setKey(R.id.calc_key_paren) { appendSmartParen() }
        setKey(R.id.calc_key_negate) { toggleNegate() }
        setKey(R.id.calc_key_ans) { appendAns() }

        // Clear & Backspace
        setKey(R.id.calc_key_clear) { clearAll() }
        findViewById<TextView>(R.id.calc_key_backspace)?.let { btn ->
            btn.setOnClickListener { v ->
                playFeedback(v)
                backspace()
            }
            btn.setOnLongClickListener { v ->
                playFeedback(v)
                clearAll()
                true
            }
        }

        // Equals
        setKey(R.id.calc_key_equals) { evaluateEquals() }

        // Alphabet switch
        setKey(R.id.calc_key_abc) {
            listener?.onClose()
        }

        // Top Header Actions
        btnHistory?.setOnClickListener { v ->
            playFeedback(v)
            toggleHistory(true)
        }

        btnCopy?.setOnClickListener { v ->
            playFeedback(v)
            copyCurrent()
        }

        btnInsert?.setOnClickListener { v ->
            playFeedback(v)
            insertResult(includeEquation = false)
        }

        btnInsert?.setOnLongClickListener { v ->
            playFeedback(v)
            insertResult(includeEquation = true)
            true
        }

        btnClose?.setOnClickListener { v ->
            playFeedback(v)
            listener?.onClose()
        }

        // History overlay buttons
        btnClearHistory?.setOnClickListener { v ->
            playFeedback(v)
            historyManager.clearHistory()
            renderHistoryItems()
        }

        btnCloseHistory?.setOnClickListener { v ->
            playFeedback(v)
            toggleHistory(false)
        }
    }

    private fun playFeedback(v: View) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
            KeyCode.NOT_SPECIFIED, v, HapticEvent.KEY_PRESS
        )
    }

    private fun appendDigit(digit: String) {
        if (justEvaluated || currentExpression == "0" || currentExpression == "Error") {
            currentExpression = digit
            justEvaluated = false
        } else {
            currentExpression += digit
        }
        updateDisplay()
    }

    private fun appendDot() {
        if (justEvaluated || currentExpression == "Error") {
            currentExpression = "0."
            justEvaluated = false
            updateDisplay()
            return
        }

        // Prevent multiple dots in the same number segment
        val lastNumberSegment = currentExpression.takeLastWhile { it.isDigit() || it == '.' }
        if (!lastNumberSegment.contains('.')) {
            if (currentExpression.isEmpty() || !currentExpression.last().isDigit()) {
                currentExpression += "0."
            } else {
                currentExpression += "."
            }
        }
        updateDisplay()
    }

    private fun appendOperator(op: String) {
        if (currentExpression == "Error") {
            currentExpression = "0"
        }
        justEvaluated = false

        if (currentExpression.isEmpty()) {
            if (op == "−") {
                currentExpression = "−"
            }
            updateDisplay()
            return
        }

        val last = currentExpression.last()
        if (last in listOf('+', '−', '×', '÷', '^')) {
            // Replace trailing operator
            currentExpression = currentExpression.dropLast(1) + op
        } else {
            currentExpression += op
        }
        updateDisplay()
    }

    private fun appendPercent() {
        if (currentExpression == "Error" || currentExpression == "0") return
        justEvaluated = false
        val last = currentExpression.last()
        if (last.isDigit() || last == ')') {
            currentExpression += "%"
            updateDisplay()
        }
    }

    private fun appendSmartParen() {
        if (justEvaluated || currentExpression == "0" || currentExpression == "Error") {
            currentExpression = "("
            justEvaluated = false
            updateDisplay()
            return
        }

        val openCount = currentExpression.count { it == '(' }
        val closeCount = currentExpression.count { it == ')' }
        val last = currentExpression.last()

        if (last in listOf('+', '−', '×', '÷', '^', '(') || currentExpression.isEmpty()) {
            currentExpression += "("
        } else if (openCount > closeCount && (last.isDigit() || last == ')' || last == '%')) {
            currentExpression += ")"
        } else {
            currentExpression += "×("
        }
        updateDisplay()
    }

    private fun toggleNegate() {
        if (justEvaluated || currentExpression == "0" || currentExpression == "Error") return
        // If whole expression starts with "−", remove it, else prepend "−(" and append ")"
        if (currentExpression.startsWith("−(") && currentExpression.endsWith(")")) {
            currentExpression = currentExpression.substring(2, currentExpression.length - 1)
        } else {
            currentExpression = "−($currentExpression)"
        }
        updateDisplay()
    }

    private fun appendAns() {
        val ans = lastAnswer ?: historyManager.getLastAnswer()?.let { runCatching { BigDecimal(it) }.getOrNull() }
        if (ans != null) {
            if (justEvaluated || currentExpression == "0" || currentExpression == "Error") {
                currentExpression = "Ans"
                justEvaluated = false
            } else {
                val last = currentExpression.last()
                if (last.isDigit() || last == ')') {
                    currentExpression += "×Ans"
                } else {
                    currentExpression += "Ans"
                }
            }
            updateDisplay()
        }
    }

    private fun clearAll() {
        currentExpression = "0"
        justEvaluated = false
        lastComputedResultFormatted = null
        updateDisplay()
    }

    private fun backspace() {
        if (justEvaluated || currentExpression == "Error" || currentExpression.length <= 1) {
            currentExpression = "0"
            justEvaluated = false
        } else {
            if (currentExpression.endsWith("Ans")) {
                currentExpression = currentExpression.dropLast(3)
            } else {
                currentExpression = currentExpression.dropLast(1)
            }
            if (currentExpression.isEmpty()) currentExpression = "0"
        }
        updateDisplay()
    }

    private fun evaluateEquals() {
        if (currentExpression.isBlank() || currentExpression == "Error") return

        val result = MathEvaluator.evaluate(currentExpression, lastAnswer)
        when (result) {
            is MathResult.Success -> {
                val formatted = result.formatted
                historyManager.addEntry(currentExpression, formatted)
                lastAnswer = result.value
                lastComputedResultFormatted = formatted
                currentExpression = formatted
                justEvaluated = true
                expressionText?.text = currentExpression
                previewText?.text = ""
                expressionScroll?.post { expressionScroll?.fullScroll(View.FOCUS_RIGHT) }
            }
            is MathResult.Error -> {
                currentExpression = "Error"
                justEvaluated = true
                expressionText?.text = currentExpression
                previewText?.text = result.message
            }
        }
    }

    private fun updateDisplay() {
        expressionText?.text = currentExpression
        expressionScroll?.post { expressionScroll?.fullScroll(View.FOCUS_RIGHT) }

        // Live calculation preview
        if (currentExpression.any { it in "+−×÷^%" } && currentExpression != "Error") {
            val liveResult = MathEvaluator.evaluate(currentExpression, lastAnswer)
            if (liveResult is MathResult.Success) {
                lastComputedResultFormatted = liveResult.formatted
                previewText?.text = "= ${liveResult.formatted}"
            } else {
                previewText?.text = ""
            }
        } else {
            previewText?.text = ""
        }
    }

    private fun copyCurrent() {
        val textToCopy = lastComputedResultFormatted ?: currentExpression
        if (textToCopy.isNotBlank() && textToCopy != "Error") {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Calculator Result", textToCopy))
            Toast.makeText(context, context.getString(R.string.calculator_copied, textToCopy), Toast.LENGTH_SHORT).show()
        }
    }

    private fun insertResult(includeEquation: Boolean) {
        val result = lastComputedResultFormatted ?: currentExpression
        if (result.isBlank() || result == "Error") return

        val textToInsert = if (includeEquation && currentExpression != result) {
            "$currentExpression = $result"
        } else {
            result
        }
        listener?.onInsertText(textToInsert)
    }

    private fun toggleHistory(show: Boolean) {
        historyOverlay?.isVisible = show
        if (show) {
            renderHistoryItems()
        }
    }

    private fun renderHistoryItems() {
        val container = historyListContainer ?: return
        container.removeAllViews()
        val items = historyManager.getHistory()
        val colors = currentColors ?: Settings.getValues().mColors

        if (items.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = context.getString(R.string.calculator_empty_history)
                gravity = Gravity.CENTER
                textSize = 14f
                setPadding(0, 32.dpToPx(resources).toInt(), 0, 32.dpToPx(resources).toInt())
                setTextColor(colors.get(ColorType.KEY_TEXT))
                alpha = 0.6f
            }
            container.addView(emptyTv)
            return
        }

        for (item in items) {
            val row = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(12.dpToPx(resources).toInt(), 8.dpToPx(resources).toInt(), 12.dpToPx(resources).toInt(), 8.dpToPx(resources).toInt())
                isClickable = true
                isFocusable = true
                background = createKeyDrawable(colors.get(ColorType.KEY_BACKGROUND), 8f)
                val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 4.dpToPx(resources).toInt(), 0, 4.dpToPx(resources).toInt())
                }
                layoutParams = lp

                setOnClickListener {
                    playFeedback(it)
                    currentExpression = item.result
                    lastAnswer = runCatching { BigDecimal(item.result) }.getOrNull()
                    justEvaluated = true
                    updateDisplay()
                    toggleHistory(false)
                }
            }

            val exprTv = TextView(context).apply {
                text = item.expression
                textSize = 14f
                setTextColor(colors.get(ColorType.KEY_TEXT))
                alpha = 0.75f
            }
            val resTv = TextView(context).apply {
                text = "= ${item.result}"
                textSize = 17f
                setTextColor(colors.get(ColorType.SUGGESTION_AUTO_CORRECT))
                textStyleBold()
            }

            row.addView(exprTv)
            row.addView(resTv)
            container.addView(row)
        }
    }

    private fun TextView.textStyleBold() {
        this.setTypeface(this.typeface, android.graphics.Typeface.BOLD)
    }

    fun applyColors(colors: Colors) {
        currentColors = colors
        colors.setBackground(this, ColorType.MAIN_BACKGROUND)
        displayCard?.background = createKeyDrawable(colors.get(ColorType.STRIP_BACKGROUND), 12f)
        historyOverlay?.background = createKeyDrawable(colors.get(ColorType.MAIN_BACKGROUND), 0f)

        val textColor = colors.get(ColorType.KEY_TEXT)
        val functionalTextColor = colors.get(ColorType.FUNCTIONAL_KEY_TEXT)
        val accentColor = colors.get(ColorType.SUGGESTION_AUTO_CORRECT)

        expressionText?.setTextColor(textColor)
        previewText?.setTextColor(accentColor)
        btnInsert?.setTextColor(accentColor)
        btnClearHistory?.setTextColor(accentColor)
        findViewById<TextView>(R.id.calc_history_title)?.setTextColor(textColor)

        colors.setColor(btnHistory ?: return, ColorType.KEY_ICON)
        colors.setColor(btnCopy ?: return, ColorType.KEY_ICON)
        colors.setColor(btnClose ?: return, ColorType.REMOVE_SUGGESTION_ICON)
        colors.setColor(btnCloseHistory ?: return, ColorType.REMOVE_SUGGESTION_ICON)

        // Keypad buttons
        val standardKeys = listOf(
            R.id.calc_key_0, R.id.calc_key_00, R.id.calc_key_1, R.id.calc_key_2, R.id.calc_key_3,
            R.id.calc_key_4, R.id.calc_key_5, R.id.calc_key_6, R.id.calc_key_7, R.id.calc_key_8,
            R.id.calc_key_9, R.id.calc_key_dot
        )
        val functionalKeys = listOf(
            R.id.calc_key_clear, R.id.calc_key_paren, R.id.calc_key_percent, R.id.calc_key_div,
            R.id.calc_key_mul, R.id.calc_key_sub, R.id.calc_key_add, R.id.calc_key_pow,
            R.id.calc_key_negate, R.id.calc_key_ans, R.id.calc_key_backspace, R.id.calc_key_abc
        )

        val keyBgColor = colors.get(ColorType.KEY_BACKGROUND)
        val funcBgColor = colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)
        val equalsBgColor = colors.get(ColorType.ACTION_KEY_BACKGROUND)

        val keyRadius = 6.dpToPx(resources).toFloat()

        standardKeys.forEach { id ->
            findViewById<TextView>(id)?.apply {
                background = createKeyDrawable(keyBgColor, keyRadius)
                setTextColor(textColor)
            }
        }

        functionalKeys.forEach { id ->
            findViewById<TextView>(id)?.apply {
                background = createKeyDrawable(funcBgColor, keyRadius)
                setTextColor(functionalTextColor)
            }
        }

        findViewById<TextView>(R.id.calc_key_equals)?.apply {
            background = createKeyDrawable(equalsBgColor, keyRadius)
            setTextColor(textColor)
        }
    }

    private fun createKeyDrawable(color: Int, radius: Float): RippleDrawable {
        val gd = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = radius
        }
        val rippleColor = ColorStateList.valueOf(Color.argb(40, 255, 255, 255))
        return RippleDrawable(rippleColor, gd, mask)
    }
}
