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
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Build;
import android.support.design.widget.FloatingActionButton;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionInfo;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionInfoList;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.CircleLayoutForFAB;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.MovableLayout;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.fab.LocalFloatingActionButton;

/**
 * Created by tkgktyk on 2015/06/15.
 */
public class FloatingAction implements View.OnClickListener {

    private static final String PREFIX_ACTION = FTD.PREFIX_ACTION + FloatingAction.class.getSimpleName() + ".";
    private static final String ACTION_SHOW_RECENTS = PREFIX_ACTION + "SHOW_RECENTS";

    private Context mContext;
    private FTD.Settings mSettings;

    private final WindowManager mWindowManager;
    private final Point mDisplaySize;
    private final WindowManager.LayoutParams mLayoutParams;
    private final CircleLayoutForFAB mCircleLayout;
    private final MovableLayout mContainer;

    private final PointF mFraction = new PointF();

    private boolean mOneShotMode;
    private boolean mActionEnabled;
    private boolean mNavigationShown;
    private ObjectAnimator mShowAnimation;
    private ObjectAnimator mDimAnimation;
    private ObjectAnimator mFadeoutAnimation;

    private ActionInfoList mActionList;
    private int mPaddingForRecents;
    private Thread mRecentsThread;
    private final ActionInfoList mRecentList = new ActionInfoList();
    private final static Set<String> mIgnoreList = Sets.newHashSet(
            "android",
            "com.android.systemui",
            "com.mediatek.bluetooth",
            "android.process.acore",
            "com.google.process.gapps",
            "com.android.smspush",
            "com.mediatek.voicecommand"
    );
    private final Runnable mLoadRecents = new Runnable() {
        private String mDefaultHomePackage;

        @Override
        public void run() {
            MyApp.logD();
            synchronized (mRecentList) {
                mRecentList.clear();
            }
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            final PackageManager pm = mContext.getPackageManager();
            mDefaultHomePackage = "com.android.launcher";
            intent.addCategory(Intent.CATEGORY_HOME);
            final ResolveInfo res = pm.resolveActivity(intent, 0);
            if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                mDefaultHomePackage = res.activityInfo.packageName;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                UsageStatsManager mUsageStatsManager = (UsageStatsManager) mContext
                        .getSystemService(Context.USAGE_STATS_SERVICE);
                Calendar beginCal = Calendar.getInstance();
                beginCal.add(Calendar.DATE, -7);
                Calendar endCal = Calendar.getInstance();
                Map<String, UsageStats> statsMap = mUsageStatsManager.queryAndAggregateUsageStats(
                        beginCal.getTimeInMillis(), endCal.getTimeInMillis());
                // Sort the stats by the last time used
                if (statsMap != null) {
                    List<UsageStats> stats = Lists.newArrayList(statsMap.values());
                    Collections.sort(stats, new Comparator<UsageStats>() {
                        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                        @Override
                        public int compare(UsageStats lhs, UsageStats rhs) {
                            return Long.compare(rhs.getLastTimeUsed(), lhs.getLastTimeUsed());
                        }
                    });
                    // first app is foreground app.
                    for (int i = 1; i < stats.size() && mRecentList.size() < mPaddingForRecents; ++i) {
                        addRecentApp(stats.get(i).getPackageName());
                    }

                    mContext.sendBroadcast(new Intent(ACTION_SHOW_RECENTS));
                }
            } else {
                ActivityManager am = (ActivityManager) mContext
                        .getSystemService(Context.ACTIVITY_SERVICE);
                final int margin = 1 + 5; // foreground app + for ignored app
                List<ActivityManager.RecentTaskInfo> recents = am
                        .getRecentTasks(mPaddingForRecents + margin, ActivityManager.RECENT_IGNORE_UNAVAILABLE);
                // first app is foreground app.
                for (int i = 1; i < recents.size() && mRecentList.size() < mPaddingForRecents; ++i) {
                    ActivityManager.RecentTaskInfo info = recents.get(i);
                    addRecentApp(info.baseIntent.getComponent().getPackageName());
                }

                mContext.sendBroadcast(new Intent(ACTION_SHOW_RECENTS));
            }

            mRecentsThread = null;
        }

        private void addRecentApp(String packageName) {
            MyApp.logD(packageName);
            if (mIgnoreList.contains(packageName) ||
                    packageName.equals(mDefaultHomePackage)) {
                MyApp.logD("ignored");
                return;
            }
            ActionInfo actionInfo = new ActionInfo(mContext,
                    mContext.getPackageManager().getLaunchIntentForPackage(packageName),
                    ActionInfo.TYPE_APP);
            synchronized (mRecentList) {
                mRecentList.add(actionInfo);
            }
        }
    };

    private Runnable mAutoHide = new Runnable() {
        @Override
        public void run() {
            fadeout();
        }
    };

    public static void show(Activity activity) {
        Intent intent = new Intent(FTD.ACTION_FLOATING_ACTION);
        intent.putExtra(FTD.EXTRA_X, 0.0f);
        intent.putExtra(FTD.EXTRA_Y, activity.getWindow().getDecorView().getHeight() / 2.0f);
        activity.sendBroadcast(intent);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Objects.equal(action, FTD.ACTION_FLOATING_ACTION)) {
                float x = intent.getFloatExtra(FTD.EXTRA_X, 0.0f);
                float y = intent.getFloatExtra(FTD.EXTRA_Y, 0.5f);
                show(x, y);
            } else if (Objects.equal(action, ACTION_SHOW_RECENTS)) {
                showRecents();
            } else if (Objects.equal(action, FTD.ACTION_FORCE_TOUCH_BEGIN)) {
                mOneShotMode = true;
                float x = intent.getFloatExtra(FTD.EXTRA_X, 0.0f);
                float y = intent.getFloatExtra(FTD.EXTRA_Y, 0.5f);
                show(x, y);
            } else if (Objects.equal(action, FTD.ACTION_FORCE_TOUCH_DOWN)) {
                mOneShotMode = false;
                float x = intent.getFloatExtra(FTD.EXTRA_X, 0.0f);
                float y = intent.getFloatExtra(FTD.EXTRA_Y, 0.5f);
                performAction(x, y);
            } else if (Objects.equal(action, FTD.ACTION_FORCE_TOUCH_UP)) {
                // doing nothing
            } else if (Objects.equal(action, FTD.ACTION_FORCE_TOUCH_END)) {
                if (mOneShotMode) {
                    float x = intent.getFloatExtra(FTD.EXTRA_X, 0.0f);
                    float y = intent.getFloatExtra(FTD.EXTRA_Y, 0.5f);
                    performAction(x, y);
                }
                hide();
            } else if (Objects.equal(action, FTD.ACTION_FORCE_TOUCH_CANCEL)) {
                hide();
            }
        }
    };

    private void showRecents() {
        int offset = mCircleLayout.getChildCount() - mPaddingForRecents;
        MyApp.logD("offset=" + offset);
        MyApp.logD("count=" + mCircleLayout.getChildCount());
        MyApp.logD("mPaddingForRecents=" + mPaddingForRecents);
        synchronized (mRecentList) {
            MyApp.logD("size=" + mRecentList.size());
            for (int i = 0; i < mRecentList.size(); ++i) {
                ImageView button = (ImageView) mCircleLayout.getChildAt(i + offset);
                ActionInfo action = mRecentList.get(i);
                button.setTag(action);
                button.setImageBitmap(action.getIcon());
            }
        }
    }

    private void performAction(float x, float y) {
        if (!mActionEnabled) {
            return;
        }
        View view = findViewAtPosition(x, y);
        if (view != null) {
            ActionInfo actionInfo = (ActionInfo) view.getTag();
            actionInfo.launch(mContext);
        }
    }

    /**
     * Determines if given points are inside view
     *
     * @param x    - x coordinate of point
     * @param y    - y coordinate of point
     * @param view - view object to compare
     * @return true if the points are within view bounds, false otherwise
     */
    private boolean isPointInsideView(float x, float y, View view) {
        int location[] = new int[2];
        view.getLocationOnScreen(location);
        int viewX = location[0];
        int viewY = location[1];

        //point is inside view bounds
        if ((x > viewX && x < (viewX + view.getWidth())) &&
                (y > viewY && y < (viewY + view.getHeight()))) {
            return true;
        } else {
            return false;
        }
    }

    private View findViewAtPosition(float x, float y) {
        int count = mCircleLayout.getChildCount();
        for (int i = count; i > 0; --i) {
            View child = mCircleLayout.getChildAt(i - 1);
            if (isPointInsideView(x, y, child)) {
                if (child instanceof ViewGroup) {
                    View v = findViewAtPosition(x, y);
                    if (v != null) {
                        return v;
                    }
                } else {
                    return child;
                }
            }
        }
        return null;
    }

    public FloatingAction(Context context, FTD.Settings settings) {
        mContext = context;
        mSettings = settings;
        context.setTheme(R.style.AppTheme);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mContainer = new MovableLayout(context);
        mContainer.setLayoutParams(lp);
        mContainer.setBackgroundColor(settings.floatingActionBackgroundColor);
        mContainer.getBackground().setAlpha(mSettings.floatingActionBackgroundAlpha);
        if (mSettings.forceTouchScreenEnable) {
            mContainer.setCallback(new MovableLayout.Callback() {
                @Override
                public void move(MovableLayout layout, float dx, float dy) {
                    // usually never reach
                }

                @Override
                public void onClick(MovableLayout layout) {
                    // usually never reach
                    hide();
                }
            });
        } else if (mSettings.floatingActionMovable) {
            mContainer.setCallback(new MovableLayout.Callback() {
                @Override
                public void move(MovableLayout layout, float dx, float dy) {
                    resetAutoHide();
                    mCircleLayout.addOffset(dx, dy);
                    mCircleLayout.requestLayout();
                }

                @Override
                public void onClick(MovableLayout layout) {
                    hide();
                }
            });
        } else {
            mContainer.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    hide();
                    return true;
                }
            });
        }
        mCircleLayout = new CircleLayoutForFAB(mContext);
        mCircleLayout.setLayoutParams(lp);
        mContainer.addView(mCircleLayout);
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
        filter.addAction(ACTION_SHOW_RECENTS);
        filter.addAction(FTD.ACTION_FORCE_TOUCH_BEGIN);
        filter.addAction(FTD.ACTION_FORCE_TOUCH_DOWN);
        filter.addAction(FTD.ACTION_FORCE_TOUCH_UP);
        filter.addAction(FTD.ACTION_FORCE_TOUCH_END);
        filter.addAction(FTD.ACTION_FORCE_TOUCH_CANCEL);
        context.registerReceiver(mBroadcastReceiver, filter);
    }

    private void setUpAnimator() {
        { // show
            PropertyValuesHolder holderScaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.0f, 1.0f);
            PropertyValuesHolder holderScaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.0f, 1.0f);
            mShowAnimation = ObjectAnimator.ofPropertyValuesHolder(mCircleLayout,
                    holderScaleX, holderScaleY);
//            mShowAnimation.setDuration(300); // default 300
            mShowAnimation.addListener(new Animator.AnimatorListener() {
                private boolean mIsCanceled;
                @Override
                public void onAnimationStart(Animator animation) {
                    mIsCanceled = false;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!mIsCanceled) {
                        mActionEnabled = true;
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
        mActionList = ActionInfoList.fromPreference(
                FTD.getSharedPreferences(context)
                        .getString(context.getString(R.string.key_floating_action_list), ""));
        if (mActionList.isEmpty()) {
            mActionList.add(new ActionInfo(context, new Intent(FTD.ACTION_RECENTS), ActionInfo.TYPE_TOOL));
            mActionList.add(new ActionInfo(context, new Intent(FTD.ACTION_HOME), ActionInfo.TYPE_TOOL));
            mActionList.add(new ActionInfo(context, new Intent(FTD.ACTION_BACK), ActionInfo.TYPE_TOOL));
        }
        mCircleLayout.removeAllViews();
        for (ActionInfo action : mActionList) {
            mCircleLayout.addView(inflateButton(context, action));
        }
        if (mSettings.floatingActionRecents) {
            mPaddingForRecents = mCircleLayout.calcPaddingToFill();
            for (int i = 0; i < mPaddingForRecents; ++i) {
                mCircleLayout.addView(inflateButton(context, new ActionInfo()));
            }
        }
    }

    private ImageView inflateButton(Context context, ActionInfo actionInfo) {
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // FloatingActionButton extends ImageView
        ImageView button;
        // for setBackgroundTintList
        if (mSettings.useLocalFAB) {
            LocalFloatingActionButton fab = new LocalFloatingActionButton(context,
                    actionInfo.getType() != ActionInfo.TYPE_TOOL);
            fab.setBackgroundTintList(ColorStateList.valueOf(mSettings.floatingActionButtonColor));
            button = fab;
        } else {
            FloatingActionButton fab = new FloatingActionButton(context);
            fab.setBackgroundTintList(ColorStateList.valueOf(mSettings.floatingActionButtonColor));
            button = fab;
        }
        button.setLayoutParams(lp);
        button.setOnClickListener(this);
        button.setTag(actionInfo);
        button.setImageBitmap(actionInfo.getIcon());
        return button;
    }

    @Override
    public void onClick(View v) {
        if (!mActionEnabled) {
            return;
        }
        // get action before erasing recent apps by hide()
        ActionInfo action = (ActionInfo) v.getTag();
        boolean sticky = false;
        if (mSettings.floatingActionStickyNavigation &&
                action.getType() == ActionInfo.TYPE_TOOL) {
            String str = action.getIntent().getAction();
            if (str.equals(FTD.ACTION_BACK) || str.equals(FTD.ACTION_FORWARD)) {
                sticky = true;
            }
        }
        if (sticky) {
            resetAutoHide();
        } else {
            hide();
        }
        action.launch(v.getContext());
    }

    private void show(float x, float y) {
        hide();

        mFraction.x = x / mDisplaySize.x;
        mFraction.y = y / mDisplaySize.y;
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

        resetAutoHide();

        if (mSettings.floatingActionRecents) {
            loadRecents();
        }
    }

    private void resetAutoHide() {
        mContainer.removeCallbacks(mAutoHide);
        if (!mSettings.forceTouchScreenEnable && mSettings.floatingActionTimeout > 0) {
            mContainer.postDelayed(mAutoHide, mSettings.floatingActionTimeout);
        }
    }

    private void loadRecents() {
        if (mRecentsThread == null) {
            mRecentsThread = new Thread(mLoadRecents);
            mRecentsThread.start();
        } else {
            showRecents();
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
        mContainer.setVisibility(View.INVISIBLE);
        mActionEnabled = false;
        mNavigationShown = false;
        mContainer.removeCallbacks(mAutoHide);
        mCircleLayout.setOffset(0, 0);

        if (mSettings.floatingActionRecents) {
            // erase recent action
            int count = mCircleLayout.getChildCount();
            for (int i = count - mPaddingForRecents; i < count; ++i) {
                ImageView button = (ImageView) mCircleLayout.getChildAt(i);
                button.setTag(new ActionInfo());
                button.setImageDrawable(null);
            }
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        mWindowManager.getDefaultDisplay().getRealSize(mDisplaySize);
        if (mNavigationShown) {
            show(mFraction.x * mDisplaySize.x, mFraction.y * mDisplaySize.y);
        }
    }

    public void onDestroy() {
        hide();
        mWindowManager.removeView(mContainer);
        mContext.unregisterReceiver(mBroadcastReceiver);

        mContext = null;
    }
}
