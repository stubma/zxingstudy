package com.happythick.zxingstudy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Region;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by maruojie on 15/11/8.
 */
public class CropRectView extends View {
	public CropRectView(Context context) {
		super(context);
	}

	public CropRectView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CropRectView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		// draw gray cover but keep center rect clipped
		int size = getWidth() * 2 / 3;
		int extraOffset = size / 4;
		Path cropPath = new Path();
		cropPath.addRect((getWidth() - size) / 2,
			(getHeight() - size) / 2 - extraOffset,
			(getWidth() + size) / 2,
			(getHeight() + size) / 2 - extraOffset,
			Path.Direction.CCW);
		Paint paint = new Paint();
		paint.setColor(Color.argb(200, 127, 127, 127));
		canvas.clipPath(cropPath, Region.Op.DIFFERENCE);
		canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
	}
}
