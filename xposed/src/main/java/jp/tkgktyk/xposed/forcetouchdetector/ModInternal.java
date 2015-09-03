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

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public class ModInternal extends XposedModule {
    private static final String CLASS_PHONE_WINDOW_MANAGER = "com.android.internal.policy.impl.PhoneWindowManager";
    private static final String CLASS_WINDOW_MANAGER_FUNCS = "android.view.WindowManagerPolicy.WindowManagerFuncs";
    private static final String CLASS_IWINDOW_MANAGER = "android.view.IWindowManager";

    private static XSharedPreferences mPrefs;

    private static Object mPhoneWindowManager;

    private static final BroadcastReceiver mActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                final String action = intent.getAction();
                logD(action);
                //
                // Key Action
                //
                if (action.equals(FTD.ACTION_BACK)) {
                    sendKeyEvent(KeyEvent.KEYCODE_BACK);
                } else if (action.equals(FTD.ACTION_HOME)) {
                    sendKeyEvent(KeyEvent.KEYCODE_HOME);
                } else if (action.equals(FTD.ACTION_RECENTS)) {
                    sendKeyEvent(KeyEvent.KEYCODE_APP_SWITCH);
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
                } else if (action.equals(FTD.ACTION_SCREENSHOT)) {
                    sendKeyEvent(KeyEvent.KEYCODE_SYSRQ);
                } else if (action.equals(FTD.ACTION_LOCK_SCREEN)) {
                    sendKeyEvent(KeyEvent.KEYCODE_POWER);
                } else if (action.equals(FTD.ACTION_LAST_APP)) {
                    // TODO: not work
                    sendKeyEventAlt(KeyEvent.KEYCODE_TAB);
                } else if (action.equals(FTD.ACTION_MENU)) {
                    sendKeyEvent(KeyEvent.KEYCODE_MENU);

                    //
                    // status bar service
                    //
                } else if (action.equals(FTD.ACTION_NOTIFICATIONS)) {
                    Object statusBarManager = XposedHelpers.callMethod(mPhoneWindowManager, "getStatusBarService");
                    XposedHelpers.callMethod(statusBarManager, "expandNotificationsPanel");
                } else if (action.equals(FTD.ACTION_QUICK_SETTINGS)) {
                    Object statusBarManager = XposedHelpers.callMethod(mPhoneWindowManager, "getStatusBarService");
                    XposedHelpers.callMethod(statusBarManager, "expandSettingsPanel");

                    //
                    // Other function
                    //
                } else if (action.equals(FTD.ACTION_KILL)) {
                    killForegroundApp(context);
                } else if (action.equals(FTD.ACTION_POWER_MENU)) {
                    showPowerMenu();
                } else if (action.equals(FTD.ACTION_SELECT_KEYBOARD)) {
                    InputMethodManager imeManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imeManager.showInputMethodPicker();
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
                    KeyEvent alt = new KeyEvent(downTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT, 0, 0);
                    Instrumentation ist = new Instrumentation();
                    ist.sendKeySync(alt);
                    {
                        KeyEvent key = new KeyEvent(downTime, eventTime, KeyEvent.ACTION_DOWN, code, 0, KeyEvent.META_ALT_ON);
                        ist.sendKeySync(key);
                        key = KeyEvent.changeAction(key, KeyEvent.ACTION_UP);
                        eventTime = SystemClock.uptimeMillis() + 100;
                        key = KeyEvent.changeTimeRepeat(key, eventTime, 0);
                        ist.sendKeySync(key);
                    }
                    alt = KeyEvent.changeAction(alt, KeyEvent.ACTION_UP);
                    eventTime = SystemClock.uptimeMillis() + 100;
                    alt = KeyEvent.changeTimeRepeat(alt, eventTime, 0);
                    ist.sendKeySync(alt);
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
                    sendKeyEvent(instrumentation, KeyEvent.KEYCODE_DPAD_DOWN, 1); // workaround to focus
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
                    sendKeyEvent(instrumentation, KeyEvent.KEYCODE_DPAD_UP, 1); // workaround to focus
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

        // Based on GravityBox[LP]
        private void killForegroundApp(final Context context) {
//            Handler handler = (Handler) XposedHelpers.getObjectField(mPhoneWindowManager, "mHandler");
//            if (handler == null) {
//                return;
//            }
//
//            handler.post(new Runnable() {
//                @Override
//                public void run() {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            final PackageManager pm = context.getPackageManager();
            String defaultHomePackage = "com.android.launcher";
            intent.addCategory(Intent.CATEGORY_HOME);

            final ResolveInfo res = pm.resolveActivity(intent, 0);
            if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                defaultHomePackage = res.activityInfo.packageName;
            }

            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> apps = am.getRunningAppProcesses();

            for (ActivityManager.RunningAppProcessInfo appInfo : apps) {
                int uid = appInfo.uid;
                // Make sure it's a foreground user application (not system,
                // root, phone, etc.)
                if (uid >= Process.FIRST_APPLICATION_UID && uid <= Process.LAST_APPLICATION_UID
                        && appInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                        !appInfo.processName.startsWith(defaultHomePackage)) {
                    if (appInfo.pkgList != null && appInfo.pkgList.length > 0) {
                        for (String pkg : appInfo.pkgList) {
                            logD("Force stopping: " + pkg);
                            XposedHelpers.callMethod(am, "forceStopPackage", pkg);
                        }
                    } else {
                        logD("Killing process ID " + appInfo.pid + ": " + appInfo.processName);
                        Process.killProcess(appInfo.pid);
                    }
                    break;
                }
            }
//                }
//            });
        }

        private void showPowerMenu() {
//                Handler handler = (Handler) XposedHelpers.getObjectField(mPhoneWindowManager, "mHandler");
//                handler.post(new Runnable() {
//                    @Override
//                    public void run() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                XposedHelpers.callMethod(mPhoneWindowManager, "showGlobalActions");
            } else {
                XposedHelpers.callMethod(mPhoneWindowManager, "showGlobalActionsDialog");
            }
//                    }
//                });
        }
    };

    public static void initZygote(XSharedPreferences prefs) {
        mPrefs = prefs;
        try {
            final Class<?> classPhoneWindowManager = XposedHelpers.findClass(CLASS_PHONE_WINDOW_MANAGER, null);
            XposedHelpers.findAndHookMethod(classPhoneWindowManager, "init",
                    Context.class, CLASS_IWINDOW_MANAGER, CLASS_WINDOW_MANAGER_FUNCS,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            mPhoneWindowManager = param.thisObject;
                            Context context = (Context) XposedHelpers
                                    .getObjectField(mPhoneWindowManager, "mContext");
                            context.registerReceiver(mActionReceiver, FTD.INTERNAL_ACTION_FILTER);
                        }
                    });
        } catch (Throwable t) {
            logE(t);
        }
    }

}
