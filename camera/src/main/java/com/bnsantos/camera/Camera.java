package com.bnsantos.camera;


import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Camera {
  private static final String TAG = Camera.class.toString();

  /**
   * Max preview width and height that is guaranteed by Camera2 API
   */
  private static final int MAX_PREVIEW_WIDTH = 1920;
  private static final int MAX_PREVIEW_HEIGHT = 1080;

  private String mCameraId;
  private boolean mFlashSupported;
  private Integer mMaxAFRegions;
  private Integer mMaxAERegions;
  private Float mMaxDigitalZoom;
  private MeteringRectangle[] mAFRegions = null; // no need for has_scalar_crop_region, as we can set to null instead
  private MeteringRectangle [] mAERegions = null; // no need for has_scalar_crop_region, as we can set to null instead
  /**
   * Orientation of the camera sensor
   */
  private int mSensorOrientation;

  /**
   * The {@link android.util.Size} of camera preview.
   */
  private Size mPreviewSize;

  private Size mJPEGSize;

  @SuppressWarnings("SuspiciousNameCombination")
  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public Camera(String cameraId, CameraCharacteristics cameraCharacteristics, Activity activity, int width, int height) {
    if(cameraCharacteristics != null) {
      StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      if (map == null) {
        return;
      }

      mMaxAFRegions = cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
      mMaxAERegions = cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);

      mMaxDigitalZoom = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
      if(mMaxDigitalZoom != null){
        mMaxDigitalZoom *= 5;//TODO
      }

      // Find out if we need to swap dimension to get the preview size relative to sensor
      // coordinate.
      int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
      //noinspection ConstantConditions
      mSensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
      boolean swappedDimensions = false;
      switch (displayRotation) {
        case Surface.ROTATION_0:
        case Surface.ROTATION_180:
          if (mSensorOrientation == 90 || mSensorOrientation == 270) {
            swappedDimensions = true;
          }
          break;
        case Surface.ROTATION_90:
        case Surface.ROTATION_270:
          if (mSensorOrientation == 0 || mSensorOrientation == 180) {
            swappedDimensions = true;
          }
          break;
        default:
          Log.e(TAG, "Display rotation is invalid: " + displayRotation);
      }

      Point displaySize = new Point();
      activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
      int rotatedPreviewWidth = width;
      int rotatedPreviewHeight = height;
      int maxPreviewWidth = displaySize.x;
      int maxPreviewHeight = displaySize.y;

      if (swappedDimensions) {
        rotatedPreviewWidth = height;
        rotatedPreviewHeight = width;
        maxPreviewWidth = displaySize.y;
        maxPreviewHeight = displaySize.x;
      }

      if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
        maxPreviewWidth = MAX_PREVIEW_WIDTH;
      }

      if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
        maxPreviewHeight = MAX_PREVIEW_HEIGHT;
      }

      // For still image captures, we use the largest available size.
      // TODO JPEG output image size
      mJPEGSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());

      // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
      // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
      // garbage capture data.
      mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, mJPEGSize);

      // Check if the flash is supported.
      Boolean available = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
      mFlashSupported = available == null ? false : available;



      mCameraId = cameraId;
    }
  }

  public String getCameraId() {
    return mCameraId;
  }

  public Size getPreviewSize(){
    return mPreviewSize;
  }

  /*
    Zoom
   */
  public boolean isDigitalZoomEnabled() {
    return mMaxDigitalZoom != null && mMaxDigitalZoom >0.0f;
  }

  public boolean isFlashSupported(){
    return mFlashSupported;
  }

  public Float getMaxDigitalZoom(){
    return mMaxDigitalZoom;
  }

  /*
    Regions
   */
  public boolean hasAERegions(){
    return mMaxAERegions != null && mMaxAERegions > 0 && mAERegions != null;
  }

  public MeteringRectangle[] getAERegions(){
    return mAERegions;
  }

  public boolean hasAFRegions(){
    return mMaxAFRegions != null && mMaxAFRegions > 0 && mAFRegions != null;
  }

  public MeteringRectangle[] getAFRegions(){
    return mAFRegions;
  }

  public int getSensorOrientation(){
    return mSensorOrientation;
  }

  public Size getJPEGSize(){
    return mJPEGSize;
  }

  /**
   * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
   * is at least as large as the respective texture view size, and that is at most as large as the
   * respective max size, and whose aspect ratio matches with the specified value. If such size
   * doesn't exist, choose the largest one that is at most as large as the respective max size,
   * and whose aspect ratio matches with the specified value.
   *
   * @param choices           The list of sizes that the camera supports for the intended output
   *                          class
   * @param textureViewWidth  The width of the texture view relative to sensor coordinate
   * @param textureViewHeight The height of the texture view relative to sensor coordinate
   * @param maxWidth          The maximum width that can be chosen
   * @param maxHeight         The maximum height that can be chosen
   * @param aspectRatio       The aspect ratio
   * @return The optimal {@code Size}, or an arbitrary one if none were big enough
   */
  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {
    // Collect the supported resolutions that are at least as big as the preview Surface
    List<Size> bigEnough = new ArrayList<>();
    // Collect the supported resolutions that are smaller than the preview Surface
    List<Size> notBigEnough = new ArrayList<>();
    int w = aspectRatio.getWidth();
    int h = aspectRatio.getHeight();
    for (Size option : choices) {
      if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight && option.getHeight() == option.getWidth() * h / w) {
        if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
          bigEnough.add(option);
        } else {
          notBigEnough.add(option);
        }
      }
    }

    // Pick the smallest of those big enough. If there is no one big enough, pick the
    // largest of those not big enough.
    if (bigEnough.size() > 0) {
      return Collections.min(bigEnough, new CompareSizesByArea());
    } else if (notBigEnough.size() > 0) {
      return Collections.max(notBigEnough, new CompareSizesByArea());
    } else {
      Log.e(TAG, "Couldn't find any suitable preview size");
      return choices[0];
    }
  }


  /**
   * Compares two {@code Size}s based on their areas.
   */
  private static class CompareSizesByArea implements Comparator<Size> {
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public int compare(Size lhs, Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }
}
