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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.ScrollView;
import android.widget.Toast;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionInfo;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ScaleRect;

/**
 * Created by tkgktyk on 2015/06/03.
 */
public class FTD {
    public static final String PACKAGE_NAME = FTD.class.getPackage().getName();
    public static final String NAME = FTD.class.getSimpleName();
    public static final String PREFIX_ACTION = PACKAGE_NAME + ".intent.action.";
    public static final String PREFIX_EXTRA = PACKAGE_NAME + ".intent.extra.";
    public static final String SUFFIX_TOUCH_ACTION = ".touch";

    // key actions
    public static final String ACTION_BACK = PREFIX_ACTION + "BACK";
    public static final String ACTION_HOME = PREFIX_ACTION + "HOME";
    public static final String ACTION_RECENTS = PREFIX_ACTION + "RECENTS";
    public static final String ACTION_FORWARD = PREFIX_ACTION + "FORWARD";
    public static final String ACTION_REFRESH = PREFIX_ACTION + "REFRESH";
    public static final String ACTION_SCROLL_UP_GLOBAL = PREFIX_ACTION + "SCROLL_UP_GLOBAL";
    public static final String ACTION_SCROLL_DOWN_GLOBAL = PREFIX_ACTION + "SCROLL_DOWN_GLOBAL";
    public static final String ACTION_VOLUME_UP = PREFIX_ACTION + "VOLUME_UP";
    public static final String ACTION_VOLUME_DOWN = PREFIX_ACTION + "VOLUME_DOWN";
    public static final String ACTION_SCREENSHOT = PREFIX_ACTION + "SCREENSHOT";
    public static final String ACTION_LOCK_SCREEN = PREFIX_ACTION + "LOCK_SCREEN";
    public static final String ACTION_LAST_APP = PREFIX_ACTION + "LAST_APP";
    public static final String ACTION_MENU = PREFIX_ACTION + "MENU";
    // status bar
    public static final String ACTION_NOTIFICATIONS = PREFIX_ACTION + "NOTIFICATIONS";
    public static final String ACTION_QUICK_SETTINGS = PREFIX_ACTION + "QUICK_SETTINGS";
    // other internal functions
    public static final String ACTION_KILL = PREFIX_ACTION + "KILL";
    public static final String ACTION_POWER_MENU = PREFIX_ACTION + "POWER_MENU";
    public static final String ACTION_SELECT_KEYBOARD = PREFIX_ACTION + "SWITCH_KEYBOARD";
    // touch event
    public static final String ACTION_DOUBLE_TAP = PREFIX_ACTION + "DOUBLE_TAP" + SUFFIX_TOUCH_ACTION;
    public static final String ACTION_LONG_PRESS = PREFIX_ACTION + "LONG_PRESS" + SUFFIX_TOUCH_ACTION;
    public static final String ACTION_LONG_PRESS_FULL = PREFIX_ACTION + "LONG_PRESS_FULL" + SUFFIX_TOUCH_ACTION;
    public static final String ACTION_SCROLL_UP = PREFIX_ACTION + "SCROLL_UP" + SUFFIX_TOUCH_ACTION;
    public static final String ACTION_SCROLL_DOWN = PREFIX_ACTION + "SCROLL_DOWN" + SUFFIX_TOUCH_ACTION;
    // other local app
    public static final String ACTION_FLOATING_ACTION = PREFIX_ACTION + "FLOATING_ACTION";

    public static final IntentFilter INTERNAL_ACTION_FILTER;

    private static class Entry {
        final int nameId;
        final int iconId;

        Entry(@StringRes int nameId, @DrawableRes int iconId) {
            this.nameId = nameId;
            this.iconId = iconId;
        }
    }

    private static final Map<String, Entry> ENTRIES = Maps.newHashMap();

    /**
     * Entry actions
     */
    static {
        //
        // key action
        //
        ENTRIES.put(ACTION_BACK, new Entry(R.string.action_back, R.drawable.ic_sysbar_back));
        ENTRIES.put(ACTION_HOME, new Entry(R.string.action_home, R.drawable.ic_sysbar_home));
        ENTRIES.put(ACTION_RECENTS, new Entry(R.string.action_recents, R.drawable.ic_sysbar_recent));
        ENTRIES.put(ACTION_FORWARD, new Entry(R.string.action_forward, R.drawable.ic_arrow_forward_white_24dp));
        ENTRIES.put(ACTION_REFRESH, new Entry(R.string.action_refresh, R.drawable.ic_refresh_white_24dp));
        ENTRIES.put(ACTION_SCROLL_UP_GLOBAL, new Entry(R.string.action_scroll_up, R.drawable.ic_vertical_align_top_white_24dp));
        ENTRIES.put(ACTION_SCROLL_DOWN_GLOBAL, new Entry(R.string.action_scroll_down, R.drawable.ic_vertical_align_bottom_white_24dp));
        ENTRIES.put(ACTION_VOLUME_UP, new Entry(R.string.action_volume_up, R.drawable.ic_volume_up_white_24dp));
        ENTRIES.put(ACTION_VOLUME_DOWN, new Entry(R.string.action_volume_down, R.drawable.ic_volume_down_white_24dp));
        ENTRIES.put(ACTION_SCREENSHOT, new Entry(R.string.action_screenshot, R.drawable.ic_camera_enhance_white_24dp));
        ENTRIES.put(ACTION_LOCK_SCREEN, new Entry(R.string.action_lock_screen, R.drawable.ic_phonelink_lock_white_24dp));
        ENTRIES.put(ACTION_LAST_APP, new Entry(R.string.action_last_app, R.drawable.ic_swap_horiz_white_24dp));
        ENTRIES.put(ACTION_MENU, new Entry(R.string.action_menu, R.drawable.ic_menu_white_24dp));
        //
        // status bar
        //
        ENTRIES.put(ACTION_NOTIFICATIONS, new Entry(R.string.action_notifications, R.drawable.ic_notifications_none_white_24dp));
        ENTRIES.put(ACTION_QUICK_SETTINGS, new Entry(R.string.action_quick_settings, R.drawable.ic_settings_white_24dp));
        //
        // internal function
        //
        ENTRIES.put(ACTION_KILL, new Entry(R.string.action_kill, R.drawable.ic_close_white_24dp));
        ENTRIES.put(ACTION_POWER_MENU, new Entry(R.string.action_power_menu, R.drawable.ic_power_settings_new_white_24dp));
        ENTRIES.put(ACTION_SELECT_KEYBOARD, new Entry(R.string.action_select_keyboard, R.drawable.ic_keyboard_white_24dp));
        //
        // touch action
        ENTRIES.put(ACTION_DOUBLE_TAP, new Entry(R.string.action_double_tap, 0));
        ENTRIES.put(ACTION_LONG_PRESS, new Entry(R.string.action_long_press, 0));
        ENTRIES.put(ACTION_LONG_PRESS_FULL, new Entry(R.string.action_long_press_full, 0));
        ENTRIES.put(ACTION_SCROLL_UP, new Entry(R.string.action_scroll_up, R.drawable.ic_vertical_align_top_white_24dp));
        ENTRIES.put(ACTION_SCROLL_DOWN, new Entry(R.string.action_scroll_down, R.drawable.ic_vertical_align_bottom_white_24dp));
        //
        // local action
        //
        ENTRIES.put(ACTION_FLOATING_ACTION, new Entry(R.string.action_floating_action, R.drawable.ic_floating_action));
    }

    /**
     * IntentFilters initialization
     */
    static {
        INTERNAL_ACTION_FILTER = new IntentFilter();
        // key action
        INTERNAL_ACTION_FILTER.addAction(ACTION_BACK);
        INTERNAL_ACTION_FILTER.addAction(ACTION_HOME);
        INTERNAL_ACTION_FILTER.addAction(ACTION_RECENTS);
        INTERNAL_ACTION_FILTER.addAction(ACTION_FORWARD);
        INTERNAL_ACTION_FILTER.addAction(ACTION_REFRESH);
        INTERNAL_ACTION_FILTER.addAction(ACTION_SCROLL_UP_GLOBAL);
        INTERNAL_ACTION_FILTER.addAction(ACTION_SCROLL_DOWN_GLOBAL);
        INTERNAL_ACTION_FILTER.addAction(ACTION_VOLUME_UP);
        INTERNAL_ACTION_FILTER.addAction(ACTION_VOLUME_DOWN);
        INTERNAL_ACTION_FILTER.addAction(ACTION_SCREENSHOT);
        INTERNAL_ACTION_FILTER.addAction(ACTION_LOCK_SCREEN);
        INTERNAL_ACTION_FILTER.addAction(ACTION_LAST_APP);
        INTERNAL_ACTION_FILTER.addAction(ACTION_MENU);
        // status bar
        INTERNAL_ACTION_FILTER.addAction(ACTION_NOTIFICATIONS);
        INTERNAL_ACTION_FILTER.addAction(ACTION_QUICK_SETTINGS);
        // other internal
        INTERNAL_ACTION_FILTER.addAction(ACTION_KILL);
        INTERNAL_ACTION_FILTER.addAction(ACTION_POWER_MENU);
        INTERNAL_ACTION_FILTER.addAction(ACTION_SELECT_KEYBOARD);
    }

    public static final String EXTRA_FRACTION_X = PREFIX_EXTRA + "FRACTION_X";
    public static final String EXTRA_FRACTION_Y = PREFIX_EXTRA + "FRACTION_Y";

    private static final Point mDisplaySize = new Point();

    @NonNull
    public static String getActionName(Context context, String action) {
        Entry entry = ENTRIES.get(action);
        if (entry != null) {
            Context mod = getModContext(context);
            return mod.getString(ENTRIES.get(action).nameId);
        }
        return "";
    }

    @DrawableRes
    public static int getActionIconResource(String action) {
        Entry entry = ENTRIES.get(action);
        if (entry != null) {
            return entry.iconId;
        }
        return 0;
    }

    public static boolean performAction(@NonNull ViewGroup container,
                                        @NonNull ActionInfo actionInfo,
                                        @NonNull MotionEvent event) {
        return performAction(container, actionInfo, event.getX(), event.getY(), event);
    }

    public static boolean performAction(@NonNull ViewGroup container,
                                        @NonNull ActionInfo actionInfo,
                                        float x, float y) {
        return performAction(container, actionInfo, x, y, null);
    }

    public static boolean performAction(@NonNull ViewGroup container,
                                        @NonNull ActionInfo actionInfo,
                                        float x, float y,
                                        @Nullable MotionEvent event) {
        Context context = container.getContext();
        Intent intent = actionInfo.getIntent();
        if (intent == null) {
            return false;
        }
        // add coordinates
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRealSize(mDisplaySize);
        intent.putExtra(EXTRA_FRACTION_X, x / mDisplaySize.x);
        intent.putExtra(EXTRA_FRACTION_Y, y / mDisplaySize.y);
        // launch action like ActionInfo#launch
        switch (actionInfo.getType()) {
            case ActionInfo.TYPE_TOOL:
                String action = intent.getAction();
                if (event != null && action.endsWith(SUFFIX_TOUCH_ACTION)) {
                    performTouchAction(container, action, event);
                } else {
                    context.sendBroadcast(intent);
                }
                break;
            case ActionInfo.TYPE_APP:
            case ActionInfo.TYPE_SHORTCUT:
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Context mod = getModContext(context);
                    Toast.makeText(mod, R.string.not_found, Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                return false;
        }
        return true;
    }

    private static void performTouchAction(@NonNull final ViewGroup container,
                                           @NonNull String action,
                                           @NonNull final MotionEvent event) {
        if (action.equals(ACTION_DOUBLE_TAP)) {
            injectMotionEvent(container, event, MotionEvent.ACTION_DOWN);
            injectMotionEvent(container, event, MotionEvent.ACTION_UP);
            injectMotionEvent(container, event, MotionEvent.ACTION_DOWN);
            injectMotionEvent(container, event, MotionEvent.ACTION_UP);
        } else if (action.equals(ACTION_LONG_PRESS)) {
            injectMotionEventForLongPress(container, event, MotionEvent.ACTION_DOWN);
            injectMotionEvent(container, event, MotionEvent.ACTION_CANCEL);
        } else if (action.equals(ACTION_LONG_PRESS_FULL)) {
            injectMotionEvent(container, event, MotionEvent.ACTION_DOWN);
            container.postDelayed(new Runnable() {
                @Override
                public void run() {
                    injectMotionEvent(container, event, MotionEvent.ACTION_UP);
                }
            }, ViewConfiguration.getLongPressTimeout() + ViewConfiguration.getTapTimeout());
        } else if (action.equals(ACTION_SCROLL_UP)) {
            findViewAtPosition(container, Math.round(event.getX()), Math.round(event.getY()),
                    new OnViewFoundListener() {
                        @Override
                        public boolean onViewFound(final View view) {
                            if (view.canScrollVertically(-1)) {
                                if (view instanceof AbsListView) {
                                    ((AbsListView) view).smoothScrollToPosition(0);
                                } else if (view instanceof ScrollView) {
                                    final ScrollView scrollView = (ScrollView) view;
                                    if (!scrollView.fullScroll(View.FOCUS_UP)) {
                                        scrollView.smoothScrollTo(scrollView.getScrollX(), 0);
                                    }
//                                } else if (view instanceof RecyclerView) { // doesn't work for support library's class
//                                    ((RecyclerView) view).smoothScrollToPosition(0);
                                } else {
                                    try {
                                        view.scrollTo(view.getScrollX(), 0);
                                    } catch (RuntimeException e) {
                                        view.getContext().sendBroadcast(new Intent(FTD.ACTION_SCROLL_UP_GLOBAL));
                                    }
                                }
                                return true;
                            }
                            return false;
                        }
                    });
        } else if (action.equals(ACTION_SCROLL_DOWN)) {
            findViewAtPosition(container, Math.round(event.getX()), Math.round(event.getY()),
                    new OnViewFoundListener() {
                        @Override
                        public boolean onViewFound(final View view) {
                            if (view.canScrollVertically(1)) {
                                if (view instanceof AbsListView) {
                                    final AbsListView listView = (AbsListView) view;
                                    listView.smoothScrollToPosition(
                                            listView.getAdapter().getCount() - 1);
                                } else if (view instanceof ScrollView) {
                                    final ScrollView scrollView = (ScrollView) view;
                                    scrollView.fullScroll(View.FOCUS_DOWN);
//                                } else if (view instanceof RecyclerView) { // doesn't work for support library's class
//                                    ((RecyclerView) view).smoothScrollToPosition(
//                                            ((RecyclerView) view).getChildCount() - 1);
                                } else {
//                                    view.scrollTo(view.getScrollX(), view.getBottom());
                                    view.getContext().sendBroadcast(new Intent(FTD.ACTION_SCROLL_DOWN_GLOBAL));
                                }
                                return true;
                            }
                            return false;
                        }
                    });
        }
    }

    private static void injectMotionEvent(@NonNull ViewGroup container, @NonNull MotionEvent base,
                                          int action) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis() + 100;
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action,
                base.getX(), base.getY(), 0.0f, 0.0f, 0, 1.0f, 1.0f, 0, 0);
        container.dispatchTouchEvent(event);
        event.recycle();
    }

    private static void injectMotionEventForLongPress(@NonNull ViewGroup container, @NonNull MotionEvent base,
                                                      int action) {
        long downTime = SystemClock.uptimeMillis() - 1000;
        long eventTime = SystemClock.uptimeMillis() + 100;
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action,
                base.getX(), base.getY(), 0.0f, 0.0f, 0, 1.0f, 1.0f, -1, 0);
        container.dispatchTouchEvent(event);
        event.recycle();
    }

    private interface OnViewFoundListener {
        boolean onViewFound(View view);
    }

    private static View findViewAtPosition(@NonNull final ViewGroup container, int x, int y,
                                           OnViewFoundListener listener) {
        int count = container.getChildCount();
        for (int i = count; i > 0; --i) {
            View child = container.getChildAt(i - 1);
            if (isPointInsideView(x, y, child)) {
                if (child instanceof ViewGroup) {
                    View v = findViewAtPosition((ViewGroup) child, x, y, listener);
                    if (v != null) {
                        return v;
                    }
                }
                if (listener.onViewFound(child)) {
                    return child;
                }
            }
        }
        return null;
    }

    /**
     * Determines if given points are inside view
     *
     * @param x    - x coordinate of point
     * @param y    - y coordinate of point
     * @param view - view object to compare
     * @return true if the points are within view bounds, false otherwise
     */
    public static boolean isPointInsideView(float x, float y, View view) {
        int location[] = new int[2];
        view.getLocationOnScreen(location);
        int viewX = location[0];
        int viewY = location[1];

        //point is inside view bounds
        if ((x > viewX && x < (viewX + view.getWidth())) &&
                (y > viewY && y < (viewY + view.getHeight()))) {
            return true;
        } else {
            return false;
        }
    }

    public static final String ACTION_SETTINGS_CHANGED = PREFIX_ACTION + "SETTINGS_CHANGED";
    public static final String EXTRA_SETTINGS = PREFIX_EXTRA + "SETTINGS";

    // called by SettingsActivity
    public static void sendSettingsChanged(Context context, SharedPreferences prefs) {
        FTD.Settings settings = new FTD.Settings(context, prefs);
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
        }
        return modContext;
    }

    public static final Integer METHOD_SIZE = 0;
    public static final Integer METHOD_PRESSURE = 1;

    public static class Settings implements Serializable {
        static final long serialVersionUID = 1L;

        private final boolean isCharging;

        // General
        // Detector
        public final ScaleRect detectionArea;
        public final boolean detectionAreaMirror;
        public final boolean detectionAreaReverse;
        public final int detectionSensitivity;
        public final int detectionWindow;
        public final int extraLongPressTimeout;
        public final Set<String> blacklist;
        // Feedback
        public final boolean vibration;
        public final int rippleColor;
        // Display
        public final boolean showDisabledActionToast;
        public final boolean showEnabledActionToast;
        public final boolean showNotification;

        // Floating Action
        public final boolean floatingActionEnable;
        public final int floatingActionButtonColor;
        public final int floatingActionBackgroundColor;
        public final int floatingActionBackgroundAlpha;
        public final int floatingActionTimeout;
        public final boolean floatingActionMovable;
        public final boolean floatingActionRecents;
        public final boolean floatingActionStickyNavigation;
        public final boolean useLocalFAB;

        // Detector
        public final int detectorMethod;

        // Force Touch
        // Setting
        public final boolean forceTouchEnable;
        public final float forceTouchThreshold;
        // Action
        public final ActionInfo.Record forceTouchActionTap;
        public final ActionInfo.Record forceTouchActionDoubleTap;
        public final ActionInfo.Record forceTouchActionLongPress;
        public final ActionInfo.Record forceTouchActionFlickLeft;
        public final ActionInfo.Record forceTouchActionFlickRight;
        public final ActionInfo.Record forceTouchActionFlickUp;
        public final ActionInfo.Record forceTouchActionFlickDown;

        // Knuckle Touch
        public final boolean knuckleTouchEnable;
        public final float knuckleTouchThreshold;
        public final ActionInfo.Record knuckleTouchActionTap;
        public final ActionInfo.Record knuckleTouchActionLongPress;

        // Wiggle Touch
        public final boolean wiggleTouchEnable;
        public final float wiggleTouchMagnification;
        public final ActionInfo.Record wiggleTouchActionTap;
        public final ActionInfo.Record wiggleTouchActionLongPress;

        // Scratch Touch
        public final boolean scratchTouchEnable;
        public final float scratchTouchMagnification;
        public final ActionInfo.Record scratchTouchActionTap;
        public final ActionInfo.Record scratchTouchActionLongPress;

        public Settings(Context context, SharedPreferences prefs) {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING;
//                    status == BatteryManager.BATTERY_STATUS_FULL;
            } else {
                isCharging = false;
            }

            // General
            // Detector
            detectionArea = ScaleRect.fromPreference(prefs.getString("key_detection_area", ""));
            detectionAreaMirror = prefs.getBoolean("key_detection_area_mirror", false);
            detectionAreaReverse = prefs.getBoolean("key_detection_area_reverse", false);
            detectionSensitivity = Integer.parseInt(getStringToParse(prefs, "key_detection_sensitivity", "9"));
            detectionWindow = Integer.parseInt(getStringToParse(prefs, "key_detection_window", "1000"));
            extraLongPressTimeout = Integer.parseInt(getStringToParse(prefs, "key_extra_long_press_timeout", "300"));
            blacklist = prefs.getStringSet("key_blacklist", Sets.<String>newHashSet());
            // Feedback
            vibration = prefs.getBoolean("key_vibration", true);
            rippleColor = Color.parseColor(getStringToParse(prefs, "key_ripple_color", "#212121"));
            // Display
            showDisabledActionToast = prefs.getBoolean("key_show_disabled_action_toast", true);
            showEnabledActionToast = prefs.getBoolean("key_show_enabled_action_toast", true);
            showNotification = prefs.getBoolean("key_show_notification", true);

            // Floating Action
            floatingActionEnable = prefs.getBoolean("key_floating_action_enable", true);
            floatingActionButtonColor = Color.parseColor(getStringToParse(prefs, "key_floating_action_button_color", "#B71C1C"));
            floatingActionBackgroundColor = Color.parseColor(getStringToParse(prefs, "key_floating_action_background_color", "#90A4AE"));
            floatingActionBackgroundAlpha = Integer.parseInt(getStringToParse(prefs, "key_floating_action_background_alpha", "64"));
            floatingActionTimeout = Integer.parseInt(getStringToParse(prefs, "key_floating_action_timeout", "3000"));
            floatingActionMovable = prefs.getBoolean("key_floating_action_movable", true);
            floatingActionRecents = prefs.getBoolean("key_floating_action_recents", false);
            floatingActionStickyNavigation = prefs.getBoolean("key_floating_action_sticky_navigation", false);
            useLocalFAB = prefs.getBoolean("key_use_local_fab", true);

            // Detector
            detectorMethod = Integer.parseInt(getStringToParse(prefs, "key_detector_method", "0"));

            // Force Touch
            forceTouchEnable = prefs.getBoolean("key_force_touch_enable", false);
            forceTouchThreshold = Float.parseFloat(getStringToParse(prefs,
                    isCharging ? "key_force_touch_threshold_charging" : "key_force_touch_threshold",
                    ModForceTouch.Detector.DEFAULT_THRESHOLD));

            forceTouchActionTap = getActionRecord(prefs, "key_force_touch_action_tap");
            forceTouchActionDoubleTap = getActionRecord(prefs, "key_force_touch_action_double_tap");
            forceTouchActionLongPress = getActionRecord(prefs, "key_force_touch_action_long_press");
            forceTouchActionFlickLeft = getActionRecord(prefs, "key_force_touch_action_flick_left");
            forceTouchActionFlickRight = getActionRecord(prefs, "key_force_touch_action_flick_right");
            forceTouchActionFlickUp = getActionRecord(prefs, "key_force_touch_action_flick_up");
            forceTouchActionFlickDown = getActionRecord(prefs, "key_force_touch_action_flick_down");

            // Knuckle Touch
            knuckleTouchEnable = prefs.getBoolean("key_knuckle_touch_enable", false);
            knuckleTouchThreshold = Float.parseFloat(getStringToParse(prefs,
                    isCharging ? "key_knuckle_touch_threshold_charging" : "key_knuckle_touch_threshold",
                    ModForceTouch.Detector.DEFAULT_THRESHOLD));
            knuckleTouchActionTap = getActionRecord(prefs, "key_knuckle_touch_action_tap");
            knuckleTouchActionLongPress = getActionRecord(prefs, "key_knuckle_touch_action_long_press");

            // Wiggle Touch
            wiggleTouchEnable = prefs.getBoolean("key_wiggle_touch_enable", false);
            wiggleTouchMagnification = Float.parseFloat(getStringToParse(prefs, "key_wiggle_touch_magnification", "1.5"));
            wiggleTouchActionTap = getActionRecord(prefs, "key_wiggle_touch_action_tap");
            wiggleTouchActionLongPress = getActionRecord(prefs, "key_wiggle_touch_action_long_press");
            
            // Scratch Touch
            scratchTouchEnable = prefs.getBoolean("key_scratch_touch_enable", false);
            scratchTouchMagnification = Float.parseFloat(getStringToParse(prefs, "key_scratch_touch_magnification", "1.5"));
            scratchTouchActionTap = getActionRecord(prefs, "key_scratch_touch_action_tap");
            scratchTouchActionLongPress = getActionRecord(prefs, "key_scratch_touch_action_long_press");
        }

        private String getStringToParse(SharedPreferences prefs, String key, String defValue) {
            String str = prefs.getString(key, defValue);
            if (Strings.isNullOrEmpty(str)) {
                str = defValue;
            }
            return str;
        }

        private ActionInfo.Record getActionRecord(SharedPreferences prefs, String key) {
            return ActionInfo.Record.fromPreference(prefs.getString(key, ""));
        }
    }
}
