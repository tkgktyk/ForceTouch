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

package jp.tkgktyk.xposed.forcetouchdetector.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;

import jp.tkgktyk.lib.ServiceNotification;
import jp.tkgktyk.xposed.forcetouchdetector.R;

/**
 * Created by tkgktyk on 2015/06/09.
 */
public class EmergencyService extends Service {
    private ServiceNotification mServiceNotification;

    private FloatingNavigation mFloatingNavigation;

    public static void startStop(Context context, boolean start) {
        Intent intent = new Intent(context, EmergencyService.class);
        if (start) {
            context.startService(intent);
        } else {
            context.stopService(intent);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mServiceNotification = new ServiceNotification(this, R.drawable.ic_stat_emergency,
                R.string.app_name, SettingsActivity.class);
        mServiceNotification.update(R.string.state_running);

        mFloatingNavigation = new FloatingNavigation(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mFloatingNavigation.onConfigurationChanged(newConfig);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mServiceNotification.stop();

        mFloatingNavigation.onDestroy(this);
    }
}
