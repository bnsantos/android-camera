package com.bnsantos.camera;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
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
}
