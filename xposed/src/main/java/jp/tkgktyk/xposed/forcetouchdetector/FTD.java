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
import android.os.SystemClock;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.ScrollView;
import android.widget.Toast;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;

import java.io.Serializable;
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

    public static final String ACTION_BACK = PREFIX_ACTION + "BACK";
    public static final String ACTION_HOME = PREFIX_ACTION + "HOME";
    public static final String ACTION_RECENTS = PREFIX_ACTION + "RECENTS";
    public static final String ACTION_NOTIFICATIONS = PREFIX_ACTION + "NOTIFICATIONS";
    public static final String ACTION_QUICK_SETTINGS = PREFIX_ACTION + "QUICK_SETTINGS";
    public static final String ACTION_FORWARD = PREFIX_ACTION + "FORWARD";
    public static final String ACTION_REFRESH = PREFIX_ACTION + "REFRESH";
    public static final String ACTION_SCROLL_UP_GLOBAL = PREFIX_ACTION + "SCROLL_UP_GLOBAL";
    public static final String ACTION_SCROLL_DOWN_GLOBAL = PREFIX_ACTION + "SCROLL_DOWN_GLOBAL";
    public static final String ACTION_VOLUME_UP = PREFIX_ACTION + "VOLUME_UP";
    public static final String ACTION_VOLUME_DOWN = PREFIX_ACTION + "VOLUME_DOWN";
    public static final String ACTION_SCREENSHOT = PREFIX_ACTION + "SCREENSHOT";

    public static final String ACTION_KILL = PREFIX_ACTION + "KILL";
    public static final String ACTION_POWER_MENU = PREFIX_ACTION + "POWER_MENU";

    public static final String ACTION_DOUBLE_TAP = PREFIX_ACTION + "DOUBLE_TAP" + SUFFIX_TOUCH_ACTION;
    public static final String ACTION_LONG_PRESS = PREFIX_ACTION + "LONG_PRESS" + SUFFIX_TOUCH_ACTION;
    public static final String ACTION_LONG_PRESS_FULL = PREFIX_ACTION + "LONG_PRESS_FULL" + SUFFIX_TOUCH_ACTION;
    public static final String ACTION_SCROLL_UP = PREFIX_ACTION + "SCROLL_UP" + SUFFIX_TOUCH_ACTION;
    public static final String ACTION_SCROLL_DOWN = PREFIX_ACTION + "SCROLL_DOWN" + SUFFIX_TOUCH_ACTION;

    public static final String ACTION_FLOATING_ACTION = PREFIX_ACTION + "FLOATING_ACTION";

    public static final IntentFilter INTERNAL_ACTION_FILTER;

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
        // status bar
        INTERNAL_ACTION_FILTER.addAction(ACTION_NOTIFICATIONS);
        INTERNAL_ACTION_FILTER.addAction(ACTION_QUICK_SETTINGS);
        // other
        INTERNAL_ACTION_FILTER.addAction(ACTION_KILL);
        INTERNAL_ACTION_FILTER.addAction(ACTION_POWER_MENU);
    }

    public static final String EXTRA_FRACTION_X = PREFIX_EXTRA + "FRACTION_X";
    public static final String EXTRA_FRACTION_Y = PREFIX_EXTRA + "FRACTION_Y";

    private static final Point mDisplaySize = new Point();

    @NonNull
    public static String getActionName(Context context, String action) {
        Context mod = getModContext(context);
        //
        // Key action
        //
        if (action.equals(ACTION_BACK)) {
            return mod.getString(R.string.action_back);
        } else if (action.equals(ACTION_HOME)) {
            return mod.getString(R.string.action_home);
        } else if (action.equals(ACTION_RECENTS)) {
            return mod.getString(R.string.action_recents);
        } else if (action.equals(ACTION_FORWARD)) {
            return mod.getString(R.string.action_forward);
        } else if (action.equals(ACTION_REFRESH)) {
            return mod.getString(R.string.action_refresh);
        } else if (action.equals(ACTION_SCROLL_UP_GLOBAL)) {
            return mod.getString(R.string.action_scroll_up);
        } else if (action.equals(ACTION_SCROLL_DOWN_GLOBAL)) {
            return mod.getString(R.string.action_scroll_down);
        } else if (action.equals(ACTION_VOLUME_UP)) {
            return mod.getString(R.string.action_volume_up);
        } else if (action.equals(ACTION_VOLUME_DOWN)) {
            return mod.getString(R.string.action_volume_down);
        } else if (action.equals(ACTION_SCREENSHOT)) {
            return mod.getString(R.string.action_screenshot);

            //
            // status bar
            //
        } else if (action.equals(ACTION_NOTIFICATIONS)) {
            return mod.getString(R.string.action_notifications);
        } else if (action.equals(ACTION_QUICK_SETTINGS)) {
            return mod.getString(R.string.action_quick_settings);

            //
            // Other functions
            //
        } else if (action.equals(ACTION_KILL)) {
            return mod.getString(R.string.action_kill);
        } else if (action.equals(ACTION_POWER_MENU)) {
            return mod.getString(R.string.action_power_menu);

            //
            // Touch action
            //
        } else if (action.equals(ACTION_DOUBLE_TAP)) {
            return mod.getString(R.string.action_double_tap);
        } else if (action.equals(ACTION_LONG_PRESS)) {
            return mod.getString(R.string.action_long_press);
        } else if (action.equals(ACTION_LONG_PRESS_FULL)) {
            return mod.getString(R.string.action_long_press_full);
        } else if (action.equals(ACTION_SCROLL_UP)) {
            return mod.getString(R.string.action_scroll_up);
        } else if (action.equals(ACTION_SCROLL_DOWN)) {
            return mod.getString(R.string.action_scroll_down);

            //
            // other local function
            //
        } else if (action.equals(ACTION_FLOATING_ACTION)) {
            return mod.getString(R.string.action_floating_action);
        }
        return "";
    }

    @DrawableRes
    public static int getActionIconResource(String action) {
        //
        // Key
        //
        if (action.equals(ACTION_BACK)) {
            return R.drawable.ic_sysbar_back;
        } else if (action.equals(ACTION_HOME)) {
            return R.drawable.ic_sysbar_home;
        } else if (action.equals(ACTION_RECENTS)) {
            return R.drawable.ic_sysbar_recent;
        } else if (action.equals(ACTION_FORWARD)) {
            return R.drawable.ic_arrow_forward_white_24dp;
        } else if (action.equals(ACTION_REFRESH)) {
            return R.drawable.ic_refresh_white_24dp;
        } else if (action.equals(ACTION_SCROLL_UP_GLOBAL)) {
            return R.drawable.ic_scroll_up_white_48px;
        } else if (action.equals(ACTION_SCROLL_DOWN_GLOBAL)) {
            return R.drawable.ic_scroll_down_white_48px;
        } else if (action.equals(ACTION_VOLUME_UP)) {
            return R.drawable.ic_volume_up_white_24dp;
        } else if (action.equals(ACTION_VOLUME_DOWN)) {
            return R.drawable.ic_volume_down_white_24dp;
        } else if (action.equals(ACTION_SCREENSHOT)) {
            return R.drawable.ic_camera_enhance_white_24dp;

            //
            // status bar
            //
        } else if (action.equals(ACTION_NOTIFICATIONS)) {
            return R.drawable.ic_notifications_none_white_24dp;
        } else if (action.equals(ACTION_QUICK_SETTINGS)) {
            return R.drawable.ic_settings_white_24dp;

            //
            // other function
            //
        } else if (action.equals(ACTION_KILL)) {
            return R.drawable.ic_close_white_24dp;
        } else if (action.equals(ACTION_POWER_MENU)) {
            return R.drawable.ic_power_settings_new_white_24dp;

            //
            // touch aciton
            //
        } else if (action.equals(ACTION_DOUBLE_TAP)) {
            return 0;
        } else if (action.equals(ACTION_LONG_PRESS)) {
            return 0;
        } else if (action.equals(ACTION_LONG_PRESS_FULL)) {
            return 0;
        } else if (action.equals(ACTION_SCROLL_UP)) {
            return R.drawable.ic_scroll_up_white_48px;
        } else if (action.equals(ACTION_SCROLL_DOWN)) {
            return R.drawable.ic_scroll_down_white_48px;

            //
            // other local action
            //
        } else if (action.equals(ACTION_FLOATING_ACTION)) {
            return R.drawable.ic_floating_action;
        }
        return 0;
    }

    public static boolean performAction(@NonNull ViewGroup container,
                                        @NonNull ActionInfo actionInfo,
                                        @NonNull MotionEvent event) {
        Context context = container.getContext();
        Intent intent = actionInfo.getIntent();
        if (intent == null) {
            return false;
        }
        // add coordinates
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRealSize(mDisplaySize);
        intent.putExtra(EXTRA_FRACTION_X, event.getX() / mDisplaySize.x);
        intent.putExtra(EXTRA_FRACTION_Y, event.getY() / mDisplaySize.y);
        // launch action like ActionInfo#launch
        switch (actionInfo.getType()) {
            case ActionInfo.TYPE_TOOL:
                String action = intent.getAction();
                if (action.endsWith(SUFFIX_TOUCH_ACTION)) {
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

    public static boolean isLocalAction(@NonNull Intent intent) {
        return isLocalAction(intent.getAction());
    }

    public static boolean isLocalAction(String action) {
        return !Strings.isNullOrEmpty(action) && action.startsWith(PREFIX_ACTION);
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
        }
        return modContext;
    }

    public static class Settings implements Serializable {
        static final long serialVersionUID = 1L;

        // General
        public final ScaleRect detectionArea;
        public final boolean detectionAreaMirror;
        public final boolean detectionAreaReverse;
        public final Set<String> blacklist;
        public final boolean showDisabledActionToast;
        public final boolean showEnabledActionToast;
        public final boolean showNotification;
        public final int detectionSensitivity;
        public final int detectionWindow;
        public final int extraLongPressTimeout;

        // Pressure
        public final Holder pressure = new Holder();

        // Size
        public final Holder size = new Holder();

        // Floating Action
        public final boolean floatingActionEnable;
        public final int floatingActionColor;
        public final int floatingActionAlpha;
        public final int floatingActionTimeout;
        public final boolean floatingActionRecents;
        public final boolean useLocalFAB;

        public Settings(SharedPreferences prefs) {
            // General
            detectionArea = ScaleRect.fromPreference(prefs.getString("key_detection_area", ""));
            detectionAreaMirror = prefs.getBoolean("key_detection_area_mirror", false);
            detectionAreaReverse = prefs.getBoolean("key_detection_area_reverse", false);
            blacklist = prefs.getStringSet("key_blacklist", Sets.<String>newHashSet());
            showDisabledActionToast = prefs.getBoolean("key_show_disabled_action_toast", true);
            showEnabledActionToast = prefs.getBoolean("key_show_enabled_action_toast", true);
            showNotification = prefs.getBoolean("key_show_notification", true);
            detectionSensitivity = Integer.parseInt(getStringToParse(prefs, "key_detection_sensitivity", "7"));
            detectionWindow = Integer.parseInt(getStringToParse(prefs, "key_detection_window", "1000"));
            extraLongPressTimeout = Integer.parseInt(getStringToParse(prefs, "key_extra_long_press_timeout", "0"));

            // Pressure
            pressure.enable = prefs.getBoolean("key_pressure_enable", false);
            pressure.threshold = Float.parseFloat(getStringToParse(prefs, "key_pressure_threshold",
                    ModForceTouch.ForceTouchDetector.DEFAULT_THRESHOLD));

            pressure.actionTap = getActionRecord(prefs, "key_pressure_action_tap");
            pressure.actionDoubleTap = getActionRecord(prefs, "key_pressure_action_double_tap");
            pressure.actionLongPress = getActionRecord(prefs, "key_pressure_action_long_press");
            pressure.actionFlickLeft = getActionRecord(prefs, "key_pressure_action_flick_left");
            pressure.actionFlickRight = getActionRecord(prefs, "key_pressure_action_flick_right");
            pressure.actionFlickUp = getActionRecord(prefs, "key_pressure_action_flick_up");
            pressure.actionFlickDown = getActionRecord(prefs, "key_pressure_action_flick_down");

            // Size
            size.enable = prefs.getBoolean("key_size_enable", false);
            size.threshold = Float.parseFloat(getStringToParse(prefs, "key_size_threshold",
                    ModForceTouch.ForceTouchDetector.DEFAULT_THRESHOLD));

            size.actionTap = getActionRecord(prefs, "key_size_action_tap");
            size.actionDoubleTap = getActionRecord(prefs, "key_size_action_double_tap");
            size.actionLongPress = getActionRecord(prefs, "key_size_action_long_press");
            size.actionFlickLeft = getActionRecord(prefs, "key_size_action_flick_left");
            size.actionFlickRight = getActionRecord(prefs, "key_size_action_flick_right");
            size.actionFlickUp = getActionRecord(prefs, "key_size_action_flick_up");
            size.actionFlickDown = getActionRecord(prefs, "key_size_action_flick_down");

            // Floating Action
            floatingActionEnable = prefs.getBoolean("key_floating_action_enable", false);
            floatingActionColor = Color.parseColor(getStringToParse(prefs, "key_floating_action_color", "#000000"));
            floatingActionAlpha = Integer.parseInt(getStringToParse(prefs, "key_floating_action_alpha", "64"));
            floatingActionTimeout = Integer.parseInt(getStringToParse(prefs, "key_floating_action_timeout", "3000"));
            floatingActionRecents = prefs.getBoolean("key_floating_action_recents", false);
            useLocalFAB = prefs.getBoolean("key_use_local_fab", true);
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

        public boolean isEnabled() {
            return pressure.enable || size.enable;
        }

        public class Holder implements Serializable {
            static final long serialVersionUID = 1L;

            // Setting
            public boolean enable;
            public float threshold;
            // Action
            public ActionInfo.Record actionTap;
            public ActionInfo.Record actionDoubleTap;
            public ActionInfo.Record actionLongPress;
            public ActionInfo.Record actionFlickLeft;
            public ActionInfo.Record actionFlickRight;
            public ActionInfo.Record actionFlickUp;
            public ActionInfo.Record actionFlickDown;
        }
    }
}
