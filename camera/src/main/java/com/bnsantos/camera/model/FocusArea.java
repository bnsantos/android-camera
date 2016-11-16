package com.bnsantos.camera.model;

import android.graphics.Rect;

/**
 * Created by bruno on 16/11/16.
 */

public class FocusArea {
  private final Rect mArea;
  private final int mWeight;

  public FocusArea(Rect area, int weight) {
    this.mArea = area;
    this.mWeight = weight;
  }

  public Rect getArea() {
    return mArea;
  }

  public int getWeight() {
    return mWeight;
  }
}
