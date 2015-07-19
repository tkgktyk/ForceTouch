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

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;

import com.google.common.base.Strings;
import com.google.gson.Gson;

import java.io.Serializable;

/**
 * Created by tkgktyk on 2015/07/18.
 */
public class ScaleRect implements Serializable {
    private static final long serialVersionUID = 1L;

    private float mScaleX;
    private float mScaleY;
    private float mPivotX;
    private float mPivotY;

    public ScaleRect() {
        this(1.0f, 1.0f);
    }

    public ScaleRect(float scaleX, float scaleY) {
        this(scaleX, scaleY, 0.0f, 0.0f);
    }

    public ScaleRect(float scaleX, float scaleY, float pivotX, float pivotY) {
        set(scaleX, scaleY, pivotX, pivotY);
    }

    public RectF getRect() {
        float left = (1.0f - mScaleX) * mPivotX;
        float right = left + mScaleX;
        float top = (1.0f - mScaleY) * mPivotY;
        float bottom = top + mScaleY;
        return new RectF(left, top, right, bottom);
    }

    private Rect getSizedRect(RectF rect, int width, int height) {
        return new Rect(Math.round(rect.left * width), Math.round(rect.top * height),
                Math.round(rect.right * width), Math.round(rect.bottom * height));
    }

    public Rect getRect(Point containerSize) {
        return getSizedRect(getRect(), containerSize.x, containerSize.y);
    }

    public Rect getRect(int width, int height) {
        return getSizedRect(getRect(), width, height);
    }

    public RectF getMirroredRect() {
        float left = (1.0f - mScaleX) * (1.0f - mPivotX);
        float right = left + mScaleX;
        float top = (1.0f - mScaleY) * mPivotY;
        float bottom = top + mScaleY;
        return new RectF(left, top, right, bottom);
    }

    public Rect getMirroredRect(Point containerSize) {
        return getSizedRect(getMirroredRect(), containerSize.x, containerSize.y);
    }

    public Rect getMirroredRect(int width, int height) {
        return getSizedRect(getMirroredRect(), width, height);
    }

    public float getScaleX() {
        return mScaleX;
    }

    public void setScaleX(float scaleX) {
        mScaleX = scaleX;
    }

    public float getScaleY() {
        return mScaleY;
    }

    public void setScaleY(float scaleY) {
        mScaleY = scaleY;
    }

    public float getPivotX() {
        return mPivotX;
    }

    public void setPivotX(float pivotX) {
        mPivotX = pivotX;
    }

    public float getPivotY() {
        return mPivotY;
    }

    public void setPivotY(float pivotY) {
        mPivotY = pivotY;
    }

    public void set(float scaleX, float scaleY, float pivotX, float pivotY) {
        mScaleX = scaleX;
        mScaleY = scaleY;
        mPivotX = pivotX;
        mPivotY = pivotY;
    }

    public String toStringForPreference() {
        return new Gson().toJson(this);
    }

    public static ScaleRect fromPreference(String stringFromPreference) {
        if (Strings.isNullOrEmpty(stringFromPreference)) {
            return new ScaleRect();
        }
        return new Gson().fromJson(stringFromPreference, ScaleRect.class);
    }
}
