package com.bnsantos.camera.view.unit;

/**
 * Simple functions that produce values along a curve for any given input and can compute input
 * times for a given output value.
 */
public interface UnitCurve {

  /**
   * Produce a unit value of this curve at time t. The function should always return a valid
   * return value for any valid t input.
   *
   * @param t ratio of time passed from (0..1)
   * @return the unit value at t.
   */
  float valueAt(float t);

  /**
   * If possible, find a value for t such that valueAt(t) == value or best guess.
   *
   * @param value to match to the output of valueAt(t)
   * @return t where valueAt(t) == value or throw.
   */
  float tAt(float value);
}
