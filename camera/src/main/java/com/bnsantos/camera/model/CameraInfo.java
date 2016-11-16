package com.bnsantos.camera.model;

import android.graphics.Rect;

public class CameraInfo {
  private final String mCameraId;
  private final boolean mHasFlash;

  private final int mMaxAERegions;
  private FocusArea[] mAERegions;
  private final int mMaxAFRegions;
  private FocusArea[] mAFRegions;


  private final int mSensorOrientation;

  private final MySize mPreviewSize;
  private final MySize mJPEGSize;
  private final MySize mVideoSize;

  private final int mMaxDigitalZoom;
  private int mZoomLevel;

  private final Rect mSensorRect;

  public CameraInfo(String cameraId,
                    boolean hasFlash,
                    int maxAERegions,
                    int maxAFRegions,
                    int sensorOrientation,
                    MySize previewSize,
                    MySize JPEGSize,
                    MySize videoSize,
                    int maxDigitalZoom,
                    Rect sensorRect) {
    mCameraId = cameraId;
    mHasFlash = hasFlash;
    mMaxAERegions = maxAERegions;
    mMaxAFRegions = maxAFRegions;
    mSensorOrientation = sensorOrientation;
    mPreviewSize = previewSize;
    mJPEGSize = JPEGSize;
    mVideoSize = videoSize;
    mMaxDigitalZoom = maxDigitalZoom;
    mSensorRect = sensorRect;
  }

  public String getCameraId() {
    return mCameraId;
  }

  public boolean isDigitalZoomEnabled() {
    return mMaxDigitalZoom > 0;
  }

  public MySize getJPEGSize() {
    return mJPEGSize;
  }

  public MySize getPreviewSize() {
    return mPreviewSize;
  }

  public MySize getVideoSize() {
    return mVideoSize;
  }


  public boolean hasFlash() {
    return mHasFlash;
  }

  public int getSensorOrientation() {
    return mSensorOrientation;
  }

  public boolean hasAFRegionsEnabled(){
    return mMaxAFRegions > 0;
  }

  public boolean hasAERegionsEnabled(){
    return mMaxAERegions > 0;
  }

  public void scaleZoom(float factor){

    //TODO
    /*if(mCameraInfo.isDigitalZoomEnabled()){
      if (scaleFactor > 1.0f) {
        if (mZoomLevel < mCameraInfo.mMaxDigitalZoom)
          mZoomLevel++;
      } else {
        if (mZoomLevel > 1)
          mZoomLevel--;
      }
      setUpPreviewRepeatingRequest();
    }*/
  }

  public FocusArea[] getAERegions() {
    return mAERegions;
  }

  public void setAERegions(FocusArea[] aeRegions) {
    this.mAERegions = aeRegions;
  }

  public FocusArea[] getAFRegions() {
    return mAFRegions;
  }

  public void setAFRegions(FocusArea[] afRegions) {
    this.mAFRegions = afRegions;
  }

  public Rect getSensorRect() {
    return mSensorRect;
  }

  public int getMaxDigitalZoom() {
    return mMaxDigitalZoom;
  }
}
