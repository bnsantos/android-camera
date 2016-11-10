package com.bnsantos.camera.view.camera;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import com.bnsantos.camera.CameraActivity;
import com.bnsantos.camera.R;
import com.bnsantos.camera.view.AutoFitTextureView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.bnsantos.camera.CameraActivity.DATE_FORMAT;
import static com.bnsantos.camera.CameraActivity.FOLDER;

@SuppressWarnings("deprecation")
public class CameraFragment extends AbstractCameraFragment implements TextureView.SurfaceTextureListener, AutoFitTextureView.TouchEventInterface, CameraActivity.CameraKeyListener, Camera.AutoFocusCallback {
  private static final String TAG = CameraFragment.class.getSimpleName();
  private Camera mCamera;
  private CameraInfo mCameraInfo;

  private int mChosenCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
  protected boolean mTextureReady;

  public static Bitmap toBitmap(byte[] data) {
    return BitmapFactory.decodeByteArray(data , 0, data.length);
  }

  public static Bitmap rotate(Bitmap in, int angle) {
    Matrix mat = new Matrix();
    mat.postRotate(angle);
    return Bitmap.createBitmap(in, 0, 0, in.getWidth(), in.getHeight(), mat, true);
  }

  private Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {
    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
      //Resuming preview
      mCamera.startPreview();

      /*
        Creating folder
       */
      File directory = Environment.getExternalStoragePublicDirectory(FOLDER);
      if(!directory.exists()){
        Log.i(TAG, "Creating folders: " + directory.mkdirs() );
      }

      /*
        "Exif" rotation
       */
      Bitmap bitmap = toBitmap(data);
      if(mChosenCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
        bitmap = rotate(bitmap, mCameraInfo.mSensorOrientation);
      }else{
        bitmap = rotate(bitmap, -mCameraInfo.mSensorOrientation);
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);

      String timeStamp = DATE_FORMAT.format(new Date());
      File file = new File(directory, "pic_"+timeStamp+".jpg");
      FileOutputStream fos = null;
      try {
        fos = new FileOutputStream(file);
        fos.write(bos.toByteArray());
        fos.close();

        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(file));
      } catch (FileNotFoundException e) {
        Log.d(TAG, "File not found: " + e.getMessage());
      } catch (IOException e) {
        Log.d(TAG, "Error accessing file: " + e.getMessage());
      } finally {

        if (null != fos) {
          try {
            fos.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }
  };

  private Camera.ShutterCallback mShutterCallback = new Camera.ShutterCallback() {
    @Override
    public void onShutter() { }
  };

  public static CameraFragment newInstance() {
    return new CameraFragment();
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    if(savedInstanceState != null){
      if(savedInstanceState.containsKey(BUNDLE_CHOSEN_CAMERA)) {
        mChosenCamera = savedInstanceState.getInt(BUNDLE_CHOSEN_CAMERA);
      }
    }
    mTextureReady = false;
    mTexture.setSurfaceTextureListener(this);
    mTexture.setTouchListener(this);
  }

  private void openCamera(){
    if(mCamera != null){
      releaseCamera();
    }
    mZoomLevel = 1;
    int numberOfCameras = Camera.getNumberOfCameras();
    for (int i = 0; i < numberOfCameras; i++) {
      Camera.CameraInfo info = new Camera.CameraInfo();
      Camera.getCameraInfo(i, info);
      if(info.facing == mChosenCamera){
        mCamera = Camera.open(i);
        mCameraInfo = new CameraInfo(i, info, mCamera.getParameters());
        updateFlashButtonVisibility(mCameraInfo.mFlashSupported);
        try {
          int orientation = getResources().getConfiguration().orientation;
          if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mTexture.setAspectRatio(mCameraInfo.mPreviewSize.width, mCameraInfo.mPreviewSize.height);
          } else {
            //noinspection SuspiciousNameCombination
            mTexture.setAspectRatio(mCameraInfo.mPreviewSize.height, mCameraInfo.mPreviewSize.width);
          }
          mCamera.setPreviewTexture(mTexture.getSurfaceTexture());
          mCamera.startPreview();
          mCamera.setDisplayOrientation(mCameraInfo.mSensorOrientation);
          mCamera.setParameters(mCameraInfo.mParameters);
        } catch (IOException e) {
          e.printStackTrace();
        }
        return;
      }
    }
  }

  private void releaseCamera() {
    if (mCamera != null) {
      mCamera.release();
      mCamera = null;
    }
  }

  @Override
  protected void changeCamera() {
    if (mChosenCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
      mChosenCamera = Camera.CameraInfo.CAMERA_FACING_FRONT;
      mChangeCamera.setImageResource(R.drawable.ic_camera_front_white_24dp);
    }else {
      mChosenCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
      mChangeCamera.setImageResource(R.drawable.ic_camera_rear_white_24dp);
    }
    openCamera();
  }

  @Override
  protected void changeFlashMode() {
    if(mCameraInfo.mFlashSupported) {
      super.changeFlashMode();
      switch (mFlashMode) {
        case FLASH_ON:
          mCameraInfo.mParameters.setFlashMode("on");
          break;
        case FLASH_OFF:
          mCameraInfo.mParameters.setFlashMode("off");
          break;
        default:
          mCameraInfo.mParameters.setFlashMode("auto");
      }
    }
  }

  @Override
  protected void takePicture() {
    mCamera.setParameters(mCameraInfo.mParameters);
    mCamera.takePicture(mShutterCallback, null, mPictureCallback);
  }

  @Override
  protected void toggleLocation() {
    mLocationEnabled = !mLocationEnabled;
    setLocationIcon();
  }

  @Override
  public void onResume() {
    super.onResume();
    if(mTextureReady){
      openCamera();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    releaseCamera();
  }

  @Override
  public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
    mTextureReady = true;
    openCamera();
  }

  @Override
  public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

  }

  @Override
  public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
    releaseCamera();
    return false;
  }

  @Override
  public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(BUNDLE_CHOSEN_CAMERA, mChosenCamera);
  }

  @Override
  public void onZoom(float scaleFactor) {
    if(mCameraInfo.isDigitalZoomEnabled()){
      if (scaleFactor > 1.0f) {
        if (mZoomLevel < mCameraInfo.mMaxDigitalZoom)
          mZoomLevel++;
      } else {
        if (mZoomLevel > 1)
          mZoomLevel--;
      }
      mCameraInfo.mParameters.setZoom(mZoomLevel);
      mCamera.setParameters(mCameraInfo.mParameters);
      mCamera.startPreview();
    }
  }

  @Override
  public void onFocus(float x, float y) {
    if(mCameraInfo.mMaxAFRegions > 0) {
      mCamera.cancelAutoFocus();
      mCameraInfo.mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
      setAreas(x, y);
      mCamera.setParameters(mCameraInfo.mParameters);
      mCamera.startPreview();
      mCamera.autoFocus(this);
      playFocusRingAnimation(x, y);
    }
  }

  private void setAreas(float x, float y) {
    List<Camera.Area> focusAreas = new ArrayList<>();
    Rect focusRect = calculateTapArea(x, y, 1f);
    focusAreas.add(new Camera.Area(focusRect, 1000));
    mCameraInfo.mParameters.setFocusAreas(focusAreas);


    if(mCameraInfo.mMaxAERegions != 0){
      List<Camera.Area> meteringAreas = new ArrayList<>();
      Rect meteringRect = calculateTapArea(x, y, 1.5f);
      meteringAreas.add(new Camera.Area(meteringRect, 1000));
      mCameraInfo.mParameters.setMeteringAreas(meteringAreas);
    }
  }

  /**
   * Convert touch position x:y to {@link Camera.Area} position -1000:-1000 to 1000:1000.
   */
  private Rect calculateTapArea(float x, float y, float coefficient) {
    int areaSize = Float.valueOf(50 * coefficient).intValue();

    int left = clamp((int) x - areaSize / 2, 0, mTexture.getWidth() - areaSize);
    int top = clamp((int) y - areaSize / 2, 0, mTexture.getHeight() - areaSize);

    Matrix matrix = new Matrix();
    // Need mirror for front camera.
    boolean mirror = (mChosenCamera == Camera.CameraInfo.CAMERA_FACING_FRONT);
    matrix.setScale(mirror ? -1 : 1, 1);
    // This is the value for android.hardware.Camera.setDisplayOrientation.
    matrix.postRotate(mCameraInfo.mSensorOrientation);
    // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
    // UI coordinates range from (0, 0) to (width, height).
    matrix.postScale(mTexture.getWidth() / 2000f, mTexture.getHeight() / 2000f);
    matrix.postTranslate(mTexture.getWidth() / 2f, mTexture.getHeight() / 2f);

    RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
    matrix.mapRect(rectF);

    return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
  }

  private int clamp(int x, int min, int max) {
    if (x > max) {
      return max;
    }
    if (x < min) {
      return min;
    }
    return x;
  }

  @Override
  public void onKeyClicked() {
    takePicture();
  }

  @Override
  public void onAutoFocus(boolean b, Camera camera) {
    Log.i("BRUNO", "Auto focus: " +b);
  }

  private class CameraInfo{
    int mCameraId;
    boolean mFlashSupported;
    int mMaxAERegions;
    int mMaxAFRegions;
    int mMaxDigitalZoom;
    int mSensorOrientation;

    Camera.Size mPreviewSize;
    Camera.Size mJPEGSize;

    Camera.Parameters mParameters;
    private final List<String> mSupportedFlashModes;

    @SuppressWarnings("SuspiciousNameCombination")
    public CameraInfo(int cameraId, Camera.CameraInfo info, Camera.Parameters parameters) {
      mCameraId = cameraId;
      mPreviewSize = parameters.getPreviewSize();
      mMaxDigitalZoom = parameters.getMaxZoom();
      mMaxAERegions = parameters.getMaxNumMeteringAreas();
      mMaxAFRegions = parameters.getMaxNumFocusAreas();

      int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
      int degrees = 0;
      switch (rotation) {
        case Surface.ROTATION_0: degrees = 0; break;
        case Surface.ROTATION_90: degrees = 90; break;
        case Surface.ROTATION_180: degrees = 180; break;
        case Surface.ROTATION_270: degrees = -90; break;
      }

      if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
        mSensorOrientation = (info.orientation + degrees) % 360;
        mSensorOrientation = (360 - mSensorOrientation) % 360;  // compensate the mirror
      } else {  // back-facing
        mSensorOrientation = (info.orientation - degrees + 360) % 360;
      }

      mParameters = mCamera.getParameters();

      if(mPreviewSize.height > mPreviewSize.width) {
        mParameters.setPictureSize(mPreviewSize.height, mPreviewSize.width);
      }else{
        mParameters.setPictureSize(mPreviewSize.width, mPreviewSize.height);
      }

      mSupportedFlashModes = parameters.getSupportedFlashModes();
      mFlashSupported = mSupportedFlashModes != null && mSupportedFlashModes.size() > 0;
      if(mFlashSupported) {
        mSupportedFlashModes.remove("torch");
      }
    }

    public boolean isDigitalZoomEnabled() {
      return mMaxDigitalZoom > 0;
    }
  }
}
