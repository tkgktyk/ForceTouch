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

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public class ModSystemUI extends XposedModule {
    private static final String CLASS_PHONE_STATUS_BAR = "com.android.systemui.statusbar.phone.PhoneStatusBar";

    private static Object mPhoneStatusBar;

    private static final BroadcastReceiver mActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                final String action = intent.getAction();
                logD(action);
                if (action.equals(FTD.ACTION_BACK)) {
                    sendKeyEvent(KeyEvent.KEYCODE_BACK);
                } else if (action.equals(FTD.ACTION_HOME)) {
                    sendKeyEvent(KeyEvent.KEYCODE_HOME);
//                    Intent home = new Intent(Intent.ACTION_MAIN);
//                    home.addCategory(Intent.CATEGORY_HOME);
//                    home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                    context.startActivity(home);
                } else if (action.equals(FTD.ACTION_RECENTS)) {
                    sendKeyEvent(KeyEvent.KEYCODE_APP_SWITCH);
//                    XposedHelpers.callMethod(mPhoneStatusBar, "toggleRecents");
                } else if (action.equals(FTD.ACTION_NOTIFICATIONS)) {
                    XposedHelpers.callMethod(mPhoneStatusBar, "animateExpandNotificationsPanel");
                } else if (action.equals(FTD.ACTION_QUICK_SETTINGS)) {
                    XposedHelpers.callMethod(mPhoneStatusBar, "animateExpandSettingsPanel");
                } else if (action.equals(FTD.ACTION_FORWARD)) {
                    sendKeyEventAlt(KeyEvent.KEYCODE_DPAD_RIGHT);
                } else if (action.equals(FTD.ACTION_REFRESH)) {
                    sendKeyEvent(KeyEvent.KEYCODE_F5);
                } else if (action.equals(FTD.ACTION_SCROLL_UP_GLOBAL)) {
                    scrollUp();
                } else if (action.equals(FTD.ACTION_SCROLL_DOWN_GLOBAL)) {
                    scrollDown();
                } else if (action.equals(FTD.ACTION_VOLUME_UP)) {
                    sendKeyEvent(KeyEvent.KEYCODE_VOLUME_UP);
                } else if (action.equals(FTD.ACTION_VOLUME_DOWN)) {
                    sendKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN);
                }
            } catch (Throwable t) {
                logE(t);
            }
        }

        private void sendKeyEvent(final int code) {
            new Thread() {
                @Override
                public void run() {
                    Instrumentation ist = new Instrumentation();
                    ist.sendKeyDownUpSync(code);
                }
            }.start();
        }

        private void sendKeyEventAlt(final int code) {
            new Thread() {
                @Override
                public void run() {
                    long downTime = SystemClock.uptimeMillis();
                    long eventTime = SystemClock.uptimeMillis() + 100;
                    KeyEvent key = new KeyEvent(downTime, eventTime, KeyEvent.ACTION_DOWN, code, 0, KeyEvent.META_ALT_ON);
                    Instrumentation ist = new Instrumentation();
                    ist.sendKeySync(key);
                    ist.sendKeySync(KeyEvent.changeAction(key, KeyEvent.ACTION_UP));
                }
            }.start();
        }

        private void scrollUp() {
            new Thread() {
                @Override
                public void run() {
                    Instrumentation instrumentation = new Instrumentation();
                    sendKeyEvent(instrumentation, KeyEvent.KEYCODE_MOVE_HOME, 2);
                    sendKeyEvent(instrumentation, KeyEvent.KEYCODE_PAGE_UP, 10);
                    sendKeyEvent(instrumentation, KeyEvent.KEYCODE_DPAD_UP, 100);
                }
            }.start();
        }

        private void scrollDown() {
            new Thread() {
                @Override
                public void run() {
                    Instrumentation instrumentation = new Instrumentation();
                    sendKeyEvent(instrumentation, KeyEvent.KEYCODE_MOVE_END, 2);
                    sendKeyEvent(instrumentation, KeyEvent.KEYCODE_PAGE_DOWN, 10);
                    sendKeyEvent(instrumentation, KeyEvent.KEYCODE_DPAD_DOWN, 100);
                }
            }.start();
        }

        private void sendKeyEvent(Instrumentation instrumentation, int code, int repeat) {
            KeyEvent key = new KeyEvent(KeyEvent.ACTION_DOWN, code);
            for (int i = 0; i < repeat; ++i) {
                instrumentation.sendKeySync(key);
            }
            KeyEvent.changeAction(key, KeyEvent.ACTION_UP);
            instrumentation.sendKeySync(key);
        }

    };

    public static void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!loadPackageParam.packageName.equals("com.android.systemui")) {
            return;
        }
        try {
            install(loadPackageParam.classLoader);
        } catch (Throwable t) {
            logE(t);
        }
    }

    private static void install(ClassLoader classLoader) {
        final Class<?> classPhoneStatusBar = XposedHelpers.findClass(CLASS_PHONE_STATUS_BAR, classLoader);
//        XposedBridge.hookAllConstructors(classPhoneStatusBar, new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                try {
//                    mPhoneStatusBar = param.thisObject;
//                    Context context = (Context) XposedHelpers.getObjectField(mPhoneStatusBar, "mContext");
//
//                    context.registerReceiver(mActionReceiver, FTD.SYSTEM_UI_ACTION_FILTER);
//                } catch (Throwable t) {
//                    logE(t);
//                }
//            }
//        });
        XposedHelpers.findAndHookMethod(classPhoneStatusBar, "makeStatusBarView", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    mPhoneStatusBar = param.thisObject;
                    Context context = (Context) XposedHelpers.getObjectField(mPhoneStatusBar, "mContext");

                    context.registerReceiver(mActionReceiver, FTD.SYSTEM_UI_ACTION_FILTER);
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
    }
}
