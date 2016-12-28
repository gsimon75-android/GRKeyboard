package org.dyndns.fules.grkey;
import org.dyndns.fules.grkey.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.res.Configuration;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.inputmethodservice.KeyboardView;
import android.util.DisplayMetrics;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.R.id;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.inputmethodservice.AbstractInputMethodService;
import android.view.ViewGroup;
import android.view.ViewParent;

public class GRKeyboardService extends InputMethodService implements SharedPreferences.OnSharedPreferenceChangeListener  {
	public static final String	SHARED_PREFS_NAME = "GRKeyboardSettings";
	private static final String	TAG = "GRKeyboard";

	LayoutInflater inflater;
	private SharedPreferences	mPrefs;					// the preferences instance
	View				kv;					// the current layout view

	boolean				forcePortrait;				// use the portrait layout even for horizontal screens
	int				lastOrientation = -1;

	ExtractedTextRequest		etreq = new ExtractedTextRequest();
	int				selectionStart = -1, selectionEnd = -1;

	// send an auto-revoked notification with a title and a message
	/*void sendNotification(String title, String msg) {
		// Simple as a pie, isn't it...
		Notification n = new Notification(android.R.drawable.ic_notification_clear_all, title, System.currentTimeMillis());
		n.flags = Notification.FLAG_AUTO_CANCEL;
		n.setLatestEventInfo(this, title, msg, PendingIntent.getActivity(this, 0, new Intent(), 0));
		((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, n);
		Log.e(TAG, title+"; "+msg);
	}*/

	@Override public void onCreate() {
		Log.d(TAG, "onCreate;");
		setTheme(R.style.Theme_GRKeyboard);
		super.onCreate();
	}

	@Override public AbstractInputMethodService.AbstractInputMethodImpl onCreateInputMethodInterface() {
		Log.d(TAG, "onCreateInputMethodInterface;");

		etreq.hintMaxChars = etreq.hintMaxLines = 0;

		mPrefs = getSharedPreferences(SHARED_PREFS_NAME, 0);
		mPrefs.registerOnSharedPreferenceChangeListener(this);
		inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		//forcePortrait = mPrefs.getBoolean("portrait_only", false);
		return super.onCreateInputMethodInterface();
	}

	@Override public void onConfigurationChanged(Configuration newConfig) {
		Log.d(TAG, "onConfigurationChanged;");
		if (newConfig.orientation != lastOrientation) {
			lastOrientation = newConfig.orientation;
			kv = inflater.inflate(R.layout.keyboard, null);
			setInputView(kv);
		}
	}

	@Override public void onStartInput(EditorInfo attribute, boolean restarting) {
		super.onStartInput(attribute, restarting); 
		Log.v(TAG, "onStartInput(..., " + restarting + ")");
		//kv.resetState();
		//kv.setInputType(attribute.inputType);
	}

	@Override public View onCreateInputView() {
		DisplayMetrics metrics = new DisplayMetrics();
		metrics.setTo(getResources().getDisplayMetrics());
		Log.v(TAG, "onCreateInputView; w=" + String.valueOf(metrics.widthPixels) + ", h=" + String.valueOf(metrics.heightPixels) + ", forceP=" + String.valueOf(forcePortrait));

		Configuration config = getResources().getConfiguration();
		lastOrientation = config.orientation;

		kv = inflater.inflate(R.layout.keyboard, null);
		Log.v(TAG, "kv=" + kv);
		return kv;
	} 

	@Override public boolean onEvaluateFullscreenMode() {
		return false; // never require fullscreen
	}

	private void sendModifiers(InputConnection ic, int action) {
		if (kv == null)
			return;
		/*if (kv.checkState("shift"))
			ic.sendKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_SHIFT_LEFT));
		if (kv.checkState("alt"))
			ic.sendKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_ALT_LEFT));
		if (kv.checkState("altgr"))
			ic.sendKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_ALT_RIGHT));*/
	}

	// Process a generated keycode
	public void onKey(int primaryCode, int[] keyCodes) {
		InputConnection ic = getCurrentInputConnection();
		sendModifiers(ic, KeyEvent.ACTION_DOWN);
		sendDownUpKeyEvents(primaryCode);
		sendModifiers(ic, KeyEvent.ACTION_UP);
	}

	// Process the generated text
	public void onText(CharSequence text) {
		InputConnection ic = getCurrentInputConnection();
		sendModifiers(ic, KeyEvent.ACTION_DOWN);
		sendKeyChar(text.charAt(0));
		sendModifiers(ic, KeyEvent.ACTION_UP);
	} 

	// Process a command
	public void execCmd(String cmd) {
		InputConnection ic = getCurrentInputConnection();

		if (cmd.equals("selectStart")) {
			selectionStart = ic.getExtractedText(etreq, 0).selectionStart;
			if ((selectionStart >= 0) && (selectionEnd >= 0)) {
				ic.setSelection(selectionStart, selectionEnd);
				selectionStart = selectionEnd = -1;
			}
		}
		else if (cmd.equals("selectEnd")) {
			selectionEnd = ic.getExtractedText(etreq, 0).selectionEnd;
			if ((selectionStart >= 0) && (selectionEnd >= 0)) {
				ic.setSelection(selectionStart, selectionEnd);
				selectionStart = selectionEnd = -1;
			}
		}
		else if (cmd.equals("selectAll"))
			ic.performContextMenuAction(android.R.id.selectAll);
		else if (cmd.equals("copy"))
			ic.performContextMenuAction(android.R.id.copy);
		else if (cmd.equals("cut"))
			ic.performContextMenuAction(android.R.id.cut);
		else if (cmd.equals("paste"))
			ic.performContextMenuAction(android.R.id.paste);
		else if (cmd.equals("switchIM"))
			ic.performContextMenuAction(android.R.id.switchInputMethod);
		else
			Log.w(TAG, "Unknown cmd '" + cmd + "'");
	}

	public void pickDefaultCandidate() {
	}

	public void swipeRight() {
	}

	public void swipeLeft() {
	}

	// Hide the view
	public void swipeDown() {
		requestHideSelf(0);
	}

	public void swipeUp() {
	}

	public void onPress(int primaryCode) {
	}

	public void onRelease(int primaryCode) {
	} 

	String getPrefString(String key, String def) {
		String s = "";

		if (lastOrientation == Configuration.ORIENTATION_PORTRAIT) {
			s = mPrefs.getString("portrait_" + key, "");
			if (s.contentEquals(""))
				s = mPrefs.getString("landscape_" + key, "");
		}
		else {
			s = mPrefs.getString("landscape_" + key, "");
			if (s.contentEquals(""))
				s = mPrefs.getString("portrait_" + key, "");
		}
		if (s.contentEquals(""))
			s = mPrefs.getString(key, "");
		return s.contentEquals("") ? def : s;
	}

	int getPrefInt(String key, int def) {
		String s = getPrefString(key, "");
		try {
			return s.contentEquals("") ? def : Integer.parseInt(s);
		}
		catch (NumberFormatException e) {
			Log.w(TAG, "Invalid value for integer preference; key='" + key + "', value='" + s +"'");
		}
		catch (ClassCastException e) {
			Log.w(TAG, "Found non-string int preference; key='" + key + "', err='" + e.getMessage() + "'");
		}
		return def;
	}

	float getPrefFloat(String key, float def) {
		String s = getPrefString(key, "");
		try {
			return s.contentEquals("") ? def : Float.parseFloat(s);
		}
		catch (NumberFormatException e) {
			Log.w(TAG, "Invalid value for float preference; key='" + key + "', value='" + s +"'");
		}
		catch (ClassCastException e) {
			Log.w(TAG, "Found non-string float preference; key='" + key + "', err='" + e.getMessage() + "'");
		}
		return def;
	}

	// Handle one change in the preferences
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		//Log.d(TAG, "Changing pref "+key);
		/*if (key.endsWith("margin_left")) {
			kv.setLeftMargin(getPrefFloat("margin_left", 0));
			getWindow().dismiss();
		}
		else if (key.endsWith("margin_right")) {
			kv.setRightMargin(getPrefFloat("margin_right", 0));
			getWindow().dismiss();
		}
		else if (key.endsWith("margin_bottom")) {
			kv.setBottomMargin(getPrefFloat("margin_bottom", 0));
			getWindow().dismiss();
		}
		else if (key.endsWith("max_keysize")) {
			kv.setMaxKeySize(getPrefFloat("max_keysize", 12));
			getWindow().dismiss();
		}
		else if (key.contentEquals("portrait_only")) {
			getWindow().dismiss();
			forcePortrait = mPrefs.getBoolean("portrait_only", false);
		}*/
	}

	public void keyClicked(View keyview) {
		if (keyview instanceof TextView) {
			TextView tv = (TextView)keyview;
			Log.d(TAG, "keyClicked('" + tv.getText().toString() + "')");
		}
		else {
			Log.d(TAG, "keyClicked(...)");
		}
	}
}

// vim: set ai si sw=8 ts=8 noet:
