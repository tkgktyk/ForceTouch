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
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;

import jp.tkgktyk.lib.ServiceNotification;
import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;

/**
 * Created by tkgktyk on 2015/06/09.
 */
public class FloatingActionService extends Service {
    private ServiceNotification mServiceNotification;

    private FloatingAction mFloatingAction;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        FTD.Settings settings = new FTD.Settings(this, FTD.getSharedPreferences(this));

        if (settings.showNotification) {
            if (mServiceNotification == null) {
                mServiceNotification = new ServiceNotification(this, R.drawable.ic_stat_floating_action,
                        R.string.app_name, SettingsActivity.class);
                mServiceNotification.update(R.string.state_floating_action);
            }
        } else {
            if (mServiceNotification != null) {
                mServiceNotification.stop();
                mServiceNotification = null;
            }
        }

        if (mFloatingAction != null) {
            mFloatingAction.onDestroy();
            mFloatingAction = null;
        }
        mFloatingAction = new FloatingAction(this, settings);
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mFloatingAction.onConfigurationChanged(newConfig);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mServiceNotification != null) {
            mServiceNotification.stop();
            mServiceNotification = null;
        }

        mFloatingAction.onDestroy();
    }
}
