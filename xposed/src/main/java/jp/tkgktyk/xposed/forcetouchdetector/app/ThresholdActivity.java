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
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.LinkedList;

import butterknife.ButterKnife;
import butterknife.InjectView;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.ModForceTouch;
import jp.tkgktyk.xposed.forcetouchdetector.R;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.PressureButton;

/**
 * Created by tkgktyk on 2015/06/07.
 */
public abstract class ThresholdActivity extends AppCompatActivity {

    private static final int MAX_COUNT = 5;
    private static final int AVERAGE_COUNT = 5;

    @InjectView(R.id.toolbar)
    Toolbar mToolbar;
    @InjectView(R.id.max_pressure)
    TextView mMaxPressureText;
    @InjectView(R.id.tap_pressure)
    TextView mTapPressureText;
    @InjectView(R.id.tap_button)
    PressureButton mTapButton;
    @InjectView(R.id.ave_pressure)
    TextView mAvePressureText;
    @InjectView(R.id.force_touch_pressure)
    TextView mForceTouchPressureText;
    @InjectView(R.id.force_touch_button)
    PressureButton mForceTouchButton;
    @InjectView(R.id.pressure_threshold)
    EditText mPressureThreshold;
    @InjectView(R.id.pressure_threshold_charging)
    EditText mPressureThresholdCharging;

    private boolean mIsChanged;

    private final LinkedList<Float> mMaxPressureList = Lists.newLinkedList();
    private final LinkedList<Float> mAvePressureList = Lists.newLinkedList();

    protected abstract int getPressureResource();

    protected abstract String getThresholdKey();

    protected abstract String getThresholdChargingKey();

    protected abstract float getParameter(MotionEvent event);

    private void updateTapPressureText(float pressure) {
        // size limited queue
        mMaxPressureList.add(pressure);
        if (mMaxPressureList.size() > MAX_COUNT) {
            mMaxPressureList.remove();
        }

        mMaxPressureText.setText(getString(R.string.max_f1, getMaxPressure()));
        mTapPressureText.setText(getString(getPressureResource(), pressure));
    }

    private float getMaxPressure() {
        return Collections.max(mMaxPressureList);
    }

    private void updateForceTouchPressureText(float pressure) {
        // size limited queue
        mAvePressureList.add(pressure);
        if (mAvePressureList.size() > AVERAGE_COUNT) {
            mAvePressureList.remove();
        }

        mAvePressureText.setText(getString(R.string.ave_f1, getAvePressure()));
        mForceTouchPressureText.setText(getString(getPressureResource(), pressure));
    }

    private float getAvePressure() {
        float sum = 0;
        for (float v : mAvePressureList) {
            sum += v;
        }
        return sum / mAvePressureList.size();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pressure_threshold);
        ButterKnife.inject(this);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mTapButton.setText(getString(R.string.please_tap_d1, MAX_COUNT));
        mForceTouchButton.setText(getString(R.string.please_force_touch_d1, AVERAGE_COUNT));

        long detectionWindow = Long.parseLong(FTD.getSharedPreferences(this)
                .getString(getString(R.string.key_detection_window), "0"));
        mTapButton.setDetectionWindow(detectionWindow);
        mTapButton.setOnPressedListener(new PressureButton.OnPressedListener() {
            private float mPressure;

            @Override
            public void onStart(MotionEvent event) {
                mPressure = getParameter(event);
            }

            @Override
            public void onUpdate(MotionEvent event) {
//                mPressure = Math.max(mPressure, getParameter(event));
                mPressure = getParameter(event);
            }

            @Override
            public void onStop() {
                updateTapPressureText(mPressure);
            }
        });
        mForceTouchButton.setDetectionWindow(detectionWindow);
        mForceTouchButton.setOnPressedListener(new PressureButton.OnPressedListener() {
            private float mPressure;

            @Override
            public void onStart(MotionEvent event) {
                mPressure = getParameter(event);
            }

            @Override
            public void onUpdate(MotionEvent event) {
                mPressure = Math.max(mPressure, getParameter(event));
            }

            @Override
            public void onStop() {
                updateForceTouchPressureText(mPressure);
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
        mPressureThreshold.setText(prefs.getString(getThresholdKey(),
                ModForceTouch.ForceTouchDetector.DEFAULT_THRESHOLD));
        mPressureThreshold.addTextChangedListener(textWatcher);
        mPressureThresholdCharging.setText(prefs.getString(getThresholdChargingKey(),
                ModForceTouch.ForceTouchDetector.DEFAULT_THRESHOLD));
        mPressureThresholdCharging.addTextChangedListener(textWatcher);
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
                    .putString(getThresholdKey(), mPressureThreshold.getText().toString())
                    .putString(getThresholdChargingKey(), mPressureThresholdCharging.getText().toString())
                    .apply();
            MyApp.showToast(R.string.saved);
        }
    }
}
