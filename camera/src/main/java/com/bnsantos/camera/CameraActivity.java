package com.bnsantos.camera;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import com.bnsantos.camera.view.camera2.Camera2Fragment;

public class CameraActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_camera);
    if(savedInstanceState == null){
      if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          getFragmentManager().beginTransaction()
              .replace(R.id.container, Camera2Fragment.newInstance())
              .commit();
      }else{
        Toast.makeText(this, "TODO", Toast.LENGTH_SHORT).show();
      }
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_MENU) {
      CameraKeyListener cameraKeyListener = (CameraKeyListener) getFragmentManager().findFragmentById(R.id.container);
      if(cameraKeyListener!=null)
        cameraKeyListener.onKeyClicked();
      return true;
    }

    return super.onKeyDown(keyCode, event);
  }

  public interface CameraKeyListener{
    void onKeyClicked();
  }
}
