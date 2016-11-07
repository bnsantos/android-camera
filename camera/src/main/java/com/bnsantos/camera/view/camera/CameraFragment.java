package com.bnsantos.camera.view.camera;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Surface;
import android.view.View;

@SuppressWarnings("deprecation")
public class CameraFragment extends AbstractCameraFragment{
  private Camera mCamera;

  public static CameraFragment newInstance() {
    return new CameraFragment();
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    mCamera = getCameraInstance();


    SurfaceTexture texture = mTexture.getSurfaceTexture();
    assert texture != null;

    // We configure the size of default buffer to be the size of camera preview we want.
    texture.setDefaultBufferSize(mTexture.getWidth(), mTexture.getHeight()); //TODO sizes
    Surface surface = new Surface(texture);


//TODO    mCamera.setPreviewDisplay(surface);
  }

  public static Camera getCameraInstance(){
    Camera c = null;
    try {
      c = Camera.open(); // attempt to get a Camera instance
    }
    catch (Exception e){
      // Camera is not available (in use or does not exist)
    }
    return c; // returns null if camera is unavailable
  }

  @Override
  protected void changeCamera() {

  }

  @Override
  protected void takePicture() {

  }

  @Override
  protected void changeFlashMode() {

  }

  @Override
  protected void toggleLocation() {

  }
}
