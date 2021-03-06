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
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.ExifInterface;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import com.bnsantos.camera.CameraActivity;
import com.bnsantos.camera.R;
import com.bnsantos.camera.model.CameraInfo;
import com.bnsantos.camera.model.MySize;
import com.bnsantos.camera.view.AutoFitTextureView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class CameraFragment extends AbstractCameraFragment implements TextureView.SurfaceTextureListener, AutoFitTextureView.TouchEventInterface, CameraActivity.CameraKeyListener, Camera.AutoFocusCallback {
  private static final String TAG = CameraFragment.class.getSimpleName();

  private Camera mCamera;
  private CameraInfo mCameraInfo;
  private Camera.Parameters mParameters;

  private int mChosenCamera = Camera.CameraInfo.CAMERA_FACING_BACK;

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
        "Exif" rotation
       */
      Bitmap bitmap = toBitmap(data);
      if(mChosenCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
        bitmap = rotate(bitmap, mCameraInfo.getSensorOrientation());
      }else{
        bitmap = rotate(bitmap, -mCameraInfo.getSensorOrientation());
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);

      File file = createFile(FILE_JPEG);

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

      if(mLocationEnabled) {
        try {
          ExifInterface exifInterface = new ExifInterface(file.getAbsolutePath());
          Location location = getLastKnownLocation();
          if(location != null) {
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE, dec2DMS(location.getLatitude()));
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, dec2DMS(location.getLongitude()));
            if (location.getLatitude() > 0)
              exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N");
            else
              exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S");
            if (location.getLongitude() > 0)
              exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E");
            else
              exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W");

            exifInterface.saveAttributes();
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  };

  public String dec2DMS(double coord) {
    coord = coord > 0 ? coord : -coord;  // -105.9876543 -> 105.9876543
    String sOut = Integer.toString((int)coord) + "/1,";   // 105/1,
    coord = (coord % 1) * 60;         // .987654321 * 60 = 59.259258
    sOut = sOut + Integer.toString((int)coord) + "/1,";   // 105/1,59/1,
    coord = (coord % 1) * 60000;             // .259258 * 60000 = 15555
    sOut = sOut + Integer.toString((int)coord) + "/1000";   // 105/1,59/1,15555/1000
    return sOut;
  }
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

        mParameters = mCamera.getParameters();

        List<String> supportedFlashModes = mParameters.getSupportedFlashModes();

        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
          case Surface.ROTATION_0: degrees = 0; break;
          case Surface.ROTATION_90: degrees = 90; break;
          case Surface.ROTATION_180: degrees = 180; break;
          case Surface.ROTATION_270: degrees = -90; break;
        }
        int sensorOrientation;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
          sensorOrientation = (info.orientation + degrees) % 360;
          sensorOrientation = (360 - sensorOrientation) % 360;  // compensate the mirror
        } else {  // back-facing
          sensorOrientation = (info.orientation - degrees + 360) % 360;
        }

        Camera.Size previewSize = mParameters.getPreviewSize();

        mCameraInfo = new CameraInfo(
            Integer.toString(i),
            supportedFlashModes != null && supportedFlashModes.size() > 0,
            mParameters.getMaxNumMeteringAreas(),
            mParameters.getMaxNumFocusAreas(),
            sensorOrientation,
            new MySize(previewSize.width, previewSize.height),
            new MySize(previewSize.width, previewSize.height),//TODO
            new MySize(previewSize.width, previewSize.height),//TODO
            mParameters.getMaxZoom(),
            null);
        updateFlashButtonVisibility(mCameraInfo.hasFlash());
        try {
          int orientation = getResources().getConfiguration().orientation;
          if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mTexture.setAspectRatio(mCameraInfo.getPreviewSize().getWidth(), mCameraInfo.getPreviewSize().getHeight());
          } else {
            //noinspection SuspiciousNameCombination
            mTexture.setAspectRatio(mCameraInfo.getPreviewSize().getHeight(), mCameraInfo.getPreviewSize().getWidth());
          }
          mCamera.setPreviewTexture(mTexture.getSurfaceTexture());
          mCamera.startPreview();
          mCamera.setDisplayOrientation(mCameraInfo.getSensorOrientation());
          mCamera.setParameters(mParameters);
        } catch (IOException e) {
          e.printStackTrace();
        }
        return;
      }
    }
  }

  private synchronized void releaseCamera() {
    if (mCamera != null) {
      mIsRecording = false;
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
    if(mCameraInfo.hasFlash()) {
      super.changeFlashMode();
      switch (mFlashMode) {
        case FLASH_ON:
          mParameters.setFlashMode("on");
          break;
        case FLASH_OFF:
          mParameters.setFlashMode("off");
          break;
        default:
          mParameters.setFlashMode("auto");
      }
    }
  }

  @Override
  protected void takePicture() {
    mCamera.setParameters(mParameters);
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
    if (mTexture.isAvailable()) {
      openCamera();
    }else{
      mTexture.setSurfaceTextureListener(this);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    releaseCamera();
    releaseMediaRecorder();
  }

  @Override
  public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
    openCamera();
  }

  @Override
  public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
      /*if(getPreviewSize().getHeight() > getPreviewSize().getWidth()) {
        mParameters.setPictureSize(getPreviewSize().getHeight(), getPreviewSize().getWidth());
      }else{
        mParameters.setPictureSize(getPreviewSize().getWidth(), getPreviewSize().getHeight());
      }*/

    mParameters.setPreviewSize(mCameraInfo.getPreviewSize().getWidth(), mCameraInfo.getPreviewSize().getHeight());
    mCamera.setParameters(mParameters);
    mCamera.startPreview();
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
      mCameraInfo.scaleZoom(scaleFactor);
      mParameters.setZoom(mZoomLevel);
      mCamera.setParameters(mParameters);
      mCamera.startPreview();
    }
  }

  @Override
  public void onFocus(float x, float y) {
    if(mCameraInfo.hasAFRegionsEnabled()) {
      mCamera.cancelAutoFocus();
      mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
      setAreas(x, y);
      mCamera.setParameters(mParameters);
      mCamera.startPreview();
      mCamera.autoFocus(this);
      playFocusRingAnimation(x, y);
    }
  }

  private void setAreas(float x, float y) {
    List<Camera.Area> focusAreas = new ArrayList<>();
    Rect focusRect = calculateTapArea(x, y, 1f);
    focusAreas.add(new Camera.Area(focusRect, 1000));
    mParameters.setFocusAreas(focusAreas);


    if(mCameraInfo.hasAERegionsEnabled()){
      List<Camera.Area> meteringAreas = new ArrayList<>();
      Rect meteringRect = calculateTapArea(x, y, 1.5f);
      meteringAreas.add(new Camera.Area(meteringRect, 1000));
      mParameters.setMeteringAreas(meteringAreas);
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
    matrix.postRotate(mCameraInfo.getSensorOrientation());
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
    Log.i(TAG, "Auto focus: " +b);
  }

  /*
    Video methods
   */
  @Override
  protected void startCaptureVideo() {
    super.startCaptureVideo();
    startRecording();
  }

  @Override
  protected void stopCaptureVideo() {
    super.stopCaptureVideo();
    stopRecording();
  }

  private boolean prepareVideoRecorder(){
    mMediaRecorder = new MediaRecorder();
    mCamera.unlock();
    mMediaRecorder.setCamera(mCamera);
    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
    mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
    mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
    mMediaRecorder.setMaxDuration(VIDEO_MAX_DURATION);
    mMediaRecorder.setMaxFileSize(VIDE_MAX_FILE_SIZE);
    mMediaRecorder.setOnErrorListener(this);
    mMediaRecorder.setOnInfoListener(this);

    if(mChosenCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
      mMediaRecorder.setOrientationHint(mCameraInfo.getSensorOrientation());
    }else{
      if(mCameraInfo.getSensorOrientation() != 0){
        mMediaRecorder.setOrientationHint(360-mCameraInfo.getSensorOrientation());
      }
    }

    //Check it
    CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
    Log.i(TAG, "audioBitRate: " + profile.audioBitRate + "\n" +
        "audioChannels: " + profile.audioChannels + "\n" +
        "audioCodec: " + profile.audioCodec + "\n" +
        "audioSampleRate: " + profile.audioSampleRate + "\n" +
        "duration: " + profile.duration + "\n" +
        "fileFormat: " + profile.fileFormat + "\n" +
        "quality: " + profile.quality + "\n" +
        "videoBitRate: " + profile.videoBitRate + "\n" +
        "videoCodec: " + profile.videoCodec + "\n" +
        "videoFrameHeight: " + profile.videoFrameHeight + "\n" +
        "videoFrameWidth: " + profile.videoFrameWidth + "\n" +
        "videoFrameRate: " + profile.videoFrameRate + "\n"
    );
    mMediaRecorder.setProfile(profile);

    File file = createFile(FILE_MP4);
    mMediaRecorder.setOutputFile(file.getPath());

    try {
      mMediaRecorder.prepare();
    } catch (IllegalStateException e) {
      Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
      releaseMediaRecorder();
      return false;
    } catch (IOException e) {
      Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
      releaseMediaRecorder();
      return false;
    }
    return true;
  }

  private void releaseMediaRecorder(){
    if (mMediaRecorder != null) {
      mMediaRecorder.reset();   // clear recorder configuration
      mMediaRecorder.release(); // release the recorder object
      mMediaRecorder = null;
      mCamera.lock();           // lock camera for later use
    }
  }

  private synchronized void startRecording(){
    if (prepareVideoRecorder()) {
      mIsRecording = true;
      mMediaRecorder.start();
    }else{
      releaseCamera();
    }
  }

  private synchronized void stopRecording(){
    if(mMediaRecorder != null && mIsRecording) {
      mMediaRecorder.stop();
      releaseMediaRecorder();
      mCamera.lock();
      mIsRecording = false;
    }
  }
}
