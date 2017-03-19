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

public class HelpKey extends Button {
	private static final String     TAG = "GRKeyboard";

	public HelpKey(Context context) {
		this(context, null);
	}

	public HelpKey(Context context, AttributeSet attributes) {
		this(context, attributes, R.attr.helpKeyStyle);
	}

	public HelpKey(Context context, AttributeSet attributes, int defStyleAttr) {
		super(context, attributes, defStyleAttr);

		if (KeyboardService.theKeyboardService != null) {
			setText(KeyboardService.theKeyboardService.getAllLabelsForKey(getId()));
		}
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
