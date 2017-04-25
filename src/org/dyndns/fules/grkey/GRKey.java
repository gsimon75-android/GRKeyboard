package org.dyndns.fules.grkey;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.io.IOException;
import java.util.Vector;
import java.util.Iterator;

public class GRKey extends Button {
	private static final String     TAG = "GRKeyboard";

	private int[]                   stateNormal = { android.R.attr.state_enabled, android.R.attr.state_window_focused, android.R.attr.state_multiline };
	private int[]                   statePressed = { android.R.attr.state_enabled, android.R.attr.state_window_focused, android.R.attr.state_multiline, android.R.attr.state_pressed };
	private int                     lastScript = -1;
	private int                     lastShiftState = -1;

	public GRKey(Context context) {
		this(context, null);
	}

	public GRKey(Context context, AttributeSet attributes) {
		this(context, attributes, R.attr.grKeyStyle);
	}

	public GRKey(Context context, AttributeSet attributes, int defStyleAttr) {
		super(context, attributes, defStyleAttr);

		updateShiftState();
	}

	public void updateShiftState() {
		if (KeyboardService.theKeyboardService != null) {
			int script = KeyboardService.theKeyboardService.getScript();
			int shiftState = KeyboardService.theKeyboardService.getShiftState();
			if ((lastScript != script) || (lastShiftState != shiftState)) {
				setText(KeyboardService.theKeyboardService.getLabelForKey(getId()));
				lastScript = script;
				lastShiftState = shiftState;
			}
		}
	}

	public void onDraw(Canvas canvas) {
		updateShiftState();
		super.onDraw(canvas);
	}

	void dumpAttributeSet(AttributeSet attrs) {
		for (int i = 0; i < attrs.getAttributeCount(); ++i) {
			Log.d(TAG, "attr; name='" + attrs.getAttributeName(i) + "', value='" + attrs.getAttributeValue(i) + "'");
		}
	}

	@Override public boolean onTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN: {
				Drawable bg = getBackground();
				if (bg.setState(statePressed))
					bg.invalidateSelf();
			}
			break;

			case MotionEvent.ACTION_UP: {
				Drawable bg = getBackground();
				if (bg.setState(stateNormal))
					bg.invalidateSelf();
			}
			break;
		}
		if ((KeyboardService.theKeyboardService != null) &&
			(KeyboardService.theKeyboardService.gestureRecogniser != null))
			KeyboardService.theKeyboardService.gestureRecogniser.onTouch(this, event);
		return true;
	}

	public void onMeasure(int w, int h) {
		super.onMeasure(w, h);
		ViewGroup.LayoutParams lp = getLayoutParams();
		if (lp instanceof LinearLayout.LayoutParams) {
			LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams)lp;
			int mw = getMeasuredWidth();
			float keyHeight = (KeyboardService.theKeyboardService == null) ? 1.0f : KeyboardService.theKeyboardService.getRelativeKeyHeight();
			setMeasuredDimension(mw, (int)(keyHeight * mw / llp.weight));
		}
	}
}

// vim: set ai si sw=4 ts=4 noet:
