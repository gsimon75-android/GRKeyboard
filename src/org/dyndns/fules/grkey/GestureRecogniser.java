package org.dyndns.fules.grkey;

import android.view.MotionEvent;
import android.view.View;

public interface GestureRecogniser {
	GestureRecogniser(KeyboardService s);
	public void onTouchEvent(View k, MotionEvent event);
}

// vim: set ai si sw=4 ts=4 noet:
