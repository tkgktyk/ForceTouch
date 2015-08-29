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
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
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
import jp.tkgktyk.lib.ForceTouchDetector;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionInfo;

/**
 * Created by tkgktyk on 2015/02/12.
 */
public class ModForceTouch extends XposedModule {
    private static final String CLASS_DECOR_VIEW = "com.android.internal.policy.impl.PhoneWindow$DecorView";
    private static final String FIELD_DETECTORS = FTD.NAME + "_forceTouchDetectors";
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
                    Context context = decorView.getContext();
                    mPrefs.reload();
                    FTD.Settings settings = new FTD.Settings(context, mPrefs);
                    settings.blacklist.add(FTD.PACKAGE_NAME);
                    String packageName = context.getPackageName();
                    if (settings.blacklist.contains(packageName)) {
                        // blacklist
                        logD("ignore: " + packageName);
                        return;
                    }
                    List<Detector> detectors = Lists.newArrayList();
                    detectors.add(
                            settings.detectionWindow > 0 ?
                                    new ForceGestureDetector(decorView, settings) :
                                    new SimpleForceGestureDetector(decorView, settings));
                    detectors.add(new LargeTouchDetector(decorView, settings));
                    detectors.add(new KnuckleTouchDetector(decorView, settings));
                    detectors.add(new WiggleTouchDetector(decorView, settings));
                    XposedHelpers.setAdditionalInstanceField(decorView,
                            FIELD_DETECTORS, detectors);
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
//        XposedHelpers.findAndHookMethod(View.class, "dispatchPointerEvent", MotionEvent.class,
//                new XC_MethodHook() {
//                    @Override
//                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                        View view = (View) param.thisObject;
//                        MotionEvent event = (MotionEvent) param.args[0];
//                        logD("dispatchPointerEvent: " + event.toString());
//                        logD("isRootView: " + (view.getRootView() == view));
//                    }
//                });
        XposedHelpers.findAndHookMethod(classDecorView, "dispatchTouchEvent", MotionEvent.class,
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        boolean handled = false;
                        try {
                            FrameLayout decorView = (FrameLayout) methodHookParam.thisObject;
                            MotionEvent event = (MotionEvent) methodHookParam.args[0];
                            List<Detector> detectors = getDetectors(decorView);
                            if (detectors != null) {
                                for (Detector ftd : detectors) {
                                    handled = handled || ftd.dispatchTouchEvent(event, methodHookParam);
                                }
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
                                    List<Detector> detectors = getDetectors(decorView);
                                    if (detectors == null) {
                                        return;
                                    }
                                    logD(decorView.getContext().getPackageName() + ": reload settings");
                                    FTD.Settings settings = (FTD.Settings) intent
                                            .getSerializableExtra(FTD.EXTRA_SETTINGS);
                                    for (Detector ftd : detectors) {
                                        ftd.onSettingsLoaded(settings);
                                    }
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

    private static List<Detector> getDetectors(View decorView) {
        return (List<Detector>) XposedHelpers
                .getAdditionalInstanceField(decorView, FIELD_DETECTORS);
    }

    public static abstract class Detector {
        protected static final int INVALIDE_POINTER_ID = -1;

        public static final String DEFAULT_THRESHOLD = "1.0";

        protected final ViewGroup mTargetView;
        protected FTD.Settings mSettings;

        // ripple
        private static final int RIPPLE_SIZE = 5;
        private static final float START_SCALE = 0.0f;
        private static final float START_ALPHA = 0.7f;
        private View mRippleView;
        private int mRippleSize;
        private ObjectAnimator mRippleAnimator;

        private Toast mToast;

        protected XC_MethodHook.MethodHookParam mMethodHookParam;

        public Detector(ViewGroup targetView, FTD.Settings settings) {
            mTargetView = targetView;
            mSettings = settings;

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

        protected void startRipple(MotionEvent event, int index) {
            startRipple(event.getX(index), event.getY(index));
        }

        protected void startRipple(float x, float y) {
            mRippleView.setTranslationX(x - mRippleSize / 2.0f);
            mRippleView.setTranslationY(y - mRippleSize / 2.0f);

            mRippleAnimator.start();
        }

        public final void onSettingsLoaded(FTD.Settings settings) {
            mSettings = settings;
            onSettingsLoaded();
        }

        protected abstract void onSettingsLoaded();

        protected Context getContext() {
            return mTargetView.getContext();
        }

        protected void showToast(String text) {
            if (mToast != null) {
                mToast.cancel();
            }
            mToast = Toast.makeText(getContext(), text, Toast.LENGTH_SHORT);
            mToast.show();
        }

        protected boolean isInDetectionArea(float x, float y) {
            int ix = Math.round(x);
            int iy = Math.round(y);
            Rect area = mSettings.detectionArea.getRect(mTargetView.getWidth(), mTargetView.getHeight());
            Rect mirroredArea = mSettings.detectionAreaMirror ?
                    mSettings.detectionArea.getMirroredRect(mTargetView.getWidth(), mTargetView.getHeight()) :
                    null;
            // touch area
            boolean contains = area.contains(ix, iy) ||
                    (mirroredArea != null && mirroredArea.contains(ix, iy));
            if (mSettings.detectionAreaReverse) {
                contains = !contains;
            }
            return contains;
        }

        final public boolean dispatchTouchEvent(MotionEvent event,
                                                XC_MethodHook.MethodHookParam methodHookParam) {
            mMethodHookParam = methodHookParam;
            return dispatchTouchEvent(event);
        }

        protected abstract boolean dispatchTouchEvent(MotionEvent event);

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

        protected void performAction(final ActionInfo.Record record, final float x, final float y,
                                     final String disabledText) {
            // force delay for complete ACTION_UP of gesture detector
            mTargetView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (FTD.performAction(mTargetView, new ActionInfo(record), x, y)) {
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
    }

    public static abstract class BaseForceGestureDetector extends Detector
            implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {
        protected final GestureDetector mGestureDetector;

        private final int mDefaultTouchSlopSquare;
        private final int mDefaultDoubleTapTouchSlopSquare;
        private final int mDefaulDoubleTapSlopSquare;
        private final int mDefaultMinimumFlingVelocity;

        private final Handler mDefaultGestureHandler;
        private final GestureHandler mGestureHandler;

        private long mDownTime;

        private final int LONG_PRESS = 2;

        private class GestureHandler extends Handler {
            GestureHandler() {
                super();
            }

            GestureHandler(Handler handler) {
                super(handler.getLooper());
            }

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case LONG_PRESS:
                        if (msg.arg1 != LONG_PRESS && mSettings.extraLongPressTimeout > 0) {
                            mGestureHandler.removeMessages(LONG_PRESS);
                            mGestureHandler.sendMessageDelayed(
                                    mGestureHandler.obtainMessage(LONG_PRESS, LONG_PRESS, 0),
                                    mSettings.extraLongPressTimeout);
                        } else {
                            XposedHelpers.callMethod(mGestureDetector, "dispatchLongPress");
                        }
                        break;
                    default:
                        mDefaultGestureHandler.handleMessage(msg);
                }
            }
        }

        public BaseForceGestureDetector(ViewGroup targetView, FTD.Settings settings) {
            super(targetView, settings);

            mGestureDetector = new GestureDetector(getContext(), this);

            mDefaultTouchSlopSquare = XposedHelpers
                    .getIntField(mGestureDetector, "mTouchSlopSquare"); // 8 * density
            mDefaultDoubleTapTouchSlopSquare = XposedHelpers
                    .getIntField(mGestureDetector, "mDoubleTapTouchSlopSquare"); // 8 * density
            mDefaulDoubleTapSlopSquare = XposedHelpers
                    .getIntField(mGestureDetector, "mDoubleTapSlopSquare"); // 100 * density
            mDefaultMinimumFlingVelocity = XposedHelpers
                    .getIntField(mGestureDetector, "mMinimumFlingVelocity"); // 50 * density

            mDefaultGestureHandler = (Handler) XposedHelpers
                    .getObjectField(mGestureDetector, "mHandler");
            mGestureHandler = new GestureHandler();
            XposedHelpers.setObjectField(mGestureDetector, "mHandler", mGestureHandler);

            onSettingsLoaded(settings);
        }

        public void onSettingsLoaded() {
            int n = mSettings.detectionSensitivity;
            XposedHelpers.setIntField(mGestureDetector, "mTouchSlopSquare",
                    mDefaultTouchSlopSquare * n * n); // 8 * density
            XposedHelpers.setIntField(mGestureDetector, "mDoubleTapTouchSlopSquare",
                    mDefaultDoubleTapTouchSlopSquare * n * n); // 8 * density
//            XposedHelpers.setIntField(mGestureDetector, "mDoubleTapSlopSquare",
//                    mDefaulDoubleTapSlopSquare * n * n); // 100 * density
            XposedHelpers.setIntField(mGestureDetector, "mMinimumFlingVelocity",
                    mDefaultMinimumFlingVelocity * n); // 50 * density

            if (mSettings.useDoubleTap) {
                mGestureDetector.setOnDoubleTapListener(this);
            } else {
                mGestureDetector.setOnDoubleTapListener(null);
            }
        }

        protected FTD.Settings.Holder judgeForceTouch(MotionEvent event) {
//            logD("index=" + event.getActionIndex());
//            logD(event.toString());
//            logD("pressure=" + event.getPressure());
//            logD("size=" + event.getSize());
            return judgeForceTouch(event, event.getActionIndex());
        }

        protected FTD.Settings.Holder judgeForceTouch(MotionEvent event, int index) {
            if (isInDetectionArea(event.getX(index), event.getY(index))) {
                // pressure and size
                if (mSettings.pressure.enable &&
                        event.getPressure(index) > mSettings.pressure.threshold) {
                    return mSettings.pressure;
                } else if (mSettings.size.enable &&
                        event.getSize(index) > mSettings.size.threshold) {
                    return mSettings.size;
                }
            }
            return null;
        }

        protected void consumeTouchEvent(int action, MotionEvent base, int pointerId) {
            if (action == MotionEvent.ACTION_DOWN) {
                mDownTime = base.getEventTime();
            }
            int index = base.findPointerIndex(pointerId);
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

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            FTD.Settings.Holder holder = judgeForceTouch(e);
            if (holder != null) {
                performAction(holder.actionTap, e, "force tap");
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            FTD.Settings.Holder holder = judgeForceTouch(e);
            if (holder != null) {
                performAction(holder.actionDoubleTap, e, "force double tap");
            }
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
            if (!mSettings.useDoubleTap) {
                // usually e is ACTION_UP event
                return onSingleTapConfirmed(
                        (MotionEvent) XposedHelpers.getObjectField(mGestureDetector, "mCurrentDownEvent"));
            }
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
            if (holder != null) {
                performAction(holder.actionLongPress, e, "force long press");
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            mTargetView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            FTD.Settings.Holder holder = judgeForceTouch(e1);
            if (holder != null) {
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
            }
            return true;
        }
    }

    public static class SimpleForceGestureDetector extends BaseForceGestureDetector {
        protected int mActivePointerId = INVALIDE_POINTER_ID;
        protected boolean mIntercepted;

        public SimpleForceGestureDetector(ViewGroup targetView, FTD.Settings settings) {
            super(targetView, settings);
        }

        @Override
        protected boolean dispatchTouchEvent(MotionEvent event) {
            if (!mSettings.pressure.enable && !mSettings.size.enable) {
                return false;
            }

            boolean consumed = mIntercepted;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    if (judgeAndStartForceTouch(event)) {
                        cancelOriginalTouchEvent(event, mMethodHookParam);
                        // start force touch, intercept touch events
                        mIntercepted = true;
                        int index = event.getActionIndex();
                        mActivePointerId = event.getPointerId(index);
                        consumeTouchEvent(MotionEvent.ACTION_DOWN, event, mActivePointerId);
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
                        consumeTouchEvent(MotionEvent.ACTION_MOVE, event, mActivePointerId);
                    }
//                        consumed = true;
//                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (mActivePointerId != INVALIDE_POINTER_ID) {
                        // stop force touch
                        consumeTouchEvent(MotionEvent.ACTION_UP, event, mActivePointerId);
                        consumed = true;
                        mActivePointerId = INVALIDE_POINTER_ID;
                    }
                    // finalize this (multi-)touch stroke
                    mIntercepted = false;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (mActivePointerId == INVALIDE_POINTER_ID) {
                        if (judgeAndStartForceTouch(event)) {
                            cancelOriginalTouchEvent(event, mMethodHookParam);
                            // start force touch, intercept touch events
                            mIntercepted = true;
                            int index = event.getActionIndex();
                            mActivePointerId = event.getPointerId(index);
                            consumeTouchEvent(MotionEvent.ACTION_DOWN, event, mActivePointerId);
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
                        consumeTouchEvent(MotionEvent.ACTION_UP, event, mActivePointerId);
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

    public static class ForceGestureDetector extends BaseForceGestureDetector {
        protected int mActivePointerId = INVALIDE_POINTER_ID;
        protected boolean mIntercepted;

        public ForceGestureDetector(ViewGroup targetView, FTD.Settings settings) {
            super(targetView, settings);
        }

        private final Handler mHandler = new Handler();
        private boolean mIsDetectionWindowOpened;
        private final Runnable mStopDetector = new Runnable() {
            @Override
            public void run() {
                mIsDetectionWindowOpened = false;
            }
        };

        @Override
        protected boolean dispatchTouchEvent(MotionEvent event) {
            if (!mSettings.pressure.enable && !mSettings.size.enable) {
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
                    holder = judgeForceTouch(event, index);
                    if (holder != null) {
                        break;
                    }
                }
                if (holder != null) {
                    mTargetView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    startRipple(event, index);

                    mHandler.removeCallbacks(mStopDetector);
                    mIsDetectionWindowOpened = false;

                    cancelOriginalTouchEvent(event, mMethodHookParam);
                    // start force touch, intercept touch events
                    mIntercepted = true;
                    mActivePointerId = event.getPointerId(index);
                    consumeTouchEvent(MotionEvent.ACTION_DOWN, event, mActivePointerId);
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
                        consumeTouchEvent(MotionEvent.ACTION_MOVE, event, mActivePointerId);
                    }
//                        consumed = true;
//                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (mActivePointerId != INVALIDE_POINTER_ID) {
                        // stop force touch
                        consumeTouchEvent(MotionEvent.ACTION_UP, event, mActivePointerId);
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
                        consumeTouchEvent(MotionEvent.ACTION_UP, event, mActivePointerId);
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

    static abstract class BaseForceTouchDetector extends Detector
            implements ForceTouchDetector.Callback {
        protected final ForceTouchDetector mForceTouchDetector;

        public BaseForceTouchDetector(ViewGroup targetView, FTD.Settings settings) {
            super(targetView, settings);

            mForceTouchDetector = new ForceTouchDetector(this);
            onSettingsLoaded(settings);
        }

        @Override
        public void cancelTouchEvent(MotionEvent cancel) {
            Object backup = mMethodHookParam.args[0];
            mMethodHookParam.args[0] = cancel;
            try {
                invokeOriginalMethod(mMethodHookParam);
            } catch (Throwable t) {
                logE(t);
            }
            mMethodHookParam.args[0] = backup;
        }

        @Override
        public float getParameter(MotionEvent event, int index) {
            return mSettings.usePressure ?
                    ForceTouchDetector.getPressure(event, index) :
                    ForceTouchDetector.getSize(event, index);
        }
    }

    static class LargeTouchDetector extends BaseForceTouchDetector
            implements ForceTouchDetector.Callback {

        public LargeTouchDetector(ViewGroup targetView, FTD.Settings settings) {
            super(targetView, settings);
        }

        @Override
        public void onSettingsLoaded() {
            mForceTouchDetector.setExtraLongPressTimeout(0);
            mForceTouchDetector.setWindowTimeInMillis(0);
            mForceTouchDetector.setWindowDelayInMillis(0);
            mForceTouchDetector.setSensitivity(getContext(), mSettings.detectionSensitivity);
            mForceTouchDetector.setBlockDragging(true);
            mForceTouchDetector.setMagnification(0);
            mForceTouchDetector.setMultipleForceTouch(false);
            mForceTouchDetector.setLongClickable(mSettings.largeTouchActionLongPress.type != ActionInfo.TYPE_NONE);
        }

        @Override
        protected boolean dispatchTouchEvent(MotionEvent event) {
            return mSettings.largeTouchEnable && mForceTouchDetector.onTouchEvent(event);
        }

        @Override
        public boolean onForceTouch(float x, float y) {
            return false;
        }

        @Override
        public void onForceTap(float x, float y) {
            performAction(mSettings.largeTouchActionTap, x, y, "large tap");
        }

        @Override
        public void onForceLongPress(float x, float y) {
            performAction(mSettings.largeTouchActionLongPress, x, y, "large long press");
        }

        @Override
        public boolean onTouchDown(float x, float y, float size) {
            if (isInDetectionArea(x, y) && size > mSettings.largeTouchThreshold) {
                startRipple(x, y);
                return true;
            }
            return false;
        }
    }

    static class KnuckleTouchDetector extends BaseForceTouchDetector
            implements ForceTouchDetector.Callback {

        public KnuckleTouchDetector(ViewGroup targetView, FTD.Settings settings) {
            super(targetView, settings);
        }

        @Override
        public void onSettingsLoaded() {
            mForceTouchDetector.setExtraLongPressTimeout(0);
            mForceTouchDetector.setWindowTimeInMillis(0);
            mForceTouchDetector.setWindowDelayInMillis(0);
            mForceTouchDetector.setSensitivity(getContext(), mSettings.detectionSensitivity);
            mForceTouchDetector.setBlockDragging(true);
            mForceTouchDetector.setMagnification(0);
            mForceTouchDetector.setMultipleForceTouch(false);
            mForceTouchDetector.setLongClickable(mSettings.knuckleTouchActionLongPress.type != ActionInfo.TYPE_NONE);
        }

        @Override
        protected boolean dispatchTouchEvent(MotionEvent event) {
            return mSettings.knuckleTouchEnable && mForceTouchDetector.onTouchEvent(event);
        }

        @Override
        public boolean onForceTouch(float x, float y) {
            return false;
        }

        @Override
        public void onForceTap(float x, float y) {
            performAction(mSettings.knuckleTouchActionTap, x, y, "knuckle tap");
        }

        @Override
        public void onForceLongPress(float x, float y) {
            performAction(mSettings.knuckleTouchActionLongPress, x, y, "knuckle long press");
        }

        @Override
        public boolean onTouchDown(float x, float y, float size) {
            if (isInDetectionArea(x, y) && size < mSettings.knuckleTouchThreshold) {
                startRipple(x, y);
                return true;
            }
            return false;
        }
    }

    static class WiggleTouchDetector extends BaseForceTouchDetector
            implements ForceTouchDetector.Callback {

        public WiggleTouchDetector(ViewGroup targetView, FTD.Settings settings) {
            super(targetView, settings);
        }

        @Override
        public void onSettingsLoaded() {
            if (mForceTouchDetector != null) {
                final int delay = 100;
                int window = mSettings.detectionWindow - delay;
                if (window < 0) {
                    window = 0;
                }
                mForceTouchDetector.setExtraLongPressTimeout(mSettings.extraLongPressTimeout);
                mForceTouchDetector.setWindowTimeInMillis(window);
                mForceTouchDetector.setWindowDelayInMillis(delay);
                mForceTouchDetector.setSensitivity(getContext(), mSettings.detectionSensitivity);
                mForceTouchDetector.setBlockDragging(true);
                mForceTouchDetector.setMagnification(mSettings.wiggleTouchMagnification);
                mForceTouchDetector.setMultipleForceTouch(false);
                mForceTouchDetector.setLongClickable(mSettings.wiggleTouchActionLongPress.type != ActionInfo.TYPE_NONE);
            }
        }

        @Override
        protected boolean dispatchTouchEvent(MotionEvent event) {
            return mSettings.wiggleTouchEnable && mForceTouchDetector.onTouchEvent(event);
        }

        @Override
        public boolean onForceTouch(float x, float y) {
            if (isInDetectionArea(x, y)) {
                startRipple(x, y);
                return true;
            }
            return false;
        }

        @Override
        public void onForceTap(float x, float y) {
            performAction(mSettings.wiggleTouchActionTap, x, y, "wiggle tap");
        }

        @Override
        public void onForceLongPress(float x, float y) {
            performAction(mSettings.wiggleTouchActionLongPress, x, y, "wiggle long press");
        }

        @Override
        public boolean onTouchDown(float x, float y, float size) {
            return false;
        }
    }
}
