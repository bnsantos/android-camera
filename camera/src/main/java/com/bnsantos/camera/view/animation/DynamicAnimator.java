package com.bnsantos.camera.view.animation;

import android.graphics.Canvas;

import com.bnsantos.camera.view.Invalidator;

import java.util.ArrayList;
import java.util.List;

/**
 * Designed to handle the lifecycle of a view that needs a continuous update /
 * redraw cycle that does not have a defined start / end time.
 *
 * Fixed length animations should NOT use this class.
 */
public class DynamicAnimator implements Invalidator {

  public final List<DynamicAnimation> animations = new ArrayList<>();

  private final Invalidator mInvalidator;
  private final AnimationClock mClock;

  private boolean mUpdateRequested = false;
  private boolean mIsDrawing = false;
  private long mLastDrawTimeMillis = 0;
  private long mDrawTimeMillis = 0;

  public DynamicAnimator(Invalidator invalidator, AnimationClock clock) {
    mInvalidator = invalidator;
    mClock = clock;
  }

  public void draw(Canvas canvas) {
    mIsDrawing = true;
    mUpdateRequested = false;

    mDrawTimeMillis = mClock.getTimeMillis();

    if (mLastDrawTimeMillis <= 0) {
      mLastDrawTimeMillis = mDrawTimeMillis; // On the initial draw, dt is zero.
    }

    long dt = mDrawTimeMillis - mLastDrawTimeMillis;
    mLastDrawTimeMillis = mDrawTimeMillis;

    // Run the animation
    for (DynamicAnimation renderer : animations) {
      if (renderer.isActive()) {
        renderer.draw(mDrawTimeMillis, dt, canvas);
      }
    }

    // If either the update or the draw methods requested new frames, then
    // invalidate the view which should give us another frame to work with.
    // Otherwise, stopAt the last update time.
    if (mUpdateRequested) {
      mInvalidator.invalidate();
    } else {
      mLastDrawTimeMillis = -1;
    }

    mIsDrawing = false;
  }

  /**
   * If a scheduleNewFrame request comes in outside of the animation loop,
   * and we didn't schedule a frame after the previous loop (or it's the
   * first time we've used this instance), invalidate the view and set the
   * last update time to the current time. Theoretically, a few milliseconds
   * have elapsed before the view gets updated.
   */
  @Override
  public void invalidate() {
    if (!mIsDrawing && !mUpdateRequested) {
      mInvalidator.invalidate();
      mLastDrawTimeMillis = mClock.getTimeMillis();
    }

    mUpdateRequested = true;
  }

  /**
   * This will return the "best guess" for the most current animation frame
   * time.  If the loop is currently drawing, then it will return the time the
   * draw began, and if an update is currently requested it will return the
   * time that the update was requested at, and if neither of these are true
   * it will return the current system clock time.
   *
   * This method will not trigger a new update.
   */
  public long getTimeMillis() {
    if (mIsDrawing) {
      return mDrawTimeMillis;
    }

    if (mUpdateRequested) {
      return mLastDrawTimeMillis;
    }

    return mClock.getTimeMillis();
  }
}
