package org.dyndns.fules.grkey;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class SimpleGestureRecogniser implements View.OnTouchListener {
	private static final String     TAG = "GRKeyboard";
	private static final int        LONG_TAP_TIMEOUT = 800;

	private LongTap                 onLongTap;

	private View                    key;
	int                             gestureCode;
	PointF                          startPoint, farthestPoint;
	float                           longestDistance2;
	float                           minGestureLength;

	SimpleGestureRecogniser() {
		this(20f);
	}

	SimpleGestureRecogniser(float minGestureLength) {
		onLongTap = new LongTap();
		this.minGestureLength = minGestureLength;
	}

	private final class LongTap implements Runnable {
		public void run() {
			gestureCode = 5; // long tap
			if (KeyboardService.theKeyboardService != null)
				KeyboardService.theKeyboardService.keyClicked(key, gestureCode);
			key = null;
		}
	}

	void gesturePartFinished(float x, float y) {
		PointF p = new PointF(x, y);
		float d2 = startPoint.dist2(p);
		boolean forthAndBack = (2*d2 < longestDistance2);

		// codify the angle to a 1-digit number, as on the numeric keypad
		// eg. 8=North, 3=South-East, etc., reserve 5 for long-tap
		// on the (touch)screen, positive y axis points downwards
		float angle = -(float)Math.atan2(farthestPoint.y - startPoint.y, farthestPoint.x - startPoint.x);

		// rotate by PI/8 for easier boundary comparison
		angle += PointF.PI / 8;
		/// convert to [0..2*PI)
		if (angle < 0)
			angle += 2*PointF.PI;

		if (longestDistance2 < minGestureLength) {
			gestureCode = 0; // Tap
		}
		else if (angle < PointF.PI) {
			if (angle < PointF.PI/2) {
				if (angle < PointF.PI/4)
					gestureCode = 6; // East
				else
					gestureCode = 9; // North-East
			}
			else { // PointF.PI/2 <= angle
				if (angle < 3*PointF.PI/4)
					gestureCode = 8; // North
				else
					gestureCode = 7; // North-West
			}
		}
		else { // PointF.PI <= angle
			if (angle < 3*PointF.PI/2) {
				if (angle < 5*PointF.PI/4)
					gestureCode = 4; // West
				else
					gestureCode = 1; // South-West
			}
			else { // 3*PointF.PI/2 <= angle
				if (angle < 7*PointF.PI/4)
					gestureCode = 2; // South
				else
					gestureCode = 3; // South-East
			}
		}

		if (forthAndBack && (gestureCode > 0))
			gestureCode = (10*gestureCode) + (10 - gestureCode);
		Log.d(TAG, "Gesture part finished; angle=" + (angle * 180.0f / Math.PI) + "', code='" + gestureCode + "'");

	}

	void processGestureMove(float x, float y) {
		PointF p = new PointF(x, y);
		float d2 = startPoint.dist2(p);
		if (longestDistance2 < d2) {
			farthestPoint = p;
			longestDistance2 = d2;
		}
	}

	public boolean onTouch(View k, MotionEvent event) {
		key = k;
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN: {
				gestureCode = 0;
				longestDistance2 = 0;
				startPoint = farthestPoint = new PointF(event.getX(), event.getY());
				key.postDelayed(onLongTap, LONG_TAP_TIMEOUT);
			}
			return true;

			case MotionEvent.ACTION_MOVE: {
				int historySize = event.getHistorySize();
				for (int h = 0; h < historySize; h++)
					processGestureMove(event.getHistoricalX(0, h), event.getHistoricalY(0, h));
				processGestureMove(event.getX(), event.getY());
			}
			return true;

			case MotionEvent.ACTION_UP: {
				if (gestureCode == 5) { // long tap is already reported
					gestureCode = 0;
					break;
				}
				key.removeCallbacks(onLongTap);

				gesturePartFinished(event.getX(), event.getY());

				if (KeyboardService.theKeyboardService != null)
					KeyboardService.theKeyboardService.keyClicked(key, gestureCode);
				key = null;
				//Log.d(TAG, "Stopped gesture;");
			}
			return true;
		}
		return false;
	}

}

// vim: set ai si sw=4 ts=4 noet:
