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

package jp.tkgktyk.lib;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.StringRes;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by tkgktyk on 2015/05/02.
 */
public abstract class BaseApplication extends Application {
    private static final String PREF_KEY_VERSION_NAME
            = BaseApplication.class.getSimpleName() + ".key_version_name";
    private static Context sContext;
    private static boolean DEBUG;

    private static String getMethodName() {
        String method = Thread.currentThread().getStackTrace()[4].getClassName();
        method += "#" + Thread.currentThread().getStackTrace()[4].getMethodName();
        method = method.substring(method.lastIndexOf(".") + 1);
        return method;
    }

    public static void logD(String text) {
        if (DEBUG) {
            Log.d(getMethodName(), text);
        }
    }

    public static void logD() {
        if (DEBUG) {
            Log.d("LogD", getMethodName());
        }
    }

    public static void logE(String text) {
        Log.e(getMethodName(), text);
    }

    public static void logE(Throwable t) {
        t.printStackTrace();
        Log.e(getMethodName(), t.toString());
    }

    public static void logE(String text, Throwable t) {
        Log.e(getMethodName(), text, t);
    }

    public static void showToast(@StringRes int id) {
        showToast(sContext.getString(id));
    }

    public static void showToast(String text) {
        Toast.makeText(sContext, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCreate() {
        sContext = this;
        DEBUG = isDebug();

        logD("check version");
        // get last running version
        MyVersion old = new MyVersion(getDefaultSharedPreferences()
                .getString(PREF_KEY_VERSION_NAME, ""));
        // save current version
        MyVersion current = new MyVersion(this);

        if (current.isNewerThan(old)) {
            logD("updated");
            onVersionUpdated(current, old);

            // reload preferences and put new version name
            final SharedPreferences prefs = getDefaultSharedPreferences();
            prefs.edit()
                    .putString(PREF_KEY_VERSION_NAME, current.toString())
                    .apply();
        }
        logD("start application");

        super.onCreate();
    }

    protected SharedPreferences getDefaultSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    protected abstract boolean isDebug();

    protected abstract String getVersionName();

    protected abstract void onVersionUpdated(MyVersion next, MyVersion old);

    public class MyVersion {
        public static final int BASE = 1000;

        int major = 0;
        int minor = 0;
        int revision = 0;

        public MyVersion(String version) {
            set(version);
        }

        public MyVersion(Context context) {
            set(getVersionName());
        }

        public void set(String version) {
            if (TextUtils.isEmpty(version)) {
                return;
            }

            String[] v = version.split("\\.");
            int n = v.length;
            if (n >= 1) {
                major = Integer.parseInt(v[0]);
            }
            if (n >= 2) {
                minor = Integer.parseInt(v[1]);
            }
            if (n >= 3) {
                revision = Integer.parseInt(v[2]);
            }
        }

        public int toInt() {
            return major * BASE * BASE + minor * BASE + revision;
        }

        public boolean isNewerThan(MyVersion v) {
            return toInt() > v.toInt();
        }

        public boolean isNewerThan(String v) {
            return isNewerThan(new MyVersion(v));
        }

        public boolean isOlderThan(MyVersion v) {
            return toInt() < v.toInt();
        }

        public boolean isOlderThan(String v) {
            return isOlderThan(new MyVersion(v));
        }

        @Override
        public String toString() {
            return Integer.toString(major)
                    + "." + Integer.toString(minor)
                    + "." + Integer.toString(revision);
        }
    }
}
