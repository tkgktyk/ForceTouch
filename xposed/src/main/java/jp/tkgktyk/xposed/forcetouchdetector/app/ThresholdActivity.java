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
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.LinkedList;

import butterknife.Bind;
import butterknife.ButterKnife;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.ModForceTouch;
import jp.tkgktyk.xposed.forcetouchdetector.R;

/**
 * Created by tkgktyk on 2015/06/07.
 */
public abstract class ThresholdActivity extends AppCompatActivity {

    private static final int FILTER_COUNT = 5;

    @Bind(R.id.toolbar)
    Toolbar mToolbar;
    @Bind(R.id.filtered_small)
    TextView mFilteredSmallText;
    @Bind(R.id.raw_small)
    TextView mRawSmallText;
    @Bind(R.id.button_small)
    Button mSmallButton;
    @Bind(R.id.filtered_large)
    TextView mFilteredLargeText;
    @Bind(R.id.raw_large)
    TextView mRawLargeText;
    @Bind(R.id.button_large)
    Button mLargeButton;
    @Bind(R.id.threshold)
    EditText mThresholdEdit;
    @Bind(R.id.threshold_charging)
    EditText mThresholdChargingEdit;

    private boolean mIsChanged;

    protected final LinkedList<Float> mSmallList = Lists.newLinkedList();
    protected final LinkedList<Float> mLargeList = Lists.newLinkedList();

    protected abstract String getThresholdKey();

    protected abstract String getThresholdChargingKey();

    protected abstract void updateSmallText(float parameter);
    protected abstract void updateLargeText(float parameter);

    protected abstract void setButtonText(int filterCount);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_threshold);
        ButterKnife.bind(this);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setButtonText(FILTER_COUNT);

        mSmallButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    float parameter = MyApp.readMethodParameter(event);
                    mSmallList.add(parameter);
                    if (mSmallList.size() > FILTER_COUNT) {
                        mSmallList.remove();
                    }
                    updateSmallText(parameter);
                }
                return false;
            }
        });
        mLargeButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    float parameter = MyApp.readMethodParameter(event);
                    mLargeList.add(parameter);
                    if (mLargeList.size() > FILTER_COUNT) {
                        mLargeList.remove();
                    }
                    updateLargeText(parameter);
                }
                return false;
            }
        });

        SharedPreferences prefs = FTD.getSharedPreferences(this);
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mIsChanged = true;
            }
        };
        mThresholdEdit.setText(prefs.getString(getThresholdKey(),
                ModForceTouch.Detector.DEFAULT_THRESHOLD));
        mThresholdEdit.addTextChangedListener(textWatcher);
        mThresholdChargingEdit.setText(prefs.getString(getThresholdChargingKey(),
                ModForceTouch.Detector.DEFAULT_THRESHOLD));
        mThresholdChargingEdit.addTextChangedListener(textWatcher);
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
    protected void onPause() {
        super.onPause();

        if (mIsChanged) {
            FTD.getSharedPreferences(this)
                    .edit()
                    .putString(getThresholdKey(), mThresholdEdit.getText().toString())
                    .putString(getThresholdChargingKey(), mThresholdChargingEdit.getText().toString())
                    .apply();
            MyApp.showToast(R.string.saved);
        }
    }

    public static class ForceTouch extends ThresholdActivity {

        @Override
        protected String getThresholdKey() {
            return getString(R.string.key_force_touch_threshold);
        }

        @Override
        protected String getThresholdChargingKey() {
            return getString(R.string.key_force_touch_threshold_charging);
        }

        @Override
        protected void updateSmallText(float parameter) {
            mFilteredSmallText.setText(getString(R.string.max_f1, Collections.max(mSmallList)));
            mRawSmallText.setText(getString(R.string.raw_f1, parameter));
        }

        @Override
        protected void updateLargeText(float parameter) {
            mFilteredLargeText.setText(getString(R.string.min_f1, Collections.min(mLargeList)));
            mRawLargeText.setText(getString(R.string.raw_f1, parameter));
        }

        @Override
        protected void setButtonText(int filterCount) {
            mSmallButton.setText(getString(R.string.please_tap_d1, filterCount));
            mLargeButton.setText(getString(R.string.please_force_touch_d1, filterCount));
        }
    }

    public static class KnuckleTouch extends ThresholdActivity {

        @Override
        protected String getThresholdKey() {
            return getString(R.string.key_knuckle_touch_threshold);
        }

        @Override
        protected String getThresholdChargingKey() {
            return getString(R.string.key_knuckle_touch_threshold_charging);
        }

        @Override
        protected void updateSmallText(float parameter) {
            mFilteredSmallText.setText(getString(R.string.max_f1, Collections.max(mSmallList)));
            mRawSmallText.setText(getString(R.string.raw_f1, parameter));
        }

        @Override
        protected void updateLargeText(float parameter) {
            mFilteredLargeText.setText(getString(R.string.min_f1, Collections.min(mLargeList)));
            mRawLargeText.setText(getString(R.string.raw_f1, parameter));
        }

        @Override
        protected void setButtonText(int filterCount) {
            mSmallButton.setText(getString(R.string.please_knuckle_touch_d1, filterCount));
            mLargeButton.setText(getString(R.string.please_tap_d1, filterCount));
        }
    }
}
