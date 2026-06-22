// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.common.Colors;
import helium314.keyboard.latin.settings.Settings;

/**
 * A laptop-style touchpad overlay that replaces the keyboard.
 * Supports:
 * - Single-finger drag: move cursor (arrow keys)
 * - Two-finger drag: fast scroll (up/down)
 * - Single tap: Enter/Click
 * - Double tap: Toggle text selection mode
 */
public class TouchpadView extends LinearLayout {

    public interface TouchpadListener {
        void onCursorMove(int keyCode, boolean isSelecting);
        void onSingleTap();
        void onDoubleTap();
        void onScroll(int direction);
        void onTwoFingerDoubleTap();
        void onThreeFingerTap();
        void onThreeFingerDoubleTap();
        void onThreeFingerSwipeLeft();
        void onThreeFingerSwipeRight();
        void onThreeFingerSwipeUp();
        void onThreeFingerSwipeDown();
    }

    private TouchpadListener mListener;
    private View mTouchpadSurface;
    private GestureDetector mGestureDetector;

    // State
    private boolean mSelectionMode = false;

    // Touch tracking for the touchpad surface
    private float mLastTouchX;
    private float mLastTouchY;
    private float mAccX;
    private float mAccY;
    private boolean mIsDragging;

    // Two-finger scroll tracking
    private boolean mIsTwoFingerScroll;
    private float mTwoFingerLastX;
    private float mTwoFingerLastY;
    private float mScrollAccX;
    private float mScrollAccY;
    private float mTwoFingerStartX;
    private float mTwoFingerStartY;
    private boolean mHasScrolledHorizontally;
    private boolean mIsTwoFingerLongPress;
    private int mTwoFingerTapCount = 0;

    private final Runnable mTwoFingerLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsTwoFingerTap) {
                mIsTwoFingerLongPress = true;
                if (mListener != null) {
                    mListener.onThreeFingerSwipeLeft();
                }
                postDelayed(this, 150);
            }
        }
    };
    
    // Two-finger tap tracking
    private boolean mIsTwoFingerTap;
    private long mTwoFingerDownTime;
    private final Runnable mTwoFingerTapRunnable = new Runnable() {
        @Override
        public void run() {
            if (mListener != null) {
                if (mTwoFingerTapCount == 1) {
                    mListener.onSingleTap();
                } else if (mTwoFingerTapCount == 2) {
                    mListener.onTwoFingerDoubleTap();
                } else if (mTwoFingerTapCount >= 3) {
                    mListener.onThreeFingerDoubleTap();
                }
            }
            mTwoFingerTapCount = 0;
        }
    };

    // Three-finger tap & swipe tracking
    private boolean mIsThreeFingerTap;
    private long mThreeFingerDownTime;
    private long mLastThreeFingerTapTime = 0;
    private final Runnable mThreeFingerTapRunnable = new Runnable() {
        @Override
        public void run() {
            if (mListener != null) mListener.onThreeFingerTap();
        }
    };
    private boolean mIsThreeFingerSwipe;
    private float mThreeFingerStartX;
    private float mThreeFingerStartY;

    private static final int SCROLL_THRESHOLD = 40;

    // Edge-scroll acceleration constants (mirrors HeliBoard's TouchpadHandler)
    private static final float EDGE_THRESHOLD_PERCENTAGE = 0.1f;
    private static final float EDGE_ACCELERATION_FACTOR = 0.95f;
    private static final int MIN_EDGE_ACCELERATION_DELAY = 20;

    // Edge-scroll state
    private final Handler mEdgeScrollHandler = new Handler(Looper.getMainLooper());
    private boolean mIsEdgeScrolling = false;
    private int mEdgeScrollDirection = KeyCode.UNSPECIFIED;
    private long mEdgeScrollDelay = 200;

    private final Runnable mEdgeScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsEdgeScrolling && mListener != null) {
                mListener.onCursorMove(mEdgeScrollDirection, mSelectionMode);
                mEdgeScrollDelay = Math.max(MIN_EDGE_ACCELERATION_DELAY,
                        (long) (mEdgeScrollDelay * EDGE_ACCELERATION_FACTOR));
                mEdgeScrollHandler.postDelayed(this, mEdgeScrollDelay);
            }
        }
    };

    public TouchpadView(Context context) {
        super(context);
        init(context);
    }

    public TouchpadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TouchpadView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        // Consume all touches so nothing passes through to views behind
        setClickable(true);
        setFocusable(true);
        setFitsSystemWindows(true);

        LayoutInflater.from(context).inflate(R.layout.touchpad_view, this, true);
        mTouchpadSurface = findViewById(R.id.touchpad_surface);

        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                mSelectionMode = true;
                applySurfaceColor();
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (mListener != null) {
                    mListener.onDoubleTap();
                    return true;
                }
                return false;
            }
        });

        setupTouchSurface();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Intercept all touches to prevent them from reaching views behind
        return false; // Let children handle their own touches
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Consume any touch not handled by children
        return true;
    }

    public void setTouchpadListener(TouchpadListener listener) {
        mListener = listener;
    }

    public void applyColors(Colors colors) {
        // Root background
        colors.setBackground(this, ColorType.MAIN_BACKGROUND);
        applySurfaceColor();
    }

    private void applySurfaceColor() {
        if (mTouchpadSurface == null) return;
        Colors colors = Settings.getValues().mColors;
        float density = getContext().getResources().getDisplayMetrics().density;
        
        GradientDrawable surfaceBg = new GradientDrawable();
        surfaceBg.setShape(GradientDrawable.RECTANGLE);
        surfaceBg.setCornerRadius(16f * density);
        surfaceBg.setColor(android.graphics.Color.WHITE);
        mTouchpadSurface.setBackground(surfaceBg);
        
        // Use a different color type for selection mode to provide visual feedback
        ColorType surfaceColorType = mSelectionMode ? ColorType.FUNCTIONAL_KEY_BACKGROUND : ColorType.KEY_BACKGROUND;
        colors.setBackground(mTouchpadSurface, surfaceColorType);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchSurface() {
        mTouchpadSurface.setOnTouchListener((v, event) -> {
            mGestureDetector.onTouchEvent(event);
            final int pointerCount = event.getPointerCount();
            android.util.Log.i("TouchpadViewRaw", "action=" + MotionEvent.actionToString(event.getActionMasked()) + ", pointers=" + pointerCount);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    android.util.Log.i("TouchpadView", "ACTION_DOWN");
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    mLastTouchX = event.getX();
                    mLastTouchY = event.getY();
                    mAccX = 0;
                    mAccY = 0;
                    mIsDragging = true;
                    mIsTwoFingerScroll = false;
                    return true;

                case MotionEvent.ACTION_POINTER_DOWN:
                    android.util.Log.i("TouchpadView", "ACTION_POINTER_DOWN: pointerCount=" + pointerCount);
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (pointerCount == 2) {
                        mIsTwoFingerScroll = true;
                        mIsTwoFingerTap = true;
                        mTwoFingerDownTime = System.currentTimeMillis();
                        mIsDragging = false;
                        mTwoFingerStartX = (event.getX(0) + event.getX(1)) / 2f;
                        mTwoFingerStartY = (event.getY(0) + event.getY(1)) / 2f;
                        mTwoFingerLastX = mTwoFingerStartX;
                        mTwoFingerLastY = mTwoFingerStartY;
                        mScrollAccX = 0;
                        mScrollAccY = 0;
                        mHasScrolledHorizontally = false;
                        mIsTwoFingerLongPress = false;
                        
                        removeCallbacks(mTwoFingerTapRunnable);
                        postDelayed(mTwoFingerLongPressRunnable, 400);
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (mIsTwoFingerScroll && pointerCount >= 2) {
                        float midX = (event.getX(0) + event.getX(1)) / 2f;
                        float midY = (event.getY(0) + event.getY(1)) / 2f;
                        float deltaX = midX - mTwoFingerStartX;
                        float deltaY = midY - mTwoFingerStartY;

                        float density = getContext().getResources().getDisplayMetrics().density;
                        
                        if (Math.abs(midX - mTwoFingerStartX) > 5f * density || Math.abs(midY - mTwoFingerStartY) > 5f * density) {
                            mIsTwoFingerTap = false;
                            removeCallbacks(mTwoFingerLongPressRunnable);
                        }

                        float swipeThreshold = 35f * density;
                        if (!mHasScrolledHorizontally && Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > swipeThreshold) {
                            mIsTwoFingerScroll = false;
                            mIsTwoFingerTap = false;
                            removeCallbacks(mTwoFingerLongPressRunnable);
                            mTwoFingerTapCount = 0;
                            removeCallbacks(mTwoFingerTapRunnable);
                            
                            if (mListener != null) {
                                if (deltaY < 0) {
                                    mListener.onThreeFingerSwipeUp();
                                } else {
                                    mListener.onThreeFingerSwipeDown();
                                }
                            }
                        } else {
                            float lastDeltaX = midX - mTwoFingerLastX;
                            mTwoFingerLastX = midX;
                            mTwoFingerLastY = midY;

                            mScrollAccX += lastDeltaX;

                            while (mScrollAccX >= SCROLL_THRESHOLD) {
                                mIsTwoFingerTap = false;
                                removeCallbacks(mTwoFingerLongPressRunnable);
                                mTwoFingerTapCount = 0;
                                removeCallbacks(mTwoFingerTapRunnable);
                                mHasScrolledHorizontally = true;
                                if (mListener != null) mListener.onScroll(KeyCode.WORD_RIGHT);
                                mScrollAccX -= SCROLL_THRESHOLD;
                            }
                            while (mScrollAccX <= -SCROLL_THRESHOLD) {
                                mIsTwoFingerTap = false;
                                removeCallbacks(mTwoFingerLongPressRunnable);
                                mTwoFingerTapCount = 0;
                                removeCallbacks(mTwoFingerTapRunnable);
                                mHasScrolledHorizontally = true;
                                if (mListener != null) mListener.onScroll(KeyCode.WORD_LEFT);
                                mScrollAccX += SCROLL_THRESHOLD;
                            }
                        }
                    } else if (mIsDragging && pointerCount == 1) {
                        float x = event.getX();
                        float y = event.getY();
                        float deltaX = x - mLastTouchX;
                        float deltaY = y - mLastTouchY;
                        mLastTouchX = x;
                        mLastTouchY = y;

                        // Edge-scroll acceleration: when pref enabled and touch is near an edge,
                        // start a repeating accelerating cursor-move instead of normal tracking.
                        if (Settings.getValues().mTouchpadEdgeScroll && handleEdgeScrolling(x, y)) {
                            mAccX = 0;
                            mAccY = 0;
                        } else {
                            stopEdgeScrolling();

                            mAccX += deltaX;
                            mAccY += deltaY;

                            int sensitivity = Settings.getValues().mTouchpadSensitivity;
                            // Base threshold is 110 for normal slow cursor, but 70 for fast selection
                            int baseThreshold = mSelectionMode ? 70 : 110;
                            int threshold = baseThreshold - (int) (sensitivity * 0.6f);
                            if (threshold < 10) threshold = 10;

                            while (mAccX >= threshold) {
                                if (mListener != null) mListener.onCursorMove(KeyCode.ARROW_RIGHT, mSelectionMode);
                                mAccX -= threshold;
                            }
                            while (mAccX <= -threshold) {
                                if (mListener != null) mListener.onCursorMove(KeyCode.ARROW_LEFT, mSelectionMode);
                                mAccX += threshold;
                            }
                            while (mAccY >= threshold) {
                                if (mListener != null) mListener.onCursorMove(KeyCode.ARROW_DOWN, mSelectionMode);
                                mAccY -= threshold;
                            }
                            while (mAccY <= -threshold) {
                                if (mListener != null) mListener.onCursorMove(KeyCode.ARROW_UP, mSelectionMode);
                                mAccY += threshold;
                            }
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    android.util.Log.i("TouchpadView", "ACTION_UP");
                    mIsDragging = false;
                    stopEdgeScrolling();
                    mIsTwoFingerScroll = false;
                    mIsTwoFingerTap = false;
                    removeCallbacks(mTwoFingerLongPressRunnable);
                    mIsTwoFingerLongPress = false;
                    if (mSelectionMode) {
                        mSelectionMode = false;
                        applySurfaceColor();
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    android.util.Log.i("TouchpadView", "ACTION_CANCEL");
                    mIsDragging = false;
                    mIsTwoFingerScroll = false;
                    mIsTwoFingerTap = false;
                    removeCallbacks(mTwoFingerLongPressRunnable);
                    mIsTwoFingerLongPress = false;
                    mTwoFingerTapCount = 0;
                    removeCallbacks(mTwoFingerTapRunnable);
                    if (mSelectionMode) {
                        mSelectionMode = false;
                        applySurfaceColor();
                    }
                    return true;

                case MotionEvent.ACTION_POINTER_UP:
                    android.util.Log.i("TouchpadView", "ACTION_POINTER_UP: pointerCount=" + pointerCount);
                    if (pointerCount == 2) {
                        removeCallbacks(mTwoFingerLongPressRunnable);
                        if (mIsTwoFingerLongPress) {
                            mIsTwoFingerLongPress = false;
                            mIsTwoFingerScroll = false;
                            mIsTwoFingerTap = false;
                            return true;
                        }
                        if (mIsTwoFingerTap && (System.currentTimeMillis() - mTwoFingerDownTime) < 300) {
                            mTwoFingerTapCount++;
                            removeCallbacks(mTwoFingerTapRunnable);
                            postDelayed(mTwoFingerTapRunnable, 250);
                        }
                        mIsTwoFingerScroll = false;
                        mIsTwoFingerTap = false;
                    }
                    return true;
            }
            return true;
        });
    }

    /** Returns true and starts/continues edge scrolling if (x, y) is within the edge threshold. */
    private boolean handleEdgeScrolling(float x, float y) {
        int w = mTouchpadSurface.getWidth();
        int h = mTouchpadSurface.getHeight();
        int thresholdX = (int) (w * EDGE_THRESHOLD_PERCENTAGE);
        int thresholdY = (int) (h * EDGE_THRESHOLD_PERCENTAGE);

        int direction;
        if (y <= thresholdY) {
            direction = KeyCode.ARROW_UP;
        } else if (y >= h - thresholdY) {
            direction = KeyCode.ARROW_DOWN;
        } else if (x <= thresholdX) {
            direction = KeyCode.ARROW_LEFT;
        } else if (x >= w - thresholdX) {
            direction = KeyCode.ARROW_RIGHT;
        } else {
            return false;
        }

        if (mIsEdgeScrolling && mEdgeScrollDirection == direction) {
            return true; // already running in this direction
        }
        stopEdgeScrolling();
        mEdgeScrollDirection = direction;
        mIsEdgeScrolling = true;
        int sensitivity = Settings.getValues().mTouchpadSensitivity;
        mEdgeScrollDelay = 300L - (long) (sensitivity * 2.5f);
        mEdgeScrollHandler.post(mEdgeScrollRunnable);
        return true;
    }

    private void stopEdgeScrolling() {
        mIsEdgeScrolling = false;
        mEdgeScrollHandler.removeCallbacks(mEdgeScrollRunnable);
    }
}
