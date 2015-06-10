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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;

import com.google.common.base.Objects;

import java.net.URISyntaxException;

import jp.tkgktyk.lib.BaseSettingsActivity;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;
import jp.tkgktyk.xposed.forcetouchdetector.app.picker.ActionPickerActivity;
import jp.tkgktyk.xposed.forcetouchdetector.app.picker.BasePickerActivity;


public class SettingsActivity extends BaseSettingsActivity {
    public static final String ACTION_TURN_OFF = "turn_off";

    @Override
    protected BaseFragment newFragment() {
        return new SettingsFragment();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent() != null &&
                Objects.equal(getIntent().getAction(), ACTION_TURN_OFF)) {
            FTD.getSharedPreferences(this)
                    .edit()
                    .putBoolean(getString(R.string.key_enabled), false)
                    .apply();
        }
    }

    public static class SettingsFragment extends BaseFragment {
        private final SharedPreferences.OnSharedPreferenceChangeListener mChangeListener
                = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                FTD.sendSettingsChanged(getActivity(), sharedPreferences);
            }
        };

        private static final int REQUEST_ACTION = 1;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
            getPreferenceManager().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(mChangeListener);
            addPreferencesFromResource(R.xml.pref_settings);

            // Setting
            setUpSwitch(R.string.key_enabled, new OnSwitchChangedListener() {
                @Override
                public void onChanged(SwitchPreference sw, boolean enabled) {
                    EmergencyService.startStop(sw.getContext(), enabled);
                }
            });
            openActivity(R.string.key_pressure_threshold, PressureThresholdActivity.class);
            showTextSummary(R.string.key_detection_area, getString(R.string.unit_detection_area));
            // Action
            pickAction(R.string.key_action_tap);
            pickAction(R.string.key_action_double_tap);
            pickAction(R.string.key_action_long_press);
            pickAction(R.string.key_action_flick_left);
            pickAction(R.string.key_action_flick_right);
            pickAction(R.string.key_action_flick_up);
            pickAction(R.string.key_action_flick_down);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            getPreferenceManager().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(mChangeListener);
        }

        private void pickAction(@StringRes int id) {
            String key = getString(id);
            final Preference pref = findPreference(key);
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(preference.getContext(), ActionPickerActivity.class);
                    ActionPickerActivity.putExtras(intent, pref.getTitle(), pref.getKey());
                    startActivityForResult(intent, REQUEST_ACTION);
                    return true;
                }
            });
            updateActionSummary(pref, pref.getSharedPreferences().getString(key, ""));
        }

        private void updateActionSummary(Preference preference, String uri) {
            updateActionSummary(preference, getIntent(uri));
        }

        private void updateActionSummary(Preference preference, Intent intent) {
            Context context = preference.getContext();
            String name;
            if (intent == null) {
                name = getString(R.string.none);
            } else if (FTD.isLocalAction(intent)) {
                name = FTD.getActionName(context, intent.getAction());
            } else if (intent.getComponent() == null) {
                name = getString(R.string.none);
            } else {
                PackageManager pm = context.getPackageManager();
                ApplicationInfo ai = null;
                try {
                    ai = pm.getApplicationInfo(intent.getComponent().getPackageName(), 0);
                } catch (PackageManager.NameNotFoundException e) {
                }
                if (ai != null) {
                    name = pm.getApplicationLabel(ai).toString();
                } else {
                    name = getString(R.string.not_found);
                }
            }
            preference.setSummary(name);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            switch (requestCode) {
                case REQUEST_ACTION:
                    if (resultCode == RESULT_OK) {
                        String key = data.getStringExtra(BasePickerActivity.EXTRA_KEY);
                        Intent intent = data.getParcelableExtra(BasePickerActivity.EXTRA_INTENT);
                        String uri = getUri(intent);
                        MyApp.logD("picked intent: " + intent);
                        MyApp.logD("picked uri: " + uri);
                        // save
                        Preference pref = findPreference(key);
                        pref.getSharedPreferences()
                                .edit()
                                .putString(key, uri)
                                .apply();
                        updateActionSummary(pref, intent);
                    }
                    break;
                default:
                    super.onActivityResult(requestCode, resultCode, data);
            }
        }

        private String getUri(Intent intent) {
            return intent.toUri(0);
        }

        private Intent getIntent(String uri) {
            try {
                return Intent.parseUri(uri, 0);
            } catch (URISyntaxException e) {
                MyApp.logE(e);
            }
            return null;
        }
    }
}
