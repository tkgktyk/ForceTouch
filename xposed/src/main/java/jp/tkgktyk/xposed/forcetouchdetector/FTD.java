/*
 * Copyright 2015 Takagi Katsuyuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.tkgktyk.xposed.forcetouchdetector;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import com.google.common.base.Strings;

import java.io.Serializable;
import java.net.URISyntaxException;

import jp.tkgktyk.xposed.forcetouchdetector.app.MyApp;

/**
 * Created by tkgktyk on 2015/06/03.
 */
public class FTD {
    public static final String PACKAGE_NAME = FTD.class.getPackage().getName();
    public static final String NAME = FTD.class.getSimpleName();
    public static final String PREFIX_ACTION = PACKAGE_NAME + ".intent.action.";
    public static final String PREFIX_EXTRA = PACKAGE_NAME + ".intent.extra.";

    public static final String ACTION_BACK = PREFIX_ACTION + "back";
    public static final String ACTION_HOME = PREFIX_ACTION + "home";
    public static final String ACTION_RECENTS = PREFIX_ACTION + "recents";
    public static final String ACTION_EXPAND_NOTIFICATIONS = PREFIX_ACTION + "expand_notifications";
    public static final String ACTION_EXPAND_QUICK_SETTINGS = PREFIX_ACTION + "expand_quick_settings";

    public static final IntentFilter LOCAL_ACTION_FILTER;
    /**
     * IntentFilters initialization
     */
    static {
        LOCAL_ACTION_FILTER = new IntentFilter();
        LOCAL_ACTION_FILTER.addAction(ACTION_BACK);
        LOCAL_ACTION_FILTER.addAction(ACTION_HOME);
        LOCAL_ACTION_FILTER.addAction(ACTION_RECENTS);
        LOCAL_ACTION_FILTER.addAction(ACTION_EXPAND_NOTIFICATIONS);
        LOCAL_ACTION_FILTER.addAction(ACTION_EXPAND_QUICK_SETTINGS);
    }

    public static String getActionName(Context context, String action) {
        if (action.equals(ACTION_BACK)) {
            return context.getString(R.string.action_back);
        } else if (action.equals(ACTION_HOME)) {
            return context.getString(R.string.action_home);
        } else if (action.equals(ACTION_RECENTS)) {
            return context.getString(R.string.action_recents);
        } else if (action.equals(ACTION_EXPAND_NOTIFICATIONS)) {
            return context.getString(R.string.action_expand_notifications);
        } else if (action.equals(ACTION_EXPAND_QUICK_SETTINGS)) {
            return context.getString(R.string.action_expand_quick_settings);
        }
        return "";
    }

    public static boolean performAction(Context context, String uri) {
        XposedModule.logD(uri);
        Intent intent = loadIntent(uri);
        if (intent == null) {
            return false;
        }
        if (isLocalAction(intent)) {
            performLocalAction(context, intent);
            return true;
        }
        if (intent.getComponent() == null) {
            return false;
        }
        context.startActivity(intent);
        return true;
    }

    private static Intent loadIntent(String uri) {
        try {
            return Intent.parseUri(uri, 0);
        } catch (URISyntaxException e) {
            XposedModule.logE(e);
        }
        return null;
    }

    public static boolean isLocalAction(@NonNull Intent intent) {
        String action = intent.getAction();
        return !Strings.isNullOrEmpty(action) && action.startsWith(PREFIX_ACTION);
    }

    private static void performLocalAction(Context context, Intent intent) {
        context.sendBroadcast(intent);
        XposedModule.logD(intent.getAction());
    }

    public static final String ACTION_SETTINGS_CHANGED = PREFIX_ACTION + "SETTINGS_CHANGED";
    public static final String EXTRA_SETTINGS = PREFIX_EXTRA + "SETTINGS";
    public static final IntentFilter SETTINGS_CHANGED_FILTER = new IntentFilter(ACTION_SETTINGS_CHANGED);

    public static void sendSettingsChanged(Context context, SharedPreferences prefs) {
        MyApp.logD("send settings changed");
        FTD.Settings settings = new FTD.Settings(prefs);
        Intent intent = new Intent(FTD.ACTION_SETTINGS_CHANGED);
        intent.putExtra(FTD.EXTRA_SETTINGS, settings);
        context.sendBroadcast(intent);
    }

    /**
     * for Settings UI
     *
     * @param context
     * @return
     */
    @SuppressWarnings("deprecation")
    @SuppressLint("WorldReadableFiles")
    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PACKAGE_NAME + "_preferences", Context.MODE_WORLD_READABLE);
    }

    public static Context getModContext(Context context) {
        Context modContext = null;
        try {
            if (context.getPackageName().equals(FTD.PACKAGE_NAME)) {
                modContext = context;
            } else {
                modContext = context.createPackageContext(
                        FTD.PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY);
            }
        } catch (Throwable t) {
            XposedModule.logE(t);
        }
        return modContext;
    }

    public static class Settings implements Serializable {
        public float pressureThreshold;

        public String actionTap;
        public String actionDoubleTap;
        public String actionLongPress;
        public String actionFlickLeft;
        public String actionFlickRight;
        public String actionFlickUp;
        public String actionFlickDown;

        public Settings(SharedPreferences prefs) {
            load(prefs);
        }

        public void load(SharedPreferences prefs) {
            pressureThreshold = Float.parseFloat(prefs.getString("key_pressure_threshold",
                    ModActivity.ForceTouchDetector.DEFAULT_PRESSURE_THRESHOLD));

            actionTap = prefs.getString("key_action_tap", "");
            actionDoubleTap = prefs.getString("key_action_double_tap", "");
            actionLongPress = prefs.getString("key_action_long_press", "");
            actionFlickLeft = prefs.getString("key_action_flick_left", "");
            actionFlickRight = prefs.getString("key_action_flick_right", "");
            actionFlickUp = prefs.getString("key_action_flick_up", "");
            actionFlickDown = prefs.getString("key_action_flick_down", "");
        }
    }
}

