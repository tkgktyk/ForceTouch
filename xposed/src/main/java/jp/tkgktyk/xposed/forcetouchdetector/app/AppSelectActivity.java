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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ListFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import butterknife.ButterKnife;
import butterknife.InjectView;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.IconCache;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public class AppSelectActivity extends AppCompatActivity {

    private static String EXTRA_TITLE = FTD.PREFIX_EXTRA + "TITLE";
    public static String EXTRA_SELECTED_HASH_SET = FTD.PREFIX_EXTRA + "SELECTED_HASH_SET";

    public static void putExtras(Intent intent, CharSequence title, Set<String> selectedApps) {
        intent.putExtra(EXTRA_TITLE, title);
        HashSet<String> selectedSet;
        if (selectedApps instanceof HashSet) {
            selectedSet = (HashSet<String>) selectedApps;
        } else {
            selectedSet = Sets.newHashSet();
            for (String app : selectedApps) {
                selectedSet.add(app);
            }
        }
        intent.putExtra(EXTRA_SELECTED_HASH_SET, selectedSet);
    }

    @InjectView(R.id.toolbar)
    Toolbar mToolbar;
    @InjectView(R.id.only_selected_check)
    CheckBox mOnlySelectedCheck;

    private AppSelectFragment mAppSelectFragment;
    private HashSet<String> mSelectedApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_select);
        ButterKnife.inject(this);

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getIntent().getStringExtra(EXTRA_TITLE));
        mSelectedApps = (HashSet<String>) getIntent().getSerializableExtra(EXTRA_SELECTED_HASH_SET);

        if (savedInstanceState == null) {
            mAppSelectFragment = new AppSelectFragment();
            mAppSelectFragment.setSelectedSet(mSelectedApps);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, mAppSelectFragment)
                    .commit();
        } else {
            mAppSelectFragment = (AppSelectFragment) getSupportFragmentManager().findFragmentById(R.id.container);
        }

        mOnlySelectedCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                mAppSelectFragment.setShowOnlySelected(isChecked);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_floating_action, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.action_floating_action:
                FloatingAction.show(this);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mAppSelectFragment.isChanged()) {
            Intent intent = new Intent();
            intent.putExtra(EXTRA_SELECTED_HASH_SET, mSelectedApps);
            setResult(RESULT_OK, intent);
        }
        super.onBackPressed();
    }

    public static class AppSelectFragment extends ListFragment {

        private static final int MSG_ICON_CACHED = 1;

        private boolean mShowOnlySelected;
        private boolean mIsChanged = false;
        private HashSet<String> mSelectedApps;

        private IconCache mIconCache;
        private ArrayList<Entry> mEntryList;

        private BroadcastReceiver mIconCachedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mHandler.removeMessages(MSG_ICON_CACHED);
                mHandler.sendEmptyMessage(MSG_ICON_CACHED);
            }
        };

        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Adapter adapter = (Adapter) getListAdapter();
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        };

        /**
         * Mandatory empty constructor for the fragment manager to instantiate the
         * fragment (e.g. upon screen orientation changes).
         */
        public AppSelectFragment() {
        }

        public void setSelectedSet(HashSet<String> selectedApps) {
            mSelectedApps = selectedApps;
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            if (mIconCache == null) {
                mIconCache = new IconCache(view.getContext());
            }

            mEntryList = Lists.newArrayList();
            // get installed application's info
            PackageManager pm = view.getContext().getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            Collections.sort(apps, new ApplicationInfo.DisplayNameComparator(pm));
            for (ApplicationInfo info : apps) {
                String appName = (String) pm.getApplicationLabel(info);
                String packageName = info.packageName;
                mEntryList.add(new Entry(appName, packageName, mSelectedApps.contains(packageName)));
            }

            updateListShown();
        }

        private void updateListShown() {
            List<Entry> entries;
            if (!mShowOnlySelected) {
                entries = mEntryList;
            } else {
                List<Entry> filtered = Lists.newArrayList();
                for (Entry entry : mEntryList) {
                    if (entry.selected) {
                        filtered.add(entry);
                    }
                }
                entries = filtered;
            }
            setListAdapter(new Adapter(getActivity(), entries));
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            Adapter adapter = (Adapter) getListAdapter();
            Entry entry = adapter.getItem(position);
            entry.selected = !entry.selected;
            if (entry.selected) {
                mSelectedApps.add(entry.packageName);
            } else {
                mSelectedApps.remove(entry.packageName);
            }
            adapter.notifyDataSetChanged();

            mIsChanged = true;

            MyApp.logD(entry.packageName);
        }

        public void setShowOnlySelected(boolean only) {
            if (only != mShowOnlySelected) {
                mShowOnlySelected = only;
                updateListShown();
            }
        }

        @Override
        public void onResume() {
            super.onResume();

            mHandler.removeMessages(MSG_ICON_CACHED);
            mHandler.sendEmptyMessage(MSG_ICON_CACHED);

            IntentFilter filter = new IntentFilter();
            filter.addAction(IconCache.LOCAL_ACTION_ICON_CACHED);
            LocalBroadcastManager.getInstance(getActivity())
                    .registerReceiver(mIconCachedReceiver, filter);
        }

        @Override
        public void onPause() {
            super.onPause();
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mIconCachedReceiver);
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();

            mIconCache.evict();
        }

        public boolean isChanged() {
            return mIsChanged;
        }

        public static class Entry {
            public final String appName;
            public final String packageName;

            public boolean selected;

            public Entry(String appName, String packageName, boolean selected) {
                this.appName = appName;
                this.packageName = packageName;
                this.selected = selected;
            }
        }

        private class Adapter extends ArrayAdapter<Entry> {
            public Adapter(Context context, List<Entry> entries) {
                super(context, 0, entries);
            }

            @Override
            public View getView(int position, View view, ViewGroup parent) {
                if (view == null) {
                    LayoutInflater inflater = LayoutInflater.from(getContext());
                    view = inflater.inflate(R.layout.view_selectable_app, parent, false);
                    ViewHolder holder = new ViewHolder();
                    holder.icon = (ImageView) view.findViewById(R.id.icon);
                    holder.appName = (TextView) view.findViewById(R.id.app_name);
                    holder.packageName = (TextView) view.findViewById(R.id.package_name);
                    holder.checkbox = (CheckBox) view.findViewById(R.id.checkbox);
                    view.setTag(holder);
                }
                ViewHolder holder = (ViewHolder) view.getTag();

                Entry entry = getItem(position);
                //
                Bitmap icon = mIconCache.get(entry.packageName);
                if (icon != null && !icon.isRecycled()) {
                    holder.icon.setImageBitmap(icon);
                } else {
                    holder.icon.setImageBitmap(null);
                    mIconCache.loadAsync(getContext(), entry.packageName);
                }
                holder.appName.setText(entry.appName);
                holder.packageName.setText(entry.packageName);
                holder.checkbox.setChecked(entry.selected);

                return view;
            }

            class ViewHolder {
                ImageView icon;
                TextView appName;
                TextView packageName;
                CheckBox checkbox;
            }
        }
    }
}

