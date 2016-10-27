package com.bnsantos.simple.camera;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.bnsantos.camera.CameraActivity;
import com.bnsantos.simple.camera.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

  private ActivityMainBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
    mBinding.camera.setOnClickListener(this);
  }

  @Override
  public void onClick(View v) {
    startActivity(new Intent(this, CameraActivity.class));
  }
}
