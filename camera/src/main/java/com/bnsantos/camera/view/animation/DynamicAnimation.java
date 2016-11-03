package com.bnsantos.camera.view.animation;

import android.graphics.Canvas;

/**
 * Rendering object that can be driven by an animator instance.
 */
public interface DynamicAnimation {

  /**
   * Check to determine if this animation is currently in a stable state.
   *
   * @return true if the animation is stable, false if it should continue to be redrawn.
   */
  boolean isActive();

  /**
   * Update and draw the animation onto the given canvas.
   *
   * @param t current animation frame time.
   * @param dt delta since the last update.
   * @param canvas the canvas to draw the animation onto.
   */
  void draw(long t, long dt, Canvas canvas);
}
