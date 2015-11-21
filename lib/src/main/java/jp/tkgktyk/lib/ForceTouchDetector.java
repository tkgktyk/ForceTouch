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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

/**
 * Created by tkgktyk on 2015/08/12.
 */
public class ForceTouchDetector {
    private static final String TAG = ForceTouchDetector.class.getSimpleName();

    private static final int INVALID_POINTER_ID = -1;

    public interface Callback {
        boolean onForceTouch(float x, float y);

        void onForceTap(float x, float y);

        void onForceLongPress(float x, float y);

        void performOriginalOnTouchEvent(MotionEvent event);

        boolean onTouchDown(float x, float y, float parameter);

        float getParameter(MotionEvent event, int index);
    }

    public static float getPressure(MotionEvent event, int index) {
        final int historySize = event.getHistorySize();
        return historySize == 0 ? event.getPressure(index) :
                event.getHistoricalPressure(index, historySize - 1);
    }

    public static float getSize(MotionEvent event, int index) {
        final int historySize = event.getHistorySize();
        return historySize == 0 ? event.getSize(index) :
                event.getHistoricalSize(index, historySize - 1);
    }

    private static final int MSG_START_DETECTOR = 1;
    private static final int MSG_STOP_DETECTOR = 2;
    private static final int MSG_LONG_PRESS = 3;

    private class TouchState {
        class TouchHandler extends Handler {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_START_DETECTOR:
                        window = true;
                        sendStopDetectorMessage(TouchState.this);
                        break;
                    case MSG_STOP_DETECTOR:
                        window = false;
                        break;
                    case MSG_LONG_PRESS:
                        window = false;
                        if (mLongClickable) {
                            mCallback.onForceLongPress(x, y);
                            onHandled();
                        }
                        break;
                }
            }
        }

        private final Handler mHandler = new TouchHandler();

        private static final float INVALID_PARAMETER = -1.0f;

        float upper = INVALID_PARAMETER;
        float lower = INVALID_PARAMETER;
        float x = 0.0f;
        float y = 0.0f;
        boolean forceTouch = false;
        boolean inTapRegion = true;
        boolean window = false;
    }

    private static final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

    private int mTouchSlopeSquare;
    private float mMagnification;
    private int mExtraLongPressTimeout;
    private int mWindowTimeInMillis;
    private int mWindowDelayInMillis;
    private boolean mBlockDragging;
    private boolean mMultipleForceTouch;
    private boolean mRewind;
    private boolean mLongClickable = true;
    private boolean mAllowUnknownType;

    public static final int TYPE_ONE_SHOT = 0;
    public static final int TYPE_WIGGLE = 1;
    public static final int TYPE_SCRATCH = 2;
    private int mType = TYPE_ONE_SHOT;

    private final Callback mCallback;

    private final Map<Integer, TouchState> mTouchStates = Maps.newHashMap();

    private boolean mHandled;
    private int mActivePointerId;
    private MotionEvent mCancelEvent;
    private final List<MotionEvent> mMotionEvents = Lists.newArrayList();

    public ForceTouchDetector(Callback callback) {
        mCallback = callback;
    }

    public void setSensitivity(Context context, int sensitivity) {
        int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop() * sensitivity;
        mTouchSlopeSquare = touchSlop * touchSlop;
    }

    public void setMagnification(float magnification) {
        mMagnification = magnification < 1.0f ? 1.0f : magnification;
    }


    public void setBlockDragging(boolean blockDragging) {
        mBlockDragging = blockDragging;
    }

    public void setWindowDelayInMillis(int windowDelayInMillis) {
        mWindowDelayInMillis = windowDelayInMillis;
    }

    public void setWindowTimeInMillis(int windowTimeInMillis) {
        mWindowTimeInMillis = windowTimeInMillis;
    }

    public void setExtraLongPressTimeout(int extraLongPressTimeout) {
        mExtraLongPressTimeout = extraLongPressTimeout;
    }

    public void setMultipleForceTouch(boolean multipleForceTouch) {
        mMultipleForceTouch = multipleForceTouch;
    }

    public void setRewind(boolean rewind) {
        mRewind = rewind;
    }

    public void setLongClickable(boolean longClickable) {
        mLongClickable = longClickable;
    }

    public void allowUnknownType(boolean allow) {
        mAllowUnknownType = allow;
    }

    public void setType(int type) {
        mType = type;
    }

    private void cleanUp() {
        mTouchStates.clear();
        mActivePointerId = INVALID_POINTER_ID;
        mHandled = false;
        if (mCancelEvent != null) {
            mCancelEvent.recycle();
            mCancelEvent = null;
        }
        for (Map.Entry<Integer, TouchState> entry : mTouchStates.entrySet()) {
            removeMessages(entry.getValue());
        }
        clearMotionEvents();
    }

    private void clearMotionEvents() {
        for (MotionEvent event : mMotionEvents) {
            event.recycle();
        }
        mMotionEvents.clear();
    }

    void sendStartDetectorMessage(TouchState state) {
        if (mWindowDelayInMillis > 0) {
            state.mHandler.removeMessages(MSG_START_DETECTOR);
            state.mHandler.removeMessages(MSG_STOP_DETECTOR);
            state.mHandler.sendEmptyMessageDelayed(MSG_START_DETECTOR, mWindowDelayInMillis);
        } else {
            state.window = true;
            sendStopDetectorMessage(state);
        }
    }

    void sendStopDetectorMessage(TouchState state) {
        if (mWindowTimeInMillis > 0) {
            state.mHandler.removeMessages(MSG_START_DETECTOR);
            state.mHandler.removeMessages(MSG_STOP_DETECTOR);
            state.mHandler.sendEmptyMessageDelayed(MSG_STOP_DETECTOR, mWindowTimeInMillis);
        } else if (mWindowTimeInMillis < 0) {
            state.mHandler.removeMessages(MSG_START_DETECTOR);
        } else {
            state.window = false;
        }
    }

    void sendLongPressMessage(TouchState state) {
        state.mHandler.removeMessages(MSG_LONG_PRESS);
        state.mHandler.sendEmptyMessageDelayed(MSG_LONG_PRESS,
                LONG_PRESS_TIMEOUT + mExtraLongPressTimeout);
    }

    void removeMessages(TouchState state) {
        state.mHandler.removeMessages(MSG_START_DETECTOR);
        state.mHandler.removeMessages(MSG_STOP_DETECTOR);
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
        state.x = event.getX(index);
        state.y = event.getY(index);
        final float parameter = mCallback.getParameter(event, index);
        sendStartDetectorMessage(state);
        if (state.window) {
            setParameter(state, parameter);
        }
        mTouchStates.put(event.getPointerId(index), state);
        if (mCallback.onTouchDown(state.x, state.y, parameter)) {
            onForceTouchStarted(event, index, state);
        }
    }

    protected void setParameter(TouchState state, float parameter) {
        switch (mType) {
            case TYPE_WIGGLE:
                state.upper = parameter * mMagnification;
                state.lower = parameter * (1.0f + (mMagnification - 1.0f) / 2.0f);
                break;
            case TYPE_SCRATCH:
                state.upper = parameter / (1.0f + (mMagnification - 1.0f) / 2.0f);
                state.lower = parameter / mMagnification;
                break;
        }
    }

    protected boolean isReset(TouchState state, float parameter) {
        switch (mType) {
            case TYPE_WIGGLE:
                return parameter < state.lower;
            case TYPE_SCRATCH:
                return parameter > state.upper;
        }
        return false;
    }

    protected boolean isForceTouch(TouchState state, float parameter) {
        switch (mType) {
            case TYPE_WIGGLE:
                return parameter > state.upper;
            case TYPE_SCRATCH:
                return parameter < state.lower;
        }
        return false;
    }

    private void removeTouchAndPerformTap(int pointerId) {
        if (mActivePointerId == pointerId) {
            TouchState state = mTouchStates.get(pointerId);
            if (!mHandled && state.inTapRegion) {
                mCallback.onForceTap(state.x, state.y);
                onHandled();
            }
            removeMessages(state);
            mActivePointerId = INVALID_POINTER_ID;
        }
        mTouchStates.remove(pointerId);
    }

    private void onHandled() {
        if (mRewind) {
            TouchState state = mTouchStates.get(mActivePointerId);
            if (state != null) {
                // rewind movements
                for (MotionEvent event : mMotionEvents) {
                    mCallback.performOriginalOnTouchEvent(event);
                }
            }
            clearMotionEvents();
        }
        mCallback.performOriginalOnTouchEvent(mCancelEvent);
        mCancelEvent.recycle();
        mCancelEvent = null;
        mHandled = true;
    }

    private void onForceTouchStarted(MotionEvent event, int index, TouchState state) {
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
                    if (!mMultipleForceTouch && mRewind) {
                        mMotionEvents.add(0, MotionEvent.obtain(event));
                    }
                    int count = event.getPointerCount();
                    for (int index = 0; index < count; ++index) {
                        TouchState state = mTouchStates.get(event.getPointerId(index));
                        if (state == null) {
                            continue;
                        }
                        if (mBlockDragging && !state.inTapRegion) {
                            continue;
                        }
                        final float parameter = mCallback.getParameter(event, index);
                        final float x = event.getX(index);
                        final float y = event.getY(index);
                        final float dx = x - state.x;
                        final float dy = y - state.y;
                        if ((dx * dx + dy * dy) > mTouchSlopeSquare) {
//                            Log.d(TAG, "out of tap region");
                            state.inTapRegion = false;
                            removeMessages(state);
                        }
                        if (state.window) {
                            if (state.upper == TouchState.INVALID_PARAMETER) {
                                setParameter(state, parameter);
                            } else if (state.forceTouch) {
                                if (mMultipleForceTouch && isReset(state, parameter)) {
                                    state.forceTouch = false;
                                    clearMotionEvents();
                                    removeMessages(state);
                                }
                            } else if (isForceTouch(state, parameter)) {
                                state.x = x;
                                state.y = y;
                                state.inTapRegion = true;
                                state.forceTouch = true;
                                if (mCallback.onForceTouch(x, y)) {
                                    onForceTouchStarted(event, index, state);
                                    break;
                                }
                            }
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
