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
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.support.annotation.StringRes;
import android.view.Menu;
import android.view.MenuItem;

import com.google.common.base.Objects;
import com.google.common.base.Strings;

import java.util.Collections;
import java.util.Set;

import jp.tkgktyk.lib.BaseSettingsActivity;
import jp.tkgktyk.xposed.forcetouchdetector.BuildConfig;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionInfo;


public class SettingsActivity extends BaseSettingsActivity {

    @Override
    protected BaseFragment newRootFragment() {
        return new HeaderFragment();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_floating_action, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_floating_action:
                FloatingAction.show(this);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
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
            changeScreen(R.string.key_header_floating_action, FloatingActionSettingsFragment.class);

            updateState(R.string.key_header_pressure, R.string.key_pressure_enable);
            updateState(R.string.key_header_size, R.string.key_size_enable);
            updateState(R.string.key_header_floating_action, R.string.key_floating_action_enable);

            // Information
            Preference about = findPreference(R.string.key_about);
            about.setSummary(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
            if (MyApp.isDonated()) {
                ((PreferenceCategory)findPreference(R.string.key_information))
                        .removePreference(findPreference(R.string.key_donate));
            }
        }

        @Override
        protected void onSettingsChanged(SharedPreferences sharedPreferences, String key) {
            if (Objects.equal(key, getString(R.string.key_pressure_enable))) {
                updateState(R.string.key_header_pressure, key);
            } else if (Objects.equal(key, getString(R.string.key_size_enable))) {
                updateState(R.string.key_header_size, key);
            } else if (Objects.equal(key, getString(R.string.key_floating_action_enable))) {
                updateState(R.string.key_header_floating_action, key);
            }
        }

        private void updateState(@StringRes int targetKey, @StringRes int valueKey) {
            updateState(targetKey, getString(valueKey));
        }

        private void updateState(@StringRes int targetKey, String valueKey) {
            Preference pref = findPreference(targetKey);
            if (pref.getSharedPreferences().getBoolean(valueKey, false)) {
                pref.setSummary(getString(R.string.state_enabled));
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

            openActivity(R.string.key_detection_area, AreaActivity.class);
//            showTextSummary(R.string.key_detection_area, R.string.unit_detection_area);
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
            setUpSwitch(R.string.key_show_notification, new OnSwitchChangeListener() {
                @Override
                public void onChange(SwitchPreference sw, boolean enabled, boolean fromUser) {
                    FTD.Settings settings = new FTD.Settings(sw.getContext(),
                            sw.getSharedPreferences());
                    MyApp.updateService(sw.getContext(), settings.pressure.enable,
                            settings.size.enable, settings.floatingActionEnable, enabled);
                }
            });
            showTextSummary(R.string.key_detection_sensitivity);
            showTextSummary(R.string.key_detection_window, R.string.unit_millisecond);
            showTextSummary(R.string.key_extra_long_press_timeout, R.string.unit_millisecond);
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
                        MyApp.showToast(R.string.saved);
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

        public SettingsFragment() {
        }

        protected void pickAction(@StringRes int id) {
            openActivityForResult(id, ActionPickerActivity.class, REQUEST_ACTION, new ExtraPutter() {
                @Override
                public void putExtras(Preference preference, Intent activityIntent) {
                    mPrefKey = preference.getKey();
                    ActionPickerActivity.putExtras(activityIntent, preference.getTitle(), true);
                }
            });
            Preference pref = findPreference(id);
            ActionInfo.Record record = ActionInfo.Record
                    .fromPreference(pref.getSharedPreferences().getString(pref.getKey(), ""));
            updateActionSummary(pref, record);
        }

        private void updateActionSummary(Preference pref, ActionInfo.Record record) {
            ActionInfo info = new ActionInfo(record);
            // name
            if (Strings.isNullOrEmpty(info.getName())) {
                if (record.type == ActionInfo.TYPE_NONE) {
                    pref.setSummary(getString(R.string.none));
                } else {
                    pref.setSummary(getString(R.string.not_found));
                }
            } else {
                pref.setSummary(info.getName());
            }
            // icon
            pref.setIcon(info.newIconDrawable(pref.getContext()));
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            switch (requestCode) {
                case REQUEST_ACTION:
                    if (resultCode == RESULT_OK) {
                        ActionInfo.Record record = (ActionInfo.Record) data
                                .getSerializableExtra(ActionPickerActivity.EXTRA_ACTION_RECORD);
                        MyApp.logD("picked intent: " + record.intentUri);
                        // save
                        Preference pref = findPreference(mPrefKey);
                        pref.getSharedPreferences()
                                .edit()
                                .putString(mPrefKey, record.toStringForPreference())
                                .apply();
                        updateActionSummary(pref, record);
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
            setUpSwitch(R.string.key_pressure_enable, new OnSwitchChangeListener() {
                @Override
                public void onChange(SwitchPreference sw, boolean enabled, boolean fromUser) {
                    FTD.Settings settings = new FTD.Settings(sw.getContext(),
                            sw.getSharedPreferences());
                    MyApp.updateService(sw.getContext(), enabled, settings.size.enable,
                            settings.floatingActionEnable, settings.showNotification);
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
            setUpSwitch(R.string.key_size_enable, new OnSwitchChangeListener() {
                @Override
                public void onChange(SwitchPreference sw, boolean enabled, boolean fromUser) {
                    FTD.Settings settings = new FTD.Settings(sw.getContext(),
                            sw.getSharedPreferences());
                    MyApp.updateService(sw.getContext(), settings.pressure.enable, enabled,
                            settings.floatingActionEnable, settings.showNotification);
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

    public static class FloatingActionSettingsFragment extends XposedFragment {
        private final SharedPreferences.OnSharedPreferenceChangeListener mChangeListener
                = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                Context context = getActivity();
                FTD.Settings settings = new FTD.Settings(context, sharedPreferences);
                MyApp.updateService(context, false, false, false, false);
                MyApp.updateService(context, settings.pressure.enable,
                        settings.size.enable, settings.floatingActionEnable,
                        settings.showNotification);
            }
        };

        @Override
        protected String getTitle() {
            return getString(R.string.header_floating_action);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_floating_action_settings);
            getPreferenceManager().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(mChangeListener);

            setUpSwitch(R.string.key_floating_action_enable, new OnSwitchChangeListener() {
                @Override
                public void onChange(SwitchPreference sw, boolean enabled, boolean fromUser) {
                    FTD.Settings settings = new FTD.Settings(sw.getContext(),
                            sw.getSharedPreferences());
                    MyApp.updateService(sw.getContext(), settings.pressure.enable,
                            settings.size.enable, enabled, settings.showNotification);
                }
            });
            openActivity(R.string.key_floating_action_list, FloatingActionActivity.class);
            showListSummary(R.string.key_floating_action_color, new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int color = Color.parseColor((String) newValue);
                    GradientDrawable drawable = new GradientDrawable();
                    drawable.setColor(color);
                    int size = getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
                    drawable.setSize(size, size);
                    preference.setIcon(drawable);
                    return true;
                }
            });
            showTextSummary(R.string.key_floating_action_alpha);
            showTextSummary(R.string.key_floating_action_timeout, R.string.unit_millisecond);
            setUpSwitch(R.string.key_floating_action_recents, new OnSwitchChangeListener() {
                @Override
                public void onChange(SwitchPreference sw, boolean enabled, boolean fromUser) {
                    if (fromUser && enabled &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                        startActivity(intent);
                    }
                }
            });
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            getPreferenceManager().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(mChangeListener);
        }
    }
}
