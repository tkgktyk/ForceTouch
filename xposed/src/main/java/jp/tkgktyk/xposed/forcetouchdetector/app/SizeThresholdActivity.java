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

package jp.tkgktyk.xposed.forcetouchdetector.app;

import android.os.Bundle;
import android.view.MotionEvent;

import jp.tkgktyk.xposed.forcetouchdetector.R;

/**
 * Created by tkgktyk on 2015/06/07.
 */
public class SizeThresholdActivity extends PressureThresholdActivity {

    @Override
    protected int getMaxPressureResource() {
        return R.string.max_size_f1;
    }

    @Override
    protected int getPressureResource() {
        return R.string.size_f1;
    }

    @Override
    protected int getAvePressureResource() {
        return R.string.ave_size_f1;
    }

    @Override
    protected String getThresholdKey() {
        return getString(R.string.key_size_threshold);
    }

    @Override
    protected float getPressure(MotionEvent event) {
        return event.getSize();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPressureThreshold.setHint(R.string.hint_size_threshold);
    }
}
