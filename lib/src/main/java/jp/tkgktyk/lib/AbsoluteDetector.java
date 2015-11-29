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

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.google.common.collect.Maps;

import java.util.Map;

/**
 * Created by tkgktyk on 2015/08/12.
 */
public class AbsoluteDetector extends ForceTouchDetector {
    private static final String TAG = AbsoluteDetector.class.getSimpleName();

    private static final int MSG_LONG_PRESS = 3;

    protected class TouchState {
        class TouchHandler extends Handler {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_LONG_PRESS:
                        window = false;
                        if (mLongClickable && mCallback.onForceLongPress(x, y)) {
                            onHandled();
                        }
                        break;
                }
            }
        }

        private final Handler mHandler = new TouchHandler();

        float x = 0.0f;
        float y = 0.0f;
        boolean inTapRegion = true;
        boolean window = false;
    }

    private static final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

    private int mTouchSlopeSquare;
    private int mExtraLongPressTimeout;
    private boolean mLongClickable = true;
    private boolean mAllowUnknownType;

    private final Map<Integer, TouchState> mTouchStates = Maps.newHashMap();

    private boolean mHandled;
    private int mActivePointerId;
    private MotionEvent mCancelEvent;

    public AbsoluteDetector(Callback callback) {
        super(callback);
    }

    public void setSensitivity(Context context, int sensitivity) {
        int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop() * sensitivity;
        mTouchSlopeSquare = touchSlop * touchSlop;
    }

    public void setExtraLongPressTimeout(int extraLongPressTimeout) {
        mExtraLongPressTimeout = extraLongPressTimeout;
    }

    public void setLongClickable(boolean longClickable) {
        mLongClickable = longClickable;
    }

    public void allowUnknownType(boolean allow) {
        mAllowUnknownType = allow;
    }

    private void cleanUp() {
        mActivePointerId = INVALID_POINTER_ID;
        mHandled = false;
        if (mCancelEvent != null) {
            mCancelEvent.recycle();
            mCancelEvent = null;
        }
        for (Map.Entry<Integer, TouchState> entry : mTouchStates.entrySet()) {
            removeMessages(entry.getValue());
        }
        mTouchStates.clear();
    }

    void sendLongPressMessage(TouchState state) {
        state.mHandler.removeMessages(MSG_LONG_PRESS);
        state.mHandler.sendEmptyMessageDelayed(MSG_LONG_PRESS,
                LONG_PRESS_TIMEOUT + mExtraLongPressTimeout);
    }

    void removeMessages(TouchState state) {
        state.mHandler.removeMessages(MSG_LONG_PRESS);
    }

    private void addTouch(MotionEvent event) {
        final int index = event.getActionIndex();
        final int toolType = event.getToolType(index);
        if (!(toolType == MotionEvent.TOOL_TYPE_FINGER ||
                (mAllowUnknownType && toolType == MotionEvent.TOOL_TYPE_UNKNOWN))) {
            return;
        }
        TouchState state = new TouchState();
        final float x = event.getX(index);
        final float y = event.getY(index);
        state.x = x;
        state.y = y;
        final float parameter = mCallback.getParameter(event, index);
        mTouchStates.put(event.getPointerId(index), state);
        if (mCallback.onAbsoluteTouch(x, y, parameter)) {
            onAbsoluteTouchStarted(event, index, state);
        }
    }

    private void removeTouchAndPerformTap(int pointerId) {
        if (mActivePointerId == pointerId) {
            TouchState state = mTouchStates.get(pointerId);
            if (!mHandled && state.inTapRegion) {
                if (mCallback.onForceTap(state.x, state.y)) {
                    onHandled();
                }
            }
            removeMessages(state);
            mActivePointerId = INVALID_POINTER_ID;
        }
        mTouchStates.remove(pointerId);
    }

    private void onHandled() {
        mCallback.performOriginalOnTouchEvent(mCancelEvent);
        mCancelEvent.recycle();
        mCancelEvent = null;
        mHandled = true;
    }

    private void onAbsoluteTouchStarted(MotionEvent event, int index, TouchState state) {
        mActivePointerId = event.getPointerId(index);
        sendLongPressMessage(state);
        if (mCancelEvent != null) {
            mCancelEvent.recycle();
        }
        mCancelEvent = MotionEvent.obtain(event);
        mCancelEvent.setAction(MotionEvent.ACTION_CANCEL);
    }

    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                cleanUp();
                addTouch(event);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!mHandled) {
                    int count = event.getPointerCount();
                    for (int index = 0; index < count; ++index) {
                        TouchState state = mTouchStates.get(event.getPointerId(index));
                        if (state == null) {
                            continue;
                        }
                        if (!state.inTapRegion) {
                            continue;
                        }
                        final float x = event.getX(index);
                        final float y = event.getY(index);
                        final float dx = x - state.x;
                        final float dy = y - state.y;
                        if ((dx * dx + dy * dy) > mTouchSlopeSquare) {
//                            Log.d(TAG, "out of tap region");
                            state.inTapRegion = false;
                            removeMessages(state);
                        }
                    }
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                cleanUp();
                break;
            case MotionEvent.ACTION_UP:
                removeTouchAndPerformTap(event.getPointerId(event.getActionIndex()));
                cleanUp();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                addTouch(event);
                break;
            case MotionEvent.ACTION_POINTER_UP: {
                final int index = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                        MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                final int pointerId = event.getPointerId(index);
                removeTouchAndPerformTap(pointerId);
                break;
            }
        }

        // disable for multiple force touch detector
//        if (!mHandled) {
//           return mCallback.performOriginalOnTouchEvent(event);
//        }
        return mHandled;
    }
}
