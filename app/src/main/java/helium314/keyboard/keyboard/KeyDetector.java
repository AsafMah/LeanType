/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard;

import android.content.Context;
import android.graphics.Rect;

import helium314.keyboard.latin.database.TouchModelDao;
import helium314.keyboard.latin.database.TouchModelManager;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.settings.SettingsValues;

/**
 * This class handles key detection.
 */
public class KeyDetector {
    // Adaptive typing: the context prior may enlarge a likely key's effective target by at most
    // this fraction of the key (deliberately a bit less than the learned-geometry cap, so the
    // prior nudges rather than dominates). See docs/ADAPTIVE_TYPING.md.
    private static final float PRIOR_MAX_FRACTION = 0.18f;
    // A neighbor is only allowed to win a tap if the touch is within this fraction of its hitbox.
    private static final float CONSIDER_MARGIN_FRACTION = 0.40f;

    private final int mKeyHysteresisDistanceSquared;
    private final int mKeyHysteresisDistanceForSlidingModifierSquared;

    private Keyboard mKeyboard;
    private int mCorrectionX;
    private int mCorrectionY;

    public KeyDetector() {
        this(0.0f /* keyHysteresisDistance */, 0.0f /* keyHysteresisDistanceForSlidingModifier */);
    }

    /**
     * Key detection object constructor with key hysteresis distances.
     *
     * @param keyHysteresisDistance if the pointer movement distance is smaller than this, the
     * movement will not be handled as meaningful movement. The unit is pixel.
     * @param keyHysteresisDistanceForSlidingModifier the same parameter for sliding input that
     * starts from a modifier key such as shift and symbols key.
     */
    public KeyDetector(final float keyHysteresisDistance,
            final float keyHysteresisDistanceForSlidingModifier) {
        mKeyHysteresisDistanceSquared = (int)(keyHysteresisDistance * keyHysteresisDistance);
        mKeyHysteresisDistanceForSlidingModifierSquared = (int)(
                keyHysteresisDistanceForSlidingModifier * keyHysteresisDistanceForSlidingModifier);
    }

    public void setKeyboard(final Keyboard keyboard, final float correctionX,
            final float correctionY) {
        if (keyboard == null) {
            throw new NullPointerException();
        }
        mCorrectionX = (int)correctionX;
        mCorrectionY = (int)correctionY;
        mKeyboard = keyboard;
    }

    public int getKeyHysteresisDistanceSquared(final boolean isSlidingFromModifier) {
        return isSlidingFromModifier
                ? mKeyHysteresisDistanceForSlidingModifierSquared : mKeyHysteresisDistanceSquared;
    }

    public int getTouchX(final int x) {
        return x + mCorrectionX;
    }

    // TODO: Remove vertical correction.
    public int getTouchY(final int y) {
        return y + mCorrectionY;
    }

    public Keyboard getKeyboard() {
        return mKeyboard;
    }

    public boolean alwaysAllowsKeySelectionByDraggingFinger() {
        return false;
    }

    /**
     * Detect the key whose hitbox the touch point is in.
     *
     * @param x The x-coordinate of a touch point
     * @param y The y-coordinate of a touch point
     * @return the key that the touch point hits.
     */
    public Key detectHitKey(final int x, final int y) {
        if (mKeyboard == null) {
            return null;
        }
        final int touchX = getTouchX(x);
        final int touchY = getTouchY(y);

        int minDistance = Integer.MAX_VALUE;
        Key primaryKey = null;
        for (final Key key: mKeyboard.getNearestKeys(touchX, touchY)) {
            // An edge key always has its enlarged hitbox to respond to an event that occurred in
            // the empty area around the key. (@see Key#markAsLeftEdge(KeyboardParams)} etc.)
            if (!key.isOnKey(touchX, touchY)) {
                continue;
            }
            final int distance = key.squaredDistanceToEdge(touchX, touchY);
            if (distance > minDistance) {
                continue;
            }
            // To take care of hitbox overlaps, we compare key's code here too.
            if (primaryKey == null || distance < minDistance
                    || key.getCode() > primaryKey.getCode()) {
                minDistance = distance;
                primaryKey = key;
            }
        }
        // Adaptive typing (opt-in): for a plain tap (not a gesture/swipe), let the learned
        // per-key landing offset and the current next-key prior gently bias which key wins —
        // bounded so only genuinely ambiguous, near-boundary taps can flip.
        if (primaryKey != null && !PointerTracker.isInGestureOrKeySwipe()) {
            final Key biased = applyAdaptiveBias(touchX, touchY, primaryKey);
            if (biased != null) return biased;
        }
        return primaryKey;
    }

    /** Returns a key that should win this tap instead of {@code geo} due to learned/prior bias,
     *  or {@code null} to keep the plain geometric result. */
    private Key applyAdaptiveBias(final int touchX, final int touchY, final Key geo) {
        final SettingsValues sv = Settings.getValues();
        if (sv == null || sv.mAdaptiveKeyGeometryStrength <= 0) return null;
        // The two halves are independently toggleable: learned per-key offset, and the
        // context prior. Either alone is enough to bias a tap; both share the strength slider.
        final boolean learn = sv.mAdaptiveKeyGeometry;
        final boolean usePrior = sv.mAdaptiveContextPrior;
        if (!learn && !usePrior) return null;
        final Context ctx = Settings.getCurrentContext();
        if (ctx == null || mKeyboard == null) return null;
        final TouchModelDao dao = learn ? TouchModelDao.getInstance(ctx) : null;
        final boolean hasPrior = usePrior && AdaptiveKeyContext.hasPrior();
        if (dao == null && !hasPrior) return null; // nothing to bias with
        final String layout = Integer.toString(mKeyboard.mId.mElementId);
        final int orientation = ctx.getResources().getConfiguration().orientation;
        final int strength = sv.mAdaptiveKeyGeometryStrength;

        Key best = geo;
        float bestScore = adjustedDistance(geo, touchX, touchY, dao, layout, orientation, strength, usePrior);
        for (final Key k : mKeyboard.getNearestKeys(touchX, touchY)) {
            if (k == geo) continue;
            final int code = k.getCode();
            if (code <= 0 || !Character.isLetter(code)) continue;
            // Only a genuinely-favored neighbor (a confident learned offset and/or a next-key
            // prior) may steal a near-boundary tap; otherwise leave the geometric result alone.
            final float prior = usePrior ? AdaptiveKeyContext.weight(code) : 0f;
            final TouchModelDao.Stat st = (dao == null) ? null : dao.get(code, layout, orientation);
            final boolean hasLearned = st != null && st.getCount() >= TouchModelDao.MIN_CONFIDENT_SAMPLES;
            if (prior <= 0f && !hasLearned) continue;
            // Bound: the touch must be within a margin of the neighbor's hitbox.
            final float margin = CONSIDER_MARGIN_FRACTION * k.getWidth();
            if (k.squaredDistanceToEdge(touchX, touchY) > margin * margin) continue;
            final float s = adjustedDistance(k, touchX, touchY, dao, layout, orientation, strength, usePrior);
            if (s < bestScore) {
                bestScore = s;
                best = k;
            }
        }
        return best == geo ? null : best;
    }

    /** Distance from the touch to the key's effective center (center shifted by the learned
     *  landing offset, when enabled) minus the capped next-key prior boost (when enabled).
     *  Smaller wins. */
    private float adjustedDistance(final Key k, final int touchX, final int touchY,
            final TouchModelDao dao, final String layout, final int orientation, final int strength,
            final boolean usePrior) {
        final Rect hb = k.getHitBox();
        float cx = hb.exactCenterX();
        float cy = hb.exactCenterY();
        if (dao != null) {
            final TouchModelDao.Stat st = dao.get(k.getCode(), layout, orientation);
            final float[] off = TouchModelManager.adjustedOffset(st, k.getWidth(), k.getHeight(), strength);
            cx += off[0];
            cy += off[1];
        }
        final float dx = touchX - cx;
        final float dy = touchY - cy;
        final float dist = (float) Math.sqrt(dx * dx + dy * dy);
        final float boost = usePrior
                ? AdaptiveKeyContext.weight(k.getCode()) * PRIOR_MAX_FRACTION * k.getWidth() * (strength / 100f)
                : 0f;
        return dist - boost;
    }
}
