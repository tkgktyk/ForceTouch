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
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Button;

/**
 * Created by tkgktyk on 2015/06/09.
 */
public class PressureButton extends Button {

    private long mDetectionWindow;
    private Handler mHandler = new Handler();
    private boolean mIsDetectionWindowOpened;
    private Runnable mStopDetector = new Runnable() {
        @Override
        public void run() {
            if (mIsDetectionWindowOpened) {
                mOnPressedListener.onStop();
            }
            mIsDetectionWindowOpened = false;
        }
    };

    public PressureButton(Context context) {
        super(context);
    }

    public PressureButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PressureButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setDetectionWindow(long msec) {
        mDetectionWindow = msec;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mIsDetectionWindowOpened) {
            mOnPressedListener.onUpdate(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mHandler.postDelayed(mStopDetector, mDetectionWindow);
                mOnPressedListener.onStart(event);
                mIsDetectionWindowOpened = true;
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mHandler.removeCallbacks(mStopDetector);
                if (mIsDetectionWindowOpened) {
                    mOnPressedListener.onStop();
                }
                mIsDetectionWindowOpened = false;
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    private OnPressedListener mOnPressedListener;

    public void setOnPressedListener(OnPressedListener listener) {
        mOnPressedListener = listener;
    }

    public interface OnPressedListener {
        void onStart(MotionEvent event);
        void onUpdate(MotionEvent event);
        void onStop();
    }
}
