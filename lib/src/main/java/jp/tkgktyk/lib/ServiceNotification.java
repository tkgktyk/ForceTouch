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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;

/**
 * Created by tkgktyk on 2015/05/14.
 */
public class ServiceNotification {

    private Service mService;
    private Notification.Builder mBuilder;
    private int mTitleId;

    public ServiceNotification(Service service, @DrawableRes int iconId, @StringRes int titleId, Class<?> activity) {
        mService = service;
        mTitleId = titleId;
        // Creates an Intent for the Activity
        Intent notifyIntent = new Intent(service, activity);
        // Sets the Activity to start in a new, empty task
        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // Creates the PendingIntent
        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        service,
                        0,
                        notifyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        mBuilder = new Notification.Builder(service)
                .setContentIntent(pendingIntent)
                .setContentTitle(service.getString(titleId))
                .setSmallIcon(iconId);
        mService.startForeground(titleId, mBuilder.build());
    }

    public void update() {
        ((NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(mTitleId, mBuilder.build());
    }

    public void update(@StringRes int textId) {
        update(mService.getString(textId));
    }

    public void update(String text) {
        mBuilder.setTicker(text);
        mBuilder.setContentText(text);
        update();
    }

    public Notification.Builder getBuilder() {
        return mBuilder;
    }

    public void stop() {
        mService.stopForeground(true);
    }
}
