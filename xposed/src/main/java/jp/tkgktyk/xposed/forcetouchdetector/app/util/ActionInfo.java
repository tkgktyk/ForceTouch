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

package jp.tkgktyk.xposed.forcetouchdetector.app.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;

import com.android.launcher3.Utilities;
import com.google.common.base.Strings;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;

import jp.tkgktyk.xposed.forcetouchdetector.FTD;
import jp.tkgktyk.xposed.forcetouchdetector.R;
import jp.tkgktyk.xposed.forcetouchdetector.app.MyApp;

/**
 * Created by tkgktyk on 2015/07/02.
 */
public class ActionInfo {
    private static final String TAG = ActionInfo.class.getSimpleName();

    public static final int TYPE_NONE = 0;
    public static final int TYPE_TOOL = 1;
    public static final int TYPE_APP = 2;
    public static final int TYPE_SHORTCUT = 3;

    private int mType;
    private Intent mIntent;
    private Bitmap mIcon;
    private String mName;

    public ActionInfo() {
        setNone();
    }

    public ActionInfo(Context context, Intent intent, int type) {
        if (intent == null) {
            // If the intent is null, we can't construct a valid ShortcutInfo, so we return null
            Log.e(TAG, "Can't construct ActionInfo with null intent");
            setNone();
            return;
        }
        mType = type;
        switch (type) {
            case TYPE_TOOL:
                fromToolIntent(context, intent);
                break;
            case TYPE_APP:
                fromAppIntent(context, intent);
                break;
            case TYPE_SHORTCUT:
                fromShortcutIntent(context, intent);
                break;
            case TYPE_NONE:
            default:
                setNone();
        }
    }

    public ActionInfo(Record record) {
        try {
            mIntent = Intent.parseUri(record.intentUri, 0);
            mType = record.type;
            if (!Strings.isNullOrEmpty(record.iconBase64)) {
                byte[] iconArray = Base64.decode(record.iconBase64, Base64.DEFAULT);
                mIcon = BitmapFactory.decodeByteArray(iconArray, 0, iconArray.length);
            }
            mName = record.name;
        } catch (URISyntaxException e) {
            Log.e(TAG, record.intentUri, e);
            setNone();
        }
    }

    public Record toRecord() {
        Record record = new Record();

        record.type = mType;
        record.intentUri = getUri();
        record.name = mName;

        if (mIcon != null) {
            byte[] iconByteArray = flattenBitmap(mIcon);
            record.iconBase64 = Base64.encodeToString(iconByteArray, 0, iconByteArray.length,
                    Base64.DEFAULT);
        }

        return record;
    }

    private byte[] flattenBitmap(Bitmap bitmap) {
        // Try go guesstimate how much space the icon will take when serialized
        // to avoid unnecessary allocations/copies during the write.
        int size = bitmap.getWidth() * bitmap.getHeight() * 4;
        ByteArrayOutputStream out = new ByteArrayOutputStream(size);
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            return out.toByteArray();
        } catch (IOException e) {
            Log.w("Favorite", "Could not write icon");
            return null;
        }
    }

    private void setNone() {
        mIntent = null;
        mType = TYPE_NONE;
        mName = null;
        mIcon = null;
    }

    private void setNotFound() {
        mIntent = null;
        mName = null;
        mIcon = null;
    }

    private void fromToolIntent(Context context, @NonNull Intent intent) {
        mIntent = intent;
        mName = FTD.getActionName(context, intent.getAction());
        mIcon = Utilities.createIconBitmap(BitmapFactory.decodeResource(context.getResources(),
                FTD.getActionIconResource(intent.getAction())), context);
    }

    private void fromAppIntent(Context context, @NonNull Intent intent) {
        mIntent = intent;
        PackageManager pm = context.getPackageManager();
        ApplicationInfo ai = null;
        if (intent.getComponent() != null) {
            try {
                ai = pm.getApplicationInfo(intent.getComponent().getPackageName(), 0);
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        if (ai != null) {
            mName = ai.loadLabel(pm).toString();
            mIcon = Utilities.createIconBitmap(ai.loadIcon(pm), context);
        } else {
            setNotFound();
        }
    }

    private void fromShortcutIntent(Context context, @NonNull Intent intent) {
        mIntent = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        mName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        Parcelable bitmap = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);

        Bitmap icon = null;

        if (bitmap instanceof Bitmap) {
            icon = Utilities.createIconBitmap((Bitmap) bitmap, context);
        } else {
            Parcelable extra = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
            if (extra instanceof Intent.ShortcutIconResource) {
                Intent.ShortcutIconResource iconResource = (Intent.ShortcutIconResource) extra;
                PackageManager packageManager = context.getPackageManager();
                // the resource
                try {
                    Resources resources = packageManager
                            .getResourcesForApplication(iconResource.packageName);
                    if (resources != null) {
                        final int id = resources.getIdentifier(iconResource.resourceName, null, null);
                        icon = Utilities.createIconBitmap(resources.getDrawable(id), context);
                    }
                } catch (Exception e) {
                    // Icon not found.
                }
            }
        }
        mIcon = icon;
    }

    @NonNull
    public String getUri() {
        if (mIntent == null) {
            return "";
        }
        return mIntent.toUri(0);
    }

    public Intent getIntent() {
        return mIntent;
    }

    public int getType() {
        return mType;
    }

    public String getName() {
        return mName;
    }

    public Bitmap getIcon() {
        return mIcon;
    }

    public BitmapDrawable newIconDrawable(Context context) {
        if (mIcon == null) {
            return null;
        }
        return new BitmapDrawable(context.getResources(), mIcon);
    }

    public boolean launch(Context context) {
        if (mIntent == null) {
            return false;
        }
        MyApp.logD("launch: " + mIntent.toString());
        switch (mType) {
            case TYPE_TOOL:
                context.sendBroadcast(mIntent);
                break;
            case TYPE_APP:
            case TYPE_SHORTCUT:
                try {
                    mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(mIntent);
                } catch (ActivityNotFoundException e) {
                    MyApp.showToast(R.string.not_found);
                }
                break;
            case TYPE_NONE:
            default:
                return false;
        }
        return true;
    }

    public static class Record implements Serializable {
        private static final long serialVersionUID = 1L;

        public int type = TYPE_NONE;
        public String intentUri = null;
        public String iconBase64 = null;
        public String name = null;

        public String toStringForPreference() {
            return new Gson().toJson(this);
        }

        public static Record fromPreference(String stringFromPreference) {
            if (Strings.isNullOrEmpty(stringFromPreference)) {
                return new Record();
            }
            return new Gson().fromJson(stringFromPreference, Record.class);
        }
    }

    public String toStringForPreference() {
        return toRecord().toStringForPreference();
    }

    public static ActionInfo fromPreference(String stringFromPreference) {
        return new ActionInfo(Record.fromPreference(stringFromPreference));
    }
}
