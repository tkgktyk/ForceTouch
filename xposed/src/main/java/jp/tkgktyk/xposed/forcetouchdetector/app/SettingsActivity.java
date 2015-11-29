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

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
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

    private static final int REQUEST_OVERLAY_PERMISSION = 1;

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

    @Override
    protected void onStart() {
        super.onStart();

        if (!canDrawOverlays()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        }
    }

    public boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return Settings.canDrawOverlays(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_OVERLAY_PERMISSION:
                if (!canDrawOverlays()) {
                    finish();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
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
            showListSummary(R.string.key_detector_method, new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    MyApp.setMethod((String) newValue);
                    return true;
                }
            });
            changeScreen(R.string.key_header_floating_action, FloatingActionSettingsFragment.class);
            changeScreen(R.string.key_header_force_touch, ForceTouchSettingsFragment.class);
            changeScreen(R.string.key_header_knuckle_touch, KnuckleTouchSettingsFragment.class);
            changeScreen(R.string.key_header_wiggle_touch, WiggleTouchSettingsFragment.class);
            changeScreen(R.string.key_header_scratch_touch, ScratchTouchSettingsFragment.class);

            updateState(R.string.key_header_floating_action, R.string.key_floating_action_enable);
            updateState(R.string.key_header_force_touch, R.string.key_force_touch_enable);
            updateState(R.string.key_header_knuckle_touch, R.string.key_knuckle_touch_enable);
            updateState(R.string.key_header_wiggle_touch, R.string.key_wiggle_touch_enable);
            updateState(R.string.key_header_scratch_touch, R.string.key_scratch_touch_enable);

            // Information
            Preference about = findPreference(R.string.key_about);
            about.setSummary(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
            if (MyApp.isDonated(getActivity())) {
                ((PreferenceCategory) findPreference(R.string.key_information))
                        .removePreference(findPreference(R.string.key_donate));
            } else {
                findPreference(R.string.key_header_scratch_touch)
                        .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(Preference preference) {
                                MyApp.showToast(R.string.message_unlock);
                                return false;
                            }
                        });
            }
        }

        @Override
        protected void onSettingsChanged(SharedPreferences sharedPreferences, String key) {
            if (Objects.equal(key, getString(R.string.key_show_notification))) {
                MyApp.updateService(getActivity(), sharedPreferences);
            } else if (Objects.equal(key, getString(R.string.key_floating_action_enable))) {
                updateState(R.string.key_header_floating_action, key);
//                MyApp.updateService(getActivity(), sharedPreferences);
            } else if (Objects.equal(key, getString(R.string.key_force_touch_enable))) {
                updateState(R.string.key_header_force_touch, key);
                MyApp.updateService(getActivity(), sharedPreferences);
            } else if (Objects.equal(key, getString(R.string.key_knuckle_touch_enable))) {
                updateState(R.string.key_header_knuckle_touch, key);
                MyApp.updateService(getActivity(), sharedPreferences);
            } else if (Objects.equal(key, getString(R.string.key_wiggle_touch_enable))) {
                updateState(R.string.key_header_wiggle_touch, key);
                MyApp.updateService(getActivity(), sharedPreferences);
            } else if (Objects.equal(key, getString(R.string.key_force_touch_screen_enable))) {
                MyApp.updateService(getActivity(), sharedPreferences);
            } else if (Objects.equal(key, getString(R.string.key_scratch_touch_enable))) {
                updateState(R.string.key_header_scratch_touch, key);
                MyApp.updateService(getActivity(), sharedPreferences);
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
            showTextSummary(R.string.key_detection_sensitivity);
            showTextSummary(R.string.key_detection_window, R.string.unit_millisecond);
            showTextSummary(R.string.key_extra_long_press_timeout, R.string.unit_millisecond);
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
            showListSummary(R.string.key_ripple_color, new Preference.OnPreferenceChangeListener() {
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
            setUpSwitch(R.string.key_hide_app_icon, new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Context context = preference.getContext();
                    ComponentName alias = new ComponentName(context,
                            SettingsActivity.class.getName() + ".Alias");
                    PackageManager pm = context.getPackageManager();
                    pm.setComponentEnabledSetting(alias,
                            (Boolean) newValue ?
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED :
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);
                    return true;
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
                        MyApp.showToast(R.string.saved);
                    }
                    break;
                default:
                    super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    public static class FloatingActionSettingsFragment extends XposedFragment {

        @Override
        protected String getTitle() {
            return getString(R.string.header_floating_action);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_floating_action_settings);

            openActivity(R.string.key_floating_action_list, FloatingActionActivity.class);
            showListSummary(R.string.key_floating_action_button_color, new Preference.OnPreferenceChangeListener() {
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
            showListSummary(R.string.key_floating_action_background_color, new Preference.OnPreferenceChangeListener() {
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
            showTextSummary(R.string.key_floating_action_background_alpha);
            showTextSummary(R.string.key_floating_action_timeout, R.string.unit_millisecond);
            setUpSwitch(R.string.key_floating_action_recents, new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if ((Boolean) newValue &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            // some manufacturer doesn't have this activity
                        }
                    }
                    return true;
                }
            });
        }

        @Override
        protected void onSettingsChanged(SharedPreferences sharedPreferences, String key) {
            super.onSettingsChanged(sharedPreferences, key);
            MyApp.updateService(getActivity(), sharedPreferences);
        }
    }

    public static abstract class SettingsFragment extends XposedFragment {

        private static final int REQUEST_ACTION = 1;

        private String mPrefKey;

        public SettingsFragment() {
        }

        protected void pickAction(@StringRes int id) {
            pickAction(id, true);
        }

        protected void pickActionForLongPress(@StringRes int id) {
            pickAction(id, false);
        }

        private void pickAction(@StringRes int id, final boolean touchable) {
            openActivityForResult(id, ActionPickerActivity.class, REQUEST_ACTION, new ExtraPutter() {
                @Override
                public void putExtras(Preference preference, Intent activityIntent) {
                    mPrefKey = preference.getKey();
                    ActionPickerActivity.putExtras(activityIntent, preference.getTitle(), touchable);
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

    public static class ForceTouchSettingsFragment extends SettingsFragment {

        public ForceTouchSettingsFragment() {
        }

        @Override
        protected String getTitle() {
            return getString(R.string.header_force_touch);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_force_touch_settings);

            // Setting
            openActivity(R.string.key_force_touch_threshold, ThresholdActivity.ForceTouch.class);
            // Action
            pickAction(R.string.key_force_touch_action_tap);
            pickAction(R.string.key_force_touch_action_double_tap);
            pickActionForLongPress(R.string.key_force_touch_action_long_press);
            pickAction(R.string.key_force_touch_action_flick_left);
            pickAction(R.string.key_force_touch_action_flick_right);
            pickAction(R.string.key_force_touch_action_flick_up);
            pickAction(R.string.key_force_touch_action_flick_down);
        }
    }

    public static class KnuckleTouchSettingsFragment extends SettingsFragment {

        public KnuckleTouchSettingsFragment() {
        }

        @Override
        protected String getTitle() {
            return getString(R.string.header_knuckle_touch);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_knuckle_touch_settings);

            // Setting
            openActivity(R.string.key_knuckle_touch_threshold, ThresholdActivity.KnuckleTouch.class);
            // Action
            pickAction(R.string.key_knuckle_touch_action_tap);
            pickActionForLongPress(R.string.key_knuckle_touch_action_long_press);
        }
    }

    public static class WiggleTouchSettingsFragment extends SettingsFragment {

        public WiggleTouchSettingsFragment() {
        }

        @Override
        protected String getTitle() {
            return getString(R.string.header_wiggle_touch);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_wiggle_touch_settings);

            // Setting
            showTextSummary(R.string.key_wiggle_touch_magnification);
            // Action
            pickAction(R.string.key_wiggle_touch_action_tap);
            pickActionForLongPress(R.string.key_wiggle_touch_action_long_press);
        }
    }

    public static class ScratchTouchSettingsFragment extends SettingsFragment {

        public ScratchTouchSettingsFragment() {
        }

        @Override
        protected String getTitle() {
            return getString(R.string.header_scratch_touch);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_scratch_touch_settings);

            // Setting
            showTextSummary(R.string.key_scratch_touch_magnification);
            // Action
            pickActionForLongPress(R.string.key_scratch_touch_action_long_press);
        }
    }
}
