/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.settings;

public class SettingsValuesForSuggestion {
    public final boolean mBlockPotentiallyOffensive;
    public final boolean mSpaceAwareGesture;
    public final String mGestureMethod;

    public SettingsValuesForSuggestion(
            final boolean blockPotentiallyOffensive,
            final boolean spaceAwareGesture,
            final String gestureMethod
            ) {
        mBlockPotentiallyOffensive = blockPotentiallyOffensive;
        mSpaceAwareGesture = spaceAwareGesture;
        mGestureMethod = gestureMethod;
    }
}
