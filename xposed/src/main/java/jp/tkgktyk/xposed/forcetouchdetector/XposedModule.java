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

import java.lang.reflect.InvocationTargetException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Created by tkgktyk on 2015/02/08.
 */
public class XposedModule {
    private static String prefix() {
        int stack = 4;
        String method = Thread.currentThread().getStackTrace()[stack].getClassName();
        method += "#" + Thread.currentThread().getStackTrace()[stack].getMethodName();
        method = method.substring(method.lastIndexOf(".") + 1);
        return FTD.NAME + "(" + method + ")";
    }

    public static void logD() {
        if (BuildConfig.DEBUG) {
            XposedBridge.log(prefix() + " [DEBUG]");
        }
    }

    public static void logD(String text) {
        if (BuildConfig.DEBUG) {
            XposedBridge.log(prefix() + " [DEBUG]: " + text);
        }
    }

    public static void log(String text) {
        XposedBridge.log(prefix() + ": " + text);
    }

    public static void logE(Throwable t) {
        XposedBridge.log(t);
    }

    public static Object invokeOriginalMethod(XC_MethodHook.MethodHookParam methodHookParam)
            throws InvocationTargetException, IllegalAccessException {
        return XposedBridge.invokeOriginalMethod(methodHookParam.method,
                methodHookParam.thisObject, methodHookParam.args);
    }
}
