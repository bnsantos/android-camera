package com.bnsantos.camera.view.camera;

import android.app.Fragment;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.bnsantos.camera.R;
import com.bnsantos.camera.view.AutoFitTextureView;
import com.bnsantos.camera.view.GridLines;
import com.bnsantos.camera.view.focusring.FocusRing;

import java.util.List;

import static android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED;
import static android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED;

public abstract class AbstractCameraFragment extends Fragment implements View.OnClickListener, View.OnTouchListener, MediaRecorder.OnInfoListener {
  private static final String TAG = AbstractCameraFragment.class.getSimpleName();
  private static final String BUNDLE_FLASH_MODE = "bundle_flash_mode";
  private static final String BUNDLE_LOCATION = "bundle_location";
  private static final String BUNDLE_SHOW_GRID = "bundle_show_grid";
  private static final String BUNDLE_ZOOM_LEVEL = "bundle_zoom_level";
  protected static final String BUNDLE_CHOSEN_CAMERA = "bundle_chosen_camera";

  protected static final int FILE_JPEG = 1;
  protected static final int FILE_MP4 = 2;

  protected static final int FLASH_AUTO = 1;
  protected static final int FLASH_ON = 2;
  protected static final int FLASH_OFF = 3;

  protected static final int VIDE_MAX_FILE_SIZE = 1024*1024*10; //10Mb
  protected static final int VIDEO_MAX_DURATION = 30000; // 30 seconds
  protected static final int MILLIS = 1000; // 1 second

  protected int mFlashMode = FLASH_AUTO;
  protected boolean mLocationEnabled;
  protected boolean mShowGrid = false;

  protected static final int ZOOM_LEVEL_START = 1;

  protected AutoFitTextureView mTexture;
  protected ImageButton mChangeCamera;
  private ImageButton mFlash;
  private ImageButton mLocationButton;
  private ImageButton mGridToggle;
  protected GridLines mGridLines;
  private MediaActionSound mMediaActionSound;
  protected int mZoomLevel = ZOOM_LEVEL_START;

  private LocationManager mLocationManager;
  private FocusRing mFocusRing;
  private TextView mChronometer;
  private CountDownTimer mCountDownTimer = new CountDownTimer(VIDEO_MAX_DURATION, MILLIS) {
    @Override
    public void onTick(long l) {
      mChronometer.setText(getString(R.string.video_count_down, l/MILLIS));
    }

    @Override
    public void onFinish() {
      stopCaptureVideo();
    }
  };

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_camera, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    if(savedInstanceState != null){
      if(savedInstanceState.containsKey(BUNDLE_FLASH_MODE)) {
        mFlashMode = savedInstanceState.getInt(BUNDLE_FLASH_MODE);
      }
      if(savedInstanceState.containsKey(BUNDLE_ZOOM_LEVEL)) {
        mZoomLevel = savedInstanceState.getInt(BUNDLE_ZOOM_LEVEL);
      }
      mLocationEnabled = savedInstanceState.getBoolean(BUNDLE_LOCATION, false);
      mShowGrid = savedInstanceState.getBoolean(BUNDLE_SHOW_GRID, false);
    }

    mTexture = (AutoFitTextureView) view.findViewById(R.id.texture);

    view.findViewById(R.id.action).setOnTouchListener(this);
    mChangeCamera = (ImageButton) view.findViewById(R.id.changeCamera);
    mChangeCamera.setOnClickListener(this);
    mFlash = (ImageButton) view.findViewById(R.id.flash);
    mFlash.setOnClickListener(this);
    mLocationButton = (ImageButton) view.findViewById(R.id.location);
    mLocationButton.setOnClickListener(this);

    mGridLines = (GridLines) view.findViewById(R.id.gridLines);
    mGridToggle = (ImageButton) view.findViewById(R.id.gridToggle);
    mGridToggle.setOnClickListener(this);
    mTexture.setAreaChangedListener(mGridLines);
    mFocusRing = (FocusRing) view.findViewById(R.id.focusRing);
    mChronometer = (TextView) view.findViewById(R.id.recordChronometer);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(BUNDLE_FLASH_MODE, mFlashMode);
    outState.putBoolean(BUNDLE_LOCATION, mLocationEnabled);
    outState.putBoolean(BUNDLE_SHOW_GRID, mShowGrid);
    outState.putInt(BUNDLE_ZOOM_LEVEL, mZoomLevel);
  }

  @Override
  public void onClick(View v) {
    int id = v.getId();
    if (id == R.id.changeCamera) {
      changeCamera();
    }else if(id == R.id.flash){
      changeFlashMode();
      updateFlashModeIcon();
    }else if(id == R.id.location){
      toggleLocation();
    }else if(id == R.id.gridToggle){
      toggleGridLines();
    }else {
      Toast.makeText(getActivity(), "TODO", Toast.LENGTH_SHORT).show();
    }
  }

  protected abstract void changeCamera();
  protected abstract void takePicture();
  protected abstract void toggleLocation();

  private void toggleGridLines(){
    mShowGrid = !mShowGrid;
    setGridVisibility();
  }

  protected void updateFlashButtonVisibility(boolean visible){
    mFlash.setVisibility(visible?View.VISIBLE:View.GONE);
  }

  protected void setLocationIcon(){
    if(mLocationEnabled){
      mLocationButton.setImageResource(R.drawable.ic_location_on_white_24dp);
    }else{
      mLocationButton.setImageResource(R.drawable.ic_location_off_white_24dp);
    }
  }

  protected void setGridVisibility(){
    mGridLines.setVisibility(mShowGrid?View.VISIBLE: View.GONE);
    mGridToggle.setImageResource(mShowGrid?R.drawable.ic_grid_on_white_24dp:R.drawable.ic_grid_off_white_24dp);
  }


  protected void changeFlashMode(){
    switch (mFlashMode) {
      case FLASH_AUTO:
        mFlashMode = FLASH_ON;
        break;
      case FLASH_ON:
        mFlashMode = FLASH_OFF;
        break;
      case FLASH_OFF:
        mFlashMode = FLASH_AUTO;
        break;
    }
  }

  protected void updateFlashModeIcon(){
    switch (mFlashMode){
      case FLASH_AUTO:
        mFlash.setImageResource(R.drawable.ic_flash_auto_white_24dp);
        break;
      case FLASH_ON:
        mFlash.setImageResource(R.drawable.ic_flash_on_white_24dp);
        break;
      case FLASH_OFF:
        mFlash.setImageResource(R.drawable.ic_flash_off_white_24dp);
        break;
    }
  }

  protected void shootSound(){
    if(mMediaActionSound == null) {
      mMediaActionSound = new MediaActionSound();
    }
    mMediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
  }

  @SuppressWarnings("MissingPermission")
  protected Location getLastKnownLocation(){
    if(mLocationEnabled) {
      if(mLocationManager == null) {
        mLocationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
      }
      Criteria criteria = new Criteria();
      criteria.setAccuracy(Criteria.ACCURACY_FINE);
      List<String> providers = mLocationManager.getProviders(criteria, true);
      for (String provider : providers) {
        Location lastKnownLocation = mLocationManager.getLastKnownLocation(provider);
        if(lastKnownLocation != null){
          return lastKnownLocation;
        }
      }
    }
    return null;
  }

  protected void playFocusRingAnimation(final float x, final float y){
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        mFocusRing.stopFocusAnimations();
        mFocusRing.startActiveFocus();
        mFocusRing.setFocusLocation(x, y);
      }
    });
  }

  @Override
  public boolean onTouch(View view, MotionEvent event) {
    switch (event.getAction()) {
      case MotionEvent.ACTION_UP:
        mHandler.removeCallbacks(mVideoRunnable);
        synchronized (this) {
          if(mIsRecording){
            stopCaptureVideo();
          }else{
            takePicture();
          }
        }
        return true;
      case MotionEvent.ACTION_DOWN:
        mHandler.removeCallbacks(mVideoRunnable);
        mHandler.postDelayed(mVideoRunnable, ViewConfiguration.getLongPressTimeout());
        return true;
    }
    return false;
  }


  protected void startCaptureVideo(){
    mChronometer.setVisibility(View.VISIBLE);
    if(mCountDownTimer!=null) {
      mCountDownTimer.cancel();
      mCountDownTimer.start();
    }
  }

  protected void stopCaptureVideo(){
    mChronometer.setVisibility(View.GONE);

    if(mCountDownTimer != null){
      mCountDownTimer.cancel();
    }
  }

  protected boolean mIsRecording;

  private Handler mHandler = new Handler();
  private Runnable mVideoRunnable = new Runnable() {
    @Override
    public void run() {
      synchronized (AbstractCameraFragment.this){
        mIsRecording = true;
        startCaptureVideo();
      }
    }
  };

  @Override
  public void onInfo(MediaRecorder mediaRecorder, int what, int extra) {
    if (what == MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
      Log.i(TAG, "MEDIA_RECORDER_INFO_MAX_DURATION_REACHED");
      stopCaptureVideo();
    }else if (what == MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED){
      Log.i(TAG, "MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED");
      stopCaptureVideo();
    }
  }
}
