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

import android.view.MotionEvent;

/**
 * Created by tkgktyk on 2015/08/12.
 */
public abstract class ForceTouchDetector {
    private static final String TAG = ForceTouchDetector.class.getSimpleName();

    protected static final int INVALID_POINTER_ID = -1;

    public interface Callback {
        boolean onAbsoluteTouch(float x, float y, float parameter);

        boolean onRelativeTouch(float x, float y, float startX, float startY);

        boolean onForceTap(float x, float y);

        boolean onForceLongPress(float x, float y);

        void performOriginalOnTouchEvent(MotionEvent event);

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

    protected final Callback mCallback;

    public ForceTouchDetector(Callback callback) {
        mCallback = callback;
    }
}
