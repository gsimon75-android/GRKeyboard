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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class GRKey extends Button {
	// Constants
	private static final String		TAG = "GRKeyboard";
	private GRKeyboardService		svc;
	private int[] stateNormal		= { android.R.attr.state_enabled, android.R.attr.state_window_focused, android.R.attr.state_multiline };
	private int[] statePressed		= { android.R.attr.state_enabled, android.R.attr.state_window_focused, android.R.attr.state_multiline, android.R.attr.state_pressed };

	public GRKey(Context context) {
		this(context, null);
	}

	public GRKey(Context context, AttributeSet attributes) {
		this(context, attributes, android.R.attr.buttonStyle);
	}

	public GRKey(Context context, AttributeSet attributes, int defStyleAttr) {
		super(context, attributes, defStyleAttr);
		Log.d(TAG, "GRKey(" + context + ", " + attributes + ", " + defStyleAttr + ")");
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

	@Override public boolean onTouchEvent(MotionEvent event) {
		Log.d(TAG, "onTouchEvent(" + event + ")");
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN: {
				Drawable bg = getBackground();
				for (int s : bg.getState()) {
					Log.d(TAG, "onTouchEvent; bg state=" + s);
				}
				if (bg.setState(statePressed)) {
					Log.d(TAG, "onTouchEvent; setState returned true");
					bg.invalidateSelf();
				}
			}
			break;

			case MotionEvent.ACTION_MOVE:
				break;

			case MotionEvent.ACTION_UP: {
				Drawable bg = getBackground();
				for (int s : bg.getState()) {
					Log.d(TAG, "onTouchEvent; bg state=" + s);
				}
				if (bg.setState(stateNormal)) {
					Log.d(TAG, "onTouchEvent; setState returned true");
					bg.invalidateSelf();
				}
				if (svc != null)
					svc.keyClicked(this);
			}
			break;
		}
		return true;
	}

}

// vim: set ai si sw=8 ts=8 noet:
