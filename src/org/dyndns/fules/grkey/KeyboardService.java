package org.dyndns.fules.grkey;
import org.dyndns.fules.grkey.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.res.Configuration;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.KeyboardView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.R.id;
import java.lang.EnumConstantNotPresentException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.HashSet;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.inputmethodservice.AbstractInputMethodService;
import android.view.ViewGroup;
import android.view.ViewParent;


public class KeyboardService extends InputMethodService implements SharedPreferences.OnSharedPreferenceChangeListener, KeyMapping.OnActionListener {
	static final String         TAG = "GRKeyboard";
	public static final String  NS_ANDROID = "http://schemas.android.com/apk/res/android";
	
	public static final String      SHARED_PREFS_NAME = "Settings";
	public static final float       DEFAULT_RELATIVE_KEY_HEIGHT = 1.0f;

	public static KeyboardService theKeyboardService = null;
	LayoutInflater              inflater;
	Resources                   res;
	SharedPreferences           mPrefs;

	View                        kv; // the current layout view
	View                        candidatesView;
	LinearLayout                candidatesLL;
	AlertDialog.Builder         helpDialogBuilder;

	int                         lastOrientation = -1;
	float                       portraitKeyHeight = DEFAULT_RELATIVE_KEY_HEIGHT;
	float                       landscapeKeyHeight = DEFAULT_RELATIVE_KEY_HEIGHT;

	ExtractedTextRequest        etreq = new ExtractedTextRequest();
	int                         selectionStart = -1, selectionEnd = -1;

	KeyMapping                  keyMapping;
	int                         currentScript = 0;
	int                         currentShiftState = 0;
	int                         nextShiftState = 0;

	//View.OnTouchListener        gestureRecogniser = new StrokeBasedGestureRecogniser();
	View.OnTouchListener        gestureRecogniser = new SimpleGestureRecogniser();

	public static String nullSafe(String s) {
		return (s == null) ? "<null>" : s;
	}

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
		res = getResources();
		setTheme(R.style.Theme_GRKeyboard);
		inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mPrefs = getSharedPreferences(SHARED_PREFS_NAME, 0);
		mPrefs.registerOnSharedPreferenceChangeListener(this);
		currentShiftState = res.getInteger(R.integer.normal);

		super.onCreate();

		keyMapping = new KeyMapping(this, R.xml.keyboard_mapping);

		helpDialogBuilder = new AlertDialog.Builder(this);
		helpDialogBuilder.setTitle(R.string.possible_gestures);

		Iterator<String> prefKey = mPrefs.getAll().keySet().iterator();
		while (prefKey.hasNext())
			onSharedPreferenceChanged(mPrefs, prefKey.next());

		theKeyboardService = this;
	}

	@Override public AbstractInputMethodService.AbstractInputMethodImpl onCreateInputMethodInterface() {
		etreq.hintMaxChars = etreq.hintMaxLines = 0;
		return super.onCreateInputMethodInterface();
	}

	@Override public void onConfigurationChanged(Configuration newConfig) {
		if (newConfig.orientation != lastOrientation) {
			lastOrientation = newConfig.orientation;
			kv = inflater.inflate(R.layout.keyboard, null);
			setInputView(kv);
		}
	}

	@Override public void onStartInput(EditorInfo attribute, boolean restarting) {
		super.onStartInput(attribute, restarting); 
		//kv.resetState();
		//kv.setInputType(attribute.inputType);
		setCandidatesViewShown(false);
	}

	@Override public View onCreateInputView() {
		DisplayMetrics metrics = new DisplayMetrics();
		metrics.setTo(getResources().getDisplayMetrics());
		//Log.v(TAG, "onCreateInputView; w=" + String.valueOf(metrics.widthPixels) + ", h=" + String.valueOf(metrics.heightPixels));

		Configuration config = getResources().getConfiguration();
		lastOrientation = config.orientation;

		kv = inflater.inflate(R.layout.keyboard, null);
		return kv;
	} 

	@Override public View onCreateCandidatesView() {
		candidatesView = inflater.inflate(R.layout.candidates, null);
		candidatesLL = (LinearLayout)candidatesView.findViewById(R.id.candidatesLL);
		return candidatesView;
	}

	@Override public int getCandidatesHiddenVisibility() {
		return View.GONE;
	}

	@Override public boolean onEvaluateFullscreenMode() {
		return false; // never require fullscreen
	}

	private void sendModifiers(InputConnection ic, int action) {
		if ((currentShiftState & res.getInteger(R.integer.shift)) != 0)
			ic.sendKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_SHIFT_LEFT));
	}

	// Process a generated keycode
	public void onKey(int primaryCode) {
		Log.d(TAG, "onKey(" + primaryCode + ")");
		InputConnection ic = getCurrentInputConnection();
		sendModifiers(ic, KeyEvent.ACTION_DOWN);
		sendDownUpKeyEvents(primaryCode);
		sendModifiers(ic, KeyEvent.ACTION_UP);
	}

	// Process the generated text
	public void onText(CharSequence text) {
		Log.d(TAG, "onText('" + text.toString() + "')");
		InputConnection ic = getCurrentInputConnection();
		int len = text.length();
		for (int i = 0; i < len; ++i) {
			sendModifiers(ic, KeyEvent.ACTION_DOWN);
			sendKeyChar(text.charAt(i));
			sendModifiers(ic, KeyEvent.ACTION_UP);
		}
	} 

	public float getRelativeKeyHeight() {
		return (lastOrientation == Configuration.ORIENTATION_PORTRAIT) ? portraitKeyHeight : landscapeKeyHeight;
	}

	public int getShiftState() {
		return currentShiftState;
	}

	private void setShiftState(int newState) {
		currentShiftState = newState;
		if (kv != null) {
			//kv.invalidate(); // does not redraw all keys, only the one being pressed!
			kv.setVisibility(View.INVISIBLE);
			kv.setVisibility(View.VISIBLE);
		}
	}

	public int getScript() {
		return currentScript;
	}

	private void setScript(int newScript) {
		currentScript = newScript;
		if (kv != null) {
			//kv.invalidate(); // does not redraw all keys, only the one being pressed!
			kv.setVisibility(View.INVISIBLE);
			kv.setVisibility(View.VISIBLE);
		}
	}

	public void onRelease(int primaryCode) {
	} 

	int getPrefInt(String key, int def) {
		// NOTE: EditTextPreference item always stores strings, we must convert manually...
		String s = mPrefs.getString(key, "");
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
		// NOTE: EditTextPreference item always stores strings, we must convert manually...
		String s = mPrefs.getString(key, "");
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
		Log.d(TAG, "onSharedPreferenceChanged(..., '" + key + "')");
		boolean changed = false;
		if (key.equals("portrait_key_height")) {
			float f = getPrefFloat(key, DEFAULT_RELATIVE_KEY_HEIGHT);
			if (portraitKeyHeight != f) {
				changed = true;
				portraitKeyHeight = f;
			}
		}
		else if (key.equals("landscape_key_height")) {
			float f = getPrefFloat(key, DEFAULT_RELATIVE_KEY_HEIGHT);
			if (landscapeKeyHeight != f) {
				changed = true;
				landscapeKeyHeight = f;
			}
		}

		/*if (changed)*/ {
			Dialog d = getWindow();
			if (d != null)
				d.dismiss();
		}
	}

	void showCandidates(final int keyId) {
		final GestureHelp.Adapter ghA = new GestureHelp.Adapter(this, R.layout.candidate_item, R.id.candidateGesture, R.id.candidateText);
		ghA.registerOnActionListener(this);
		//ghA.clear();
		keyMapping.collectHelpForKey(ghA, keyId, currentScript, currentShiftState);
		ghA.sort(ghA.defaultComparator);

		int n = ghA.getCount();
		int oldn = candidatesLL.getChildCount();
		if (oldn > n)
			candidatesLL.removeViews(n, oldn - n);
		for (int i = 0; i < n; i++) {
			View oldv = candidatesLL.getChildAt(i);
			View v = ghA.getView(i, oldv, candidatesLL);
			if (v != oldv) {
				if (oldv != null)
					candidatesLL.removeViewAt(i);
				candidatesLL.addView(v, i);
			}
		}
		candidatesLL.invalidate();
		setCandidatesViewShown(true);
	}

	// Process a command
	public void execCmd(String cmd, int keyId) {
		Log.d(TAG, "execCmd('" + cmd + "')");
		InputConnection ic = getCurrentInputConnection();

		if (!cmd.equals("showGestures"))
			setCandidatesViewShown(false);

		// ---- internal commands
		if (cmd.equals("hide"))
			requestHideSelf(0);
		else if (cmd.equals("switchIM"))
			ic.performContextMenuAction(android.R.id.switchInputMethod);
		else if (cmd.equals("showGestures"))
			showCandidates(keyId);
		// ---- selection commands
		else if (cmd.equals("selectStart")) {
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
		// ---- shift commands
		else if (cmd.equals("normal")) {
			nextShiftState = res.getInteger(R.integer.normal); 
			setShiftState(nextShiftState);
		}
		else if (cmd.equals("caps")) {
			nextShiftState = currentShiftState ^ res.getInteger(R.integer.shift);
			setShiftState(nextShiftState);
		}
		else if (cmd.equals("shift"))
			setShiftState(currentShiftState ^ res.getInteger(R.integer.shift));
		else if (cmd.equals("ctrl"))
			setShiftState(currentShiftState ^ res.getInteger(R.integer.ctrl));
		// ---- script change commands
		else if (cmd.equals("latin"))
			setScript(res.getInteger(R.integer.latin));
		else if (cmd.equals("cyrillic"))
			setScript(res.getInteger(R.integer.cyrillic));
		else if (cmd.equals("greek"))
			setScript(res.getInteger(R.integer.greek));
		else if (cmd.equals("arabic"))
			setScript(res.getInteger(R.integer.arabic));
		// ----
		else
			Log.w(TAG, "Unknown cmd '" + cmd + "'");
	}

	void performAction(KeyMapping.Action a, int keyId) {
		if (a == null) {
		}
		else if (a.getCmd() != null) {
			execCmd(a.getCmd(), keyId);
		}
		else {
			if (a.getCode() >= 0)
				onKey(a.getCode());
			else if (a.getText() != null)
				onText(a.getText());
			else
				Log.d(TAG, "Empty action for this key;");

			if (currentShiftState != nextShiftState)
				setShiftState(nextShiftState);
			setCandidatesViewShown(false);
		}
	}

	public String getLabelForKey(int keyId) {
		KeyMapping.Action a = keyMapping.keyMap.getActionFor(keyId, currentScript, currentShiftState, res.getInteger(R.integer.tap));
		String s = (a != null) ? a.getLabel() : null;
		return (s != null) ? s : "☹";
	}

	public String getAllLabelsForKey(int keyId) {
		StringBuilder sb = new StringBuilder();
		HashSet<String> alreadyListed = new HashSet<String>();
		ArrayList<String> items = new ArrayList<String>();

		GestureHelp.Adapter ghA = new GestureHelp.Adapter(this, -1, -1, -1);
		keyMapping.collectHelpForKey(ghA, keyId, currentScript, currentShiftState);
		ghA.sort(ghA.defaultComparator);

		int n = ghA.getCount();
		for (int i = 0; i < n; ++i) {
			GestureHelp gh = ghA.getItem(i);
			String s = null;

			/*if (gh.action.text != null)
				s = gh.action.text;
			else if (gh.action.code >= 0)
				s = gh.action.label;*/
			if (gh.action.text != null)
				s = gh.action.text;
			else if ((gh.action.cmd == null) || !gh.action.cmd.equals("showGestures"))
				s = gh.action.label;

			if ((s != null) && !alreadyListed.contains(s)) {
				items.add(s);
				alreadyListed.add(s);
			}
		}

		n = items.size();
		int nCols = (int)Math.ceil(Math.sqrt(n));
		//int nRows = (int)Math.ceil((double)n / (double)nCols);
		for (int i = 0; i < n; ++i) {
			if (i == 0) 
				;
			else if ((i % nCols) == 0)
				sb.append('\n');
			else
				sb.append(' ');
			sb.append(items.get(i));
		}
		//Log.d(TAG, "getAllLabelsForKey; n=" + n + ", nRows=" + nRows + ", nCols=" + nCols + ", s='" + sb.toString() + "'");
		return sb.toString();
	}

	public void keyClicked(View keyview, int gestureCode) {
		if (keyview instanceof GRKey) {
			GRKey key = (GRKey)keyview;
			Log.d(TAG, "keyClicked('" + key.getText().toString() + "'), id=" + key.getId() + ", state=" + currentShiftState + ", gesture=" + gestureCode);

			KeyMapping.Action a = keyMapping.keyMap.getActionFor(key.getId(), currentScript, currentShiftState, gestureCode);
			if (a != null)
				performAction(a, key.getId());
		}
	}

	public void showHelpScreen(View v) {
		Intent showHelpScreen = new Intent(Intent.ACTION_MAIN).setClass(this, HelpScreen.class).addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
		startActivity(showHelpScreen);
	}

	public void onActionRequested(KeyMapping.Action a) {
		performAction(a, 0);
	}
}

// vim: set ai si sw=4 ts=4 noet:
