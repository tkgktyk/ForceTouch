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
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.ListFragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.google.common.collect.Lists;

import java.util.ArrayList;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.ModActivity;
import jp.tkgktyk.xposed.forcetouchdetector.R;

/**
 * Created by tkgktyk on 2015/06/07.
 */
public class PressureThresholdActivity extends AppCompatActivity {
    @InjectView(R.id.toolbar)
    Toolbar mToolbar;
    @InjectView(R.id.pressure_peeper)
    PressurePeeper mPressurePeeper;
    @InjectView(R.id.tab_layout)
    TabLayout mTabLayout;
    @InjectView(R.id.view_pager)
    ViewPager mViewPager;
    @InjectView(R.id.current_pressure)
    TextView mCurrentPressureText;
    @InjectView(R.id.max_pressure)
    TextView mMaxPressureText;
    @InjectView(R.id.force_touch)
    TextView mForceTouchText;
    @InjectView(R.id.reset_button)
    Button mResetButton;
    @InjectView(R.id.pressure_threshold)
    EditText mPressureThreshold;

    private float mMaxPressure;

    @OnClick(R.id.reset_button)
    void onReset(Button button) {
        mMaxPressure = 0.0f;
        resetPressureText();
    }

    private void resetPressureText() {
        mCurrentPressureText.setText(getString(R.string.pressure));
        mMaxPressureText.setText(getString(R.string.pressure_max));
        mForceTouchText.setText(getString(R.string.pressure_force_touch));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pressure_threshold);
        ButterKnife.inject(this);
        setSupportActionBar(mToolbar);

        mPressurePeeper.setOnPressureUpdatedListener(new PressurePeeper.OnPressureUpdatedListener() {
            @Override
            public void onPressureUpdated(float pressure, boolean forceTouch) {
                if (pressure > mMaxPressure) {
                    mMaxPressure = pressure;
                }
                mCurrentPressureText.setText(String.format("%.4f", pressure));
                mMaxPressureText.setText(String.format("%.4f", mMaxPressure));
                if (forceTouch) {
                    mForceTouchText.setText(String.format("%.4f", pressure));
                }
            }
        });
        resetPressureText();

        mViewPager.setAdapter(new PagerAdapter(getSupportFragmentManager()));
        mTabLayout.setupWithViewPager(mViewPager);

        String key = getString(R.string.key_pressure_threshold);
        mPressureThreshold.setText(FTD.getSharedPreferences(this)
                .getString(key, ModActivity.ForceTouchDetector.DEFAULT_PRESSURE_THRESHOLD));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        String key = getString(R.string.key_pressure_threshold);
        FTD.getSharedPreferences(this)
                .edit()
                .putString(key, mPressureThreshold.getText().toString())
                .apply();
    }

    public class PagerAdapter extends FragmentPagerAdapter {

        public PagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return TouchPracticeFragment.newInstance();
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getString(R.string.tab_title_d1, position);
        }
    }

    public static class TouchPracticeFragment extends ListFragment
    implements ConfirmDialogFragment.OnConfirmedListener {
        private static final int REQUEST_DESCRIPTION = 1;

        public static TouchPracticeFragment newInstance() {
            return new TouchPracticeFragment();
        }

        private static int[] TEXT_ID_LIST = {
                R.string.practice_description,
                R.string.practice_tap,
                R.string.practice_double_tap,
                R.string.practice_long_press,
                R.string.practice_scroll,
                R.string.practice_swipe,
                R.string.practice_flick
        };

        private static int EXTRA_ITEM_COUNT = 30;

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            view.setBackgroundResource(R.color.primary_light);

            ArrayList<String> texts = Lists.newArrayList();
            for (int id : TEXT_ID_LIST) {
                texts.add(getString(id));
            }
            for (int i = 0; i < EXTRA_ITEM_COUNT; ++i) {
                texts.add("" + i);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(view.getContext(),
                    android.R.layout.simple_list_item_multiple_choice, texts);
            setListAdapter(adapter);

            getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            if (position != 0) {
                return;
            }
            ConfirmDialogFragment
                    .newInstance(getString(R.string.help), (String) getListAdapter().getItem(0),
                            getString(R.string.open_readme), getString(R.string.back),
                            null, this, REQUEST_DESCRIPTION)
                    .show(getFragmentManager(), "description");
        }

        @Override
        public void onConfirmed(int requestCode, Bundle extras) {
            switch (requestCode) {
                case REQUEST_DESCRIPTION:
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tkgktyk"));
                    startActivity(intent);
                    break;
            }
        }
    }
}
