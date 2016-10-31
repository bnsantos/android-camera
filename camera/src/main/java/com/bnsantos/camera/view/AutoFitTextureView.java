package com.bnsantos.camera.view;

import android.content.Context;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

/**
 * A {@link TextureView} that can be adjusted to a specified aspect ratio.
 */
public class AutoFitTextureView extends TextureView {
  private int mRatioWidth = 0;
  private int mRatioHeight = 0;

  public AutoFitTextureView(Context context) {
    this(context, null);
  }

  public AutoFitTextureView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  /**
   * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
   * calculated from the parameters. Note that the actual sizes of parameters don't matter, that
   * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
   *
   * @param width  Relative horizontal size
   * @param height Relative vertical size
   */
  public void setAspectRatio(int width, int height) {
    if (width < 0 || height < 0) {
      throw new IllegalArgumentException("Size cannot be negative.");
    }
    mRatioWidth = width;
    mRatioHeight = height;
    requestLayout();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = MeasureSpec.getSize(heightMeasureSpec);
    if (0 == mRatioWidth || 0 == mRatioHeight) {
      setMeasuredDimension(width, height);
      mRect.set(0,0,width,height);
    } else {
      if (width < height * mRatioWidth / mRatioHeight) {
        mRect.set(0,0,width, width * mRatioHeight / mRatioWidth);
        setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
      } else {
        mRect.set(0,0,height * mRatioWidth / mRatioHeight, height);
        setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
      }
    }
    if(mListener!=null){
      mListener.onPreviewAreaChanged(mRect);
    }
  }

  /**
   * This listener gets notified when the actual preview frame changes due
   * to a transform matrix being applied to the TextureView
   */
  public interface PreviewAreaChangedListener {
    public void onPreviewAreaChanged(RectF previewArea);
  }

  private PreviewAreaChangedListener mListener;
  private RectF mRect = new RectF();

  public void setListener(PreviewAreaChangedListener listener) {
    this.mListener = listener;
  }
}
