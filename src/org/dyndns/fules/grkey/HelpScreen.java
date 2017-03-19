package org.dyndns.fules.grkey;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;

public class HelpScreen extends Activity {
	private static final String     TAG = "GRKeyboard";

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.help_screen);
	}
}

// vim: set ai si sw=4 ts=4 noet:
