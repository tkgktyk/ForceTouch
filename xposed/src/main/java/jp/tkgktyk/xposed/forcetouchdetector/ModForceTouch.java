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
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

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
                    ForceTouchDetector ftd = new ForceTouchDetector(decorView, settings);
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
                                handled = ftd.onTouchEvent(event);
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
                            BroadcastReceiver settingsLoadedReceiver = new BroadcastReceiver() {
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
                                    FIELD_SETTINGS_CHANGED_RECEIVER, settingsLoadedReceiver);
                            decorView.getContext().registerReceiver(settingsLoadedReceiver,
                                    FTD.SETTINGS_CHANGED_FILTER);
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
                            BroadcastReceiver settingsLoadedReceiver =
                                    (BroadcastReceiver) XposedHelpers.getAdditionalInstanceField(decorView,
                                            FIELD_SETTINGS_CHANGED_RECEIVER);
                            if (settingsLoadedReceiver != null) {
                                decorView.getContext().unregisterReceiver(settingsLoadedReceiver);
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

    public static class ForceTouchDetector implements GestureDetector.OnGestureListener,
            GestureDetector.OnDoubleTapListener {
        private static final int INVALIDE_POINTER_ID = -1;

        public static final String DEFAULT_THRESHOLD = "1.0";

        private final ViewGroup mTargetView;
        private FTD.Settings mSettings;
        private final GestureDetector mGestureDetector;
        private int mActivePointerId = INVALIDE_POINTER_ID;
        private long mDownTime;
        private Toast mToast;

        // ripple
        private static final int RIPPLE_SIZE = 5;
        private static final float START_SCALE = 0.0f;
        private static final float START_ALPHA = 0.7f;
        private View mRippleView;
        private int mRippleSize;
        private ObjectAnimator mRippleAnimator;

        public ForceTouchDetector(ViewGroup targetView, FTD.Settings settings) {
            mTargetView = targetView;
            onSettingsLoaded(settings);
            mGestureDetector = new GestureDetector(getContext(), this);

            int n = 4;
            multiplyIntField("mTouchSlopSquare", n * n); // 8 * density
            multiplyIntField("mDoubleTapTouchSlopSquare", n * n); // 8 * density
//            multiplyIntField("mDoubleTapSlopSquare", n * n); // 100 * density
            multiplyIntField("mMinimumFlingVelocity", n); // 50 * density

            setUpRipple();
        }

        private void multiplyIntField(String fieldName, int n) {
            int value = XposedHelpers.getIntField(mGestureDetector, fieldName);
//            logD(fieldName + " = " + value);
            XposedHelpers.setIntField(mGestureDetector, fieldName, value * n);
        }

        public void onSettingsLoaded(FTD.Settings settings) {
            mSettings = settings;
        }

        private Context getContext() {
            return mTargetView.getContext();
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

        public void showToast(String text) {
            if (mToast != null) {
                mToast.cancel();
            }
            mToast = Toast.makeText(mTargetView.getContext(), text, Toast.LENGTH_SHORT);
            mToast.show();
        }

        public boolean onTouchEvent(MotionEvent event) {
            if (!mSettings.isEnabled()) {
                return false;
            }

            logD(event.toString());
            boolean consumed = false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    if (judgeAndStartForceTouch(event)) {
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
                    consumeTouchEvent(MotionEvent.ACTION_MOVE, event);
//                        consumed = true;
//                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (mActivePointerId != INVALIDE_POINTER_ID) {
                        consumeTouchEvent(MotionEvent.ACTION_UP, event);
                        consumed = true;
                        mActivePointerId = INVALIDE_POINTER_ID;
                    }
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (mActivePointerId == INVALIDE_POINTER_ID) {
                        if (judgeAndStartForceTouch(event)) {
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
                        consumeTouchEvent(MotionEvent.ACTION_UP, event);
                        consumed = true;
                        mActivePointerId = INVALIDE_POINTER_ID;
                    }
                    break;
            }

            return consumed;
        }

        private void consumeTouchEvent(int action, MotionEvent base) {
            if (action == MotionEvent.ACTION_DOWN) {
                mDownTime = base.getEventTime();
            }
            int index = base.findPointerIndex(mActivePointerId);
            logD("mActivePointerId=" + mActivePointerId);
            logD("index=" + index);
            if (index == -1) {
                return;
            }
            MotionEvent event = MotionEvent
                    .obtain(mDownTime, base.getEventTime(), action,
                            base.getX(index), base.getY(index),
                            base.getPressure(index), base.getSize(index), base.getMetaState(),
                            base.getXPrecision(), base.getYPrecision(), base.getDeviceId(),
                            base.getEdgeFlags());
            logD(event.toString());
            mGestureDetector.onTouchEvent(event);
            event.recycle();
        }

        private FTD.Settings.Holder judgeForceTouch(MotionEvent event) {
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

        private boolean judgeAndStartForceTouch(MotionEvent event) {
            int index = event.getActionIndex();
            FTD.Settings.Holder holder = judgeForceTouch(event);
            if (holder != null) {
                mActivePointerId = event.getPointerId(index);
                mTargetView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                startRipple(event, index);
                return true;
            }
            return false;
        }

        private void startRipple(MotionEvent event, int index) {
            float x = event.getX(index);
            float y = event.getY(index);
            mRippleView.setTranslationX(x - mRippleSize / 2.0f);
            mRippleView.setTranslationY(y - mRippleSize / 2.0f);

            mRippleAnimator.start();
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            FTD.Settings.Holder holder = judgeForceTouch(e);
            if (!FTD.performAction(mTargetView, holder.actionTap, e)) {
                showToast("force tap");
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            FTD.Settings.Holder holder = judgeForceTouch(e);
            if (!FTD.performAction(mTargetView, holder.actionDoubleTap, e)) {
                showToast("force double tap");
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
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            mTargetView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            FTD.Settings.Holder holder = judgeForceTouch(e);
            if (!FTD.performAction(mTargetView, holder.actionLongPress, e)) {
                showToast("force long press");
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            mTargetView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            FTD.Settings.Holder holder = judgeForceTouch(e1);
            float x = e2.getX() - e1.getX();
            float y = e2.getY() - e1.getY();
            if (Math.abs(x) > Math.abs(y)) {
                if (x > 0) {
                    if (!FTD.performAction(mTargetView, holder.actionFlickRight, e2)) {
                        showToast("force fling x: " + x);
                    }
                } else {
                    if (!FTD.performAction(mTargetView, holder.actionFlickLeft, e2)) {
                        showToast("force fling x: " + x);
                    }
                }
            } else {
                if (y > 0) {
                    if (!FTD.performAction(mTargetView, holder.actionFlickDown, e2)) {
                        showToast("force fling y: " + y);
                    }
                } else {
                    if (!FTD.performAction(mTargetView, holder.actionFlickUp, e2)) {
                        showToast("force fling y: " + y);
                    }
                }
            }
            return true;
        }
    }
}
