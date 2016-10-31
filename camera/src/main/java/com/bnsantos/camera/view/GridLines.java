package com.bnsantos.camera.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.v4.content.res.ResourcesCompat;
import android.util.AttributeSet;
import android.view.View;

import com.bnsantos.camera.R;

/**
 * GridLines is a view which directly overlays the preview and draws
 * evenly spaced grid lines.
 */
public class GridLines extends View implements AutoFitTextureView.PreviewAreaChangedListener {

  private RectF mDrawBounds;
  Paint mPaint = new Paint();

  public GridLines(Context context, AttributeSet attrs) {
    super(context, attrs);
    mPaint.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.grid_line_width));
    mPaint.setColor(ResourcesCompat.getColor(getResources(), R.color.grid_line, context.getTheme()));
  }

  @Override
  public void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (mDrawBounds != null) {
      int lines = 3;
      float thirdWidth = mDrawBounds.width() / lines;
      float thirdHeight = mDrawBounds.height() / lines;
      for (int i = 1; i < lines; i++) {
        // Draw the vertical lines.
        final float x = thirdWidth * i;
        canvas.drawLine(mDrawBounds.left + x, mDrawBounds.top, mDrawBounds.left + x, mDrawBounds.bottom, mPaint);
        // Draw the horizontal lines.
        final float y = thirdHeight * i;
        canvas.drawLine(mDrawBounds.left, mDrawBounds.top + y, mDrawBounds.right, mDrawBounds.top + y, mPaint);
      }
    }
  }

  @Override
  public void onPreviewAreaChanged(final RectF previewArea) {
    setDrawBounds(previewArea);
  }

  private void setDrawBounds(final RectF previewArea) {
    mDrawBounds = new RectF(previewArea);
    invalidate();
  }
}
