package org.dyndns.fules.grkey;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Point;
import android.text.Html;
import android.content.res.Resources;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HelpScreen extends Activity {
	private static final String     TAG = "GRKeyboard";
	TextView help_text;

	public int getStatusBarHeight() {
		Resources res = getResources();
		int resourceId = res.getIdentifier("status_bar_height", "dimen", "android");
		return (resourceId <= 0) ? 0 : res.getDimensionPixelSize(resourceId);
	}

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.help_screen);

		// display html text - can't do it directly from xml
		help_text = (TextView)findViewById(R.id.help_text);
		help_text.setText(Html.fromHtml(getString(R.string.help_general)));

		// resize help_keyboard to fit screen - can't do it from xml either
		LinearLayout help_keyboard = (LinearLayout)findViewById(R.id.help_keyboard);
		ViewGroup.LayoutParams lp = help_keyboard.getLayoutParams();
		if (lp instanceof LinearLayout.LayoutParams) {
			LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams)lp;
			Point size = new Point();
			getWindowManager().getDefaultDisplay().getSize(size);
			llp.height = size.y - getStatusBarHeight();
		}
	}
}

// vim: set ai si sw=4 ts=4 noet:
