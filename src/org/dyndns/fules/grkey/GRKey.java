package org.dyndns.fules.grkey;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.TextView;
import java.io.IOException;
import java.util.Vector;
import java.util.Iterator;

public class GRKey extends Button {
	// Constants
	private static final String		TAG = "GRKeyboard";
	private GRKeyboardService		svc;
	private int[] stateNormal		= { android.R.attr.state_enabled, android.R.attr.state_window_focused, android.R.attr.state_multiline };
	private int[] statePressed		= { android.R.attr.state_enabled, android.R.attr.state_window_focused, android.R.attr.state_multiline, android.R.attr.state_pressed };

	JitterFilter		jitterFilter = new JitterFilter(0.6f);
	LinearRegression	strokeFinder = new LinearRegression(0.8f, 40f);
	int			gestureCode;

	public GRKey(Context context) {
		this(context, null);
	}

	public GRKey(Context context, AttributeSet attributes) {
		this(context, attributes, android.R.attr.buttonStyle);
	}

	public GRKey(Context context, AttributeSet attributes, int defStyleAttr) {
		super(context, attributes, defStyleAttr);
		//Log.d(TAG, "GRKey(" + context + ", " + attributes + ", " + defStyleAttr + ")");
		//dumpAttributeSet(attributes);
		if (context instanceof GRKeyboardService) {
			svc = (GRKeyboardService)context;
		}
		else {
			Log.d(TAG, "Context of key is not a GRKeyboardService");
		}
	}

	void dumpAttributeSet(AttributeSet attrs) {
		for (int i = 0; i < attrs.getAttributeCount(); ++i) {
			Log.d(TAG, "attr; name='" + attrs.getAttributeName(i) + "', value='" + attrs.getAttributeValue(i) + "'");
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

	@Override public boolean onTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN: {
				Drawable bg = getBackground();
				if (bg.setState(statePressed))
					bg.invalidateSelf();
				//Log.d(TAG, "Start gesture;");
				gestureCode = 0;
				jitterFilter.clear();
				strokeFinder.clear();
				processGestureMove(event.getX(), event.getY());
			}
			break;

			case MotionEvent.ACTION_MOVE: {
			     	int historySize = event.getHistorySize();
				for (int h = 0; h < historySize; h++)
					processGestureMove(event.getHistoricalX(0, h), event.getHistoricalY(0, h));
				processGestureMove(event.getX(), event.getY());
			}
			break;

			case MotionEvent.ACTION_UP: {
				Drawable bg = getBackground();
				if (bg.setState(stateNormal))
					bg.invalidateSelf();

				Log.d(TAG, "Stopping gesture;");
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
				Log.d(TAG, "Stopped gesture;");
				if (svc != null)
					svc.keyClicked(this, gestureCode);
			}
			break;
		}
		return true;
	}

}

// vim: set ai si sw=8 ts=8 noet:
