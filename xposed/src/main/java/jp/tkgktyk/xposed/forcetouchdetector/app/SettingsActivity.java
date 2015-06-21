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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.support.annotation.StringRes;

import com.google.common.base.Objects;

import java.util.Collections;
import java.util.Set;

import jp.tkgktyk.lib.BaseSettingsActivity;
import jp.tkgktyk.xposed.forcetouchdetector.BuildConfig;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionIntent;


public class SettingsActivity extends BaseSettingsActivity {

    @Override
    protected BaseFragment newRootFragment() {
        return new HeaderFragment();
    }

    public static abstract class XposedFragment extends BaseFragment {
        private final SharedPreferences.OnSharedPreferenceChangeListener mChangeListener
                = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                FTD.sendSettingsChanged(getActivity(), sharedPreferences);
                onSettingsChanged(sharedPreferences, key);
            }
        };

        public XposedFragment() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
            getPreferenceManager().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(mChangeListener);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            getPreferenceManager().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(mChangeListener);
        }

        protected void onSettingsChanged(SharedPreferences sharedPreferences, String key) {
        }
    }

    public static class HeaderFragment extends XposedFragment {
        public HeaderFragment() {
        }

        @Override
        protected String getTitle() {
            return getString(R.string.app_name);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_header_settings);

            changeScreen(R.string.key_header_general, GeneralSettingsFragment.class);
            changeScreen(R.string.key_header_pressure, PressureSettingsFragment.class);
            changeScreen(R.string.key_header_size, SizeSettingsFragment.class);

            updateState(R.string.key_header_pressure, R.string.key_pressure_enabled);
            updateState(R.string.key_header_size, R.string.key_size_enabled);

            // Information
            Preference about = findPreference(R.string.key_about);
            about.setSummary(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
        }

        @Override
        protected void onSettingsChanged(SharedPreferences sharedPreferences, String key) {
            if (Objects.equal(key, getString(R.string.key_pressure_enabled))) {
                updateState(R.string.key_header_pressure, key);
            } else if (Objects.equal(key, getString(R.string.key_size_enabled))) {
                updateState(R.string.key_header_size, key);
            }
        }

        private void updateState(@StringRes int targetKey, @StringRes int valueKey) {
            updateState(targetKey, getString(valueKey));
        }

        private void updateState(@StringRes int targetKey, String valueKey) {
            Preference pref = findPreference(targetKey);
            if (pref.getSharedPreferences().getBoolean(valueKey, false)) {
                pref.setSummary(getString(R.string.state_running));
            } else {
                pref.setSummary(getString(R.string.state_disabled));
            }
        }
    }

    public static class GeneralSettingsFragment extends XposedFragment {
        private static final int REQUEST_BLACKLIST = 1;

        private String mPrefKey;

        public GeneralSettingsFragment() {
        }

        @Override
        protected String getTitle() {
            return getString(R.string.header_general);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general_settings);

            showTextSummary(R.string.key_detection_area, R.string.unit_detection_area);
            openActivityForResult(R.string.key_blacklist, AppSelectActivity.class,
                    REQUEST_BLACKLIST, new ExtraPutter() {
                        @Override
                        public void putExtras(Preference preference, Intent activityIntent) {
                            mPrefKey = preference.getKey();
                            Set<String> blacklist = preference.getSharedPreferences()
                                    .getStringSet(mPrefKey, Collections.<String>emptySet());
                            AppSelectActivity.putExtras(activityIntent,
                                    preference.getTitle(), blacklist);
                        }
                    });
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            switch (requestCode) {
                case REQUEST_BLACKLIST:
                    if (resultCode == RESULT_OK) {
                        Set<String> blacklist = (Set<String>) data.getSerializableExtra(
                                AppSelectActivity.EXTRA_SELECTED_HASH_SET);
                        getPreferenceManager().getSharedPreferences()
                                .edit()
                                .putStringSet(mPrefKey, blacklist)
                                .apply();
                    }
                    break;
                default:
                    super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    public static abstract class SettingsFragment extends XposedFragment {

        private static final int REQUEST_ACTION = 1;

        private String mPrefKey;
        private String mPrefNameKey;

        public SettingsFragment() {
        }

        protected void pickAction(@StringRes int id) {
            openActivityForResult(id, ActionPickerActivity.class, REQUEST_ACTION, new ExtraPutter() {
                @Override
                public void putExtras(Preference preference, Intent activityIntent) {
                    mPrefKey = preference.getKey();
                    ActionPickerActivity.putExtras(activityIntent, preference.getTitle());
                }
            });
            Preference pref = findPreference(id);
            pref.setSummary(ActionIntent.getAppName(pref.getContext(),
                    pref.getSharedPreferences().getString(pref.getKey(), "")));
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            switch (requestCode) {
                case REQUEST_ACTION:
                    if (resultCode == RESULT_OK) {
                        Intent intent = data.getParcelableExtra(ActionPickerActivity.EXTRA_INTENT);
                        String uri = ActionIntent.getUri(intent);
                        MyApp.logD("picked intent: " + intent);
                        MyApp.logD("picked uri: " + uri);
                        // save
                        Preference pref = findPreference(mPrefKey);
                        pref.getSharedPreferences()
                                .edit()
                                .putString(mPrefKey, uri)
                                .apply();
                        pref.setSummary(ActionIntent.getAppName(getActivity(), intent));
                    }
                    break;
                default:
                    super.onActivityResult(requestCode, resultCode, data);
            }
        }

    }

    public static class PressureSettingsFragment extends SettingsFragment {

        public PressureSettingsFragment() {
        }

        @Override
        protected String getTitle() {
            return getString(R.string.header_pressure);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_pressure_settings);

            // Setting
            setUpSwitch(R.string.key_pressure_enabled, new OnSwitchChangeListener() {
                @Override
                public void onChange(SwitchPreference sw, boolean enabled) {
                    FTD.Settings settings = new FTD.Settings(sw.getSharedPreferences());
                    EmergencyService.startStop(sw.getContext(), enabled || settings.size.enabled);
                }
            });
            openActivity(R.string.key_pressure_threshold, PressureThresholdActivity.class);
            // Action
            pickAction(R.string.key_pressure_action_tap);
            pickAction(R.string.key_pressure_action_double_tap);
            pickAction(R.string.key_pressure_action_long_press);
            pickAction(R.string.key_pressure_action_flick_left);
            pickAction(R.string.key_pressure_action_flick_right);
            pickAction(R.string.key_pressure_action_flick_up);
            pickAction(R.string.key_pressure_action_flick_down);
        }
    }

    public static class SizeSettingsFragment extends SettingsFragment {

        public SizeSettingsFragment() {
        }

        @Override
        protected String getTitle() {
            return getString(R.string.header_size);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_size_settings);

            // Setting
            setUpSwitch(R.string.key_size_enabled, new OnSwitchChangeListener() {
                @Override
                public void onChange(SwitchPreference sw, boolean enabled) {
                    FTD.Settings settings = new FTD.Settings(sw.getSharedPreferences());
                    EmergencyService.startStop(sw.getContext(), enabled || settings.pressure.enabled);
                }
            });
            openActivity(R.string.key_size_threshold, SizeThresholdActivity.class);
            // Action
            pickAction(R.string.key_size_action_tap);
            pickAction(R.string.key_size_action_double_tap);
            pickAction(R.string.key_size_action_long_press);
            pickAction(R.string.key_size_action_flick_left);
            pickAction(R.string.key_size_action_flick_right);
            pickAction(R.string.key_size_action_flick_up);
            pickAction(R.string.key_size_action_flick_down);
        }
    }
}
