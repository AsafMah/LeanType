/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.permissions;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import helium314.keyboard.latin.OtpNotificationListenerService;

/**
 * Utility class for permissions.
 */
public class PermissionsUtil {
    /**
     * Queries if al the permissions are granted for the given permission strings.
     */
    public static boolean checkAllPermissionsGranted(Context context, String... permissions) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            // For all pre-M devices, we should have all the permissions granted on install.
            return true;
        }

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotificationListenerEnabled(Context context) {
        if (context == null) return false;
        ComponentName component = new ComponentName(context, OtpNotificationListenerService.class);
        String requiredComponent = component.flattenToString();

        String enabledListeners = android.provider.Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (enabledListeners == null) return false;

        for (String listener : enabledListeners.split(":")) {
            if (listener.equals(requiredComponent) || listener.contains(context.getPackageName())) {
                return true;
            }
        }
        return false;
    }
}
