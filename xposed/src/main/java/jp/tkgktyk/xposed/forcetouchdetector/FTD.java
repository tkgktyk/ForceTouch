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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

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
    public static final String SUFFIX_TOUCH_ACTION = ".touch";

    public static final String ACTION_BACK = PREFIX_ACTION + "back";
    public static final String ACTION_HOME = PREFIX_ACTION + "home";
    public static final String ACTION_RECENTS = PREFIX_ACTION + "recents";
    public static final String ACTION_EXPAND_NOTIFICATIONS = PREFIX_ACTION + "expand_notifications";
    public static final String ACTION_EXPAND_QUICK_SETTINGS = PREFIX_ACTION + "expand_quick_settings";

    public static final String ACTION_DOUBLE_TAP = PREFIX_ACTION + "double_tap" + SUFFIX_TOUCH_ACTION;
    public static final String ACTION_LONG_PRESS = PREFIX_ACTION + "long_press" + SUFFIX_TOUCH_ACTION;

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
        } else if (action.equals(ACTION_DOUBLE_TAP)) {
            return context.getString(R.string.action_double_tap);
        } else if (action.equals(ACTION_LONG_PRESS)) {
            return context.getString(R.string.action_long_press);
        }
        return "";
    }

    public static boolean performAction(@NonNull ViewGroup container, String uri,
                                        MotionEvent event) {
        XposedModule.logD(uri);
        Intent intent = loadIntent(uri);
        if (intent == null) {
            return false;
        }
        if (isLocalAction(intent)) {
            performLocalAction(container, intent, event);
            return true;
        }
        if (intent.getComponent() == null) {
            return false;
        }
        container.getContext().startActivity(intent);
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

    private static void performLocalAction(@NonNull ViewGroup container, @NonNull Intent intent,
                                           MotionEvent event) {
        String action = intent.getAction();
        XposedModule.logD(action);
        if (action.endsWith(SUFFIX_TOUCH_ACTION)) {
            if (event != null) {
                performTouchAction(container, action, event);
            }
        } else {
            container.getContext().sendBroadcast(intent);
        }
    }

    private static void performTouchAction(@NonNull ViewGroup container, @NonNull String action,
                                           @NonNull MotionEvent event) {
        if (action.equals(ACTION_DOUBLE_TAP)) {
            // TODO: implement. use input command?
        } else if (action.equals(ACTION_LONG_PRESS)) {
            // TODO: this works partially. use input command?
            View view = findViewOnPoint(container, event.getX(), event.getY(), true);
            if (view == null) {
                XposedModule.logD("view was not found");
                return;
            }
            XposedModule.logD(view.toString());
            view.performLongClick();
        }
    }

    private static View findViewOnPoint(ViewGroup container, float x, float y,
                                        boolean longClickable) {
        int location[] = new int[2];

        int count = container.getChildCount();
        for (int i = count - 1; i >= 0; --i) {
            View child = container.getChildAt(i);
            if (child instanceof ViewGroup) {
                View view = findViewOnPoint((ViewGroup) child, x, y, longClickable);
                if (view != null) {
                    return view;
                }
            } else {
                child.getLocationOnScreen(location);
                int viewX = location[0];
                int viewY = location[1];
                // point is inside view bounds
                if ((x > viewX && x < (viewX + child.getWidth())) &&
                        (y > viewY && y < (viewY + child.getHeight()))) {
                    if (longClickable) {
                        if (child.isLongClickable()) {
                            return child;
                        }
                    } else {
                        if (child.isClickable()) {
                            return child;
                        }
                    }
                }
            }
        }
        return null;
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
        static final long serialVersionUID = 1L;

        // General
        public final float forceTouchArea;

        // Pressure
        public final Holder pressure = new Holder();

        // Size
        public final Holder size = new Holder();

        public Settings(SharedPreferences prefs) {
            int area = Integer.parseInt(getStringToParse(prefs, "key_detection_area", "100"));
            forceTouchArea = (100.0f - area) / 100.0f;

            // Pressure
            pressure.enabled = prefs.getBoolean("key_pressure_enabled", false);
            pressure.threshold = Float.parseFloat(getStringToParse(prefs, "key_pressure_threshold",
                    ModActivity.ForceTouchDetector.DEFAULT_THRESHOLD));

            pressure.actionTap = prefs.getString("key_pressure_action_tap", "");
            pressure.actionDoubleTap = prefs.getString("key_pressure_action_double_tap", "");
            pressure.actionLongPress = prefs.getString("key_pressure_action_long_press", "");
            pressure.actionFlickLeft = prefs.getString("key_pressure_action_flick_left", "");
            pressure.actionFlickRight = prefs.getString("key_pressure_action_flick_right", "");
            pressure.actionFlickUp = prefs.getString("key_pressure_action_flick_up", "");
            pressure.actionFlickDown = prefs.getString("key_pressure_action_flick_down", "");

            // Size
            size.enabled = prefs.getBoolean("key_size_enabled", false);
            size.threshold = Float.parseFloat(getStringToParse(prefs, "key_size_threshold",
                    ModActivity.ForceTouchDetector.DEFAULT_THRESHOLD));

            size.actionTap = prefs.getString("key_size_action_tap", "");
            size.actionDoubleTap = prefs.getString("key_size_action_double_tap", "");
            size.actionLongPress = prefs.getString("key_size_action_long_press", "");
            size.actionFlickLeft = prefs.getString("key_size_action_flick_left", "");
            size.actionFlickRight = prefs.getString("key_size_action_flick_right", "");
            size.actionFlickUp = prefs.getString("key_size_action_flick_up", "");
            size.actionFlickDown = prefs.getString("key_size_action_flick_down", "");
        }

        private String getStringToParse(SharedPreferences prefs, String key, String defValue) {
            String str = prefs.getString(key, defValue);
            if (Strings.isNullOrEmpty(str)) {
                str = defValue;
            }
            return str;
        }

        public boolean isEnabled() {
            return pressure.enabled || size.enabled;
        }

        public class Holder implements Serializable {
            static final long serialVersionUID = 1L;

            // Setting
            public boolean enabled;
            public float threshold;
            // Action
            public String actionTap;
            public String actionDoubleTap;
            public String actionLongPress;
            public String actionFlickLeft;
            public String actionFlickRight;
            public String actionFlickUp;
            public String actionFlickDown;
        }
    }
}
