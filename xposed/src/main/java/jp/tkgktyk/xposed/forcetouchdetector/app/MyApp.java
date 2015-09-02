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

package jp.tkgktyk.xposed.forcetouchdetector.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.MotionEvent;

import com.google.common.base.Objects;
import com.google.common.base.Strings;

import jp.tkgktyk.lib.BaseApplication;
import jp.tkgktyk.xposed.forcetouchdetector.BuildConfig;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.ModForceTouch;
import jp.tkgktyk.xposed.forcetouchdetector.R;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionInfo;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionInfoList;

/**
 * Created by tkgktyk on 2015/06/06.
 */
public class MyApp extends BaseApplication {

    private static final boolean DONATED = true;

    /**
     * for version name
     *
     * @return
     */
    @Override
    protected SharedPreferences getDefaultSharedPreferences() {
        return FTD.getSharedPreferences(this);
    }

    @Override
    protected boolean isDebug() {
        return BuildConfig.DEBUG;
    }

    @Override
    protected String getVersionName() {
        return BuildConfig.VERSION_NAME;
    }

    private static boolean mIsDonated;

    public static boolean isDonated() {
        return mIsDonated || (BuildConfig.DEBUG && DONATED);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        String installer = null;
        try {
            installer = getPackageManager().getInstallerPackageName(
                    "jp.tkgktyk.key.forcetouchdetector");
        } catch (IllegalArgumentException e) {
            // not installed
        }
        logD("Installer of key = " + installer);
        mIsDonated = Objects.equal("com.android.vending", installer);
        if (!mIsDonated) {
            installer = getPackageManager().getInstallerPackageName(FTD.PACKAGE_NAME);
            logD("Installer of FTD = " + installer);
            mIsDonated = Objects.equal("com.android.vending", installer);
        }

        SharedPreferences prefs = getDefaultSharedPreferences();
        setMethod(prefs.getString(getString(R.string.key_detector_method), ""));
    }

    @Override
    protected void onVersionUpdated(MyVersion next, MyVersion old) {
        SharedPreferences prefs = getDefaultSharedPreferences();
        if (old.isOlderThan("0.2.0")) {
            prefs.edit()
                    .putBoolean("key_pressure_enabled",
                            prefs.getBoolean("key_enabled", false))
                    .putString("key_pressure_action_tap",
                            prefs.getString("key_action_tap", ""))
                    .putString("key_pressure_action_double_tap",
                            prefs.getString("key_action_double_tap", ""))
                    .putString("key_pressure_action_long_press",
                            prefs.getString("key_action_long_press", ""))
                    .putString("key_pressure_action_flick_left",
                            prefs.getString("key_action_flick_left", ""))
                    .putString("key_pressure_action_flick_right",
                            prefs.getString("key_action_flick_right", ""))
                    .putString("key_pressure_action_flick_up",
                            prefs.getString("key_action_flick_up", ""))
                    .putString("key_pressure_action_flick_down",
                            prefs.getString("key_action_flick_down", ""))
                    .apply();
        }
        if (old.isOlderThan("0.2.1")) {
            prefs.edit()
                    .putString("key_pressure_action_flick_down",
                            prefs.getString("key_action_flick_down", ""))
                    .apply();
        }
        String[] keys = {
                "key_pressure_action_tap",
                "key_pressure_action_double_tap",
                "key_pressure_action_long_press",
                "key_pressure_action_flick_left",
                "key_pressure_action_flick_right",
                "key_pressure_action_flick_up",
                "key_pressure_action_flick_down",
                "key_size_action_tap",
                "key_size_action_double_tap",
                "key_size_action_long_press",
                "key_size_action_flick_left",
                "key_size_action_flick_right",
                "key_size_action_flick_up",
                "key_size_action_flick_down",
        };
        if (old.isOlderThan("0.3.0")) {
            for (String key : keys) {
                ActionInfo.Record record = new ActionInfo.Record();
                record.intentUri = prefs.getString(key, "");
                ActionInfo info = new ActionInfo(record);
                Intent intent = info.getIntent();
                if (intent == null) {
                    continue;
                }
                if (Objects.equal(intent.getAction(), FTD.PREFIX_ACTION + "FLOATING_NAVIGATION")) {
                    intent.setAction(FTD.ACTION_FLOATING_ACTION);
                }
                if (Strings.isNullOrEmpty(record.intentUri)) {
                    info = new ActionInfo(this, intent, ActionInfo.TYPE_NONE);
                } else if (!Strings.isNullOrEmpty(intent.getAction()) &&
                        intent.getAction().startsWith(FTD.PREFIX_ACTION)) {
                    info = new ActionInfo(this, intent, ActionInfo.TYPE_TOOL);
                } else {
                    info = new ActionInfo(this, intent, ActionInfo.TYPE_APP);
                }
                prefs.edit()
                        .putString(key, info.toStringForPreference())
                        .apply();
            }
        }
        if (old.isOlderThan("0.3.3")) {
            for (String key : keys) {
                ActionInfo.Record record = new ActionInfo.Record();
                record.intentUri = prefs.getString(key, "");
                ActionInfo info = new ActionInfo(record);
                Intent intent = info.getIntent();
                if (intent == null) {
                    continue;
                }
                String action = intent.getAction();
                if (Objects.equal(action, FTD.PREFIX_ACTION + "EXPAND_NOTIFICATIONS")) {
                    intent.setAction(FTD.ACTION_NOTIFICATIONS);
                } else if (Objects.equal(intent.getAction(), FTD.PREFIX_ACTION + "EXPAND_QUICK_SETTINGS")) {
                    intent.setAction(FTD.ACTION_QUICK_SETTINGS);
                }
                prefs.edit()
                        .putString(key, info.toStringForPreference())
                        .apply();
            }
        }
        if (old.isOlderThan("0.3.4")) {
            prefs.edit().remove(getString(R.string.key_detection_area)).apply();
        }
        if (old.isOlderThan("0.3.5")) {
            // rename enabled to enable
            prefs.edit()
                    .putBoolean("key_pressure_enable",
                            prefs.getBoolean("key_pressure_enabled", false))
                    .putBoolean("key_size_enable",
                            prefs.getBoolean("key_size_enabled", false))
                    .putBoolean("key_floating_action_enable",
                            prefs.getBoolean("key_floating_action_enabled", false))
                    .apply();
        }
        if (old.isOlderThan("0.3.6")) {
            prefs.edit()
                    .putString("key_pressure_threshold_charging",
                            prefs.getString("key_pressure_threshold",
                                    ModForceTouch.Detector.DEFAULT_THRESHOLD))
                    .putString("key_size_threshold_charging",
                            prefs.getString("key_size_threshold",
                                    ModForceTouch.Detector.DEFAULT_THRESHOLD))
                    .apply();
        }
        if (old.isOlderThan("0.4.2")) {
            try {
                ActionInfoList actions = ActionInfoList.fromPreference(
                        prefs.getString(getString(R.string.key_floating_action_list), ""));
            } catch (Exception e) {
                prefs.edit().remove(getString(R.string.key_floating_action_list)).apply();
            }
        }
        if (old.isOlderThan("0.4.3")) {
            boolean usePressure = prefs.getBoolean("key_use_pressure", false);
            boolean pressure = prefs.getBoolean("key_pressure_enable", false);
            boolean size = prefs.getBoolean("key_size_enable", false);
            boolean largeTouch = prefs.getBoolean("key_large_touch_enable", false);
            
            if (pressure || (!size && usePressure && !largeTouch)) {
                prefs.edit()
                        .putString(getString(R.string.key_detector_method), FTD.METHOD_PRESSURE.toString())
                        .putBoolean(getString(R.string.key_force_touch_enable), pressure)
                        .putString(getString(R.string.key_force_touch_threshold),
                                prefs.getString("key_pressure_threshold", ""))
                        .putString(getString(R.string.key_force_touch_threshold_charging),
                                prefs.getString("key_pressure_threshold_charging", ""))
                        .putString(getString(R.string.key_force_touch_action_tap),
                                prefs.getString("key_pressure_action_tap", ""))
                        .putString(getString(R.string.key_force_touch_action_long_press),
                                prefs.getString("key_pressure_action_long_press", ""))
                        .putString(getString(R.string.key_force_touch_action_double_tap),
                                prefs.getString("key_pressure_action_double_tap", ""))
                        .putString(getString(R.string.key_force_touch_action_flick_left),
                                prefs.getString("key_pressure_action_flick_left", ""))
                        .putString(getString(R.string.key_force_touch_action_flick_right),
                                prefs.getString("key_pressure_action_flick_right", ""))
                        .putString(getString(R.string.key_force_touch_action_flick_up),
                                prefs.getString("key_pressure_action_flick_up", ""))
                        .putString(getString(R.string.key_force_touch_action_flick_down),
                                prefs.getString("key_pressure_action_flick_down", ""))
                        .apply();
            } else if (size || (!pressure && !usePressure && !largeTouch)) {
                prefs.edit()
                        .putString(getString(R.string.key_detector_method), FTD.METHOD_SIZE.toString())
                        .putBoolean(getString(R.string.key_force_touch_enable), size)
                        .putString(getString(R.string.key_force_touch_threshold),
                                prefs.getString("key_size_threshold", ""))
                        .putString(getString(R.string.key_force_touch_threshold_charging),
                                prefs.getString("key_size_threshold_charging", ""))
                        .putString(getString(R.string.key_force_touch_action_tap),
                                prefs.getString("key_size_action_tap", ""))
                        .putString(getString(R.string.key_force_touch_action_long_press),
                                prefs.getString("key_size_action_long_press", ""))
                        .putString(getString(R.string.key_force_touch_action_double_tap),
                                prefs.getString("key_size_action_double_tap", ""))
                        .putString(getString(R.string.key_force_touch_action_flick_left),
                                prefs.getString("key_size_action_flick_left", ""))
                        .putString(getString(R.string.key_force_touch_action_flick_right),
                                prefs.getString("key_size_action_flick_right", ""))
                        .putString(getString(R.string.key_force_touch_action_flick_up),
                                prefs.getString("key_size_action_flick_up", ""))
                        .putString(getString(R.string.key_force_touch_action_flick_down),
                                prefs.getString("key_size_action_flick_down", ""))
                        .apply();
            } else {
                prefs.edit()
                        .putString(getString(R.string.key_detector_method),
                                usePressure ? FTD.METHOD_PRESSURE.toString() : FTD.METHOD_SIZE.toString())
                        .putBoolean(getString(R.string.key_force_touch_enable), largeTouch)
                        .putString(getString(R.string.key_force_touch_threshold),
                                prefs.getString("key_large_touch_threshold", ""))
                        .putString(getString(R.string.key_force_touch_threshold_charging),
                                prefs.getString("key_large_touch_threshold_charging", ""))
                        .putString(getString(R.string.key_force_touch_action_tap),
                                prefs.getString("key_large_touch_action_tap", ""))
                        .putString(getString(R.string.key_force_touch_action_long_press),
                                prefs.getString("key_large_touch_action_long_press", ""))
                        .apply();
            }
        }
    }

    public static void updateService(Context context, SharedPreferences prefs) {
        FTD.Settings settings = new FTD.Settings(context, prefs);
        updateService(context, settings.forceTouchEnable, settings.knuckleTouchEnable,
                settings.wiggleTouchEnable, settings.scratchTouchEnable,
                settings.floatingActionEnable, settings.showNotification);
    }

    public static void updateService(Context context, boolean force, boolean knuckle,
                                     boolean wiggle, boolean scratch, boolean floatingAction,
                                     boolean showNotification) {
        Intent em = new Intent(context, EmergencyService.class);
        Intent fa = new Intent(context, FloatingActionService.class);
        boolean ftdEnable = force || knuckle || wiggle || scratch;
        if (ftdEnable) {
            if (floatingAction) {
                context.stopService(em);
                context.startService(fa);
            } else if (showNotification) {
                context.stopService(fa);
                context.startService(em);
            } else {
                context.stopService(em);
                context.stopService(fa);
            }
        } else {
            context.stopService(em);
            context.stopService(fa);
        }
    }

    public static void stopService(Context context) {
        Intent em = new Intent(context, EmergencyService.class);
        Intent fa = new Intent(context, FloatingActionService.class);
        context.stopService(em);
        context.stopService(fa);
    }

    private static int mMethod;

    public static void setMethod(String methodString) {
        int method = FTD.METHOD_SIZE;
        if (!Strings.isNullOrEmpty(methodString)) {
            method = Integer.parseInt(methodString);
        }
        setMethod(method);
    }

    public static void setMethod(int method) {
        mMethod = method;
    }

    public static float readMethodParameter(MotionEvent event) {
        return mMethod == FTD.METHOD_PRESSURE ? event.getPressure() : event.getSize();
    }
}
