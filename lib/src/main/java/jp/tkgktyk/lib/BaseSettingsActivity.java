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
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

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
        BaseFragment fragment = (BaseFragment) getFragmentManager()
                .findFragmentById(R.id.container);
        mToolbar.setTitle(fragment.getTitle());
    }

    protected void changeFragment(BaseFragment fragment) {
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(fragment.getTag())
                .commit();
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
            Preference pref = findPreference(id);
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
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
            showTextSummary(id, null);
        }

        protected void showTextSummary(@StringRes int id, @StringRes int suffix) {
            showTextSummary(id, getString(suffix));
        }

        protected void showTextSummary(@StringRes int id, @Nullable final String suffix) {
            EditTextPreference et = (EditTextPreference) findPreference(id);
            et.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String value = (String) newValue;
                    if (!Strings.isNullOrEmpty(suffix)) {
                        value += suffix;
                    }
                    preference.setSummary(getString(R.string.current_s1, value));
                    return true;
                }
            });
            et.getOnPreferenceChangeListener().onPreferenceChange(et, et.getText());
        }

        protected void setUpSwitch(@StringRes int id, final OnSwitchChangeListener listener) {
            final SwitchPreference sw = (SwitchPreference) findPreference(id);
            sw.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean enabled = (Boolean) newValue;
                    listener.onChange(sw, enabled);
                    return true;
                }
            });
            sw.getOnPreferenceChangeListener().onPreferenceChange(sw, sw.isChecked());
        }

        protected interface OnSwitchChangeListener {
            void onChange(SwitchPreference sw, boolean enabled);
        }

        protected void openActivity(@StringRes int id, final Class<?> cls) {
            openActivity(id, cls, null);
        }

        protected void openActivity(@StringRes int id, final Class<?> cls, final ExtendsPutter putter) {
            Preference pref = findPreference(id);
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent activity = new Intent(preference.getContext(), cls);
                    if (putter != null) {
                        putter.putExtends(activity);
                    }
                    startActivity(activity);
                    return true;
                }
            });
        }

        protected interface ExtendsPutter {
            void putExtends(Intent activityIntent);
        }
    }
}
