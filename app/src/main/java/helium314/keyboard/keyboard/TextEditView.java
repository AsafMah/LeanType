// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import helium314.keyboard.keyboard.internal.KeyboardIconsSet;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.common.Colors;
import helium314.keyboard.latin.settings.Settings;

public class TextEditView extends LinearLayout {

    public interface TextEditListener {
        void onCursorMove(int keyCode, boolean isSelecting);
        void onCodeInput(int keyCode);
        void onClose();
    }

    private TextEditListener mListener;
    private boolean mSelectionMode = false;

    // Buttons
    private TextView mBtnSelectAll;
    private TextView mBtnSelect;
    private TextView mBtnCut;
    private TextView mBtnCopy;
    private TextView mBtnPaste;
    private ImageView mBtnClose;

    private ImageView mBtnHome;
    private ImageView mBtnWordLeft;
    private ImageView mBtnArrowUp;
    private ImageView mBtnWordRight;
    private ImageView mBtnEnd;

    private ImageView mBtnBackspace;
    private ImageView mBtnArrowLeft;
    private ImageView mBtnArrowDown;
    private ImageView mBtnArrowRight;
    private ImageView mBtnDelete;

    public TextEditView(Context context) {
        super(context);
        init(context);
    }

    public TextEditView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TextEditView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        setClickable(true);
        setFocusable(true);
        setFitsSystemWindows(true);

        LayoutInflater.from(context).inflate(R.layout.text_edit_view, this, true);

        mBtnSelectAll = findViewById(R.id.btn_select_all);
        mBtnSelect = findViewById(R.id.btn_select);
        mBtnCut = findViewById(R.id.btn_cut);
        mBtnCopy = findViewById(R.id.btn_copy);
        mBtnPaste = findViewById(R.id.btn_paste);
        mBtnClose = findViewById(R.id.btn_close);

        mBtnHome = findViewById(R.id.btn_home);
        mBtnWordLeft = findViewById(R.id.btn_word_left);
        mBtnArrowUp = findViewById(R.id.btn_arrow_up);
        mBtnWordRight = findViewById(R.id.btn_word_right);
        mBtnEnd = findViewById(R.id.btn_end);

        mBtnBackspace = findViewById(R.id.btn_backspace);
        mBtnArrowLeft = findViewById(R.id.btn_arrow_left);
        mBtnArrowDown = findViewById(R.id.btn_arrow_down);
        mBtnArrowRight = findViewById(R.id.btn_arrow_right);
        mBtnDelete = findViewById(R.id.btn_delete);

        setupClickListeners();
    }

    private void setupClickListeners() {
        mBtnSelectAll.setOnClickListener(v -> {
            if (mListener != null) mListener.onCodeInput(KeyCode.CLIPBOARD_SELECT_ALL);
        });

        mBtnSelect.setOnClickListener(v -> {
            mSelectionMode = !mSelectionMode;
            applyColors(Settings.getValues().mColors);
        });

        mBtnCut.setOnClickListener(v -> {
            if (mListener != null) mListener.onCodeInput(KeyCode.CLIPBOARD_CUT);
            mSelectionMode = false;
            applyColors(Settings.getValues().mColors);
        });

        mBtnCopy.setOnClickListener(v -> {
            if (mListener != null) mListener.onCodeInput(KeyCode.CLIPBOARD_COPY);
            mSelectionMode = false;
            applyColors(Settings.getValues().mColors);
        });

        mBtnPaste.setOnClickListener(v -> {
            if (mListener != null) mListener.onCodeInput(KeyCode.CLIPBOARD_PASTE);
        });

        mBtnClose.setOnClickListener(v -> {
            if (mListener != null) mListener.onClose();
        });

        mBtnHome.setOnClickListener(v -> {
            if (mListener != null) mListener.onCodeInput(KeyCode.MOVE_START_OF_LINE);
        });

        mBtnWordLeft.setOnClickListener(v -> {
            if (mListener != null) mListener.onCodeInput(KeyCode.WORD_LEFT);
        });

        mBtnArrowUp.setOnClickListener(v -> {
            if (mListener != null) mListener.onCursorMove(KeyCode.ARROW_UP, mSelectionMode);
        });

        mBtnWordRight.setOnClickListener(v -> {
            if (mListener != null) mListener.onCodeInput(KeyCode.WORD_RIGHT);
        });

        mBtnEnd.setOnClickListener(v -> {
            if (mListener != null) mListener.onCodeInput(KeyCode.MOVE_END_OF_LINE);
        });

        mBtnBackspace.setOnClickListener(v -> {
            if (mListener != null) mListener.onCodeInput(KeyCode.DELETE);
        });

        mBtnArrowLeft.setOnClickListener(v -> {
            if (mListener != null) mListener.onCursorMove(KeyCode.ARROW_LEFT, mSelectionMode);
        });

        mBtnArrowDown.setOnClickListener(v -> {
            if (mListener != null) mListener.onCursorMove(KeyCode.ARROW_DOWN, mSelectionMode);
        });

        mBtnArrowRight.setOnClickListener(v -> {
            if (mListener != null) mListener.onCursorMove(KeyCode.ARROW_RIGHT, mSelectionMode);
        });

        mBtnDelete.setOnClickListener(v -> {
            if (mListener != null) mListener.onCodeInput(KeyCode.FORWARD_DELETE);
        });
    }

    public void setTextEditListener(TextEditListener listener) {
        mListener = listener;
    }

    public void applyColors(Colors colors) {
        colors.setBackground(this, ColorType.MAIN_BACKGROUND);

        int keyTextColor = colors.get(ColorType.KEY_TEXT);
        int functionalKeyTextColor = colors.get(ColorType.FUNCTIONAL_KEY_TEXT);
        int keyIconColor = colors.get(ColorType.KEY_ICON);

        // Apply background and text colors to Action Buttons
        setKeyStyle(mBtnSelectAll, colors, false, keyTextColor);
        setKeyStyle(mBtnSelect, colors, mSelectionMode, mSelectionMode ? functionalKeyTextColor : keyTextColor);
        setKeyStyle(mBtnCut, colors, false, keyTextColor);
        setKeyStyle(mBtnCopy, colors, false, keyTextColor);
        setKeyStyle(mBtnPaste, colors, false, keyTextColor);

        // Retrieve theme-aware icons
        KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
        KeyboardIconsSet iconsSet = (switcher != null && switcher.getKeyboard() != null) ? switcher.getKeyboard().mIconsSet : null;

        setIconKeyStyle(mBtnClose, iconsSet, "close_history", colors, false, keyIconColor);
        setIconKeyStyle(mBtnHome, iconsSet, "page_start", colors, false, keyIconColor);
        setIconKeyStyle(mBtnWordLeft, iconsSet, "word_left", colors, false, keyIconColor);
        setIconKeyStyle(mBtnArrowUp, iconsSet, "up", colors, false, keyIconColor);
        setIconKeyStyle(mBtnWordRight, iconsSet, "word_right", colors, false, keyIconColor);
        setIconKeyStyle(mBtnEnd, iconsSet, "page_end", colors, false, keyIconColor);
        setIconKeyStyle(mBtnBackspace, iconsSet, "delete_key", colors, false, keyIconColor);
        setIconKeyStyle(mBtnArrowLeft, iconsSet, "left", colors, false, keyIconColor);
        setIconKeyStyle(mBtnArrowDown, iconsSet, "down", colors, false, keyIconColor);
        setIconKeyStyle(mBtnArrowRight, iconsSet, "right", colors, false, keyIconColor);
        setIconKeyStyle(mBtnDelete, iconsSet, "clear_clipboard", colors, false, keyIconColor);
    }

    private void setKeyStyle(TextView textView, Colors colors, boolean isHighlighted, int textColor) {
        textView.setBackground(createKeyBackground(colors, isHighlighted));
        textView.setTextColor(textColor);
    }

    private void setIconKeyStyle(ImageView imageView, KeyboardIconsSet iconsSet, String iconName, Colors colors, boolean isHighlighted, int iconColor) {
        imageView.setBackground(createKeyBackground(colors, isHighlighted));
        if (iconsSet != null) {
            Drawable icon = iconsSet.getIconDrawable(iconName);
            if (icon != null) {
                Drawable mutated = icon.mutate();
                mutated.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
                imageView.setImageDrawable(mutated);
            }
        }
    }

    private Drawable createKeyBackground(Colors colors, boolean isHighlighted) {
        float density = getContext().getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(6f * density);

        ColorType colorType = isHighlighted ? ColorType.FUNCTIONAL_KEY_BACKGROUND : ColorType.KEY_BACKGROUND;
        gd.setColor(colors.get(colorType));
        return gd;
    }
}
