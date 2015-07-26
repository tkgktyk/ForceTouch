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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
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
import jp.tkgktyk.xposed.forcetouchdetector.app.util.fab.LocalFloatingActionButton;

/**
 * Created by tkgktyk on 2015/06/15.
 */
public class FloatingAction implements View.OnClickListener {

    private static final String ACTION_SHOW_RECENTS = FTD.PREFIX_ACTION + "SHOW_RECENTS";

    private Context mContext;
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
            } else if (Objects.equal(action, ACTION_SHOW_RECENTS)) {
                showRecents();
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

    public FloatingAction(Context context) {
        mContext = context;
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
        filter.addAction(ACTION_SHOW_RECENTS);
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
            fab.setBackgroundTintList(ColorStateList.valueOf(mSettings.floatingActionColor));
            button = fab;
        } else {
            FloatingActionButton fab = new FloatingActionButton(context);
            fab.setBackgroundTintList(ColorStateList.valueOf(mSettings.floatingActionColor));
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
        // get action before erasing recent apps by hide()
        ActionInfo action = (ActionInfo) v.getTag();
        hide();
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

        if (mSettings.floatingActionRecents) {
            loadRecents();
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
        mContainer.setVisibility(View.GONE);
        mNavigationShown = false;
        mContainer.removeCallbacks(mAutoHide);

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
            show();
        }
    }

    public void onDestroy(Context context) {
        hide();
        mWindowManager.removeView(mContainer);
        context.unregisterReceiver(mBroadcastReceiver);
    }
}
