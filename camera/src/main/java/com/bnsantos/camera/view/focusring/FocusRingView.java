package com.bnsantos.camera.view.focusring;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Region;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.bnsantos.camera.R;
import com.bnsantos.camera.view.Invalidator;
import com.bnsantos.camera.view.animation.AnimationClock;
import com.bnsantos.camera.view.animation.DynamicAnimator;
import com.bnsantos.camera.view.animation.LinearScale;

/**
 * Custom view for running the focus ring animations.
 */
public class FocusRingView extends View implements Invalidator, FocusRing {
  private static final String TAG = new String("FocusRingView");
  private static final float FADE_IN_DURATION_MILLIS = 1000f;
  private static final float FADE_OUT_DURATION_MILLIS = 250f;

  private final AutoFocusRing mAutoFocusRing;
  private final ManualFocusRing mManualFocusRing;
  private final DynamicAnimator mAnimator;
  private final LinearScale mRatioScale;
  private final float mDefaultRadiusPx;

  private FocusRingRenderer currentFocusAnimation;
  private boolean isFirstDraw;
  private float mLastRadiusPx;

  @Nullable
  private RectF mPreviewSize;

  public FocusRingView(Context context, AttributeSet attrs) {
    super(context, attrs);

    Resources res = getResources();
    Paint paint = makePaint(res, R.color.focus_color);

    float focusCircleMinSize = res.getDimensionPixelSize(R.dimen.focus_circle_min_size);
    float focusCircleMaxSize = res.getDimensionPixelSize(R.dimen.focus_circle_max_size);
    mDefaultRadiusPx = res.getDimensionPixelSize(R.dimen.focus_circle_initial_size);

    mRatioScale = new LinearScale(0, 1, focusCircleMinSize, focusCircleMaxSize);
    mAnimator = new DynamicAnimator(this, new AnimationClock.SystemTimeClock());

    mAutoFocusRing = new AutoFocusRing(mAnimator, paint,
        FADE_IN_DURATION_MILLIS,
        FADE_OUT_DURATION_MILLIS);
    mManualFocusRing = new ManualFocusRing(mAnimator, paint,
        FADE_OUT_DURATION_MILLIS);

    mAnimator.animations.add(mAutoFocusRing);
    mAnimator.animations.add(mManualFocusRing);

    isFirstDraw = true;
    mLastRadiusPx = mDefaultRadiusPx;
  }

  @Override
  public boolean isPassiveFocusRunning() {
    return mAutoFocusRing.isActive();
  }

  @Override
  public boolean isActiveFocusRunning() {
    return mManualFocusRing.isActive();
  }

  @Override
  public void startPassiveFocus() {
    mAnimator.invalidate();
    long tMs = mAnimator.getTimeMillis();

    if (mManualFocusRing.isActive() && !mManualFocusRing.isExiting()) {
      mManualFocusRing.stop(tMs);
    }

    mAutoFocusRing.start(tMs, mLastRadiusPx, mLastRadiusPx);
    currentFocusAnimation = mAutoFocusRing;
  }

  @Override
  public void startActiveFocus() {
    mAnimator.invalidate();
    long tMs = mAnimator.getTimeMillis();

    if (mAutoFocusRing.isActive() && !mAutoFocusRing.isExiting()) {
      mAutoFocusRing.stop(tMs);
    }

    mManualFocusRing.start(tMs, 0.0f, mLastRadiusPx);
    currentFocusAnimation = mManualFocusRing;
  }

  @Override
  public void stopFocusAnimations() {
    long tMs = mAnimator.getTimeMillis();
    if (mManualFocusRing.isActive() && !mManualFocusRing.isExiting()
        && !mManualFocusRing.isEntering()) {
      mManualFocusRing.exit(tMs);
    }

    if (mAutoFocusRing.isActive() && !mAutoFocusRing.isExiting()) {
      mAutoFocusRing.exit(tMs);
    }
  }

  @Override
  public void setFocusLocation(float viewX, float viewY) {
    mAutoFocusRing.setCenterX((int) viewX);
    mAutoFocusRing.setCenterY((int) viewY);
    mManualFocusRing.setCenterX((int) viewX);
    mManualFocusRing.setCenterY((int) viewY);
  }

  @Override
  public void centerFocusLocation() {
    Point center = computeCenter();
    mAutoFocusRing.setCenterX(center.x);
    mAutoFocusRing.setCenterY(center.y);
    mManualFocusRing.setCenterX(center.x);
    mManualFocusRing.setCenterY(center.y);
  }

  @Override
  public void setRadiusRatio(float ratio) {
    setRadius(mRatioScale.scale(mRatioScale.clamp(ratio)));
  }

  @Override
  public void configurePreviewDimensions(RectF previewArea) {
    mPreviewSize = previewArea;
    mLastRadiusPx = mDefaultRadiusPx;

    if (!isFirstDraw) {
      centerAutofocusRing();
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (isFirstDraw) {
      isFirstDraw = false;
      centerAutofocusRing();
    }

    if (mPreviewSize != null) {
      canvas.clipRect(mPreviewSize, Region.Op.REPLACE);
    }

    mAnimator.draw(canvas);
  }

  private void setRadius(float radiusPx) {
    long tMs = mAnimator.getTimeMillis();
    // Some devices return zero for invalid or "unknown" diopter values.
    if (currentFocusAnimation != null && radiusPx > 0.1f) {
      currentFocusAnimation.setRadius(tMs, radiusPx);
      mLastRadiusPx = radiusPx;
    }
  }

  private void centerAutofocusRing() {
    Point center = computeCenter();
    mAutoFocusRing.setCenterX(center.x);
    mAutoFocusRing.setCenterY(center.y);
  }

  private Point computeCenter() {
    if (mPreviewSize != null && (mPreviewSize.width() * mPreviewSize.height() > 0.01f)) {
      Log.i(TAG, "Computing center via preview size.");
      return new Point((int) mPreviewSize.centerX(), (int) mPreviewSize.centerY());
    }
    Log.i(TAG, "Computing center via view bounds.");
    return new Point(getWidth() / 2, getHeight() / 2);
  }

  private Paint makePaint(Resources res, int color) {
    Paint paint = new Paint();
    paint.setAntiAlias(true);
    paint.setColor(res.getColor(color));
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeWidth(res.getDimension(R.dimen.focus_circle_stroke));
    return paint;
  }
}
