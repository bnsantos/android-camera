package com.bnsantos.camera.view;

/**
 * Basic interface for objects that can be invalidated.
 */
public interface Invalidator {
  /**
   * Request that the object should be redrawn whenever it gets
   * the chance.
   */
  void invalidate();
}
