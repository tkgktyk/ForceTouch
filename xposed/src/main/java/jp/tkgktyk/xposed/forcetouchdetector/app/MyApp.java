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

import com.google.common.base.Objects;
import com.google.common.base.Strings;

import jp.tkgktyk.lib.BaseApplication;
import jp.tkgktyk.xposed.forcetouchdetector.BuildConfig;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionInfo;

/**
 * Created by tkgktyk on 2015/06/06.
 */
public class MyApp extends BaseApplication {

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

    private static boolean mIsInstalledFromStore;

    public static boolean isInstalledFromStore() {
        return mIsInstalledFromStore;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        String installer = getPackageManager().getInstallerPackageName(FTD.PACKAGE_NAME);
        MyApp.logE("Installer = " + installer);
        mIsInstalledFromStore = Objects.equal("com.android.vending", installer);
    }

    @Override
    protected void onVersionUpdated(MyVersion next, MyVersion old) {
        SharedPreferences prefs = getDefaultSharedPreferences();
        if (old.isOlderThan("0.2.0")) {
            prefs.edit()
                    .putBoolean("key_pressure_enabled",
                            prefs.getBoolean("key_enabled", false))
                    .putString(getString(R.string.key_pressure_action_tap),
                            prefs.getString("key_action_tap", ""))
                    .putString(getString(R.string.key_pressure_action_double_tap),
                            prefs.getString("key_action_double_tap", ""))
                    .putString(getString(R.string.key_pressure_action_long_press),
                            prefs.getString("key_action_long_press", ""))
                    .putString(getString(R.string.key_pressure_action_flick_left),
                            prefs.getString("key_action_flick_left", ""))
                    .putString(getString(R.string.key_pressure_action_flick_right),
                            prefs.getString("key_action_flick_right", ""))
                    .putString(getString(R.string.key_pressure_action_flick_up),
                            prefs.getString("key_action_flick_up", ""))
                    .putString(getString(R.string.key_pressure_action_flick_down),
                            prefs.getString("key_action_flick_down", ""))
                    .apply();
        }
        if (old.isOlderThan("0.2.1")) {
            prefs.edit()
                    .putString(getString(R.string.key_pressure_action_flick_down),
                            prefs.getString("key_action_flick_down", ""))
                    .apply();
        }
        String[] keys = {
                getString(R.string.key_pressure_action_tap),
                getString(R.string.key_pressure_action_double_tap),
                getString(R.string.key_pressure_action_long_press),
                getString(R.string.key_pressure_action_flick_left),
                getString(R.string.key_pressure_action_flick_right),
                getString(R.string.key_pressure_action_flick_up),
                getString(R.string.key_pressure_action_flick_down),
                getString(R.string.key_size_action_tap),
                getString(R.string.key_size_action_double_tap),
                getString(R.string.key_size_action_long_press),
                getString(R.string.key_size_action_flick_left),
                getString(R.string.key_size_action_flick_right),
                getString(R.string.key_size_action_flick_up),
                getString(R.string.key_size_action_flick_down),
        };
        if (old.isOlderThan("0.3.0")) {
            for (String key : keys) {
                ActionInfo.Record record = new ActionInfo.Record();
                record.intentUri = prefs.getString(key, "");
                ActionInfo info = new ActionInfo(record);
                Intent intent = info.getIntent();
                if (Objects.equal(intent.getAction(), FTD.PREFIX_ACTION + "FLOATING_NAVIGATION")) {
                    intent.setAction(FTD.ACTION_FLOATING_ACTION);
                }
                if (Strings.isNullOrEmpty(record.intentUri)) {
                    info = new ActionInfo(this, intent, ActionInfo.TYPE_NONE);
                } else if (FTD.isLocalAction(intent)) {
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
                    .putBoolean(getString(R.string.key_pressure_enable),
                            prefs.getBoolean("key_pressure_enabled", false))
                    .putBoolean(getString(R.string.key_size_enable),
                            prefs.getBoolean("key_size_enabled", false))
                    .putBoolean(getString(R.string.key_floating_action_enable),
                            prefs.getBoolean("key_floating_action_enabled", false))
                    .apply();
        }
    }

    public static void updateService(Context context, boolean pressure, boolean size,
                                     boolean floatingAction, boolean showNotification) {
        Intent em = new Intent(context, EmergencyService.class);
        Intent fa = new Intent(context, FloatingActionService.class);
        boolean ftdEnable = pressure || size;
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
}
