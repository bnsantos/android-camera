package com.bnsantos.camera.view.animation;

import android.os.SystemClock;

/**
 * Wraps the SystemClock static time methods so they can be exercised in tests.
 */
public abstract class AnimationClock {

  public abstract long getTimeMillis();

  /**
   * Forwards calls to SystemClock.uptimeMillis() since it is the most consistent clock for
   * animations.
   */
  public static class SystemTimeClock extends AnimationClock {

    @Override
    public long getTimeMillis() {
      return SystemClock.uptimeMillis();
    }
  }
}