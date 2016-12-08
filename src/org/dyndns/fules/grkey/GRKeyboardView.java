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
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.inputmethodservice.KeyboardView;
import android.media.MediaPlayer;
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
import android.view.SoundEffectConstants;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class GRKeyboardView extends RelativeLayout {
	// Constants
	private static final String		TAG = "GRKeyboard";
	private static final int		LONG_TAP_TIMEOUT = 500;

	// Parameters
	float					keyMM = 12;	// maximal key size in mm-s
	float					marginLeft = 0, marginRight = 0, marginBottom = 0; // margins in mm-s

	float					fontSize;	// height of the key caption font in pixels
	float					fontDispY;	// Y-displacement of key caption font (top to baseline)
	boolean					isTypingPassword; // is the user typing a password

	Paint					textPaint;	// Paint for drawing key captions
	Paint					specPaint;	// Paint for drawing special key captions
	KeyboardView.OnKeyboardActionListener	actionListener;	// owner of the callback methods, result is passed to this instance

	HashSet<String>				modifiers;	// currently active modifiers
	HashSet<String>				locks;		// currently active locks

	float					downX, downY, upX, upY;
	LongTap					onLongTap;	// long tap checker
	boolean					wasLongTap;	// marker to skip processing the release of a long tap

	/*
	 * Long tap handler
	 */
	private final class LongTap implements Runnable {
		public void run() {
			wasLongTap = true; // FIXME: really?
		}
	}

	/*
	 * Methods of CompassKeyboardView
	 */

	public GRKeyboardView(Context context) {
		super(context);
		init(context);
	}

	public GRKeyboardView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public void init(Context context) {
	}

	// Recalculate all the sizes according to the display metrics
	/*public void calculateSizesForMetrics(DisplayMetrics metrics) {
		// note: the metrics may change during the lifetime of the instance, so these precalculations could not be done in the constructor
		int i, totalWidth; 
		int marginPixelsLeft = Math.round(marginLeft * metrics.xdpi / 25.4f);
		int marginPixelsRight = Math.round(marginRight * metrics.xdpi / 25.4f);
		int marginPixelsBottom = Math.round(marginBottom * metrics.ydpi / 25.4f);
		setPadding(marginPixelsLeft, 0, marginPixelsRight, marginPixelsBottom);

		// Desired "key size" in pixels is keyMM * metrics.xdpi / 25.4f
                // This "key size" is an abstraction of a key that has 3 symbol columns (and therefore 4 gaps: gSgSgSg),
		// so that the gaps take up 1/3 and the symbols take up 2/3 of the key, so
		//   4*gaps = 1/3 * keySize	-> gap = keySize / 12
		//   3*sym = 2/3 * keySize	-> sym = 2 * keySize / 9
		// We have nKeys keys and nColumns columns, that means nKeys*gap + nColumns*(sym + gap), that is
		//   nKeys*keySize/12 + nColumns*keySize*(2/9 + 1/12) = keySize * (nKeys/12 + nColumns*11/36)
		totalWidth = Math.round(keyMM * metrics.xdpi / 25.4f * ((nKeys / 12.f) + (nColumns * 11 / 36.f)));
		// Regardless of keyMM, it must fit the metrics, that is width - margins - 1 pixel between keys
		i = metrics.widthPixels - marginPixelsLeft - marginPixelsRight - (nKeys - 1);
		if (i < totalWidth)
			totalWidth = i;

		// Now back to real key sizes, we still have nKeys keys and nColumns columns for these totalWidth pixels, which means 
		//   nKeys*gap + nColumns*(sym + gap) = gap*(nKeys+nColumns) + sym*nColumns <= totalWidth

		// Rounding errors can foul up everything, and the sum above is more sensitive on the error of gap than of sym,
		//   so we calculate gap first (with rounding) and then adjust sym to it.
		// As decided, a gap to symbol ratio of 1/3 to 2/3 would be ergonomically pleasing, so 2*4*gap = 3*sym, that is sym = 8*gap/3, so
		//   gap*(nKeys+nColumns) + 8*gap/3*nColumns = totalWidth
		//   gap*(nKeys+nColumns + 8/3*nColumns) = totalWidth
		gap = Math.round(totalWidth / (nKeys+nColumns + 8*nColumns/3.f));
		// Calculating sym as 8/3*gap is tempting, but that wouldn't compensate the rounding error above, so we have to derive
		// it from totalWidth and rounding it only downwards:
		//   gap*(nKeys+nColumns) + sym*nColumns = totalWidth
		sym = (totalWidth - gap*(nKeys+nColumns)) / nColumns;

		// Sample data: nKeys=5, columns=13; Galaxy Mini: 240x320, Ace: 320x480, S: 480x80, S3: 720x1280 

		// construct the Paint used for printing the labels
		textPaint.setTextSize(sym);
		int newSym = sym;

		specPaint.setTextSize(sym);
		candidatePaint.setTextSize(sym * 3 / 2);
		candidatePaint.setStrokeWidth(gap);

		Paint.FontMetrics fm = textPaint.getFontMetrics();
		fontSize = fm.descent - fm.ascent;
		fontDispY = -fm.ascent;

		Log.v(TAG, "keyMM=" + String.valueOf(keyMM) + ", xdpi=" + String.valueOf(metrics.xdpi) + ", ydpi=" + String.valueOf(metrics.ydpi) + ", nKeys=" + String.valueOf(nKeys) + ", nColumns=" + String.valueOf(nColumns) + ", totalWidth=" + String.valueOf(totalWidth) + ", max=" + String.valueOf(i) + ", sym=" + String.valueOf(sym) + ", gap=" + String.valueOf(gap) + ", reqFS="+String.valueOf(sym)+", fs="+String.valueOf(fontSize)+", asc="+String.valueOf(fm.ascent)+", desc="+String.valueOf(fm.descent));

		toast.setGravity(Gravity.TOP + Gravity.CENTER_HORIZONTAL, 0, -sym);

		int n = kbd.getChildCount();
		for (i = 0; i < n; i++) {
			EmbeddableItem e = (EmbeddableItem)kbd.getChildAt(i);
			e.calculateSizes();
		}
		commitState();
	} */

	// Common touch event handler - record coordinates and manage long tap handlers
	public boolean processTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				// remember the swipe starting coordinates for checking for global swipes
				downX = event.getX();
				downY = event.getY();
				// register a long tap handler
				wasLongTap = false;
				postDelayed(onLongTap, LONG_TAP_TIMEOUT);
				return true;

			case MotionEvent.ACTION_UP:
				// end of swipe
				upX = event.getX();
				upY = event.getY();
				// check if this is the end of a long tap
				if (wasLongTap) {
					wasLongTap = false;
					return true;
				}
				// cancel any pending checks for long tap
				removeCallbacks(onLongTap);
				// touch event processed
				return true;

			case MotionEvent.ACTION_MOVE:
				// cancel any pending checks for long tap
				removeCallbacks(onLongTap);
				return false;
		}
		// we're not interested in other kinds of events
		return false;
	}

	@Override public boolean onTouchEvent(MotionEvent event) {
		boolean res = processTouchEvent(event);

		switch (event.getAction()) {
			case MotionEvent.ACTION_UP:
				//d = getGlobalSwipeDirection(downX, downY, upX, upY);
				break;

			case MotionEvent.ACTION_MOVE:
				//d = getGlobalSwipeDirection(downX, downY, upX, upY);
				break;
		}
		return res;
	}

	public void setOnKeyboardActionListener(KeyboardView.OnKeyboardActionListener listener) {
		actionListener = listener;
	}

	public void setInputType(int type) {
		isTypingPassword = ((type & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT) &&
				   ((type & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD);
	}

	/*private boolean processAction(Action cd) {
		if (cd == null)
			return false;

		if (cd.mod != null) {
			changeState(cd.mod, cd.isLock);		// process a 'mod' or 'lock'
		}
		else if (actionListener != null) {
			resetState();
			if (cd.code != null)
				actionListener.onText(cd.code); // process a 'code'
			else if (cd.keyCode >= 0)
				actionListener.onKey(cd.keyCode, null); // process a 'key'
			else if (actionListener instanceof CompassKeyboard) {
				CompassKeyboard ck = (CompassKeyboard)actionListener;
				if (cd.layout >= 0)
					ck.updateLayout(cd.layout); // process a 'layout'
				else if ((cd.cmd != null) && (cd.cmd.length() > 0))
					ck.execCmd(cd.cmd); // process a 'cmd'
			}
			vibrateCode(vibrateOnKey);
		}

		return true;
	}*/

	public boolean checkState(String mod) {
		return modifiers.contains(mod);
	}

	public void resetState() {
		if (!modifiers.isEmpty()) {
			modifiers.clear();
			//commitState();
		}
	}

	public void changeState(String state, boolean isLock) {
		if (state == null) {
			resetState();
		}
		else if (state.contentEquals("hide")) {
			resetState();
			if (actionListener != null)
				actionListener.swipeDown(); // simulate hiding request
		}
		else if (isLock){
			resetState();
			if (!locks.add(state))
				locks.remove(state);
			//commitState();
		}
		else {
			if (!modifiers.add(state))
				modifiers.remove(state);
			//commitState();
		}
	}

	public void setLeftMargin(float f) {
		marginLeft = f;
		//calculateSizesForMetrics(getResources().getDisplayMetrics());
	}

	public void setRightMargin(float f) {
		marginRight = f;
		//calculateSizesForMetrics(getResources().getDisplayMetrics());
	}

	public void setBottomMargin(float f) {
		marginBottom = f;
		//calculateSizesForMetrics(getResources().getDisplayMetrics());
	}

	public void setMaxKeySize(float f) {
		keyMM = f > 0 ? f : 12;
		//calculateSizesForMetrics(getResources().getDisplayMetrics());
	}
}

// vim: set ai si sw=8 ts=8 noet:
