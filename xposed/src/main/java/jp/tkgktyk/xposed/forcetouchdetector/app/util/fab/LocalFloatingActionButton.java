/*
 * Copyright (C) 2015 The Android Open Source Project
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

package jp.tkgktyk.xposed.forcetouchdetector.app.util.fab;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import java.util.List;

import jp.tkgktyk.xposed.forcetouchdetector.R;

/**
 * Floating action buttons are used for a special type of promoted action. They are distinguished
 * by a circled icon floating above the UI and have special motion behaviors related to morphing,
 * launching, and the transferring anchor point.
 *
 * <p>Floating action buttons come in two sizes: the default and the mini. The size can be
 * controlled with the {@code fabSize} attribute.</p>
 *
 * <p>As this class descends from {@link ImageView}, you can control the icon which is displayed
 * via {@link #setImageDrawable(Drawable)}.</p>
 *
 * <p>The background color of this view defaults to the your theme's {@code colorAccent}. If you
 * wish to change this at runtime then you can do so via
 * {@link #setBackgroundTintList(ColorStateList)}.</p>
 *
 * @attr ref android.support.design.R.styleable#FloatingActionButton_fabSize
 */
@CoordinatorLayout.DefaultBehavior(LocalFloatingActionButton.Behavior.class)
public class LocalFloatingActionButton extends ImageView {

    // These values must match those in the attrs declaration
    private static final int SIZE_MINI = 1;
    private static final int SIZE_NORMAL = 0;

    private ColorStateList mBackgroundTint;
    private PorterDuff.Mode mBackgroundTintMode;

    private int mBorderWidth;
    private int mRippleColor;
    private int mSize;
    private int mContentPadding;

    private final Rect mShadowPadding;

    private final FloatingActionButtonImpl mImpl;

    public LocalFloatingActionButton(Context context) {
        this(context, false);
    }

    public LocalFloatingActionButton(Context context, boolean bigContent) {
        this(context, null, bigContent);
    }

    public LocalFloatingActionButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LocalFloatingActionButton(Context context, AttributeSet attrs, boolean bigContent) {
        this(context, attrs, 0, bigContent);
    }

    public LocalFloatingActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, false);
    }

    public LocalFloatingActionButton(Context context, AttributeSet attrs, int defStyleAttr,
                                     boolean bigContent) {
        super(context, attrs, defStyleAttr);

        mShadowPadding = new Rect();

//        TypedArray a = context.obtainStyledAttributes(attrs,
//                R.styleable.FloatingActionButton, defStyleAttr,
//                R.style.Widget_Design_FloatingActionButton);
        Drawable background = getResources().getDrawable(R.drawable.design_fab_background);
//                a.getDrawable(R.styleable.FloatingActionButton_android_background);
        mBackgroundTint = ColorStateList.valueOf(Color.BLACK);
//                a.getColorStateList(R.styleable.FloatingActionButton_backgroundTint);
        mBackgroundTintMode = parseTintMode(-1, null);
//                parseTintMode(a.getInt(
//                R.styleable.FloatingActionButton_backgroundTintMode, -1), null);
        mRippleColor = 0;
//                a.getColor(R.styleable.FloatingActionButton_rippleColor, 0);
        mSize = SIZE_NORMAL;
//                a.getInt(R.styleable.FloatingActionButton_fabSize, SIZE_NORMAL);
        mBorderWidth = getResources().getDimensionPixelSize(R.dimen.design_fab_border_width);
//                a.getDimensionPixelSize(R.styleable.FloatingActionButton_borderWidth, 0);
        final float elevation = getResources().getDimensionPixelSize(R.dimen.design_fab_elevation);
//                a.getDimension(R.styleable.FloatingActionButton_elevation, 0f);
        final float pressedTranslationZ = getResources().getDimensionPixelSize(R.dimen.design_fab_translation_z_pressed);
//                a.getDimension(
//                R.styleable.FloatingActionButton_pressedTranslationZ, 0f);
//        a.recycle();

        final ShadowViewDelegate delegate = new ShadowViewDelegate() {
            @Override
            public float getRadius() {
                return getSizeDimension() / 2f;
            }

            @Override
            public void setShadowPadding(int left, int top, int right, int bottom) {
                mShadowPadding.set(left, top, right, bottom);

                setPadding(left + mContentPadding, top + mContentPadding,
                        right + mContentPadding, bottom + mContentPadding);
            }

            @Override
            public void setBackgroundDrawable(Drawable background) {
                LocalFloatingActionButton.super.setBackgroundDrawable(background);
            }
        };

        final int sdk = Build.VERSION.SDK_INT;
        if (sdk >= 21) {
            mImpl = new FloatingActionButtonLollipop(this, delegate);
        } else if (sdk >= 12) {
            mImpl = new FloatingActionButtonHoneycombMr1(this, delegate);
        } else {
            mImpl = new FloatingActionButtonEclairMr1(this, delegate);
        }

        final int maxContentSize = (int) getResources().getDimension(
                bigContent? R.dimen.local_fab_content_size: R.dimen.design_fab_image_size);
        mContentPadding = (getSizeDimension() - maxContentSize) / 2;

        mImpl.setBackgroundDrawable(background, mBackgroundTint,
                mBackgroundTintMode, mRippleColor, mBorderWidth);
        mImpl.setElevation(elevation);
        mImpl.setPressedTranslationZ(pressedTranslationZ);

        setClickable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int preferredSize = getSizeDimension();

        final int w = resolveAdjustedSize(preferredSize, widthMeasureSpec);
        final int h = resolveAdjustedSize(preferredSize, heightMeasureSpec);

        // As we want to stay circular, we set both dimensions to be the
        // smallest resolved dimension
        final int d = Math.min(w, h);

        // We add the shadow's padding to the measured dimension
        setMeasuredDimension(
                d + mShadowPadding.left + mShadowPadding.right,
                d + mShadowPadding.top + mShadowPadding.bottom);
    }

    /**
     * Set the ripple color for this {@link LocalFloatingActionButton}.
     * <p>
     * When running on devices with KitKat or below, we draw a fill rather than a ripple.
     *
     * @param color ARGB color to use for the ripple.
     */
    public void setRippleColor(@ColorInt int color) {
        if (mRippleColor != color) {
            mRippleColor = color;
            mImpl.setRippleColor(color);
        }
    }

    /**
     * Return the tint applied to the background drawable, if specified.
     *
     * @return the tint applied to the background drawable
     * @see #setBackgroundTintList(ColorStateList)
     */
    @Nullable
    @Override
    public ColorStateList getBackgroundTintList() {
        return mBackgroundTint;
    }

    /**
     * Applies a tint to the background drawable. Does not modify the current tint
     * mode, which is {@link PorterDuff.Mode#SRC_IN} by default.
     *
     * @param tint the tint to apply, may be {@code null} to clear tint
     */
    public void setBackgroundTintList(@Nullable ColorStateList tint) {
        if (mBackgroundTint != tint) {
            mBackgroundTint = tint;
            mImpl.setBackgroundTintList(tint);
        }
    }


    /**
     * Return the blending mode used to apply the tint to the background
     * drawable, if specified.
     *
     * @return the blending mode used to apply the tint to the background
     *         drawable
     * @see #setBackgroundTintMode(PorterDuff.Mode)
     */
    @Nullable
    @Override
    public PorterDuff.Mode getBackgroundTintMode() {
        return mBackgroundTintMode;
    }

    /**
     * Specifies the blending mode used to apply the tint specified by
     * {@link #setBackgroundTintList(ColorStateList)}} to the background
     * drawable. The default mode is {@link PorterDuff.Mode#SRC_IN}.
     *
     * @param tintMode the blending mode used to apply the tint, may be
     *                 {@code null} to clear tint
     */
    public void setBackgroundTintMode(@Nullable PorterDuff.Mode tintMode) {
        if (mBackgroundTintMode != tintMode) {
            mBackgroundTintMode = tintMode;
            mImpl.setBackgroundTintMode(tintMode);
        }
    }

    @Override
    public void setBackgroundDrawable(@NonNull Drawable background) {
        if (mImpl != null) {
            mImpl.setBackgroundDrawable(
                    background, mBackgroundTint, mBackgroundTintMode, mRippleColor, mBorderWidth);
        }
    }

    /**
     * Shows the button.
     * <p>This method will animate it the button show if the view has already been laid out.</p>
     */
    public void show() {
        mImpl.show();
    }

    /**
     * Hides the button.
     * <p>This method will animate the button hide if the view has already been laid out.</p>
     */
    public void hide() {
        mImpl.hide();
    }

    final int getSizeDimension() {
        switch (mSize) {
            case SIZE_MINI:
                return getResources().getDimensionPixelSize(R.dimen.design_fab_size_mini);
            case SIZE_NORMAL:
            default:
                return getResources().getDimensionPixelSize(R.dimen.design_fab_size_normal);
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        mImpl.onDrawableStateChanged(getDrawableState());
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        mImpl.jumpDrawableToCurrentState();
    }

    private static int resolveAdjustedSize(int desiredSize, int measureSpec) {
        int result = desiredSize;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                // Parent says we can be as big as we want. Just don't be larger
                // than max size imposed on ourselves.
                result = desiredSize;
                break;
            case MeasureSpec.AT_MOST:
                // Parent says we can be as big as we want, up to specSize.
                // Don't be larger than specSize, and don't be larger than
                // the max size imposed on ourselves.
                result = Math.min(desiredSize, specSize);
                break;
            case MeasureSpec.EXACTLY:
                // No choice. Do what we are told.
                result = specSize;
                break;
        }
        return result;
    }

    static PorterDuff.Mode parseTintMode(int value, PorterDuff.Mode defaultMode) {
        switch (value) {
            case 3:
                return PorterDuff.Mode.SRC_OVER;
            case 5:
                return PorterDuff.Mode.SRC_IN;
            case 9:
                return PorterDuff.Mode.SRC_ATOP;
            case 14:
                return PorterDuff.Mode.MULTIPLY;
            case 15:
                return PorterDuff.Mode.SCREEN;
            default:
                return defaultMode;
        }
    }

    /**
     * Behavior designed for use with {@link LocalFloatingActionButton} instances. It's main function
     * is to move {@link LocalFloatingActionButton} views so that any displayed {@link Snackbar}s do
     * not cover them.
     */
    public static class Behavior extends CoordinatorLayout.Behavior<LocalFloatingActionButton> {
        // We only support the FAB <> Snackbar shift movement on Honeycomb and above. This is
        // because we can use view translation properties which greatly simplifies the code.
        private static final boolean SNACKBAR_BEHAVIOR_ENABLED = Build.VERSION.SDK_INT >= 11;

        private Rect mTmpRect;

        @Override
        public boolean layoutDependsOn(CoordinatorLayout parent,
                LocalFloatingActionButton child, View dependency) {
            // We're dependent on all SnackbarLayouts (if enabled)
            return SNACKBAR_BEHAVIOR_ENABLED && dependency instanceof Snackbar.SnackbarLayout;
        }

        @Override
        public boolean onDependentViewChanged(CoordinatorLayout parent, LocalFloatingActionButton child,
                View dependency) {
            if (dependency instanceof Snackbar.SnackbarLayout) {
                updateFabTranslationForSnackbar(parent, child, dependency);
            } else if (dependency instanceof AppBarLayout) {
                // If we're depending on an AppBarLayout we will show/hide it automatically
                // if the FAB is anchored to the AppBarLayout
                updateFabVisibility(parent, (AppBarLayout) dependency, child);
            }
            return false;
        }

        @Override
        public void onDependentViewRemoved(CoordinatorLayout parent, LocalFloatingActionButton child,
                View dependency) {
            if (dependency instanceof Snackbar.SnackbarLayout) {
                // If the removed view is a SnackbarLayout, we will animate back to our normal
                // position
                if (ViewCompat.getTranslationY(child) != 0f) {
                    ViewCompat.animate(child)
                            .translationY(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setInterpolator(AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR)
                            .setListener(null);
                }
            }
        }

        private boolean updateFabVisibility(CoordinatorLayout parent,
                AppBarLayout appBarLayout, LocalFloatingActionButton child) {
            final CoordinatorLayout.LayoutParams lp =
                    (CoordinatorLayout.LayoutParams) child.getLayoutParams();
            if (lp.getAnchorId() != appBarLayout.getId()) {
                // The anchor ID doesn't match the dependency, so we won't automatically
                // show/hide the FAB
                return false;
            }

            if (mTmpRect == null) {
                mTmpRect = new Rect();
            }

            // First, let's get the visible rect of the dependency
            final Rect rect = mTmpRect;
            ViewGroupUtils.getDescendantRect(parent, appBarLayout, rect);

            if (rect.bottom <= appBarLayout.getMinimumHeightForVisibleOverlappingContent()) {
                // If the anchor's bottom is below the seam, we'll animate our FAB out
                child.hide();
            } else {
                // Else, we'll animate our FAB back in
                child.show();
            }
            return true;
        }

        private void updateFabTranslationForSnackbar(CoordinatorLayout parent,
                LocalFloatingActionButton fab, View snackbar) {
            if (fab.getVisibility() != View.VISIBLE) {
                return;
            }

            final float translationY = getFabTranslationYForSnackbar(parent, fab);
            ViewCompat.setTranslationY(fab, translationY);
        }

        private float getFabTranslationYForSnackbar(CoordinatorLayout parent,
                LocalFloatingActionButton fab) {
            float minOffset = 0;
            final List<View> dependencies = parent.getDependencies(fab);
            for (int i = 0, z = dependencies.size(); i < z; i++) {
                final View view = dependencies.get(i);
                if (view instanceof Snackbar.SnackbarLayout && parent.doViewsOverlap(fab, view)) {
                    minOffset = Math.min(minOffset,
                            ViewCompat.getTranslationY(view) - view.getHeight());
                }
            }

            return minOffset;
        }

        @Override
        public boolean onLayoutChild(CoordinatorLayout parent, LocalFloatingActionButton child,
                int layoutDirection) {
            // First, lets make sure that the visibility of the FAB is consistent
            final List<View> dependencies = parent.getDependencies(child);
            for (int i = 0, count = dependencies.size(); i < count; i++) {
                final View dependency = dependencies.get(i);
                if (dependency instanceof AppBarLayout
                        && updateFabVisibility(parent, (AppBarLayout) dependency, child)) {
                    break;
                }
            }
            // Now let the CoordinatorLayout lay out the FAB
            parent.onLayoutChild(child, layoutDirection);
            // Now offset it if needed
            offsetIfNeeded(parent, child);
            return true;
        }

        /**
         * Pre-Lollipop we use padding so that the shadow has enough space to be drawn. This method
         * offsets our layout position so that we're positioned correctly if we're on one of
         * our parent's edges.
         */
        private void offsetIfNeeded(CoordinatorLayout parent, LocalFloatingActionButton fab) {
            final Rect padding = fab.mShadowPadding;

            if (padding != null && padding.centerX() > 0 && padding.centerY() > 0) {
                final CoordinatorLayout.LayoutParams lp =
                        (CoordinatorLayout.LayoutParams) fab.getLayoutParams();

                int offsetTB = 0, offsetLR = 0;

                if (fab.getRight() >= parent.getWidth() - lp.rightMargin) {
                    // If we're on the left edge, shift it the right
                    offsetLR = padding.right;
                } else if (fab.getLeft() <= lp.leftMargin) {
                    // If we're on the left edge, shift it the left
                    offsetLR = -padding.left;
                }
                if (fab.getBottom() >= parent.getBottom() - lp.bottomMargin) {
                    // If we're on the bottom edge, shift it down
                    offsetTB = padding.bottom;
                } else if (fab.getTop() <= lp.topMargin) {
                    // If we're on the top edge, shift it up
                    offsetTB = -padding.top;
                }

                fab.offsetTopAndBottom(offsetTB);
                fab.offsetLeftAndRight(offsetLR);
            }
        }
    }
}
