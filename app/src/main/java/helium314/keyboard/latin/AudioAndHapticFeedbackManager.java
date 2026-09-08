/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

import helium314.keyboard.event.HapticEvent;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.settings.SettingsValues;
import helium314.keyboard.latin.sound.CustomSoundManager;

/**
 * This class gathers audio feedback and haptic feedback functions.
 * <p>
 * It offers a consistent and simple interface that allows LatinIME to forget about the
 * complexity of settings and the like.
 */
public final class AudioAndHapticFeedbackManager {
    private Context mContext;
    private AudioManager mAudioManager;
    private Vibrator mVibrator;

    private SettingsValues mSettingsValues;
    private boolean mSoundOn;
    private boolean mDoNotDisturb;

    private static final AudioAndHapticFeedbackManager sInstance =
            new AudioAndHapticFeedbackManager();

    public static AudioAndHapticFeedbackManager getInstance() {
        return sInstance;
    }

    private AudioAndHapticFeedbackManager() {
        // Intentional empty constructor for singleton.
    }

    public static void init(final Context context) {
        sInstance.initInternal(context);
    }

    private void initInternal(final Context context) {
        mContext = context.getApplicationContext();
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        CustomSoundManager.Companion.getInstance(mContext);
    }

    public void performHapticAndAudioFeedback(
        final int code,
        final View viewToPerformHapticFeedbackOn,
        final HapticEvent hapticEvent
    ) {
        performHapticFeedback(viewToPerformHapticFeedbackOn, hapticEvent);
        performAudioFeedback(code, hapticEvent);
    }

    public boolean hasVibrator() {
        return mVibrator != null && mVibrator.hasVibrator();
    }

    public boolean hasAmplitudeControl() {
        return mVibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mVibrator.hasAmplitudeControl();
    }

    public void vibrate(final long milliseconds) {
        vibrate(milliseconds, -1);
    }

    public void vibrate(final long milliseconds, final int amplitudePercent) {
        if (mVibrator == null || milliseconds <= 0) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final int safeAmplitude;
            if (amplitudePercent < 0) {
                safeAmplitude = VibrationEffect.DEFAULT_AMPLITUDE;
            } else {
                safeAmplitude = Math.min(255, Math.max(1, (int) ((amplitudePercent / 100f) * 255)));
            }
            try {
                mVibrator.vibrate(VibrationEffect.createOneShot(milliseconds, safeAmplitude));
            } catch (Exception e) {
                mVibrator.vibrate(milliseconds);
            }
        } else {
            mVibrator.vibrate(milliseconds);
        }
    }

    private boolean reevaluateIfSoundIsOn() {
        if (mSettingsValues == null || !mSettingsValues.mSoundOn || mAudioManager == null) {
            return false;
        }
        if (mSettingsValues.mSoundMuteInDnd && mDoNotDisturb) {
            return false;
        }
        if (mSettingsValues.mSoundMuteInSilent && mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            return false;
        }
        return true;
    }

    public void performAudioFeedback(final int code, final HapticEvent hapticEvent) {
        performAudioFeedback(code, hapticEvent, 0.5f);
    }

    public void performAudioFeedback(final int code, final HapticEvent hapticEvent, final float keyXRatio) {
        if (!mSoundOn) {
            return;
        }
        if (hapticEvent != HapticEvent.KEY_PRESS) {
            return;
        }
        final float volume = mSettingsValues != null ? mSettingsValues.mKeypressSoundVolume : -0.01f;
        if (mContext != null) {
            final boolean played = CustomSoundManager.Companion.getInstance(mContext).playSound(code, volume, keyXRatio);
            if (played) {
                return;
            }
        }
        // Fallback to system AudioManager
        if (mAudioManager == null) {
            return;
        }
        final int sound = switch (code) {
            case KeyCode.DELETE -> AudioManager.FX_KEYPRESS_DELETE;
            case Constants.CODE_ENTER -> AudioManager.FX_KEYPRESS_RETURN;
            case Constants.CODE_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR;
            default -> AudioManager.FX_KEYPRESS_STANDARD;
        };
        mAudioManager.playSoundEffect(sound, volume);
    }

    public void performHapticFeedback(final View viewToPerformHapticFeedbackOn, final HapticEvent hapticEvent) {
        if (!mSettingsValues.mVibrateOn || (mDoNotDisturb && !mSettingsValues.mVibrateInDndMode)) {
            return;
        }
        if (hapticEvent == HapticEvent.NO_HAPTICS) {
            // Avoid surprises with the handling of HapticFeedbackConstants.NO_HAPTICS
            return;
        }
        if (hapticEvent.allowCustomDuration && (mSettingsValues.mKeypressVibrationDuration >= 0 || mSettingsValues.mKeypressVibrationAmplitude >= 0)) {
            final int duration = mSettingsValues.mKeypressVibrationDuration >= 0 ? mSettingsValues.mKeypressVibrationDuration : 15;
            vibrate(duration, mSettingsValues.mKeypressVibrationAmplitude);
            return;
        }
        // Go ahead with the system default
        if (viewToPerformHapticFeedbackOn != null) {
            viewToPerformHapticFeedbackOn.performHapticFeedback(
                    hapticEvent.feedbackConstant,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    public void onSettingsChanged(final SettingsValues settingsValues) {
        mSettingsValues = settingsValues;
        mSoundOn = reevaluateIfSoundIsOn();
        if (mContext != null && settingsValues != null && settingsValues.mKeypressSoundStyle != null) {
            CustomSoundManager.Companion.getInstance(mContext).setSoundPack(settingsValues.mKeypressSoundStyle);
        }
    }

    public void onRingerModeChanged(boolean doNotDisturb) {
        mDoNotDisturb = doNotDisturb;
        mSoundOn = reevaluateIfSoundIsOn();
    }

    public void onStartInputView() {
        if (mContext != null && mSoundOn) {
            CustomSoundManager.Companion.getInstance(mContext).onStartInputView();
        }
    }

    public void onFinishInputView() {
        if (mContext != null) {
            CustomSoundManager.Companion.getInstance(mContext).onFinishInputView();
        }
    }

    public void onDestroy() {
        if (mContext != null) {
            CustomSoundManager.Companion.getInstance(mContext).onDestroy();
        }
    }
}

