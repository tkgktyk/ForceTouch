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
public class ForceTouchScreenHelper {
    private static final String TAG = ForceTouchScreenHelper.class.getSimpleName();

    private static final int INVALID_POINTER_ID = -1;

    public interface Callback {
        boolean onForceTouchBegin(float x, float y, float startX, float startY);

        void onForceTouchDown(float x, float y, int count);

        void onForceTouchUp(float x, float y, int count);

        void onForceTouchEnd(float x, float y, int count);

        void onForceTouchCancel(float x, float y, int count);

        boolean performOriginalOnTouchEvent(MotionEvent event);

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
                }
            }
        }

        private final Handler mHandler = new TouchHandler();

        private static final float INVALID_PARAMETER = -1.0f;

        float upper = INVALID_PARAMETER;
        float lower = INVALID_PARAMETER;
        float startX = 0.0f;
        float startY = 0.0f;
        boolean forceTouch = false;
        boolean window = false;
    }

    private int mTouchSlopeSquare;
    private float mMagnification;
    private int mWindowTimeInMillis;
    private int mWindowDelayInMillis;
    private boolean mRewind;

    public static final int TYPE_WIGGLE = 1;
    public static final int TYPE_SCRATCH = 2;
    private int mType = TYPE_WIGGLE;

    private final Callback mCallback;

    private final Map<Integer, TouchState> mTouchStates = Maps.newHashMap();

    private int mCount;
    private int mActivePointerId;
    private MotionEvent mCancelEvent;
    private final List<MotionEvent> mMotionEvents = Lists.newArrayList();

    public ForceTouchScreenHelper(Callback callback) {
        mCallback = callback;
    }

    public void setSensitivity(Context context, int sensitivity) {
        int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop() * sensitivity;
        mTouchSlopeSquare = touchSlop * touchSlop;
    }

    public void setMagnification(float magnification) {
        mMagnification = magnification < 1.0f ? 1.0f : magnification;
    }

    public void setWindowDelayInMillis(int windowDelayInMillis) {
        mWindowDelayInMillis = windowDelayInMillis;
    }

    public void setWindowTimeInMillis(int windowTimeInMillis) {
        mWindowTimeInMillis = windowTimeInMillis;
    }

    public void setRewind(boolean rewind) {
        mRewind = rewind;
    }

    public void setType(int type) {
        mType = type;
    }

    private void cleanUp() {
        mTouchStates.clear();
        mActivePointerId = INVALID_POINTER_ID;
        mCount = 0;
        if (mCancelEvent != null) {
            mCancelEvent.recycle();
            mCancelEvent = null;
        }
        for (Map.Entry<Integer, TouchState> entry : mTouchStates.entrySet()) {
            removeMessages(entry.getValue());
        }
        clearMotionEvents();
    }

    public int getCount() {
        return mCount;
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

    void removeMessages(TouchState state) {
        state.mHandler.removeMessages(MSG_START_DETECTOR);
        state.mHandler.removeMessages(MSG_STOP_DETECTOR);
    }

    private void addTouch(MotionEvent event) {
        final int index = event.getActionIndex();
        if (event.getToolType(index) != MotionEvent.TOOL_TYPE_FINGER) {
            return;
        }
        TouchState state = new TouchState();
        state.startX = event.getX(index);
        state.startY = event.getY(index);
        final float parameter = mCallback.getParameter(event, index);
        sendStartDetectorMessage(state);
        if (state.window) {
            setParameter(state, parameter);
        }
        mTouchStates.put(event.getPointerId(index), state);
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

    private void removeTouchAndPerformTap(int pointerId, float x, float y) {
        if (mActivePointerId == pointerId) {
            mCallback.onForceTouchEnd(x, y, mCount);
            TouchState state = mTouchStates.get(pointerId);
            removeMessages(state);
            mActivePointerId = INVALID_POINTER_ID;
        }
        mTouchStates.remove(pointerId);
    }

    private void onForceTouchStarted(MotionEvent event, int index) {
        mActivePointerId = event.getPointerId(index);
        if (mCancelEvent != null) {
            mCancelEvent.recycle();
        }
        if (mRewind) {
            // rewind movements
            for (MotionEvent e : mMotionEvents) {
                mCallback.performOriginalOnTouchEvent(e);
            }
            clearMotionEvents();
        }
        mCancelEvent = MotionEvent.obtain(event);
        mCancelEvent.setAction(MotionEvent.ACTION_CANCEL);
        mCallback.performOriginalOnTouchEvent(mCancelEvent);
        mCancelEvent.recycle();
        mCancelEvent = null;
    }

    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                cleanUp();
                addTouch(event);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mRewind && mCount == 0) {
                    mMotionEvents.add(0, MotionEvent.obtain(event));
                }
                int count = event.getPointerCount();
                for (int index = 0; index < count; ++index) {
                    TouchState state = mTouchStates.get(event.getPointerId(index));
                    if (state == null) {
                        continue;
                    }
                    if (state.window) {
                        final float parameter = mCallback.getParameter(event, index);
                        final float x = event.getX(index);
                        final float y = event.getY(index);
                        if (state.upper == TouchState.INVALID_PARAMETER) {
                            setParameter(state, parameter);
                        } else if (state.forceTouch) {
                            if (isReset(state, parameter)) {
                                state.forceTouch = false;
                                clearMotionEvents();
                                mCallback.onForceTouchUp(x, y, mCount);
                            }
                        } else if (isForceTouch(state, parameter)) {
                            removeMessages(state);
                            state.forceTouch = true;
                            ++mCount;
                            if (mCount == 1) {
                                onForceTouchStarted(event, index);
                                if (!mCallback.onForceTouchBegin(x, y, state.startX, state.startY)) {
                                    mCount = 0;
                                }
                            } else {
                                mCallback.onForceTouchDown(x, y, mCount);
                            }
                        }
                    }
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                cleanUp();
                final int index = event.getActionIndex();
                final float x = event.getX(index);
                final float y = event.getY(index);
                mCallback.onForceTouchCancel(x, y, mCount);
                break;
            }
            case MotionEvent.ACTION_UP: {
                final int index = event.getActionIndex();
                final float x = event.getX(index);
                final float y = event.getY(index);
                removeTouchAndPerformTap(event.getPointerId(index), x, y);
                cleanUp();
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mActivePointerId == INVALID_POINTER_ID) {
                    addTouch(event);
                }
                break;
            case MotionEvent.ACTION_POINTER_UP: {
                final int index = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                        MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                final float x = event.getX(index);
                final float y = event.getY(index);
                removeTouchAndPerformTap(event.getPointerId(index), x, y);
                break;
            }
        }

        return mCount != 0 || mCallback.performOriginalOnTouchEvent(event);
    }
}
