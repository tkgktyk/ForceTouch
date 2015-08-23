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

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

/**
 * Created by tkgktyk on 2015/08/24.
 */
public class MovableLayout extends FrameLayout {
    private static final String TAG = MovableLayout.class.getSimpleName();

    /**
     * Sentinel value for no current active pointer. Used by
     * {@link #mActivePointerId}.
     */
    private static final int INVALID_POINTER = -1;

    private final int mTouchSlopSquare;

    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private int mActivePointerId = INVALID_POINTER;
    /**
     * True if the user is currently dragging this ScrollView around. This is
     * not the same as 'is being flinged', which can be checked by
     * mScroller.isFinished() (flinging begins when the user lifts his finger).
     */
    private boolean mIsBeingDragged = false;

    /**
     * Position of the last motion event.
     */
    private int mLastMotionX;
    private int mLastMotionY;

    public interface Callback {
        void move(MovableLayout layout, float dx, float dy);
        void onClick(MovableLayout layout);
    }

    private Callback mCallback = new Callback() {
        @Override
        public void move(MovableLayout layout, float dx, float dy) {
            setX(getX() + dx);
            setY(getY() + dy);
        }

        @Override
        public void onClick(MovableLayout layout) {
        }
    };

    public MovableLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mTouchSlopSquare = touchSlop * touchSlop;
    }

    public MovableLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MovableLayout(Context context) {
        this(context, null);
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
            /*
             * This method JUST determines whether we want to intercept the motion.
             * If we return true, onMotionEvent will be called and we do the actual
             * scrolling there.
             */

            /*
             * Shortcut the most recurring case: the user is in the dragging state
             * and he is moving his finger. We want to intercept this motion.
             */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) && mIsBeingDragged) {
            return true;
        }

        boolean dragLocally = false;

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
                    /*
                     * mIsBeingDragged == false, otherwise the shortcut would have
                     * caught it. Check whether the user has moved far enough from his
                     * original down touch.
                     */

                    /*
                     * Locally do absolute value. mLastMotionY is set to the y value of
                     * the down event.
                     */
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on
                    // content.
                    break;
                }

                final int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + activePointerId
                            + " in onInterceptTouchEvent");
                    break;
                }

                final int x = (int) ev.getX(pointerIndex);
                final int deltaX = x - mLastMotionX;
                final int y = (int) ev.getY(pointerIndex);
                final int deltaY = y - mLastMotionY;
                if (deltaX * deltaX + deltaY * deltaY > mTouchSlopSquare) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    dragLocally = true;
                }
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                final int x = (int) ev.getX();
                final int y = (int) ev.getY();
                    /*
                     * Remember location of down touch. ACTION_DOWN always refers to
                     * pointer index 0.
                     */
                mLastMotionX = x;
                mLastMotionY = y;
                mActivePointerId = ev.getPointerId(0);
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // always does not intercept

                    /* Release the drag */
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }
            /*
             * The only time we want to intercept motion events is if we are in the
             * drag mode.
             */
        return mIsBeingDragged || dragLocally;
    }

    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        final int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                if (getChildCount() == 0) {
                    return false;
                }

                // Remember where the motion event started
                mLastMotionX = (int) ev.getX();
                mLastMotionY = (int) ev.getY();
                mActivePointerId = ev.getPointerId(0);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                final int activePointerIndex = ev
                        .findPointerIndex(mActivePointerId);
                if (activePointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + mActivePointerId + " in onTouchEvent");
                    break;
                }

                final int x = (int) ev.getX(activePointerIndex);
                final int deltaX = x - mLastMotionX;
                final int y = (int) ev.getY(activePointerIndex);
                final int deltaY = y - mLastMotionY;
                if (!mIsBeingDragged) {
                    if (deltaX * deltaX + deltaY * deltaY > mTouchSlopSquare) {
                        final ViewParent parent = getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                        mIsBeingDragged = true;
                    }
                }
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    mCallback.move(this, deltaX, deltaY);
                    mLastMotionX = x;
                    mLastMotionY = y;
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (mIsBeingDragged) {
                    mActivePointerId = INVALID_POINTER;
                    mIsBeingDragged = false;
                } else {
                    mCallback.onClick(this);
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged && getChildCount() > 0) {
                    mActivePointerId = INVALID_POINTER;
                    mIsBeingDragged = false;
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                mLastMotionX = (int) ev.getX(index);
                mLastMotionY = (int) ev.getY(index);
                mActivePointerId = ev.getPointerId(index);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                mLastMotionX = (int) ev.getX(ev.findPointerIndex(mActivePointerId));
                mLastMotionY = (int) ev.getY(ev.findPointerIndex(mActivePointerId));
                break;
        }

        return mIsBeingDragged;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = (int) ev.getX(newPointerIndex);
            mLastMotionY = (int) ev.getY(newPointerIndex);
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }
}
