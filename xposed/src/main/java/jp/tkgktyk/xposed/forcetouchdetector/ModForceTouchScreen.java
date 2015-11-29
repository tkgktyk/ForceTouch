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
import android.os.Build;
import android.support.annotation.Nullable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.google.common.collect.Lists;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import jp.tkgktyk.lib.ForceTouchDetector;
import jp.tkgktyk.lib.ForceTouchScreenHelper;
import jp.tkgktyk.lib.RelativeDetector;
import jp.tkgktyk.xposed.forcetouchdetector.app.util.ActionInfo;

/**
 * Created by tkgktyk on 2015/02/12.
 */
public class ModForceTouchScreen extends XposedModule {
    private static final String CLASS_DECOR_VIEW = "com.android.internal.policy.impl.PhoneWindow$DecorView";
    private static final String CLASS_DECOR_VIEW_M = "com.android.internal.policy.PhoneWindow$DecorView";
    private static final String CLASS_POPUP_WINDOW = "android.widget.PopupWindow";
    private static final String CLASS_POPUP_VIEW_CONTAINER = "android.widget.PopupWindow$PopupViewContainer";
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
        final XC_MethodReplacement dispatchTouchEvent = new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                boolean handled = false;
                try {
                    View container = (View) methodHookParam.thisObject;
                    MotionEvent event = (MotionEvent) methodHookParam.args[0];
                    List<Detector> detectors = getDetectors(container);
                    if (detectors != null) {
                        for (Detector ftd : detectors) {
                            handled = handled || ftd.dispatchTouchEvent(event, methodHookParam);
                        }
                    }
                    if (!handled) {
                        handled = (Boolean) invokeOriginalMethod(methodHookParam);
                    }
                } catch (Throwable t) {
                    logE(t);
                    handled = (Boolean) invokeOriginalMethod(methodHookParam);
                }
                return handled;
            }
        };
        class Installer extends XC_MethodHook {
            protected void run(@Nullable View target) {
                if (target == null) {
                    return;
                }
                try {
                    install(target);
                    registerReceiver(target);
                } catch (Throwable t) {
                    logE(t);
                }
            }

            private void install(View target) {
                List<Detector> detectors = getDetectors(target);
                if (detectors != null) {
                    return;
                }
                Context context = target.getContext();
                mPrefs.reload();
                FTD.Settings settings = new FTD.Settings(context, mPrefs);
                settings.blacklist.add(FTD.PACKAGE_NAME);
                String packageName = context.getPackageName();
                if (settings.blacklist.contains(packageName)) {
                    // blacklist
                    logD("ignore: " + packageName);
                    return;
                }
                detectors = Lists.newArrayList();
                detectors.add(new ForceTouchScreen(target, settings));
                detectors.add(new ScratchTouchDetector(target, settings));
                XposedHelpers.setAdditionalInstanceField(target,
                        FIELD_DETECTORS, detectors);
            }

            private void registerReceiver(final View target) {
                BroadcastReceiver settingsChangedReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        List<Detector> detectors = getDetectors(target);
                        if (detectors == null) {
                            return;
                        }
                        logD(target.getContext().getPackageName() + ": reload settings");
                        FTD.Settings settings = (FTD.Settings) intent
                                .getSerializableExtra(FTD.EXTRA_SETTINGS);
                        for (Detector detector : detectors) {
                            detector.onSettingsLoaded(settings);
                        }
                    }
                };
                XposedHelpers.setAdditionalInstanceField(target,
                        FIELD_SETTINGS_CHANGED_RECEIVER, settingsChangedReceiver);
                target.getContext().registerReceiver(settingsChangedReceiver,
                        new IntentFilter(FTD.ACTION_SETTINGS_CHANGED));
            }
        }
        class Uninstaller extends XC_MethodHook {
            protected void run(@Nullable View target) {
                if (target == null) {
                    return;
                }
                try {
                    unregisterReceiver(target);
                    uninstall(target);
                } catch (Throwable t) {
                    logE(t);
                }
            }

            private void unregisterReceiver(View target) {
                BroadcastReceiver settingsChangedReceiver =
                        (BroadcastReceiver) XposedHelpers.getAdditionalInstanceField(target,
                                FIELD_SETTINGS_CHANGED_RECEIVER);
                if (settingsChangedReceiver != null) {
                    target.getContext().unregisterReceiver(settingsChangedReceiver);
                }
            }

            private void uninstall(View target) {
                List<Detector> detectors = getDetectors(target);
                if (detectors != null) {
                    for (Detector detector : detectors) {
                        detector.onDestroy();
                    }
                    XposedHelpers.removeAdditionalInstanceField(target, FIELD_DETECTORS);
                }
            }
        }

        Class<?> classDecorView;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            XposedHelpers.findAndHookMethod(ViewGroup.class, "dispatchTouchEvent",
                    MotionEvent.class, dispatchTouchEvent);
            classDecorView = XposedHelpers.findClass(CLASS_DECOR_VIEW_M, null);
        } else {
            classDecorView = XposedHelpers.findClass(CLASS_DECOR_VIEW, null);
            XposedHelpers.findAndHookMethod(classDecorView, "dispatchTouchEvent",
                    MotionEvent.class, dispatchTouchEvent);
            final Class<?> classPopupViewContainer = XposedHelpers
                    .findClass(CLASS_POPUP_VIEW_CONTAINER, null);
            XposedHelpers.findAndHookMethod(classPopupViewContainer, "dispatchTouchEvent",
                    MotionEvent.class, dispatchTouchEvent);
        }
        XposedHelpers.findAndHookMethod(classDecorView, "onAttachedToWindow",
                new Installer() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        run((View) param.thisObject);
                    }
                });
        XposedHelpers.findAndHookMethod(classDecorView, "onDetachedFromWindow",
                new Uninstaller() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        run((View) param.thisObject);
                    }
                });

        final Class<?> classPopupWindow = XposedHelpers.findClass(CLASS_POPUP_WINDOW, null);
        XposedHelpers.findAndHookMethod(classPopupWindow, "invokePopup", WindowManager.LayoutParams.class,
                new Installer() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        run(getPopupView(param));
                    }
                });
        XposedHelpers.findAndHookMethod(classPopupWindow, "dismiss",
                new Uninstaller() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        run(getPopupView(param));
                    }
                });
    }

    private static List<Detector> getDetectors(View decorView) {
        return (List<Detector>) XposedHelpers.getAdditionalInstanceField(decorView, FIELD_DETECTORS);
    }

    private static View getPopupView(XC_MethodHook.MethodHookParam param) {
        return (View) XposedHelpers.getObjectField(param.thisObject,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                        "mBackgroundView" : "mPopupView");
    }

    public static abstract class Detector {
        protected final View mTargetView;
        protected FTD.Settings mSettings;

        private XC_MethodHook.MethodHookParam mMethodHookParam;

        public Detector(View targetView, FTD.Settings settings) {
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
            intent.putExtra(FTD.EXTRA_PACKAGE_NAME, getContext().getPackageName());

            int location[] = new int[2];
            mTargetView.getLocationOnScreen(location);
            intent.putExtra(FTD.EXTRA_X, x + location[0]);
            intent.putExtra(FTD.EXTRA_Y, y + location[1]);
            getContext().sendBroadcast(intent);
        }
    }

    private static class ForceTouchScreen extends Detector
            implements ForceTouchScreenHelper.Callback {
        protected final ForceTouchScreenHelper mForceTouchScreenHelper;

        public ForceTouchScreen(View targetView, FTD.Settings settings) {
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
            mForceTouchScreenHelper.setMagnification(mSettings.wiggleTouchMagnification);
            mForceTouchScreenHelper.setType(ForceTouchScreenHelper.TYPE_WIGGLE);
            mForceTouchScreenHelper.setRewind(false);
            mForceTouchScreenHelper.allowUnknownType(mSettings.allowUnknownInputType);
        }

        @Override
        protected void onDestroy() {
            if (mForceTouchScreenHelper.getCount() > 0) {
                onForceTouchCancel(0.0f, 0.0f, mForceTouchScreenHelper.getCount());
            }
        }

        @Override
        protected boolean dispatchTouchEvent(MotionEvent event) {
            return mSettings.forceTouchScreenEnable ?
                    mForceTouchScreenHelper.onTouchEvent(event) :
                    performOriginalOnTouchEvent(event);
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

    private static class ScratchTouchDetector extends Detector
            implements ForceTouchDetector.Callback {

        private final RelativeDetector mRelativeDetector;

        public ScratchTouchDetector(View targetView, FTD.Settings settings) {
            super(targetView, settings);

            mRelativeDetector = new RelativeDetector(this);
            onSettingsLoaded(settings);
        }

        @Override
        public void onSettingsLoaded() {
            if (mRelativeDetector != null) {
                final int delay = 100;
                int window = mSettings.detectionWindow - delay;
                if (window < 0) {
                    window = 0;
                }
                mRelativeDetector.setExtraLongPressTimeout(mSettings.extraLongPressTimeout);
                mRelativeDetector.setWindowTimeInMillis(window);
                mRelativeDetector.setWindowDelayInMillis(delay);
                mRelativeDetector.setSensitivity(getContext(), mSettings.detectionSensitivity);
                mRelativeDetector.setBlockDragging(true);
                mRelativeDetector.setMagnification(mSettings.scratchTouchMagnification);
                mRelativeDetector.setMultipleForceTouch(false);
                mRelativeDetector.setLongClickable(mSettings.scratchTouchActionLongPress.type != ActionInfo.TYPE_NONE);
                mRelativeDetector.setType(RelativeDetector.TYPE_SCRATCH);
                mRelativeDetector.setRewind(false);
                mRelativeDetector.allowUnknownType(mSettings.allowUnknownInputType);
            }
        }

        @Override
        protected void onDestroy() {
        }

        @Override
        protected boolean dispatchTouchEvent(MotionEvent event) {
            return mSettings.scratchTouchEnable && mRelativeDetector.onTouchEvent(event);
        }

        @Override
        public void performOriginalOnTouchEvent(MotionEvent event) {
            invokeOriginalDispatchTouchEvent(event);
        }

        @Override
        public float getParameter(MotionEvent event, int index) {
            return getMethodParameter(event, index);
        }

        @Override
        public boolean onAbsoluteTouch(float x, float y, float parameter) {
            // never reach
            return false;
        }

        @Override
        public boolean onRelativeTouch(float x, float y, float startX, float startY) {
            if (isInDetectionArea(startX, startY)) {
//                performHapticFeedback();
                return true;
            }
            return false;
        }

        @Override
        public boolean onForceTap(float x, float y) {
            return false;
        }

        @Override
        public boolean onForceLongPress(float x, float y) {
            performHapticFeedback();
            performAction(mSettings.scratchTouchActionLongPress, x, y);
            return true;
        }

        private void performAction(final ActionInfo.Record record, final float x, final float y) {
            // force delay for complete ACTION_UP of gesture detector
            mTargetView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    FTD.performAction(mTargetView, new ActionInfo(record), x, y);
                }
            }, 1);
        }
    }
}
