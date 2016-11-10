package com.bnsantos.camera.view.camera;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import com.bnsantos.camera.CameraActivity;
import com.bnsantos.camera.ImageSaver;
import com.bnsantos.camera.R;
import com.bnsantos.camera.view.AutoFitTextureView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH;
import static android.hardware.camera2.CameraMetadata.FLASH_MODE_OFF;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static com.bnsantos.camera.CameraActivity.DATE_FORMAT;
import static com.bnsantos.camera.CameraActivity.FOLDER;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Fragment extends AbstractCamera2PermissionsFragment implements View.OnClickListener, CameraActivity.CameraKeyListener, AutoFitTextureView.TouchEventInterface {
  private static final String TAG = Camera2Fragment.class.getSimpleName();

  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  private CameraInfo mCameraInfo;
  private CameraDevice mCameraDevice;
  private int mChosenCamera = CameraMetadata.LENS_FACING_BACK;

  /**
   * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a {@link TextureView}.
   */
  private final TextureView.SurfaceTextureListener mSurfaceListener = new TextureView.SurfaceTextureListener() {
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
      openCamera(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
      configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
  };

  /**
   * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
   */
  private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
    @Override
    public void onOpened(@NonNull CameraDevice cameraDevice) {
      // This method is called when the camera is opened.  We start camera preview here.
      mCameraOpenCloseLock.release();
      mCameraDevice = cameraDevice;
      createCameraPreviewSession();
    }

    @Override
    public void onDisconnected(@NonNull CameraDevice cameraDevice) {
      mCameraOpenCloseLock.release();
      cameraDevice.close();
      mCameraDevice = null;
    }

    @Override
    public void onError(@NonNull CameraDevice cameraDevice, int error) {
      mCameraOpenCloseLock.release();
      cameraDevice.close();
      mCameraDevice = null;
      Activity activity = getActivity();
      if (null != activity) {
        activity.finish();
      }
    }
  };

  private Semaphore mCameraOpenCloseLock = new Semaphore(1);
  private Handler mBackgroundHandler;
  private HandlerThread mBackgroundThread;

  private File mFile;

  private ImageReader mImageReader;


  /**
   * {@link CaptureRequest.Builder} builder for the camera preview
   */
  private CaptureRequest.Builder mPreviewRequestBuilder;

  /**
   * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
   * still image is ready to be saved.
   */
  private final ImageReader.OnImageAvailableListener mOnImageAvailableListener  = new ImageReader.OnImageAvailableListener() {
    @Override
    public void onImageAvailable(ImageReader reader) {
      File directory = Environment.getExternalStoragePublicDirectory(FOLDER);
      if(!directory.exists()){
        Log.i(TAG, "Creating folders: " + directory.mkdirs() );
      }

      String timeStamp = DATE_FORMAT.format(new Date());
      mFile = new File(directory, "pic_"+timeStamp+".jpg");
      mBackgroundHandler.post(new ImageSaver(getActivity(), reader.acquireNextImage(), mFile));
    }
  };


  private CameraCaptureSession mCaptureSession;
  private int mState = STATE_PREVIEW;
  /**
   * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
   */
  private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
    private void process(CaptureResult result) {
      switch (mState) {
        case STATE_PREVIEW: {
          // We have nothing to do when the camera preview is working normally.
          break;
        }
        case STATE_WAITING_LOCK: {
          Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
          if (afState == null) {
            captureStillPicture();
          } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
            // CONTROL_AE_STATE can be null on some devices
            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
              mState = STATE_PICTURE_TAKEN;
              captureStillPicture();
            } else {
              runPrecaptureSequence();
            }
          }
          break;
        }
        case STATE_WAITING_PRECAPTURE: {
          // CONTROL_AE_STATE can be null on some devices
          Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
          if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
            mState = STATE_WAITING_NON_PRECAPTURE;
          }
          break;
        }
        case STATE_WAITING_NON_PRECAPTURE: {
          // CONTROL_AE_STATE can be null on some devices
          Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
          if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
            mState = STATE_PICTURE_TAKEN;
            captureStillPicture();
          }
          break;
        }
      }
    }

    @Override
    public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
      process(partialResult);
      super.onCaptureProgressed(session, request, partialResult);
    }

    @Override
    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
      process(result);
    }
  };
  private Surface surface;

  public static Camera2Fragment newInstance() {
    return new Camera2Fragment();
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    if(savedInstanceState != null){
      if(savedInstanceState.containsKey(BUNDLE_CHOSEN_CAMERA)) {
        mChosenCamera = savedInstanceState.getInt(BUNDLE_CHOSEN_CAMERA);
      }
    }
    updateFlashModeIcon();
  }

  @Override
  public void onResume() {
    super.onResume();
    setLocationIcon();
    startBackgroundThread();
    setGridVisibility();

    if(mTexture.isAvailable()){
      openCamera(mTexture.getWidth(), mTexture.getHeight());
    }else{
      mTexture.setSurfaceTextureListener(mSurfaceListener);
    }
  }

  @Override
  public void onPause() {
    closeCamera();
    stopBackgroundThread();
    super.onPause();
  }

  private void startBackgroundThread() {
    mBackgroundThread = new HandlerThread("CameraBackground");
    mBackgroundThread.start();
    mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
  }

  private void stopBackgroundThread() {
    mBackgroundThread.quitSafely();
    try {
      mBackgroundThread.join();
      mBackgroundThread = null;
      mBackgroundHandler = null;
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private void closeCamera() {
    try {
      mCameraOpenCloseLock.acquire();
      if (null != mCaptureSession) {
        mCaptureSession.close();
        mCaptureSession = null;
      }
      if (null != mCameraDevice) {
        mCameraDevice.close();
        mCameraDevice = null;
      }
      if (null != mImageReader) {
        mImageReader.close();
        mImageReader = null;
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      mCameraOpenCloseLock.release();
    }
  }

  @SuppressWarnings("MissingPermission")
  private void openCamera(int w, int h){
    if(hasCameraPermission()) {
      setUpCameraOutputs(w, h);
      configureTransform(w, h);
      CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
      try {
        if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
          throw new RuntimeException("Time out waiting to lock camera opening.");
        }
        if (mCameraInfo == null || mCameraInfo.mCameraId == null) {
          throw new RuntimeException("No camera found.");
        }
        manager.openCamera(mCameraInfo.mCameraId, mStateCallback, mBackgroundHandler);
      } catch (CameraAccessException e) {
        e.printStackTrace();
      } catch (InterruptedException e) {
        throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
      }
    }
  }

  /**
   * Sets up member variables related to camera.
   *
   * @param width  The width of available size for camera preview
   * @param height The height of available size for camera preview
   */
  @SuppressWarnings("SuspiciousNameCombination")
  private void setUpCameraOutputs(int width, int height) {
    Activity activity = getActivity();
    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      for (String cameraId : manager.getCameraIdList()) {
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing != mChosenCamera) {
          continue;
        }

        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
          continue;
        }

        mCameraInfo = new CameraInfo(cameraId, characteristics, width, height);

        if(mCameraInfo.isDigitalZoomEnabled()) {
          mTexture.setTouchListener(this);
        }else{
          mTexture.setTouchListener(null);
        }

        mImageReader = ImageReader.newInstance(mCameraInfo.mJPEGSize.getWidth(), mCameraInfo.mJPEGSize.getHeight(), ImageFormat.JPEG, /*maxImages*/2);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

        // We fit the aspect ratio of TextureView to the size of preview we picked.
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
          mTexture.setAspectRatio(mCameraInfo.mPreviewSize.getWidth(), mCameraInfo.mPreviewSize.getHeight());
        } else {
          mTexture.setAspectRatio(mCameraInfo.mPreviewSize.getHeight(), mCameraInfo.mPreviewSize.getWidth());
        }

        updateFlashButtonVisibility(mCameraInfo.mFlashSupported);
        return;
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      // Currently an NPE is thrown when the Camera2API is used but not supported on the
      // device this code runs.
      Log.e(TAG, "Error", e);
    }
  }

  /**
   * Creates a new {@link CameraCaptureSession} for camera preview.
   */
  private void createCameraPreviewSession() {
    try {
      SurfaceTexture texture = mTexture.getSurfaceTexture();
      assert texture != null;

      // We configure the size of default buffer to be the size of camera preview we want.
      texture.setDefaultBufferSize(mCameraInfo.mPreviewSize.getWidth(), mCameraInfo.mPreviewSize.getHeight());

      // This is the output Surface we need to start preview.
      surface = new Surface(texture);

      // We set up a CaptureRequest.Builder with the output Surface.
      mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      mPreviewRequestBuilder.addTarget(surface);

      // Here, we create a CameraCaptureSession for camera preview.
      mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {

            @Override
            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
              // The camera is already closed
              if (null == mCameraDevice) {
                return;
              }

              // When the session is ready, we start displaying the preview.
              mCaptureSession = cameraCaptureSession;
              setUpPreviewRepeatingRequest();
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
              Log.e(Camera2Fragment.class.getSimpleName(), "Failed");
            }
          }, mBackgroundHandler
      );
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
   * This method should be called after the camera preview size is determined in
   * setUpCameraOutputs and also the size of `mTextureView` is fixed.
   *
   * @param viewWidth  The width of `mTextureView`
   * @param viewHeight The height of `mTextureView`
   */
  private void configureTransform(int viewWidth, int viewHeight) {
    Activity activity = getActivity();
    if (null == mTexture || null == mCameraInfo.mPreviewSize || null == activity) {
      return;
    }
    int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
    Matrix matrix = new Matrix();
    RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    RectF bufferRect = new RectF(0, 0, mCameraInfo.mPreviewSize.getHeight(), mCameraInfo.mPreviewSize.getWidth());
    float centerX = viewRect.centerX();
    float centerY = viewRect.centerY();
    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
      float scale = Math.max(
          (float) viewHeight / mCameraInfo.mPreviewSize.getHeight(),
          (float) viewWidth / mCameraInfo.mPreviewSize.getWidth());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    } else if (Surface.ROTATION_180 == rotation) {
      matrix.postRotate(180, centerX, centerY);
    }
    mTexture.setTransform(matrix);
  }

  @Override
  public void onKeyClicked() {
    takePicture();
  }

  /**
   * Retrieves the JPEG orientation from the specified screen rotation.
   *
   * @param rotation The screen rotation.
   * @return The JPEG orientation (one of 0, 90, 270, and 360)
   */
  private int getOrientation(int rotation) {
    // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
    // We have to take that into account and rotate JPEG properly.
    // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
    // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
    return (ORIENTATIONS.get(rotation) + mCameraInfo.mSensorOrientation + 270) % 360;
  }

  @Override
  protected void changeFlashMode(){
    if(mCameraInfo.mFlashSupported) {
      super.changeFlashMode();
      setUpPreviewRepeatingRequest();
    }
  }

  /*
     Take picture
   */

  /**
   * Initiate a still image capture.
   */
  @Override
  protected void takePicture() {
    if(hasStoragePermission())
      lockFocus();
  }

  /**
   * Lock the focus as the first step for a still image capture.
   */
  private void lockFocus() {
    try {
      // This is how to tell the camera to lock focus.
      mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
      // Tell #mCaptureCallback to wait for the lock.
      mState = STATE_WAITING_LOCK;
      mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * Run the precapture sequence for capturing a still image. This method should be called when
   * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
   */
  private void runPrecaptureSequence() {
    try {
      // This is how to tell the camera to trigger.
      mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
      // Tell #mCaptureCallback to wait for the precapture sequence to be set.
      mState = STATE_WAITING_PRECAPTURE;
      mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
      mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * Capture a still picture. This method should be called when we get a response in
   * {@link #mCaptureCallback} from both {@link #lockFocus()}.
   */
  private void captureStillPicture() {
    try {
      final Activity activity = getActivity();
      if (null == activity || null == mCameraDevice) {
        return;
      }
      // This is the CaptureRequest.Builder that we use to take a picture.
      final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
      setUpRequestBuilder(captureBuilder);
      captureBuilder.addTarget(mImageReader.getSurface());

      // Orientation
      int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
      captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
      captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
      CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
          Log.i(TAG, "Capture completed");
          unlockFocus();
        }

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
          super.onCaptureStarted(session, request, timestamp, frameNumber);
          shootSound();
        }
      };

      mCaptureSession.stopRepeating();
      mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * Unlock the focus. This method should be called when still image capture sequence is
   * finished.
   */
  private void unlockFocus() {
    try {
      // Reset the auto-focus trigger
      mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
      mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
      // After this, the camera will go back to the normal state of preview.
      mState = STATE_PREVIEW;

      setUpPreviewRepeatingRequest();
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(BUNDLE_CHOSEN_CAMERA, mChosenCamera);
  }

  @Override
  protected void toggleLocation(){
    if(!mLocationEnabled){
      if(hasLocationPermission(true)){
        mLocationEnabled = true;
      }
    }else{
      mLocationEnabled = false;
    }
    setLocationIcon();
  }

  @Override
  protected void changeCamera() {
    if (mChosenCamera == CameraMetadata.LENS_FACING_BACK) {
      mChosenCamera = CameraMetadata.LENS_FACING_FRONT;
      mChangeCamera.setImageResource(R.drawable.ic_camera_front_white_24dp);
    }else {
      mChosenCamera = CameraMetadata.LENS_FACING_BACK;
      mChangeCamera.setImageResource(R.drawable.ic_camera_rear_white_24dp);
    }
    closeCamera();
    openCamera(mTexture.getWidth(), mTexture.getHeight());
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
      setUpPreviewRepeatingRequest();
    }
  }

  @Override
  public void onFocus(final float x, final float y) {
    if(mCameraInfo.mMaxAFRegions > 0) {

      MeteringRectangle[] regions = getMeteringRectangles(x, y);
      mCameraInfo.mAERegions = regions;
      mCameraInfo.mAFRegions = regions;

      playFocusRingAnimation(x, y);
    }
  }

  //TODO too much constants
  @NonNull
  private MeteringRectangle[] getMeteringRectangles(final float x, final float y) {
    float [] coords = {x, y};
    Matrix matrix = cameraToPreviewMatrix();
    matrix.mapPoints(coords);

    float focus_x = coords[0];
    float focus_y = coords[1];

    int focus_size = 50;
    Rect rect = new Rect();
    rect.left = (int)focus_x - focus_size;
    rect.right = (int)focus_x + focus_size;
    rect.top = (int)focus_y - focus_size;
    rect.bottom = (int)focus_y + focus_size;
    if( rect.left < -1000 ) {
      rect.left = -1000;
      rect.right = rect.left + 2*focus_size;
    }
    else if( rect.right > 1000 ) {
      rect.right = 1000;
      rect.left = rect.right - 2*focus_size;
    }
    if( rect.top < -1000 ) {
      rect.top = -1000;
      rect.bottom = rect.top + 2*focus_size;
    }
    else if( rect.bottom > 1000 ) {
      rect.bottom = 1000;
      rect.top = rect.bottom - 2*focus_size;
    }
    return new MeteringRectangle[]{new MeteringRectangle(convertRectFromCamera2(rect), 1000)};
  }

  private Rect convertRectFromCamera2(Rect camera2_rect) {
    Rect sensor_rect = mCameraInfo.mSensorRect;
    // inverse of convertRectToCamera2()
    double left_f = (camera2_rect.left+1000)/2000.0;
    double top_f = (camera2_rect.top+1000)/2000.0;
    double right_f = (camera2_rect.right+1000)/2000.0;
    double bottom_f = (camera2_rect.bottom+1000)/2000.0;
    int left = (int)(left_f * (sensor_rect.width()-1));
    int right = (int)(right_f * (sensor_rect.width()-1));
    int top = (int)(top_f * (sensor_rect.height()-1));
    int bottom = (int)(bottom_f * (sensor_rect.height()-1));
    left = Math.max(left, 0);
    right = Math.max(right, 0);
    top = Math.max(top, 0);
    bottom = Math.max(bottom, 0);
    left = Math.min(left, sensor_rect.width()-1);
    right = Math.min(right, sensor_rect.width()-1);
    top = Math.min(top, sensor_rect.height()-1);
    bottom = Math.min(bottom, sensor_rect.height()-1);

    Rect rect = new Rect(left, top, right, bottom);
    return rect;
  }

  private Matrix cameraToPreviewMatrix(){
    Matrix m = new Matrix();
    m.reset();
    boolean mirror = mChosenCamera == LENS_FACING_FRONT;
    m.setScale(1, mirror ? -1 : 1);

    //TODO WTF 2000f and 2f
    // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
    // UI coordinates range from (0, 0) to (width, height).
    m.postRotate(mCameraInfo.mSensorOrientation);
    Rect croppingRegion = getCroppingRegion();
    m.mapRect(new RectF(croppingRegion));
    m.postScale(mTexture.getWidth() / 2000f, mTexture.getHeight() / 2000f);
    m.postTranslate(mTexture.getWidth() / 2f, mTexture.getHeight() / 2f);
    Matrix inverse = new Matrix();
    m.invert(inverse);
    return inverse;
  }

  private void setUpRequestBuilder(CaptureRequest.Builder builder){
    setAEMode(builder);
    setAFMode(builder);
    setAFRegions(builder);
    setAERegions(builder);
    setZoom(builder);
    setLocation(builder);
  }

  private void setAEMode(CaptureRequest.Builder builder){
    switch (mFlashMode){
      case FLASH_ON:
        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
        builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
        break;
      case FLASH_AUTO:
        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);
        builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
        break;
      default:
        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
    }
  }

  private void setAFMode(CaptureRequest.Builder builder) {
    /*
      TODO not sure what AFMode to use here
     */
    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
  }

  private void setAERegions(CaptureRequest.Builder builder) {
    if(mCameraInfo.hasAERegions()){
      builder.set(CaptureRequest.CONTROL_AE_REGIONS, mCameraInfo.mAERegions);
    }
  }

  private void setAFRegions(CaptureRequest.Builder builder) {
    if(mCameraInfo.hasAFRegions()){
      builder.set(CaptureRequest.CONTROL_AF_REGIONS, mCameraInfo.mAFRegions);
    }
  }

  private void setZoom(CaptureRequest.Builder builder) {
    if (mCameraInfo.isDigitalZoomEnabled() && mZoomLevel != ZOOM_LEVEL_START) {

      builder.set(CaptureRequest.SCALER_CROP_REGION, getCroppingRegion());
    }
  }

  private void setLocation(CaptureRequest.Builder builder){
    if(mLocationEnabled){
      builder.set(CaptureRequest.JPEG_GPS_LOCATION, getLastKnownLocation());
    }
  }

  private void setUpPreviewRepeatingRequest(){
    setUpRequestBuilder(mPreviewRequestBuilder);
    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

    try {
      mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  private Rect getCroppingRegion(){
    Rect m = mCameraInfo.mSensorRect;

    if (m != null) {
      int minW = (int) (m.width() / mCameraInfo.mMaxDigitalZoom);
      int minH = (int) (m.height() / mCameraInfo.mMaxDigitalZoom);
      int difW = m.width() - minW;
      int difH = m.height() - minH;
      int cropW = difW / 100 * mZoomLevel;
      int cropH = difH / 100 * mZoomLevel;

      return new Rect(cropW, cropH, m.width() - cropW, m.height() - cropH);
    }
    return null ;
  }

  /**
   * Max preview width and height that is guaranteed by Camera2 API
   */
  private static final int MAX_PREVIEW_WIDTH = 1920;
  private static final int MAX_PREVIEW_HEIGHT = 1080;

  private class CameraInfo{
    String mCameraId;
    boolean mFlashSupported;
    Integer mMaxAFRegions;
    Integer mMaxAERegions;
    Float mMaxDigitalZoom;
    MeteringRectangle[] mAFRegions = null; // no need for has_scalar_crop_region, as we can set to null instead
    MeteringRectangle [] mAERegions = null; // no need for has_scalar_crop_region, as we can set to null instead
    /**
     * Orientation of the camera sensor
     */
    int mSensorOrientation;

    Size mPreviewSize;
    Size mJPEGSize;

    Rect mSensorRect;

    public CameraInfo(String cameraId, CameraCharacteristics cameraCharacteristics, int width, int height){
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
        int displayRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
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
        getActivity().getWindowManager().getDefaultDisplay().getSize(displaySize);
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

        mSensorRect = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        mCameraId = cameraId;
      }
    }

    public boolean isDigitalZoomEnabled() {
      return mMaxDigitalZoom != null && mMaxDigitalZoom >0.0f;
    }

    public boolean hasAERegions(){
      return mMaxAERegions != null && mMaxAERegions > 0 && mAERegions != null;
    }

    public boolean hasAFRegions(){
      return mMaxAFRegions != null && mMaxAFRegions > 0 && mAFRegions != null;
    }
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
