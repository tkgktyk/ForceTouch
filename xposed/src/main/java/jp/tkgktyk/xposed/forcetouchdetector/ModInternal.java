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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Process;

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
                if (action.equals(FTD.ACTION_KILL)) {
                    killForegroundApp(context);
                }
            } catch (Throwable t) {
                logE(t);
            }
        }

        // Based on GravityBox[LP]
        private void killForegroundApp(final Context context) {
            Handler handler = (Handler) XposedHelpers.getObjectField(mPhoneWindowManager, "mHandler");
            if (handler == null) {
                return;
            }

            handler.post(new Runnable() {
                @Override
                public void run() {
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
                }
            });
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
