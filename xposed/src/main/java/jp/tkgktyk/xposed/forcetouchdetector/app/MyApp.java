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

import android.content.SharedPreferences;

import jp.tkgktyk.lib.BaseApplication;
import jp.tkgktyk.xposed.forcetouchdetector.BuildConfig;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;

/**
 * Created by tkgktyk on 2015/06/06.
 */
public class MyApp extends BaseApplication {

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

    @Override
    protected void onVersionUpdated(MyVersion next, MyVersion old) {
        if (old.isOlderThan("0.2.0")) {
            SharedPreferences prefs = getDefaultSharedPreferences();
            prefs.edit()
                    .putBoolean(getString(R.string.key_pressure_enabled),
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
            SharedPreferences prefs = getDefaultSharedPreferences();
            prefs.edit()
                    .putString(getString(R.string.key_pressure_action_flick_down),
                            prefs.getString("key_action_flick_down", ""))
                    .apply();
        }
    }
}
