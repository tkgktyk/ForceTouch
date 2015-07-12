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

package jp.tkgktyk.xposed.forcetouchdetector;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionInfo;

/**
 * Created by tkgktyk on 2015/02/12.
 */
public class ModForceTouch extends XposedModule {
    private static final String CLASS_DECOR_VIEW = "com.android.internal.policy.impl.PhoneWindow$DecorView";
    private static final String FIELD_FORCE_TOUCH_DETECTOR = FTD.NAME + "_forceTouchDetector";
    private static final String FIELD_SETTINGS_CHANGED_RECEIVER = FTD.NAME + "_settingsChangedReceiver";

    private static XSharedPreferences mPrefs;

    public static void initZygote(XSharedPreferences prefs) {
        mPrefs = prefs;
        try {
            install();
        } catch (Throwable t) {
            logE(t);
        }
    }

    private static void install() {
        final Class<?> classDecorView = XposedHelpers.findClass(CLASS_DECOR_VIEW, null);
        XposedBridge.hookAllConstructors(classDecorView, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    FrameLayout decorView = (FrameLayout) param.thisObject;
                    FTD.Settings settings = newSettings(mPrefs);
                    settings.blacklist.add(FTD.PACKAGE_NAME);
                    String packageName = decorView.getContext().getPackageName();
                    if (settings.blacklist.contains(packageName)) {
                        // blacklist
                        log("ignore: " + packageName);
                        return;
                    }
                    ForceTouchDetector ftd = settings.detectionWindow == 0 ?
                            new SimpleForceTouchDetector(decorView, settings) :
                            settings.detectionWindow > 0 ?
                                    new TransparentForceTouchDetector(decorView, settings) :
                                    new LazyForceTouchDetector(decorView, settings);
                    XposedHelpers.setAdditionalInstanceField(decorView,
                            FIELD_FORCE_TOUCH_DETECTOR, ftd);
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
        XposedHelpers.findAndHookMethod(classDecorView, "dispatchTouchEvent", MotionEvent.class,
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        boolean handled = false;
                        try {
                            FrameLayout decorView = (FrameLayout) methodHookParam.thisObject;
                            MotionEvent event = (MotionEvent) methodHookParam.args[0];
                            ForceTouchDetector ftd = getForceTouchDetector(decorView);
                            if (ftd != null) {
                                handled = ftd.dispatchTouchEvent(event, methodHookParam);
                            }
                        } catch (Throwable t) {
                            logE(t);
                        }
                        if (handled) {
                            return true;
                        }
                        return invokeOriginalMethod(methodHookParam);
                    }
                });

        XposedHelpers.findAndHookMethod(classDecorView, "onAttachedToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            final FrameLayout decorView = (FrameLayout) param.thisObject;
                            BroadcastReceiver settingsChangedReceiver = new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context context, Intent intent) {
                                    ForceTouchDetector ftd = getForceTouchDetector(decorView);
                                    if (ftd == null) {
                                        return;
                                    }
                                    logD(decorView.getContext().getPackageName() + ": reload settings");
                                    FTD.Settings settings = (FTD.Settings) intent
                                            .getSerializableExtra(FTD.EXTRA_SETTINGS);
                                    ftd.onSettingsLoaded(settings);
                                }
                            };
                            XposedHelpers.setAdditionalInstanceField(decorView,
                                    FIELD_SETTINGS_CHANGED_RECEIVER, settingsChangedReceiver);
                            decorView.getContext().registerReceiver(settingsChangedReceiver,
                                    new IntentFilter(FTD.ACTION_SETTINGS_CHANGED));
                        } catch (Throwable t) {
                            logE(t);
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(classDecorView, "onDetachedFromWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            FrameLayout decorView = (FrameLayout) param.thisObject;
                            BroadcastReceiver settingsChangedReceiver =
                                    (BroadcastReceiver) XposedHelpers.getAdditionalInstanceField(decorView,
                                            FIELD_SETTINGS_CHANGED_RECEIVER);
                            if (settingsChangedReceiver != null) {
                                decorView.getContext().unregisterReceiver(settingsChangedReceiver);
                            }
                        } catch (Throwable t) {
                            logE(t);
                        }
                    }
                });
    }

    private static ForceTouchDetector getForceTouchDetector(View decorView) {
        return (ForceTouchDetector) XposedHelpers.getAdditionalInstanceField(decorView,
                FIELD_FORCE_TOUCH_DETECTOR);
    }

    public static abstract class ForceTouchDetector implements GestureDetector.OnGestureListener,
            GestureDetector.OnDoubleTapListener {
        protected static final int INVALIDE_POINTER_ID = -1;

        public static final String DEFAULT_THRESHOLD = "1.0";

        protected final ViewGroup mTargetView;
        protected FTD.Settings mSettings;
        protected final GestureDetector mGestureDetector;

        private final int mDefaultTouchSlopSquare;
        private final int mDefaultDoubleTapTouchSlopSquare;
        private final int mDefaulDoubleTapSlopSquare;
        private final int mDefaultMinimumFlingVelocity;

        private Toast mToast;

        public ForceTouchDetector(ViewGroup targetView, FTD.Settings settings) {
            mTargetView = targetView;
            mGestureDetector = new GestureDetector(getContext(), this);

            mDefaultTouchSlopSquare = XposedHelpers
                    .getIntField(mGestureDetector, "mTouchSlopSquare"); // 8 * density
            mDefaultDoubleTapTouchSlopSquare = XposedHelpers
                    .getIntField(mGestureDetector, "mDoubleTapTouchSlopSquare"); // 8 * density
            mDefaulDoubleTapSlopSquare = XposedHelpers
                    .getIntField(mGestureDetector, "mDoubleTapSlopSquare"); // 100 * density
            mDefaultMinimumFlingVelocity = XposedHelpers
                    .getIntField(mGestureDetector, "mMinimumFlingVelocity"); // 50 * density

            onSettingsLoaded(settings);
        }

        public void onSettingsLoaded(FTD.Settings settings) {
            mSettings = settings;
            int n = settings.detectionSensitivity;
            XposedHelpers.setIntField(mGestureDetector, "mTouchSlopSquare",
                    mDefaultTouchSlopSquare * n * n); // 8 * density
            XposedHelpers.setIntField(mGestureDetector, "mDoubleTapTouchSlopSquare",
                    mDefaultDoubleTapTouchSlopSquare * n * n); // 8 * density
//            XposedHelpers.setIntField(mGestureDetector, "mDoubleTapSlopSquare",
//                    mDefaulDoubleTapSlopSquare * n * n); // 100 * density
            XposedHelpers.setIntField(mGestureDetector, "mMinimumFlingVelocity",
                    mDefaultMinimumFlingVelocity * n); // 50 * density
        }

        protected Context getContext() {
            return mTargetView.getContext();
        }

        public void showToast(String text) {
            if (mToast != null) {
                mToast.cancel();
            }
            mToast = Toast.makeText(mTargetView.getContext(), text, Toast.LENGTH_SHORT);
            mToast.show();
        }

        protected FTD.Settings.Holder judgeForceTouch(MotionEvent event) {
            int index = event.getActionIndex();
            float y = event.getY(index);
            // touch area
            if (y > mTargetView.getHeight() * mSettings.forceTouchArea) {
                // pressure and size
                if (mSettings.pressure.enabled &&
                        event.getPressure(index) > mSettings.pressure.threshold) {
                    return mSettings.pressure;
                } else if (mSettings.size.enabled &&
                        event.getSize(index) > mSettings.size.threshold) {
                    return mSettings.size;
                }
            }
            return null;
        }

        public abstract boolean dispatchTouchEvent(MotionEvent event,
                                                   XC_MethodHook.MethodHookParam methodHookParam);

        protected void performAction(final ActionInfo.Record record, final MotionEvent event,
                                     final String disabledText) {
            // force delay for complete ACTION_UP of gesture detector
            mTargetView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (FTD.performAction(mTargetView, new ActionInfo(record), event)) {
                        if (mSettings.showEnabledActionToast) {
                            if (!Strings.isNullOrEmpty(record.name)) {
                                showToast(record.name);
                            }
                        }
                    } else if (mSettings.showDisabledActionToast) {
                        showToast(disabledText);
                    }
                }
            }, 1);
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            FTD.Settings.Holder holder = judgeForceTouch(e);
            performAction(holder.actionTap, e, "force tap");
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            FTD.Settings.Holder holder = judgeForceTouch(e);
            performAction(holder.actionDoubleTap, e, "force double tap");
            return true;
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                                float distanceY) {
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            mTargetView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            FTD.Settings.Holder holder = judgeForceTouch(e);
            performAction(holder.actionLongPress, e, "force long press");
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            mTargetView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            FTD.Settings.Holder holder = judgeForceTouch(e1);
            float x = e2.getX() - e1.getX();
            float y = e2.getY() - e1.getY();
            if (Math.abs(x) > Math.abs(y)) {
                if (x > 0) {
                    performAction(holder.actionFlickRight, e2, "force flick right");
                } else {
                    performAction(holder.actionFlickLeft, e2, "force flick left");
                }
            } else {
                if (y > 0) {
                    performAction(holder.actionFlickDown, e2, "force flick down");
                } else {
                    performAction(holder.actionFlickUp, e2, "force flick up");
                }
            }
            return true;
        }
    }

    public static class SimpleForceTouchDetector extends ForceTouchDetector {
        protected int mActivePointerId = INVALIDE_POINTER_ID;
        private long mDownTime;
        protected boolean mIntercepted;

        // ripple
        private static final int RIPPLE_SIZE = 5;
        private static final float START_SCALE = 0.0f;
        private static final float START_ALPHA = 0.7f;
        private View mRippleView;
        private int mRippleSize;
        private ObjectAnimator mRippleAnimator;

        public SimpleForceTouchDetector(ViewGroup targetView, FTD.Settings settings) {
            super(targetView, settings);
            setUpRipple();
        }

        private void setUpRipple() {
            mRippleView = new View(getContext());
            mRippleSize = getContext().getResources().getDimensionPixelSize(android.R.dimen.app_icon_size) *
                    RIPPLE_SIZE;
            mRippleView.setLayoutParams(new ViewGroup.LayoutParams(mRippleSize, mRippleSize));
            Context mod = FTD.getModContext(getContext());
            mRippleView.setBackground(mod.getResources().getDrawable(R.drawable.force_touch_ripple));

            PropertyValuesHolder holderScaleX = PropertyValuesHolder.ofFloat("scaleX", START_SCALE, 1.0f);
            PropertyValuesHolder holderScaleY = PropertyValuesHolder.ofFloat("scaleY", START_SCALE, 1.0f);
            PropertyValuesHolder holderAlpha = PropertyValuesHolder.ofFloat("alpha", START_ALPHA, 0.0f);

            mRippleAnimator = ObjectAnimator.ofPropertyValuesHolder(mRippleView,
                    holderScaleX, holderScaleY, holderAlpha);
            mRippleAnimator.setDuration(300); // default
            mRippleAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mRippleView.setScaleX(START_SCALE);
                    mRippleView.setScaleY(START_SCALE);
                    mRippleView.setAlpha(START_ALPHA);

                    mTargetView.addView(mRippleView);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mTargetView.removeView(mRippleView);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    onAnimationEnd(animation);
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
        }

        public boolean dispatchTouchEvent(MotionEvent event, XC_MethodHook.MethodHookParam methodHookParam) {
            if (!mSettings.isEnabled()) {
                return false;
            }

            boolean consumed = mIntercepted;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    if (judgeAndStartForceTouch(event)) {
                        cancelOriginalTouchEvent(event, methodHookParam);
                        // start force touch, intercept touch events
                        mIntercepted = true;
                        int index = event.getActionIndex();
                        mActivePointerId = event.getPointerId(index);
                        consumeTouchEvent(MotionEvent.ACTION_DOWN, event);
                        consumed = true;
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE:
                    // cannot get ActivePointer when ACTION_MODE
//                    logD("getActionIndex = " + event.getActionIndex());
//                    logD("getAction = " + ((event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
//                            MotionEvent.ACTION_POINTER_INDEX_SHIFT));
//                    if (isActivePointer(event)) {
                    if (mIntercepted) {
                        consumeTouchEvent(MotionEvent.ACTION_MOVE, event);
                    }
//                        consumed = true;
//                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (mActivePointerId != INVALIDE_POINTER_ID) {
                        // stop force touch
                        consumeTouchEvent(MotionEvent.ACTION_UP, event);
                        consumed = true;
                        mActivePointerId = INVALIDE_POINTER_ID;
                    }
                    // finalize this (multi-)touch stroke
                    mIntercepted = false;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (mActivePointerId == INVALIDE_POINTER_ID) {
                        if (judgeAndStartForceTouch(event)) {
                            cancelOriginalTouchEvent(event, methodHookParam);
                            // start force touch, intercept touch events
                            mIntercepted = true;
                            int index = event.getActionIndex();
                            mActivePointerId = event.getPointerId(index);
                            consumeTouchEvent(MotionEvent.ACTION_DOWN, event);
                            consumed = true;
                        }
                    }
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                            MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                    int pointerId = event.getPointerId(pointerIndex);
                    if (pointerId == mActivePointerId) {
                        // stop force touch
                        consumeTouchEvent(MotionEvent.ACTION_UP, event);
                        consumed = true;
                        mActivePointerId = INVALIDE_POINTER_ID;
                    }
                    break;
            }

            // intercept ACTION_UP and ACTION_CANCEL too when intercepted
            // because already send ACTION_CANCEL to target view when force touch is started.
            return consumed || mIntercepted;
        }

        protected void consumeTouchEvent(int action, MotionEvent base) {
            if (action == MotionEvent.ACTION_DOWN) {
                mDownTime = base.getEventTime();
            }
            int index = base.findPointerIndex(mActivePointerId);
//            logD("mActivePointerId=" + mActivePointerId);
//            logD("index=" + index);
            if (index == -1) {
                return;
            }
            MotionEvent event = MotionEvent
                    .obtain(mDownTime, base.getEventTime(), action,
                            base.getX(index), base.getY(index),
                            base.getPressure(index), base.getSize(index), base.getMetaState(),
                            base.getXPrecision(), base.getYPrecision(), base.getDeviceId(),
                            base.getEdgeFlags());
//            logD(event.toString());
            mGestureDetector.onTouchEvent(event);
            event.recycle();
        }

        protected boolean judgeAndStartForceTouch(MotionEvent event) {
            int index = event.getActionIndex();
            FTD.Settings.Holder holder = judgeForceTouch(event);
            if (holder != null) {
                // feedback
                mTargetView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                startRipple(event, index);
                return true;
            }
            return false;
        }

        protected void startRipple(MotionEvent event, int index) {
            float x = event.getX(index);
            float y = event.getY(index);
            mRippleView.setTranslationX(x - mRippleSize / 2.0f);
            mRippleView.setTranslationY(y - mRippleSize / 2.0f);

            mRippleAnimator.start();
        }

        protected void cancelOriginalTouchEvent(MotionEvent event,
                                                XC_MethodHook.MethodHookParam methodHookParam) {
            // cancel other touch events
            MotionEvent event2 = MotionEvent.obtain(event);
            event2.setAction(MotionEvent.ACTION_CANCEL);
            Object backup = methodHookParam.args[0];
            methodHookParam.args[0] = event2;
            try {
                invokeOriginalMethod(methodHookParam);
            } catch (Throwable t) {
                logE(t);
            }
            methodHookParam.args[0] = backup;
            event2.recycle();
        }
    }

    public static class LazyForceTouchDetector extends SimpleForceTouchDetector {

        public LazyForceTouchDetector(ViewGroup targetView, FTD.Settings settings) {
            super(targetView, settings);
        }

        private Handler mHandler = new Handler();
        private boolean mIsDetectionWindowOpened;
        private Runnable mStopDetector = new Runnable() {
            @Override
            public void run() {
                if (mIsDetectionWindowOpened) {
                    synchronized (mMotionEventList) {
                        for (MotionEvent e : mMotionEventList) {
                            Object backup = mMethodHookParam.args[0];
                            mMethodHookParam.args[0] = e;
                            try {
                                invokeOriginalMethod(mMethodHookParam);
                            } catch (Throwable t) {
                                logE(t);
                            }
                            mMethodHookParam.args[0] = backup;
                            e.recycle();
                        }
                        mMotionEventList.clear();
                    }
                }
                mIsDetectionWindowOpened = false;
            }
        };
        private final List<MotionEvent> mMotionEventList = Lists.newArrayList();
        private XC_MethodHook.MethodHookParam mMethodHookParam;

        public boolean dispatchTouchEvent(MotionEvent event, XC_MethodHook.MethodHookParam methodHookParam) {
            if (!mSettings.isEnabled()) {
                return false;
            }
            boolean consumed = mIntercepted;
            synchronized (mMotionEventList) {
                mMethodHookParam = methodHookParam;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: {
                        mHandler.postDelayed(mStopDetector, mSettings.detectionWindow);
                        mIsDetectionWindowOpened = true;
                        break;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mHandler.removeCallbacks(mStopDetector);
                        if (mIsDetectionWindowOpened) {
                            for (MotionEvent e : mMotionEventList) {
                                Object backup = mMethodHookParam.args[0];
                                mMethodHookParam.args[0] = e;
                                try {
                                    invokeOriginalMethod(mMethodHookParam);
                                } catch (Throwable t) {
                                    logE(t);
                                }
                                mMethodHookParam.args[0] = backup;
                                e.recycle();
                            }
                            mMotionEventList.clear();
                            mIsDetectionWindowOpened = false;
                        }
                        break;
                }
                if (mIsDetectionWindowOpened) {
                    int count = event.getPointerCount();
                    FTD.Settings.Holder holder = null;
                    int index = 0;
                    for (; index < count; ++index) {
                        float y = event.getY(index);
                        // touch area
                        if (y > mTargetView.getHeight() * mSettings.forceTouchArea) {
                            // pressure and size
                            if (mSettings.pressure.enabled &&
                                    event.getPressure(index) > mSettings.pressure.threshold) {
                                holder = mSettings.pressure;
                                break;
                            } else if (mSettings.size.enabled &&
                                    event.getSize(index) > mSettings.size.threshold) {
                                holder = mSettings.size;
                                break;
                            }
                        }
                    }
                    if (holder != null) {
                        mTargetView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        startRipple(event, index);

                        mHandler.removeCallbacks(mStopDetector);
                        mMotionEventList.clear();
                        mIsDetectionWindowOpened = false;
                        // start force touch, intercept touch events
                        mIntercepted = true;
                        mActivePointerId = event.getPointerId(index);
                        consumeTouchEvent(MotionEvent.ACTION_DOWN, event);
                        consumed = true;
                    }
                }

                if (mIsDetectionWindowOpened) {
                    mMotionEventList.add(MotionEvent.obtain(event));
                    consumed = true;
                } else {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_MOVE:
                            // cannot get ActivePointer when ACTION_MODE
//                    logD("getActionIndex = " + event.getActionIndex());
//                    logD("getAction = " + ((event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
//                            MotionEvent.ACTION_POINTER_INDEX_SHIFT));
//                    if (isActivePointer(event)) {
                            if (mIntercepted) {
                                consumeTouchEvent(MotionEvent.ACTION_MOVE, event);
                            }
//                        consumed = true;
//                    }
                            break;
                        case MotionEvent.ACTION_CANCEL:
                        case MotionEvent.ACTION_UP:
                            if (mActivePointerId != INVALIDE_POINTER_ID) {
                                // stop force touch
                                consumeTouchEvent(MotionEvent.ACTION_UP, event);
                                consumed = true;
                                mActivePointerId = INVALIDE_POINTER_ID;
                            }
                            // finalize this (multi-)touch stroke
                            mIntercepted = false;
                            break;
                        case MotionEvent.ACTION_POINTER_UP:
                            int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                                    MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                            int pointerId = event.getPointerId(pointerIndex);
                            if (pointerId == mActivePointerId) {
                                // stop force touch
                                consumeTouchEvent(MotionEvent.ACTION_UP, event);
                                consumed = true;
                                mActivePointerId = INVALIDE_POINTER_ID;
                            }
                            break;
                    }
                }
            }
            // intercept ACTION_UP and ACTION_CANCEL too when intercepted
            // because already send ACTION_CANCEL to target view when force touch is started.
            return consumed || mIntercepted;
        }
    }

    public static class TransparentForceTouchDetector extends SimpleForceTouchDetector {

        public TransparentForceTouchDetector(ViewGroup targetView, FTD.Settings settings) {
            super(targetView, settings);
        }

        private Handler mHandler = new Handler();
        private boolean mIsDetectionWindowOpened;
        private Runnable mStopDetector = new Runnable() {
            @Override
            public void run() {
                mIsDetectionWindowOpened = false;
            }
        };

        public boolean dispatchTouchEvent(MotionEvent event, XC_MethodHook.MethodHookParam methodHookParam) {
            if (!mSettings.isEnabled()) {
                return false;
            }
            boolean consumed = mIntercepted;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    mHandler.postDelayed(mStopDetector, Math.abs(mSettings.detectionWindow));
                    mIsDetectionWindowOpened = true;
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mHandler.removeCallbacks(mStopDetector);
                    mIsDetectionWindowOpened = false;
                    break;
            }
            if (mIsDetectionWindowOpened) {
                int count = event.getPointerCount();
                FTD.Settings.Holder holder = null;
                int index = 0;
                for (; index < count; ++index) {
                    float y = event.getY(index);
                    // touch area
                    if (y > mTargetView.getHeight() * mSettings.forceTouchArea) {
                        // pressure and size
                        if (mSettings.pressure.enabled &&
                                event.getPressure(index) > mSettings.pressure.threshold) {
                            holder = mSettings.pressure;
                            break;
                        } else if (mSettings.size.enabled &&
                                event.getSize(index) > mSettings.size.threshold) {
                            holder = mSettings.size;
                            break;
                        }
                    }
                }
                if (holder != null) {
                    mTargetView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    startRipple(event, index);

                    mHandler.removeCallbacks(mStopDetector);
                    mIsDetectionWindowOpened = false;

                    cancelOriginalTouchEvent(event, methodHookParam);
                    // start force touch, intercept touch events
                    mIntercepted = true;
                    mActivePointerId = event.getPointerId(index);
                    consumeTouchEvent(MotionEvent.ACTION_DOWN, event);
                    consumed = true;
                }
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    // cannot get ActivePointer when ACTION_MODE
//                    logD("getActionIndex = " + event.getActionIndex());
//                    logD("getAction = " + ((event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
//                            MotionEvent.ACTION_POINTER_INDEX_SHIFT));
//                    if (isActivePointer(event)) {
                    if (mIntercepted) {
                        consumeTouchEvent(MotionEvent.ACTION_MOVE, event);
                    }
//                        consumed = true;
//                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (mActivePointerId != INVALIDE_POINTER_ID) {
                        // stop force touch
                        consumeTouchEvent(MotionEvent.ACTION_UP, event);
                        consumed = true;
                        mActivePointerId = INVALIDE_POINTER_ID;
                    }
                    // finalize this (multi-)touch stroke
                    mIntercepted = false;
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                            MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                    int pointerId = event.getPointerId(pointerIndex);
                    if (pointerId == mActivePointerId) {
                        // stop force touch
                        consumeTouchEvent(MotionEvent.ACTION_UP, event);
                        consumed = true;
                        mActivePointerId = INVALIDE_POINTER_ID;
                    }
                    break;
            }
            // intercept ACTION_UP and ACTION_CANCEL too when intercepted
            // because already send ACTION_CANCEL to target view when force touch is started.
            return consumed || mIntercepted;
        }
    }
}
