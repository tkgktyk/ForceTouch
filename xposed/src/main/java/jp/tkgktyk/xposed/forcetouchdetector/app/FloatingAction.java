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

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PointF;
import android.support.design.widget.FloatingActionButton;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.common.base.Objects;

import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionInfo;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionInfoList;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.CircleLayoutForFAB;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.fab.LocalFloatingActionButton;

/**
 * Created by tkgktyk on 2015/06/15.
 */
public class FloatingAction implements View.OnClickListener {

    private FTD.Settings mSettings;

    private final WindowManager mWindowManager;
    private final Point mDisplaySize;
    private final WindowManager.LayoutParams mLayoutParams;
    private final CircleLayoutForFAB mCircleLayout;
    private final FrameLayout mContainer;

    private final PointF mFraction = new PointF();

    private boolean mNavigationShown;
    private ObjectAnimator mShowAnimation;
    private ObjectAnimator mDimAnimation;
    private ObjectAnimator mFadeoutAnimation;

    private ActionInfoList mActionList;

    private Runnable mAutoHide = new Runnable() {
        @Override
        public void run() {
            fadeout();
        }
    };

    public static void show(Context context) {
        Intent intent = new Intent(FTD.ACTION_FLOATING_ACTION);
        intent.putExtra(FTD.EXTRA_FRACTION_X, 0.0f);
        intent.putExtra(FTD.EXTRA_FRACTION_Y, 0.5f);
        context.sendBroadcast(intent);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Objects.equal(action, FTD.ACTION_FLOATING_ACTION)) {
                mFraction.x = intent.getFloatExtra(FTD.EXTRA_FRACTION_X, 0.0f);
                mFraction.y = intent.getFloatExtra(FTD.EXTRA_FRACTION_Y, 0.0f);
                show();
            }
        }
    };

    public FloatingAction(Context context) {
        mSettings = new FTD.Settings(FTD.getSharedPreferences(context));
        context.setTheme(R.style.AppTheme);
        mContainer = (FrameLayout) LayoutInflater.from(context)
                .inflate(R.layout.view_floating_action_container, null);
        mContainer.getBackground().setAlpha(mSettings.floatingActionAlpha);
        mCircleLayout = (CircleLayoutForFAB) mContainer.findViewById(R.id.circle_layout);
        mCircleLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                hide();
                return true;
            }
        });
        setUpAnimator();
        // force hide
        mNavigationShown = true;
        hide();
        loadActions(context);

        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
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
        mWindowManager.addView(mContainer, mLayoutParams);

        IntentFilter filter = new IntentFilter();
        filter.addAction(FTD.ACTION_FLOATING_ACTION);
        context.registerReceiver(mBroadcastReceiver, filter);
    }

    private void setUpAnimator() {
        { // show
            PropertyValuesHolder holderScaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.0f, 1.0f);
            PropertyValuesHolder holderScaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.0f, 1.0f);
            mShowAnimation = ObjectAnimator.ofPropertyValuesHolder(mCircleLayout,
                    holderScaleX, holderScaleY);
//            mShowAnimation.setDuration(300); // default 300

            PropertyValuesHolder holderAlpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.0f, 1.0f);
            mDimAnimation = ObjectAnimator.ofPropertyValuesHolder(mContainer, holderAlpha);
//            mDimAnimation.setDuration(300); // default 300
        }
        { // hide
            PropertyValuesHolder holderAlpha = PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f, 0.0f);
            mFadeoutAnimation = ObjectAnimator.ofPropertyValuesHolder(mContainer, holderAlpha);
            mFadeoutAnimation.addListener(new Animator.AnimatorListener() {
                private boolean mIsCanceled;

                @Override
                public void onAnimationStart(Animator animation) {
                    mIsCanceled = false;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!mIsCanceled) {
                        disappear();
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    mIsCanceled = true;
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
            mFadeoutAnimation.setDuration(1000); // default 300
        }
    }

    private void loadActions(Context context) {
        mCircleLayout.removeAllViews();
        mActionList = ActionInfoList.fromPreference(
                FTD.getSharedPreferences(context)
                        .getString(context.getString(R.string.key_floating_action_list), ""));
        if (mActionList.isEmpty()) {
            mActionList.add(new ActionInfo(context, new Intent(FTD.ACTION_RECENTS), ActionInfo.TYPE_TOOL));
            mActionList.add(new ActionInfo(context, new Intent(FTD.ACTION_HOME), ActionInfo.TYPE_TOOL));
            mActionList.add(new ActionInfo(context, new Intent(FTD.ACTION_BACK), ActionInfo.TYPE_TOOL));
        }
        LayoutInflater inflater = LayoutInflater.from(context);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        for (ActionInfo action : mActionList) {
            // FloatingActionButton extends ImageView
            ImageView button;
            // for setBackgroundTintList
            if (mSettings.useLocalFAB) {
                LocalFloatingActionButton fab = new LocalFloatingActionButton(context,
                        action.getType() != ActionInfo.TYPE_TOOL);
                fab.setLayoutParams(lp);
                fab.setBackgroundTintList(ColorStateList.valueOf(mSettings.floatingActionColor));
                button = fab;
            } else {
                FloatingActionButton fab = (FloatingActionButton) inflater
                        .inflate(R.layout.view_floating_action, mCircleLayout, false);
                fab.setBackgroundTintList(ColorStateList.valueOf(mSettings.floatingActionColor));
                button = fab;
            }
            button.setOnClickListener(this);
            button.setTag(action);
            button.setImageBitmap(action.getIcon());
            mCircleLayout.addView(button);
        }
    }

    @Override
    public void onClick(View v) {
        hide();
        ActionInfo action = (ActionInfo) v.getTag();
        action.launch(v.getContext());
    }

    private void show() {
        hide();
        float x;
        float y = mFraction.y * mDisplaySize.y;
        float rotation;
        if (mFraction.x > 0.5) {
            x = mDisplaySize.x;
            rotation = 180.0f;
            mCircleLayout.setReverseDirection(true);
        } else {
            x = 0.0f;
            rotation = 0.0f;
            mCircleLayout.setReverseDirection(false);
        }
        mNavigationShown = true;

        mCircleLayout.setCircleOrigin(x, y);
        mCircleLayout.setRotation(rotation);
        mCircleLayout.requestLayout();
        mFadeoutAnimation.cancel();
        mShowAnimation.start();
        mDimAnimation.start();
        mContainer.setVisibility(View.VISIBLE);

        mContainer.removeCallbacks(mAutoHide);
        if (mSettings.floatingActionTimeout > 0) {
            mContainer.postDelayed(mAutoHide, mSettings.floatingActionTimeout);
        }
    }

    private void hide() {
        if (mNavigationShown) {
            mShowAnimation.cancel();
            mDimAnimation.cancel();
            mFadeoutAnimation.cancel();
            disappear();
        }
    }

    private void fadeout() {
        if (mNavigationShown) {
            mShowAnimation.cancel();
            mDimAnimation.cancel();
            mFadeoutAnimation.start();
            mContainer.removeCallbacks(mAutoHide);
        }
    }

    private void disappear() {
        mContainer.setVisibility(View.GONE);
        mNavigationShown = false;
        mContainer.removeCallbacks(mAutoHide);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        mWindowManager.getDefaultDisplay().getRealSize(mDisplaySize);
        if (mNavigationShown) {
            show();
        }
    }

    public void onDestroy(Context context) {
        hide();
        mWindowManager.removeView(mContainer);
        context.unregisterReceiver(mBroadcastReceiver);
    }
}
