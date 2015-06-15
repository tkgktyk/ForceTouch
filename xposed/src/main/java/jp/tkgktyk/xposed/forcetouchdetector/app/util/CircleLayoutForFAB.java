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

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.google.common.collect.Lists;

import java.util.ArrayList;

/**
 * Created by tkgktyk on 2015/06/02.
 */
public class CircleLayoutForFAB extends FrameLayout {

    private static final int INVALID_POINTER = -1;

    private final PointF mCircleOrigin = new PointF();
    private float mCircleRadius;

    private boolean mReverseDirection = false;

    private float mChildSize;
    private final ArrayList<Float> mItemRadians = Lists.newArrayList();

    private float mRotationRadian;

    private final Rect mChildrenRect = new Rect();
    private final ArrayList<Rect> mChildRects = Lists.newArrayList();

    private ObjectAnimator mShowAnimation;
    private ObjectAnimator mHideAnimation;

    public CircleLayoutForFAB(Context context) {
        super(context);
        init();
    }

    public CircleLayoutForFAB(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircleLayoutForFAB(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    void init() {
        float density = getResources().getDisplayMetrics().density;
        mChildSize = (56 + 16) * density; // R.dimen.fab_size_normal + 16dp
        mCircleRadius = mChildSize * 2;

        updateCircleParameters();
        setOnHierarchyChangeListener(new OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                updateCircleParameters();
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
                updateCircleParameters();
            }
        });

        setUpAnimator();
    }

    private void updateCircleParameters() {
        mChildRects.clear();
        int count = getChildCount();
        for (int i = 0; i < count; ++i) {
            mChildRects.add(new Rect());
        }

        mItemRadians.clear();

        int maxLevel = calcLevel(count) + 1;
        for (int i = 0; i < maxLevel; ++i) {
            float radius = mCircleRadius + i * mChildSize;
            mItemRadians.add((float) (Math.asin((mChildSize / 2) / radius) * 2));
        }
    }

    private int calcLevel(int index) {
        return 0;
    }

    private int calcPosition(int index) {
        return calcPosition(index, calcLevel(index));
    }

    private int calcPosition(int index, int level) {
        return index;
    }

    private void setUpAnimator() {
        { // show
            PropertyValuesHolder holderScaleX = PropertyValuesHolder.ofFloat("scaleX", 0.0f, 1.0f);
            PropertyValuesHolder holderScaleY = PropertyValuesHolder.ofFloat("scaleY", 0.0f, 1.0f);

            mShowAnimation = ObjectAnimator.ofPropertyValuesHolder(this,
                    holderScaleX, holderScaleY);
            mShowAnimation.setDuration(300); // default 300
        }
        { // hide
            PropertyValuesHolder holderScaleX = PropertyValuesHolder.ofFloat("scaleX", 1.0f, 0.0f);
            PropertyValuesHolder holderScaleY = PropertyValuesHolder.ofFloat("scaleY", 1.0f, 0.0f);

            mHideAnimation = ObjectAnimator.ofPropertyValuesHolder(this,
                    holderScaleX, holderScaleY);
            mHideAnimation.setDuration(300); // default 300
        }
    }

    public void show(float x, float y, float rotation) {
        setCircleOrigin(x, y);
        setRotation(rotation);
        show();
    }

    public void show() {
        mHideAnimation.cancel();
        mShowAnimation.start();
    }

    public void hide() {
        mShowAnimation.cancel();
        mHideAnimation.start();
    }

    public void setCircleOrigin(float x, float y) {
        mCircleOrigin.set(x, y);
        setPivotX(x);
        setPivotY(y);
    }

    public void setRotation(float rotation) {
        mRotationRadian = (float) (rotation * Math.PI / 180.0f);
    }

    public void setReverseDirection(boolean reverse) {
        mReverseDirection = reverse;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        float firstAngle = calcFirstAngle();

        mChildrenRect.set(0, 0, 0, 0);
        final int count = getChildCount();
        for (int i = 0; i < count; ++i) {
            View child = getChildAt(i);

            int level = calcLevel(i);
            int position = calcPosition(i, level);
            float angle = firstAngle + mItemRadians.get(level) * position;
            if (mReverseDirection) {
                angle = -angle;
            }
            angle += mRotationRadian;
            float radius = mCircleRadius + level * mChildSize;
            float x = (float) Math.cos(angle) * radius;
            float y = (float) Math.sin(angle) * radius;

            int childLeft = Math.round(mCircleOrigin.x + x - child.getMeasuredWidth() / 2);
            int childTop = Math.round(mCircleOrigin.y + y - child.getMeasuredHeight() / 2);
            int childRight = childLeft + child.getMeasuredWidth();
            int childBottom = childTop + child.getMeasuredHeight();
            Rect rect = mChildRects.get(i);
            rect.set(childLeft, childTop, childRight, childBottom);
            mChildrenRect.union(rect);
        }
        int dx = 0;
        int dy = 0;
        if (mChildrenRect.left < getLeft()) {
            dx = getLeft() - mChildrenRect.left;
        } else if (mChildrenRect.right > getRight()) {
            dx = getRight() - mChildrenRect.right;
        }
        if (mChildrenRect.top < getTop()) {
            dy = getTop() - mChildrenRect.top;
        } else if (mChildrenRect.bottom > getBottom()) {
            dy = getBottom() - mChildrenRect.bottom;
        }
        for (int i = 0; i < count; ++i) {
            View child = getChildAt(i);
            Rect rect = mChildRects.get(i);
            child.layout(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy);
        }
    }

    private float calcFirstAngle() {
        return -mItemRadians.get(0) * (getChildCount() - 1) / 2;
    }
}
