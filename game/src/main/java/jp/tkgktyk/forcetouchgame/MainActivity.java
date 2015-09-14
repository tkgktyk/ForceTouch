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

package jp.tkgktyk.forcetouchgame;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnticipateInterpolator;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import jp.tkgktyk.lib.ForceTouchDetector;

public class MainActivity extends AppCompatActivity
        implements ForceTouchDetector.Callback, View.OnClickListener {
    private static final String TAG = "ForceTouchGame";

    private ForceTouchDetector mForceTouchDetector;
    private int mForceTouch;
    private boolean mUsePressure;

    @Bind(R.id.pressure_switch)
    Switch mPressureSwitch;
    @Bind(R.id.model)
    TextView mModelText;
    @Bind(R.id.android)
    TextView mAndroidText;
    @Bind(R.id.build)
    TextView mBuildText;
    @Bind(R.id.text)
    TextView mTextView;
    @Bind(R.id.sprite_container)
    FrameLayout mSpriteContainer;
    @Bind(R.id.animation_container)
    FrameLayout mAnimationContainer;
    @Bind(R.id.red)
    ImageView mRed;
    @Bind(R.id.green)
    ImageView mGreen;
    @Bind(R.id.ripple)
    View mRippleView;

    private Rect mHitRect = new Rect();
    private boolean mGrabSprite;
    private int mSpriteColor;
    private int mSpriteSize;
    private ObjectAnimator mRippleAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mPressureSwitch.setText("Use Pressure");
        mPressureSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mUsePressure = isChecked;
            }
        });

        mModelText.setText(Build.MODEL);
        mAndroidText.setText("Android " + Build.VERSION.RELEASE);
        mBuildText.setText("Build " + Build.DISPLAY);

        mForceTouchDetector = new ForceTouchDetector(this);
        mForceTouchDetector.setBlockDragging(false);
        mForceTouchDetector.setSensitivity(this, 7);
        mForceTouchDetector.setMagnification(1.7f);
        mForceTouchDetector.setWindowDelayInMillis(50);
        mForceTouchDetector.setWindowTimeInMillis(-1);
        mForceTouchDetector.setExtraLongPressTimeout(300);
        mForceTouchDetector.setMultipleForceTouch(true);
        mForceTouchDetector.setLongClickable(true);
//        mForceTouchDetector.updateParameters(this, 7, 1.7f, 0, 0); // test of Knuckle Touch

        View content = findViewById(android.R.id.content);
        content.setOnClickListener(this);

        mSpriteSize = getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);

        final float startScale = 0.0f;
        final float startAlpha = 1.0f;
        PropertyValuesHolder holderScaleX = PropertyValuesHolder.ofFloat("scaleX", startScale, 1.0f);
        PropertyValuesHolder holderScaleY = PropertyValuesHolder.ofFloat("scaleY", startScale, 1.0f);
        PropertyValuesHolder holderAlpha = PropertyValuesHolder.ofFloat("alpha", startAlpha, 0.0f);

        mRippleView.setVisibility(View.INVISIBLE);
        mRippleAnimator = ObjectAnimator.ofPropertyValuesHolder(mRippleView,
                holderScaleX, holderScaleY, holderAlpha);
        mRippleAnimator.setDuration(300); // default
        mRippleAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mRippleView.setScaleX(startScale);
                mRippleView.setScaleY(startScale);
                mRippleView.setAlpha(startAlpha);
                mRippleView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mRippleView.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEnd(animation);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void addSprite(float x, float y) {
        Log.d(TAG, "add(" + x + ", " + y + ")");
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(mSpriteSize, mSpriteSize);
        ImageView sprite = new ImageView(this);
        sprite.setLayoutParams(lp);
        move(sprite, x, y, mSpriteSize, mSpriteSize);
        sprite.setBackgroundColor(mSpriteColor);

        mSpriteContainer.addView(sprite);
    }

    private void grabSprite(float x, float y) {
        addSprite(x, y);
        View v = mSpriteContainer.getChildAt(mSpriteContainer.getChildCount() - 1);
        mSpriteContainer.removeView(v);
        mSpriteContainer.addView(v, 0);
    }

    private void move(View sprite, float x, float y) {
        move(sprite, x, y, sprite.getWidth(), sprite.getHeight());
    }

    private void move(View sprite, float x, float y, int width, int height) {
        int location[] = new int[2];
        mSpriteContainer.getLocationOnScreen(location);
        int viewX = location[0];
        int viewY = location[1];
        sprite.setX(x - viewX - width / 2);
        sprite.setY(y - viewY - height / 2);
    }

    private void removeSprite() {
        removeSprite(0);
    }

    private void removeSprite(final int index) {
        View sprite = mSpriteContainer.getChildAt(index);
        mSpriteContainer.removeViewAt(index);
        mAnimationContainer.addView(sprite);

        PropertyValuesHolder holderY = PropertyValuesHolder.ofFloat(View.Y, sprite.getY(),
                sprite.getY() + getResources().getDimensionPixelSize(R.dimen.sprite_drop));
        PropertyValuesHolder holderAlpha = PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f, 0.0f);

        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(sprite,
                holderY, holderAlpha);
        animator.setDuration(600); // default 300
        animator.setInterpolator(new AnticipateInterpolator());
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimationContainer.removeViewAt(0);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEnd(animation);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
        animator.start();
    }

    private void removeAllSprites() {
        int count = mSpriteContainer.getChildCount();
        for (int i = 0; i < count; ++i) {
            removeSprite(0);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mForceTouch = 0;

                final float x = ev.getX();
                final float y = ev.getY();
                final int x2 = Math.round(x);
                final int y2 = Math.round(y);
                mRed.getGlobalVisibleRect(mHitRect);
                if (mHitRect.contains(x2, y2)) {
                    mSpriteColor = getResources().getColor(android.R.color.holo_red_dark);
                    grabSprite(x, y);
                    mGrabSprite = true;
                }
                mGreen.getGlobalVisibleRect(mHitRect);
                if (mHitRect.contains(x2, y2)) {
                    mSpriteColor = getResources().getColor(android.R.color.holo_green_dark);
                    grabSprite(x, y);
                    mGrabSprite = true;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!mGrabSprite) {
                    break;
                }
                final float x = ev.getX();
                final float y = ev.getY();
                View cursor = mSpriteContainer.getChildAt(0);
                move(cursor, x, y);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (mGrabSprite) {
                    mGrabSprite = false;
                    mSpriteContainer.removeViewAt(0);
                }
                break;
        }
        return mForceTouchDetector.onTouchEvent(ev) || super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onForceTouch(float x, float y) {
        ++mForceTouch;
        mTextView.setText("event: Force Touch " + mForceTouch);
        mRippleAnimator.cancel();
        move(mRippleView, x, y);
        mRippleAnimator.start();
        if (mGrabSprite) {
            addSprite(x, y);
        }
        return true;
    }

    @Override
    public void onForceTap(float x, float y) {
        mTextView.setText("event: Force Tap");
    }

    @Override
    public void onForceLongPress(float x, float y) {
        mTextView.setText("event: Force Long Press");
        if (!mGrabSprite) {
            removeAllSprites();
        }
    }

    @Override
    public void performOriginalOnTouchEvent(MotionEvent event) {
        super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchDown(float x, float y, float parameter) {
        if (mUsePressure) {
            mTextView.setText(getString(R.string.hello_world) + " pressure=" + parameter);
        } else {
            mTextView.setText(getString(R.string.hello_world) + " size=" + parameter);
        }
//        return down.getSize() < 0.23; // test of Knuckle Touch
        return false;
    }

    @Override
    public float getParameter(MotionEvent event, int index) {
        return mUsePressure ?
                ForceTouchDetector.getPressure(event, index) :
                ForceTouchDetector.getSize(event, index);
    }

    @Override
    public void onClick(View v) {
        if (mForceTouch > 0) {
//            mTextView.setText("event: Force Tap");
        } else {
            mTextView.setText("event: Normal Tap");
            if (mSpriteContainer.getChildCount() > 0) {
                removeSprite();
            }
        }
    }
}
