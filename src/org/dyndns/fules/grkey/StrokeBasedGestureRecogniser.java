package org.dyndns.fules.grkey;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class StrokeBasedGestureRecogniser implements View.OnTouchListener {
	private static final String     TAG = "GRKeyboard";
	private static final int        LONG_TAP_TIMEOUT = 800;
	static final float              GESTURE_JITTER_LIMIT = 0.6f;
	static final float              GESTURE_QUALITY_THRESHOLD = 0.8f;
	static final float              GESTURE_MINIMAL_LENGTH = 20f;

	private LongTap                 onLongTap;

	private View                    key;
	JitterFilter                    jitterFilter;
	LinearRegression                strokeFinder;
	int                             gestureCode;

	StrokeBasedGestureRecogniser() {
		this(GESTURE_JITTER_LIMIT, GESTURE_QUALITY_THRESHOLD, GESTURE_MINIMAL_LENGTH);
	}

	StrokeBasedGestureRecogniser(float jitterLimit, float qualityThreshold, float minimalRequiredLength) {
		jitterFilter = new JitterFilter(jitterLimit);
		strokeFinder = new LinearRegression(qualityThreshold, minimalRequiredLength);
		onLongTap = new LongTap();
	}

	private final class LongTap implements Runnable {
		public void run() {
			gestureCode = 5; // long tap
			if (KeyboardService.theKeyboardService != null)
				KeyboardService.theKeyboardService.keyClicked(key, gestureCode);
			key = null;
		}
	}

	void gesturePartFinished() {
		float angle = strokeFinder.getAngle();
		PointF deviation = strokeFinder.getDeviation();
		float quality = strokeFinder.getQuality();

		// codify the angle to a 1-digit number, as on the numeric keypad
		// eg. 8=North, 3=South-East, etc., reserve 5 for long-tap
		int gestureDigit;

		// on the (touch)screen, positive y axis points downwards
		angle = -angle;
		// rotate by PI/8 for easier boundary comparison
		angle += PointF.PI / 8;
		/// convert to [0..2*PI)
		if (angle < 0)
			angle += 2*PointF.PI;

		if (deviation.abs2() < 1) {
			gestureDigit = 0; // Tap
		}
		else if (angle < PointF.PI) {
			if (angle < PointF.PI/2) {
				if (angle < PointF.PI/4)
					gestureDigit = 6; // East
				else
					gestureDigit = 9; // North-East
			}
			else { // PointF.PI/2 <= angle
				if (angle < 3*PointF.PI/4)
					gestureDigit = 8; // North
				else
					gestureDigit = 7; // North-West
			}
		}
		else { // PointF.PI <= angle
			if (angle < 3*PointF.PI/2) {
				if (angle < 5*PointF.PI/4)
					gestureDigit = 4; // West
				else
					gestureDigit = 1; // South-West
			}
			else { // 3*PointF.PI/2 <= angle
				if (angle < 7*PointF.PI/4)
					gestureDigit = 2; // South
				else
					gestureDigit = 3; // South-East
			}
		}
		Log.d(TAG, "Gesture part finished; angle=" + (angle * 180.0f / Math.PI) + "', deviation='" + deviation + "', quality='" + quality + "', code='" + gestureDigit + "'");

		gestureCode = 10*gestureCode + gestureDigit;
	}

	void processGestureMove(float x, float y) {
		PointF p = new PointF(x, y);

		if (!jitterFilter.add(p))
			return; // within jitter limit, nothing to do (yet)

		PointF pp = jitterFilter.getLast();
		if (!strokeFinder.add(pp)) {
			// backward or diverging move: evaluate the gesture drawn so far
			gesturePartFinished();
			strokeFinder.clearButLast();
			strokeFinder.add(pp);
		}
	}

	public boolean onTouch(View k, MotionEvent event) {
		key = k;
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN: {
				gestureCode = 0;
				jitterFilter.clear();
				strokeFinder.clear();
				processGestureMove(event.getX(), event.getY());
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

				PointF p = new PointF(event.getX(), event.getY());
				if (!jitterFilter.add(p))
					jitterFilter.stop(); // stop forcibly if otherwise would remain in jitter range

				PointF pp = jitterFilter.getLast();
				if (!strokeFinder.add(pp)) {
					// backward or diverging move: evaluate the gesture drawn so far
					gesturePartFinished();
					strokeFinder.clearButLast();
				}
				else {
					if (strokeFinder.isLongEnough())
						gesturePartFinished();
				}
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
