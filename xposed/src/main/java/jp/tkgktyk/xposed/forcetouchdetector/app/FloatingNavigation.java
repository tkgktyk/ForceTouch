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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.support.design.widget.FloatingActionButton;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import butterknife.ButterKnife;
import butterknife.OnClick;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.CircleLayoutForFAB;

/**
 * Created by tkgktyk on 2015/06/15.
 */
public class FloatingNavigation {

    private final WindowManager mWindowManager;
    private final Point mDisplaySize;
    private final WindowManager.LayoutParams mLayoutParams;
    private final CircleLayoutForFAB mCircleLayout;

    private boolean mNavigationShown;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            float x = intent.getFloatExtra(FTD.EXTRA_X_FLOAT, 0.0f);
            float y = intent.getFloatExtra(FTD.EXTRA_Y_FLOAT, 0.0f);
            float rotation;
            if (x / mDisplaySize.x > 0.5) {
                x = mDisplaySize.x;
                rotation = 180.0f;
                mCircleLayout.setReverseDirection(true);
            } else {
                x = 0.0f;
                rotation = 0.0f;
                mCircleLayout.setReverseDirection(false);
            }
            show(x, y, rotation);
        }
    };

    @OnClick({R.id.back_button, R.id.home_button, R.id.recent_button})
    void onNavigationClick(FloatingActionButton fab) {
        String action = null;
        switch (fab.getId()) {
            case R.id.back_button:
                action = FTD.ACTION_BACK;
                break;
            case R.id.home_button:
                action = FTD.ACTION_HOME;
                break;
            case R.id.recent_button:
                action = FTD.ACTION_RECENTS;
                break;
        }
        if (action != null) {
            hide();
            fab.getContext().sendBroadcast(new Intent(action));
        }
    }

    public FloatingNavigation(Service service) {
        service.setTheme(R.style.AppTheme);
        mCircleLayout = (CircleLayoutForFAB) LayoutInflater.from(service)
                .inflate(R.layout.view_floating_navigation, null);
        ButterKnife.inject(this, mCircleLayout);
        mCircleLayout.hide();
        mCircleLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                hide();
                return true;
            }
        });

        mWindowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        mDisplaySize = new Point();
        mWindowManager.getDefaultDisplay().getRealSize(mDisplaySize);
        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        mLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;

        service.registerReceiver(mBroadcastReceiver, FTD.APP_ACTION_FILTER);
    }

    private void show(float x, float y, float rotation) {
        hide();
        mNavigationShown = true;
        mCircleLayout.setVisibility(View.INVISIBLE);
        mWindowManager.addView(mCircleLayout, mLayoutParams);
        mCircleLayout.show(x, y, rotation);
        mCircleLayout.setVisibility(View.VISIBLE);
    }

    private void hide() {
        if (mNavigationShown) {
            mWindowManager.removeView(mCircleLayout);
            mNavigationShown = false;
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        mWindowManager.getDefaultDisplay().getRealSize(mDisplaySize);
    }

    public void onDestroy(Service context) {
        hide();
        context.unregisterReceiver(mBroadcastReceiver);
    }
}
