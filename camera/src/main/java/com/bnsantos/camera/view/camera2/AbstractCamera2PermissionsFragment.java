package com.bnsantos.camera.view.camera2;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;

import com.bnsantos.camera.R;

public class AbstractCamera2PermissionsFragment extends Fragment {
  private static final int REQUEST_CAMERA_PERMISSION = 1;
  private static final int REQUEST_LOCATION_PERMISSION = 2;
  private static final int REQUEST_STORAGE_PERMISSION = 3;

  /*
    Camera States
   */
  protected static final int STATE_PREVIEW = 0;
  protected static final int STATE_WAITING_LOCK = 1;
  protected static final int STATE_WAITING_PRECAPTURE = 2;
  protected static final int STATE_WAITING_NON_PRECAPTURE = 3;
  protected static final int STATE_PICTURE_TAKEN = 4;

  /**
   * Max preview width and height that is guaranteed by Camera2 API
   */
  protected static final int MAX_PREVIEW_WIDTH = 1920;
  protected static final int MAX_PREVIEW_HEIGHT = 1080;

  protected boolean hasCameraPermission(){
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        requestCameraPermission();
        return false;
      }
    }
    return true;
  }

  protected boolean hasLocationPermission(){
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        requestLocationPermission();
        return false;
      }
    }
    return true;
  }

  protected boolean hasStoragePermission(){
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        requestStoragePermission();
        return false;
      }
    }
    return true;
  }

  /*
    Permission
   */
  private void requestCameraPermission() {
    requestPermission(Manifest.permission.CAMERA, R.string.ask_camera_permission, REQUEST_CAMERA_PERMISSION);
  }

  private void requestLocationPermission() {
    requestPermission(Manifest.permission.ACCESS_FINE_LOCATION, R.string.ask_location_permission, REQUEST_LOCATION_PERMISSION);
  }

  private void requestStoragePermission() {
    requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, R.string.ask_storage_permission, REQUEST_STORAGE_PERMISSION);
  }

  private void requestPermission(String permission, int message, int requestCode){
    if (FragmentCompat.shouldShowRequestPermissionRationale(this, permission)) {
      Bundle bundle = new Bundle();
      bundle.putInt(ConfirmationDialog.ARGS_MESSAGE, message);
      bundle.putInt(ConfirmationDialog.ARGS_REQ_CODE, requestCode);
      bundle.putString(ConfirmationDialog.ARGS_PERMISSION, permission);
      ConfirmationDialog dialog = new ConfirmationDialog();
      dialog.setArguments(bundle);
      dialog.show(getChildFragmentManager(), "dialog");
    } else {
      FragmentCompat.requestPermissions(this, new String[]{permission}, requestCode);
    }
  }

  /**
   * Shows OK/Cancel confirmation dialog about camera permission.
   */
  public static class ConfirmationDialog extends DialogFragment {
    static final String ARGS_MESSAGE = "args_message";
    static final String ARGS_REQ_CODE = "args_req_code";
    static final String ARGS_PERMISSION = "args_permission";
    int message;
    int requestCode;
    String permission;

    public ConfirmationDialog() { }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      if(getArguments()!=null) {
        message = getArguments().getInt(ARGS_MESSAGE);
        requestCode = getArguments().getInt(ARGS_REQ_CODE);
        permission = getArguments().getString(ARGS_PERMISSION);
      }else{
        throw new IllegalArgumentException("Dialog requires");
      }
      final android.app.Fragment parent = getParentFragment();
      return new AlertDialog.Builder(getActivity())
          .setMessage(message)
          .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              FragmentCompat.requestPermissions(parent, new String[]{permission}, requestCode);
            }
          })
          .setNegativeButton(android.R.string.cancel,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  Activity activity = parent.getActivity();
                  if (activity != null) {
                    activity.finish();
                  }
                }
              })
          .create();
    }
  }
}
