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
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.LinkedList;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.ModActivity;
import jp.tkgktyk.xposed.forcetouchdetector.R;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.PressureButton;

/**
 * Created by tkgktyk on 2015/06/07.
 */
public class PressureThresholdActivity extends AppCompatActivity {

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
    @InjectView(R.id.learn_more_button)
    Button mLearnMoreButton;

    private final LinkedList<Float> mMaxPressureList = Lists.newLinkedList();
    private final LinkedList<Float> mAvePressureList = Lists.newLinkedList();

    protected int getMaxPressureResource() {
        return R.string.max_pressure_f1;
    }

    protected int getPressureResource() {
        return R.string.pressure_f1;
    }

    protected int getAvePressureResource() {
        return R.string.ave_pressure_f1;
    }

    protected String getThresholdKey() {
        return getString(R.string.key_pressure_threshold);
    }

    protected float getPressure(MotionEvent event) {
        return event.getPressure();
    }

    private void updateTapPressureText(float pressure) {
        // size limited queue
        mMaxPressureList.add(pressure);
        if (mMaxPressureList.size() > MAX_COUNT) {
            mMaxPressureList.remove();
        }

        mMaxPressureText.setText(getString(getMaxPressureResource(), getMaxPressure()));
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

        mAvePressureText.setText(getString(getAvePressureResource(), getAvePressure()));
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

        mTapButton.setText(getString(R.string.please_tap_d1, MAX_COUNT));
        mForceTouchButton.setText(getString(R.string.please_force_touch_d1, AVERAGE_COUNT));

        mTapButton.setOnPressedListener(new PressureButton.OnPressedListener() {
            @Override
            public void onPressed(MotionEvent event) {
                updateTapPressureText(getPressure(event));
            }
        });
        mForceTouchButton.setOnPressedListener(new PressureButton.OnPressedListener() {
            @Override
            public void onPressed(MotionEvent event) {
                updateForceTouchPressureText(getPressure(event));
            }
        });

        mPressureThreshold.setText(FTD.getSharedPreferences(this)
                .getString(getThresholdKey(), ModActivity.ForceTouchDetector.DEFAULT_THRESHOLD));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        FTD.getSharedPreferences(this)
                .edit()
                .putString(getThresholdKey(), mPressureThreshold.getText().toString())
                .apply();
    }

    @OnClick(R.id.learn_more_button)
    void onLearnMoreClicked(Button button) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_readme)));
        startActivity(intent);
    }
}
