package com.bnsantos.camera.view.camera;

import android.app.Fragment;
import android.media.MediaActionSound;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.bnsantos.camera.R;
import com.bnsantos.camera.view.GridLines;

import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH;
import static android.hardware.camera2.CameraMetadata.FLASH_MODE_OFF;

public abstract class AbstractCameraFragment extends Fragment implements View.OnClickListener {
  private static final String BUNDLE_FLASH_MODE = "bundle_flash_mode";
  private static final String BUNDLE_LOCATION = "bundle_location";
  private static final String BUNDLE_SHOW_GRID = "bundle_show_grid";
  private static final String BUNDLE_ZOOM_LEVEL = "bundle_zoom_level";

  protected int mFlashMode = CONTROL_AE_MODE_ON_AUTO_FLASH;
  protected boolean mLocationEnabled;
  protected boolean mShowGrid = false;

  protected static final int ZOOM_LEVEL_START = 1;

  protected int mFocusState = FOCUS_STATE_IDLE;
  protected static final int FOCUS_STATE_IDLE = 0; // Focus is not active.
  protected static final int FOCUS_STATE_FOCUSING = 1; // Focus is in progress.
  // Focus is in progress and the camera should take a picture after focus finishes.
  protected static final int FOCUS_STATE_FOCUSING_SNAP_ON_FINISH = 2;
  protected static final int FOCUS_STATE_SUCCESS = 3; // Focus finishes and succeeds.
  protected static final int FOCUS_STATE_FAIL = 4; // Focus finishes and fails.

  protected ImageButton mChangeCamera;
  private ImageButton mFlash;
  private ImageButton mLocationButton;
  protected boolean mFlashSupported;
  private ImageButton mGridToggle;
  protected GridLines mGridLines;
  private MediaActionSound mMediaActionSound;
  protected int mZoomLevel = ZOOM_LEVEL_START;

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

    view.findViewById(R.id.action).setOnClickListener(this);
    mChangeCamera = (ImageButton) view.findViewById(R.id.changeCamera);
    mChangeCamera.setOnClickListener(this);
    mFlash = (ImageButton) view.findViewById(R.id.flash);
    mFlash.setOnClickListener(this);
    mLocationButton = (ImageButton) view.findViewById(R.id.location);
    mLocationButton.setOnClickListener(this);

    mGridLines = (GridLines) view.findViewById(R.id.gridLines);
    mGridToggle = (ImageButton) view.findViewById(R.id.gridToggle);
    mGridToggle.setOnClickListener(this);
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
    }else if(id == R.id.action){
      takePicture();
    }else if(id == R.id.flash){
      changeFlashMode();
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
  protected abstract void changeFlashMode();
  protected abstract void toggleLocation();
  private void toggleGridLines(){
    mShowGrid = !mShowGrid;
    setGridVisibility();
  }

  protected void updateFlashButtonVisibility(){
    mFlash.setVisibility(mFlashSupported?View.VISIBLE:View.GONE);
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

  protected void updateFlashModeIcon(){
    switch (mFlashMode){
      case CONTROL_AE_MODE_ON_AUTO_FLASH:
        mFlash.setImageResource(R.drawable.ic_flash_auto_white_24dp);
        break;
      case CONTROL_AE_MODE_ON_ALWAYS_FLASH:
        mFlash.setImageResource(R.drawable.ic_flash_on_white_24dp);
        break;
      case FLASH_MODE_OFF:
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
}
