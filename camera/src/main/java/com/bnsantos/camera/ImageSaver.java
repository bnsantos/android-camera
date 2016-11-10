package com.bnsantos.camera;


import android.app.Activity;
import android.content.Intent;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

public class ImageSaver implements Runnable {
  private final Image mImage;
  private final File mFile;
  private final WeakReference<Activity> mActivity;

  public ImageSaver(Activity activity, Image image, File file) {
    mActivity = new WeakReference<>(activity);
    this.mImage = image;
    this.mFile = file;
  }

  @RequiresApi(api = Build.VERSION_CODES.KITKAT)
  @Override
  public void run() {
    ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    FileOutputStream output = null;

    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
    mediaScanIntent.setData(Uri.fromFile(mFile));
    if(mActivity != null && mActivity.get() != null) {
      mActivity.get().sendBroadcast(mediaScanIntent);
    }
    try {
      output = new FileOutputStream(mFile);
      output.write(bytes);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      mImage.close();
      if (null != output) {
        try {
          output.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }
}
