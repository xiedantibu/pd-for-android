/**
 * 
 * @author Peter Brinkmann (peter.brinkmann@gmail.com)
 * 
 * For information on usage and redistribution, and for a DISCLAIMER OF ALL
 * WARRANTIES, see the file, "LICENSE.txt," in this distribution.
 * 
 */

package org.puredata.android.fifths;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.BlurMaskFilter.Blur;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public final class CircleView extends View {

	private static enum State { UP, MAJOR, MINOR, SHIFT };
	private static final float RIDGE_WIDTH = 0.01f;
	private static final String[] notesSharp = { "C", "C\u266f", "D", "D\u266f", "E", "F", "F\u266f", "G", "G\u266f", "A", "A\u266f", "B" };
	private static final String[] notesFlat  = { "C", "D\u266d", "D", "E\u266d", "E", "F", "G\u266d", "G", "A\u266d", "A", "B\u266d", "B" };
	private static final int[] shifts =        {  0,   -5,   2,   -3,   4,   -1,  6,    1,   -4,   3,   -2,   5  };
	private static final float R0 = 0.25f;
	private static final float R2 = 0.95f;
	private static final float R1 = (float) Math.sqrt((R0 * R0 + R2 * R2) / 2);  // equal area for major and minor fields
	private int top = 0;
	private float xCenter, yCenter, xNorm, yNorm;
	private int selectedSegment = -1;
	private State currentState = State.UP;
	private CircleOfFifths owner;

	private Bitmap keySigs[];
	private Bitmap wheel = null;
	private Paint backgroundPaint;
	private Paint ridgePaint;
	private Paint labelPaint;
	private Paint selectedPaint;

	public CircleView(Context context) {
		super(context);
		init();
	}

	public CircleView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public CircleView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	public void setOwner(CircleOfFifths owner) {
		this.owner = owner;
	}

	public void setTop(int top) {
		this.top = top;
		invalidate();
	}

	private static Paint createDefaultPaint() {
		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setFilterBitmap(true);
		return paint;
	}

	private void init() {
		backgroundPaint = createDefaultPaint();
		backgroundPaint.setColor(Color.LTGRAY);
		backgroundPaint.setStyle(Paint.Style.FILL);

		ridgePaint = createDefaultPaint();
		ridgePaint.setColor(Color.DKGRAY);
		ridgePaint.setMaskFilter(new BlurMaskFilter(0.005f, Blur.NORMAL));
		ridgePaint.setStyle(Paint.Style.STROKE);
		ridgePaint.setStrokeWidth(RIDGE_WIDTH);

		labelPaint = createDefaultPaint();
		labelPaint.setColor(Color.BLACK);
		labelPaint.setTextAlign(Paint.Align.CENTER);
		labelPaint.setTypeface(Typeface.MONOSPACE);
		labelPaint.setTextSize(0.2f);

		selectedPaint = new Paint(labelPaint);
		selectedPaint.setColor(Color.RED);
		selectedPaint.setTextSize(0.3f);

		Resources res = getResources();
		keySigs = new Bitmap[] {
				BitmapFactory.decodeResource(res, R.drawable.ks00), BitmapFactory.decodeResource(res, R.drawable.ks01), 
				BitmapFactory.decodeResource(res, R.drawable.ks02), BitmapFactory.decodeResource(res, R.drawable.ks03), 
				BitmapFactory.decodeResource(res, R.drawable.ks04), BitmapFactory.decodeResource(res, R.drawable.ks05), 
				BitmapFactory.decodeResource(res, R.drawable.ks06), BitmapFactory.decodeResource(res, R.drawable.ks07), 
				BitmapFactory.decodeResource(res, R.drawable.ks08), BitmapFactory.decodeResource(res, R.drawable.ks09), 
				BitmapFactory.decodeResource(res, R.drawable.ks10), BitmapFactory.decodeResource(res, R.drawable.ks11)
		};
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int xDim = getDim(widthMeasureSpec);
		int yDim = getDim(heightMeasureSpec);
		int dim = Math.min(xDim, yDim);
		setMeasuredDimension(dim, dim);
	}

	private int getDim(int widthMeasureSpec) {
		int mode = MeasureSpec.getMode(widthMeasureSpec);
		int size = MeasureSpec.getSize(widthMeasureSpec);
		return (mode == MeasureSpec.UNSPECIFIED) ? 320 : size;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		canvas.translate(xCenter, yCenter);
		canvas.scale(xCenter, yCenter);
		canvas.save(Canvas.MATRIX_SAVE_FLAG);
		canvas.rotate(-top * 30);
		canvas.drawBitmap(wheel, null, new RectF(-1, -1, 1, 1), null);
		canvas.restore();
		int c = (top * 7) % 12;
		float dy = R0 / 1.8f;
		float dx = dy * 1.38f;
		canvas.drawBitmap(keySigs[c], null, new RectF(-dx, -dy, dx, dy), null);
		int s0 = shifts[c];
		for (int i = 0; i < 12; i++) {
			int s1 = s0 + i;
			if (i > 6) s1 -= 12;
			String label = (s1 >= 0) ? notesSharp[c] : notesFlat[c];
			drawLabel(canvas, label, (R1 + R2) / 2, currentState == State.MAJOR && i == selectedSegment);
			c = (c + 9) % 12;
			label = (s1 >= 0) ? notesSharp[c] : notesFlat[c];
			drawLabel(canvas, label.toLowerCase(), (R0 + R1) / 2, currentState == State.MINOR && i == selectedSegment);
			c = (c + 10) % 12;
			canvas.rotate(30);
		}
	}

	private void drawLabel(Canvas canvas, String label, float r, boolean selected) {
		Paint paint = selected ? selectedPaint : labelPaint;
		float d = paint.getTextSize() / 3f - r;
		if (label.length() > 1) {
			// ugly hack to work around unicode spacing problem
			canvas.drawText(label.charAt(0) + " ", 0, d, paint);
			canvas.drawText(" " + label.charAt(1), 0, d, paint);
		} else {
			canvas.drawText(label, 0, d, paint);				
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		xCenter = w / 2;
		xNorm = 1 / xCenter;
		yCenter = h / 2;
		yNorm = 1 / yCenter;
		drawWheel(w, h);
	}

	private void drawWheel(int w, int h) {
		if (wheel != null) {
			wheel.recycle();
		}
		Canvas canvas = new Canvas();
		wheel = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		canvas.setBitmap(wheel);
		canvas.translate(xCenter, yCenter);
		canvas.scale(xCenter, yCenter);
		canvas.drawCircle(0, 0, 1, backgroundPaint);
		canvas.drawCircle(0, 0, R0, ridgePaint);
		canvas.drawCircle(0, 0, R1, ridgePaint);
		canvas.drawCircle(0, 0, R2 - RIDGE_WIDTH / 2, ridgePaint);
		canvas.rotate(15);
		for (int i = 0; i < 12; i++) {
			canvas.drawLine(0, R0, 0, 1, ridgePaint);
			canvas.rotate(30);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		float x = (event.getX() - xCenter) * xNorm;
		float y = (event.getY() - yCenter) * yNorm;
		float angle = (float) (Math.atan2(x, -y) * 6 / Math.PI);
		int segment = (int) (angle + 12.5f) % 12;
		float radiusSquared = x * x + y * y;
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			if (radiusSquared >= R0 * R0) {
				selectedSegment = segment;
				if (radiusSquared >= R2 * R2) {
					currentState = State.SHIFT;
				} else {
					int note = (top * 7 + segment * 7) % 12;
					if (radiusSquared >= R1 * R1) {
						currentState = State.MAJOR;
						owner.playChord(true, note);
					} else {
						currentState = State.MINOR;
						note = (note + 9) % 12;
						owner.playChord(false, note);
					}
				}
				invalidate();
			}
			break;
		case MotionEvent.ACTION_MOVE:
			if (currentState == State.SHIFT && radiusSquared >= R0 * R0) {
				int step = (selectedSegment - segment + 12) % 12;
				if (step > 0) {
					selectedSegment = segment;
					top = (top + step) % 12;
					invalidate();
					owner.setTop(top);
				}
			}
			break;
		case MotionEvent.ACTION_UP:
		default:
			if (currentState == State.MAJOR || currentState == State.MINOR) {
				owner.endChord();
			}
			currentState = State.UP;
			invalidate();
			break;
		}
		return true;
	}
}
