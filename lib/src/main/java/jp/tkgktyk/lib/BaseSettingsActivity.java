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

package jp.tkgktyk.lib;

import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.google.common.base.Strings;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by tkgktyk on 2015/04/27.
 */
public abstract class BaseSettingsActivity extends AppCompatActivity {

    private Toolbar mToolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        mToolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(mToolbar);

        getFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                updateTitle();
            }
        });
        if (savedInstanceState == null) {
            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, newRootFragment())
                    .commit();
        } else {
            updateTitle();
        }
    }

    private void updateTitle() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            FragmentManager fm = getFragmentManager();
            BaseFragment fragment = (BaseFragment) fm.findFragmentById(R.id.container);
            actionBar.setTitle(fragment.getTitle());
            actionBar.setDisplayHomeAsUpEnabled(fm.getBackStackEntryCount() > 0);
        }
    }

    protected void changeFragment(BaseFragment fragment) {
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        } else {
            super.onBackPressed();
        }
    }

    protected abstract BaseFragment newRootFragment();

    public static abstract class BaseFragment extends PreferenceFragment {

        protected abstract String getTitle();

        protected Preference findPreference(@StringRes int id) {
            return findPreference(getString(id));
        }

        protected void changeScreen(@StringRes int id, final Class<?> cls) {
            changeScreen(id, cls, null);
        }

        protected void changeScreen(@StringRes int id, final Class<?> cls,
                                    @Nullable final Preference.OnPreferenceClickListener extraListener) {
            Preference pref = findPreference(id);
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (extraListener == null || extraListener.onPreferenceClick(preference)) {
                        try {
                            ((BaseSettingsActivity) getActivity())
                                    .changeFragment((BaseFragment) cls.getConstructor().newInstance());
                        } catch (java.lang.InstantiationException e) {
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        } catch (NoSuchMethodException e) {
                            e.printStackTrace();
                        }
                    }
                    return true;
                }
            });
        }

        protected void showListSummary(@StringRes int id) {
            showListSummary(id, null);
        }

        protected void showListSummary(@StringRes int id,
                                       @Nullable final Preference.OnPreferenceChangeListener extraListener) {
            ListPreference list = (ListPreference) findPreference(id);
            list.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setListSummary((ListPreference) preference, (String) newValue);
                    if (extraListener != null) {
                        return extraListener.onPreferenceChange(preference, newValue);
                    }
                    return true;
                }
            });
            // pre-perform
            list.getOnPreferenceChangeListener().onPreferenceChange(list, list.getValue());
        }

        private void setListSummary(ListPreference pref, String value) {
            int index = pref.findIndexOfValue(value);
            CharSequence entry;
            if (index != -1) {
                entry = pref.getEntries()[index];
            } else {
                entry = getString(R.string.not_selected);
            }
            pref.setSummary(getString(R.string.current_s1, entry));
        }

        protected void showTextSummary(@StringRes int id) {
            showTextSummary(id, "", null);
        }

        protected void showTextSummary(@StringRes int id, OnTextChangeListener listener) {
            showTextSummary(id, "", listener);
        }

        protected void showTextSummary(@StringRes int id, @StringRes int suffix) {
            showTextSummary(id, getString(suffix), null);
        }

        protected void showTextSummary(@StringRes int id, @StringRes int suffix,
                                       OnTextChangeListener listener) {
            showTextSummary(id, getString(suffix), listener);
        }

        private void showTextSummary(@StringRes int id, @Nullable final String suffix,
                                     final OnTextChangeListener listener) {
            EditTextPreference et = (EditTextPreference) findPreference(id);
            et.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String value = (String) newValue;
                    if (!Strings.isNullOrEmpty(suffix)) {
                        value += suffix;
                    }
                    preference.setSummary(getString(R.string.current_s1, value));
                    return listener == null ||
                            listener.onChange((EditTextPreference) preference, value);
                }
            });
            et.getOnPreferenceChangeListener().onPreferenceChange(et, et.getText());
        }

        protected interface OnTextChangeListener {
            boolean onChange(EditTextPreference edit, String text);
        }

        protected void setUpSwitch(@StringRes int id, final Preference.OnPreferenceChangeListener listener) {
            SwitchPreference sw = (SwitchPreference) findPreference(id);
            sw.setOnPreferenceChangeListener(listener);
        }

        protected void openActivity(@StringRes int id, final Class<?> cls) {
            openActivity(id, cls, null);
        }

        protected void openActivity(@StringRes int id, final Class<?> cls, final ExtraPutter putter) {
            Preference pref = findPreference(id);
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent activity = new Intent(preference.getContext(), cls);
                    if (putter != null) {
                        putter.putExtras(preference, activity);
                    }
                    startActivity(activity);
                    return true;
                }
            });
        }

        protected void openActivityForResult(@StringRes int id, final Class<?> cls,
                                             int requestCode) {
            openActivityForResult(id, cls, requestCode, null);
        }

        protected void openActivityForResult(@StringRes int id, final Class<?> cls,
                                             final int requestCode, final ExtraPutter putter) {
            Preference pref = findPreference(id);
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent activity = new Intent(preference.getContext(), cls);
                    if (putter != null) {
                        putter.putExtras(preference, activity);
                    }
                    startActivityForResult(activity, requestCode);
                    return true;
                }
            });
        }

        protected interface ExtraPutter {
            void putExtras(Preference preference, Intent activityIntent);
        }
    }
}
