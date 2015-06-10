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
public class ModActivity extends XposedModule {
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
                    Context context = decorView.getContext();
                    if (context.getPackageName().equals(FTD.PACKAGE_NAME)) {
                        // blacklist
                        return;
                    }
                    ForceTouchDetector ftd = new ForceTouchDetector(decorView, newSettings(mPrefs));
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
                            if (ftd != null && ftd.getSettings().enabled) {
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
        public static final String DEFAULT_PRESSURE_THRESHOLD = "1.0";

        private final ViewGroup mTargetView;
        private FTD.Settings mSettings;
        private final GestureDetector mGestureDetector;
        private boolean mIsForceTouch;
        private Toast mToast;

        public ForceTouchDetector(ViewGroup targetView, FTD.Settings settings) {
            mTargetView = targetView;
            onSettingsLoaded(settings);
            mGestureDetector = new GestureDetector(mTargetView.getContext(), this);

            int n = 4;
            n = n * n;
            multiplyIntField("mTouchSlopSquare", n);
            multiplyIntField("mDoubleTapTouchSlopSquare", n);
            multiplyIntField("mDoubleTapSlopSquare", n);
        }

        private void multiplyIntField(String fieldName, int n) {
            int value = XposedHelpers.getIntField(mGestureDetector, fieldName);
            XposedHelpers.setIntField(mGestureDetector, fieldName, value * n);
        }

        public void onSettingsLoaded(FTD.Settings settings) {
            mSettings = settings;
        }

        public FTD.Settings getSettings() {
            return mSettings;
        }

        public void showToast(String text) {
            if (mToast != null) {
                mToast.cancel();
            }
            mToast = Toast.makeText(mTargetView.getContext(), text, Toast.LENGTH_SHORT);
            mToast.show();
        }

        public boolean onTouchEvent(MotionEvent event) {
            boolean gesture = false;
            int action = event.getAction() & MotionEvent.ACTION_MASK;
            if (mIsForceTouch && action == MotionEvent.ACTION_MOVE) {
                gesture = true;
            } else {
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        float y = event.getY();
                        if (event.getPressure() > mSettings.pressureThreshold &&
                                y > mTargetView.getHeight() * mSettings.forceTouchArea) {
                            mTargetView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                            mIsForceTouch = true;
                            gesture = true;
                            startRipple(event);
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        mIsForceTouch = false;
                        gesture = true;
                        break;
                }
            }

            if (gesture) {
                mGestureDetector.onTouchEvent(event);
            }
            return mIsForceTouch;
        }

        private static final int RIPPLE_SIZE = 5;

        private void startRipple(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            Context context = mTargetView.getContext();
            final View ripple = new View(context);
            int size = context.getResources().getDimensionPixelSize(android.R.dimen.app_icon_size) *
                    RIPPLE_SIZE;
            ripple.setLayoutParams(new ViewGroup.LayoutParams(size, size));
            Context mod = FTD.getModContext(context);
            ripple.setBackground(mod.getResources().getDrawable(R.drawable.force_touch_ripple));
            ripple.setTranslationX(x - size / 2.0f);
            ripple.setTranslationY(y - size / 2.0f);

            float startScale = 1.0f / RIPPLE_SIZE;
            PropertyValuesHolder holderScaleX = PropertyValuesHolder.ofFloat("scaleX", startScale, 1.0f);
            PropertyValuesHolder holderScaleY = PropertyValuesHolder.ofFloat("scaleY", startScale, 1.0f);
            PropertyValuesHolder holderAlpha = PropertyValuesHolder.ofFloat("alpha", 0.7f, 0.0f);

            ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(ripple,
                    holderScaleX, holderScaleY, holderAlpha);
            animator.setDuration(300); // default
            animator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mTargetView.addView(ripple);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mTargetView.removeView(ripple);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    mTargetView.removeView(ripple);
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
            animator.start();
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (!FTD.performAction(mTargetView, mSettings.actionTap, e)) {
                showToast("force tap");
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (!FTD.performAction(mTargetView, mSettings.actionDoubleTap, e)) {
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
            if (!FTD.performAction(mTargetView, mSettings.actionLongPress, e)) {
                showToast("force long press");
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            mTargetView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (Math.abs(velocityX) > Math.abs(velocityY)) {
                if (velocityX > 0) {
                    if (!FTD.performAction(mTargetView, mSettings.actionFlickRight, e2)) {
                        showToast("force fling x: " + velocityX);
                    }
                } else {
                    if (!FTD.performAction(mTargetView, mSettings.actionFlickLeft, e2)) {
                        showToast("force fling x: " + velocityX);
                    }
                }
            } else {
                if (velocityY > 0) {
                    if (!FTD.performAction(mTargetView, mSettings.actionFlickDown, e2)) {
                        showToast("force fling y: " + velocityY);
                    }
                } else {
                    if (!FTD.performAction(mTargetView, mSettings.actionFlickUp, e2)) {
                        showToast("force fling y: " + velocityY);
                    }
                }
            }
            return true;
        }
    }
}
