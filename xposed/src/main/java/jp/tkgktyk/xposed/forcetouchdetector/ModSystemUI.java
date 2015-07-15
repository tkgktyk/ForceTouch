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
                if (action.equals(FTD.ACTION_NOTIFICATIONS)) {
                    XposedHelpers.callMethod(mPhoneStatusBar, "animateExpandNotificationsPanel");
                } else if (action.equals(FTD.ACTION_QUICK_SETTINGS)) {
                    XposedHelpers.callMethod(mPhoneStatusBar, "animateExpandSettingsPanel");
                }
            } catch (Throwable t) {
                logE(t);
            }
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
