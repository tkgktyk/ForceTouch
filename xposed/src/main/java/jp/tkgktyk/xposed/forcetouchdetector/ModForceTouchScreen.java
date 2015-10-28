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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import jp.tkgktyk.lib.ForceTouchDetector;
import jp.tkgktyk.lib.ForceTouchScreenHelper;

/**
 * Created by tkgktyk on 2015/02/12.
 */
public class ModForceTouchScreen extends XposedModule {
    private static final String CLASS_DECOR_VIEW = "com.android.internal.policy.impl.PhoneWindow$DecorView";
    private static final String FIELD_DETECTOR = FTD.NAME + "_forceTouchDetector";
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
        XposedHelpers.findAndHookMethod(classDecorView, "dispatchTouchEvent", MotionEvent.class,
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        boolean handled;
                        try {
                            FrameLayout decorView = (FrameLayout) methodHookParam.thisObject;
                            MotionEvent event = (MotionEvent) methodHookParam.args[0];
                            Detector detector = getDetector(decorView);
                            if (detector != null) {
                                handled = detector.dispatchTouchEvent(event, methodHookParam);
                            } else {
                                handled = (Boolean) invokeOriginalMethod(methodHookParam);
                            }
                        } catch (Throwable t) {
                            logE(t);
                            handled = (Boolean) invokeOriginalMethod(methodHookParam);
                        }
                        return handled;
                    }
                });

        XposedHelpers.findAndHookMethod(classDecorView, "onAttachedToWindow",
                new XC_MethodHook() {
                    private FrameLayout mDecorView;

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            mDecorView = (FrameLayout) param.thisObject;
                            install();
                            registerReceiver();
                        } catch (Throwable t) {
                            logE(t);
                        }
                    }

                    private void install() {
                        Detector detector = getDetector(mDecorView);
                        if (detector != null) {
                            return;
                        }
                        Context context = mDecorView.getContext();
                        mPrefs.reload();
                        FTD.Settings settings = new FTD.Settings(context, mPrefs);
                        settings.blacklist.add(FTD.PACKAGE_NAME);
                        String packageName = context.getPackageName();
                        if (settings.blacklist.contains(packageName)) {
                            // blacklist
                            logD("ignore: " + packageName);
                            return;
                        }
                        detector = new ForceTouchScreen(mDecorView, settings);
                        XposedHelpers.setAdditionalInstanceField(mDecorView,
                                FIELD_DETECTOR, detector);
                    }

                    private void registerReceiver() {
                        BroadcastReceiver settingsChangedReceiver = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                Detector detector = getDetector(mDecorView);
                                if (detector == null) {
                                    return;
                                }
                                logD(mDecorView.getContext().getPackageName() + ": reload settings");
                                FTD.Settings settings = (FTD.Settings) intent
                                        .getSerializableExtra(FTD.EXTRA_SETTINGS);
                                detector.onSettingsLoaded(settings);
                            }
                        };
                        XposedHelpers.setAdditionalInstanceField(mDecorView,
                                FIELD_SETTINGS_CHANGED_RECEIVER, settingsChangedReceiver);
                        mDecorView.getContext().registerReceiver(settingsChangedReceiver,
                                new IntentFilter(FTD.ACTION_SETTINGS_CHANGED));
                    }
                });
        XposedHelpers.findAndHookMethod(classDecorView, "onDetachedFromWindow",
                new XC_MethodHook() {
                    private FrameLayout mDecorView;

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            mDecorView = (FrameLayout) param.thisObject;
                            unregisterReceiver();
                            uninstall();
                        } catch (Throwable t) {
                            logE(t);
                        }
                    }

                    private void unregisterReceiver() {
                        BroadcastReceiver settingsChangedReceiver =
                                (BroadcastReceiver) XposedHelpers.getAdditionalInstanceField(mDecorView,
                                        FIELD_SETTINGS_CHANGED_RECEIVER);
                        if (settingsChangedReceiver != null) {
                            mDecorView.getContext().unregisterReceiver(settingsChangedReceiver);
                        }
                    }

                    private void uninstall() {
                        Detector detector = getDetector(mDecorView);
                        if (detector != null) {
                            detector.onDestroy();
                            XposedHelpers.removeAdditionalInstanceField(mDecorView, FIELD_DETECTOR);
                        }
                    }
                });
    }

    private static Detector getDetector(View decorView) {
        return (Detector) XposedHelpers.getAdditionalInstanceField(decorView, FIELD_DETECTOR);
    }

    public static abstract class Detector {
        private final ViewGroup mTargetView;
        protected FTD.Settings mSettings;

        private XC_MethodHook.MethodHookParam mMethodHookParam;

        public Detector(ViewGroup targetView, FTD.Settings settings) {
            mTargetView = targetView;
            mSettings = settings;
        }

        public final void onSettingsLoaded(FTD.Settings settings) {
            mSettings = settings;
            onSettingsLoaded();
        }

        protected abstract void onSettingsLoaded();

        protected abstract void onDestroy();

        protected float getMethodParameter(MotionEvent event, int index) {
            return mSettings.detectorMethod == FTD.METHOD_PRESSURE ?
                    ForceTouchScreenHelper.getPressure(event, index) :
                    ForceTouchScreenHelper.getSize(event, index);
        }

        protected Context getContext() {
            return mTargetView.getContext();
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

        protected void performHapticFeedback() {
            if (mSettings.vibration) {
                mTargetView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        }

        protected boolean invokeOriginalDispatchTouchEvent(MotionEvent event) {
            boolean ret = false;
            Object backup = mMethodHookParam.args[0];
            mMethodHookParam.args[0] = event;
            try {
                ret = (Boolean) invokeOriginalMethod(mMethodHookParam);
            } catch (Throwable t) {
                logE(t);
            }
            mMethodHookParam.args[0] = backup;
            return ret;
        }

        protected void sendBroadcast(String action, float x, float y) {
            Intent intent = new Intent(action);
            intent.putExtra(FTD.EXTRA_X, x);
            intent.putExtra(FTD.EXTRA_Y, y);
            getContext().sendBroadcast(intent);
        }
    }

    private static class ForceTouchScreen extends Detector
            implements ForceTouchScreenHelper.Callback {
        protected final ForceTouchScreenHelper mForceTouchScreenHelper;

        public ForceTouchScreen(ViewGroup targetView, FTD.Settings settings) {
            super(targetView, settings);

            mForceTouchScreenHelper = new ForceTouchScreenHelper(this);
            onSettingsLoaded(settings);
        }

        @Override
        protected void onSettingsLoaded() {
            final int delay = 100;
            int window = mSettings.detectionWindow - delay;
            if (window < 0) {
                window = 0;
            }
            mForceTouchScreenHelper.setWindowTimeInMillis(window);
            mForceTouchScreenHelper.setWindowDelayInMillis(delay);
            mForceTouchScreenHelper.setSensitivity(getContext(), mSettings.detectionSensitivity);
            mForceTouchScreenHelper.setMagnification(mSettings.wiggleTouchMagnification);
            mForceTouchScreenHelper.setType(ForceTouchDetector.TYPE_WIGGLE);
            mForceTouchScreenHelper.setRewind(false);
        }

        @Override
        protected void onDestroy() {
            if (mForceTouchScreenHelper.getCount() > 0) {
                onForceTouchCancel(0.0f, 0.0f, mForceTouchScreenHelper.getCount());
            }
        }

        @Override
        protected boolean dispatchTouchEvent(MotionEvent event) {
            return mSettings.forceTouchScreenEnable && mForceTouchScreenHelper.onTouchEvent(event);
        }

        @Override
        public boolean onForceTouchBegin(float x, float y, float startX, float startY) {
            if (isInDetectionArea(startX, startY)) {
                performHapticFeedback();
                sendBroadcast(FTD.ACTION_FORCE_TOUCH_BEGIN, x, y);
                return true;
            }
            return false;
        }

        @Override
        public void onForceTouchDown(float x, float y, int count) {
            performHapticFeedback();
            sendBroadcast(FTD.ACTION_FORCE_TOUCH_DOWN, x, y);
        }

        @Override
        public void onForceTouchUp(float x, float y, int count) {
            sendBroadcast(FTD.ACTION_FORCE_TOUCH_UP, x, y);
        }

        @Override
        public void onForceTouchEnd(float x, float y, int count) {
            sendBroadcast(FTD.ACTION_FORCE_TOUCH_END, x, y);
        }

        @Override
        public void onForceTouchCancel(float x, float y, int count) {
            sendBroadcast(FTD.ACTION_FORCE_TOUCH_CANCEL, x, y);
        }

        @Override
        public boolean performOriginalOnTouchEvent(MotionEvent event) {
            return invokeOriginalDispatchTouchEvent(event);
        }

        @Override
        public float getParameter(MotionEvent event, int index) {
            return getMethodParameter(event, index);
        }
    }
}
