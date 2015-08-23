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
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Switch;

import butterknife.ButterKnife;
import butterknife.Bind;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ScaleRect;

/**
 * Created by tkgktyk on 2015/07/18.
 */
public class AreaActivity extends AppCompatActivity {
    public static final String EXTRA_SCALE_RECT = FTD.PREFIX_EXTRA + "SCALE_RECT";
    public static final String EXTRA_MIRROR = FTD.PREFIX_EXTRA + "MIRROR";
    public static final String EXTRA_REVERSE = FTD.PREFIX_EXTRA + "REVERSE";

    public static void putExtras(Intent intent, ScaleRect scaleRect, boolean mirror,
                                 boolean reverse) {
        intent.putExtra(EXTRA_SCALE_RECT, scaleRect);
        intent.putExtra(EXTRA_MIRROR, mirror);
        intent.putExtra(EXTRA_REVERSE, reverse);
    }

    private ScaleRect mScaleRect;
    private boolean mIsChanged;

    private boolean mMirror;
    private boolean mReverse;

    private WindowManager mWindowManager;
    private final Point mDisplaySize = new Point();
    private WindowManager.LayoutParams mLayoutParams;
    private FrameLayout mContainer;
    private View mDetectionArea;
    private View mMirroredDetectionArea;

    @Bind(R.id.toolbar)
    Toolbar mToolbar;
    @Bind(R.id.area_size_width_seek)
    SeekBar mWidthSeek;
    @Bind(R.id.area_size_height_seek)
    SeekBar mHeightSeek;
    @Bind(R.id.area_pivot_x_seek)
    SeekBar mXSeek;
    @Bind(R.id.area_pivot_y_seek)
    SeekBar mYSeek;
    @Bind(R.id.mirror_switch)
    Switch mMirrorSwitch;
    @Bind(R.id.reverse_switch)
    Switch mReverseSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_area);
        ButterKnife.bind(this);

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        SharedPreferences prefs = FTD.getSharedPreferences(this);
        mScaleRect = ScaleRect.fromPreference(prefs.getString(getString(R.string.key_detection_area), ""));
        mMirror = prefs.getBoolean(getString(R.string.key_detection_area_mirror), false);
        mReverse = prefs.getBoolean(getString(R.string.key_detection_area_reverse), false);

        initSeekBar();
        initSwitch();

        initDetectionArea();

        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        mWindowManager.getDefaultDisplay().getRealSize(mDisplaySize);
        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        mLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
    }

    private void initSeekBar() {
        final float FINENESS = 10.0f;
        mWidthSeek.setProgress(Math.round(mScaleRect.getScaleX() * FINENESS));
        mWidthSeek.setMax((int) FINENESS);
        mWidthSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mScaleRect.setScaleX(progress / FINENESS);
                mIsChanged = true;
                updateDetectionArea();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        mHeightSeek.setProgress(Math.round(mScaleRect.getScaleY() * FINENESS));
        mHeightSeek.setMax((int) FINENESS);
        mHeightSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mScaleRect.setScaleY(progress / FINENESS);
                mIsChanged = true;
                updateDetectionArea();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        mXSeek.setProgress(Math.round(mScaleRect.getPivotX() * FINENESS));
        mXSeek.setMax((int) FINENESS);
        mXSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mScaleRect.setPivotX(progress / FINENESS);
                mIsChanged = true;
                updateDetectionArea();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        mYSeek.setProgress(Math.round(mScaleRect.getPivotY() * FINENESS));
        mYSeek.setMax((int) FINENESS);
        mYSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mScaleRect.setPivotY(progress / FINENESS);
                mIsChanged = true;
                updateDetectionArea();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void initSwitch() {
        mMirrorSwitch.setChecked(mMirror);
        mMirrorSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mIsChanged = true;
                mMirror = isChecked;
                updateDetectionArea();
            }
        });
        mReverseSwitch.setChecked(mReverse);
        mReverseSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mIsChanged = true;
                mReverse = isChecked;
                updateDetectionArea();
            }
        });
    }

    private void initDetectionArea() {
        mContainer = new FrameLayout(this);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mContainer.setLayoutParams(lp);

        mDetectionArea = new View(this);
        mDetectionArea.setLayoutParams(lp);
        mDetectionArea.setBackgroundResource(R.color.detection_area);
        mContainer.addView(mDetectionArea);

        mMirroredDetectionArea = new View(this);
        mMirroredDetectionArea.setLayoutParams(lp);
        mMirroredDetectionArea.setBackgroundResource(R.color.detection_area);
        mContainer.addView(mMirroredDetectionArea);
    }

    private void updateDetectionArea() {
        Rect rect = mScaleRect.getRect(mDisplaySize);
        mDetectionArea.setX(rect.left);
        mDetectionArea.setY(rect.top);
        ViewGroup.LayoutParams lp = mDetectionArea.getLayoutParams();
        lp.width = rect.width();
        lp.height = rect.height();
        mDetectionArea.setLayoutParams(lp);
        if (mMirror) {
            rect = mScaleRect.getMirroredRect(mDisplaySize);
            mMirroredDetectionArea.setX(rect.left);
            mMirroredDetectionArea.setY(rect.top);
            lp = mMirroredDetectionArea.getLayoutParams();
            lp.width = rect.width();
            lp.height = rect.height();
            mMirroredDetectionArea.setLayoutParams(lp);
            mMirroredDetectionArea.setVisibility(View.VISIBLE);
        } else {
            mMirroredDetectionArea.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWindowManager.addView(mContainer, mLayoutParams);
        updateDetectionArea();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mWindowManager.removeView(mContainer);
        if (mIsChanged) {
            FTD.getSharedPreferences(this)
                    .edit()
                    .putString(getString(R.string.key_detection_area), mScaleRect.toStringForPreference())
                    .putBoolean(getString(R.string.key_detection_area_mirror), mMirror)
                    .putBoolean(getString(R.string.key_detection_area_reverse), mReverse)
                    .apply();
            MyApp.showToast(R.string.saved);
        }
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
}
