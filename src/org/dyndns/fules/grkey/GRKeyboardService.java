package org.dyndns.fules.grkey;
import org.dyndns.fules.grkey.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
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
import android.util.SparseArray;
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
import android.widget.TextView;
import android.R.id;
import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.HashSet;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.inputmethodservice.AbstractInputMethodService;
import android.view.ViewGroup;
import android.view.ViewParent;


public class GRKeyboardService extends InputMethodService implements SharedPreferences.OnSharedPreferenceChangeListener  {
	public static final String  SHARED_PREFS_NAME = "GRKeyboardSettings";
	private static final String TAG = "GRKeyboard";

	public static final String  NS_ANDROID = "http://schemas.android.com/apk/res/android";

	LayoutInflater              inflater;
	Resources                   res;
	SharedPreferences           mPrefs;

	View                        kv; // the current layout view
	AlertDialog.Builder         helpDialogBuilder;

	int                         lastOrientation = -1;
	float                       relativeKeyHeight = 0.8f;

	ExtractedTextRequest        etreq = new ExtractedTextRequest();
	int                         selectionStart = -1, selectionEnd = -1;

	KeyMap                      keyMap;
	int                         currentScript = 0;
	int                         currentShiftState = 0;
	int                         nextShiftState = 0;

	GestureRecogniser           gestureRecogniser = new GestureRecogniser(this, 0.6f, 0.8f, 20f);

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


	class XmlStructure {
		String tagName;

		public int skipUntilTag(XmlResourceParser parser) throws XmlPullParserException, IOException {
			int type;
			do {
				type = parser.next();
			} while ((type != XmlResourceParser.START_TAG) && (type != XmlResourceParser.END_TAG) && (type != XmlResourceParser.END_DOCUMENT));
			return type;
		}

		public XmlStructure() {
		}

		public void parse(XmlResourceParser parser) throws XmlPullParserException, IOException {
			// parse and skip opening tag
			if (parser.getEventType() != XmlResourceParser.START_TAG)
				throw new XmlPullParserException("Expected tag start", parser, null);
			tagName = parser.getName();
			parseAttributes(parser);
			skipUntilTag(parser);

			// parse contents
			while (parser.getEventType() == XmlResourceParser.START_TAG) {
				if (!parseContent(parser))
					throw new XmlPullParserException("Tag <" + parser.getName() + "> is not valid here", parser, null);
				skipUntilTag(parser);
			}

			// check closing tag and stay on the closing tag
			if (!parser.getName().contentEquals(tagName))
				throw new XmlPullParserException("Mismatched closing tag; found='" + parser.getName() + "', expected='" + tagName + "'", parser, null);
		}

		// Parse the attributes, but do not modify (eg. step ahead) the parser
		protected void parseAttributes(XmlResourceParser parser) throws XmlPullParserException, IOException {
			/*int n = parser.getAttributeCount();
			for (int i = 0; i < n; ++i) {
			Log.d(TAG, "<" + tagName + "> attribute[" + i + "] = { ns='" + parser.getAttributeNamespace(i) + "', name='" + parser.getAttributeName(i) + "', value='" + parser.getAttributeValue(i) + "' }");
			}*/
		}

		// Parse a content tag, and leave the parser at the closing of this tag
		protected boolean parseContent(XmlResourceParser parser) throws XmlPullParserException, IOException {
			if (parser.getName().equals("include")) {
				int resId = parser.getAttributeResourceValue(null, "id", -1);
				if (resId >= 0) {
					XmlResourceParser np = getResources().getXml(resId);
					while (np.getEventType() == XmlResourceParser.START_DOCUMENT)
						np.next();
					parse(np);
					return true;
				}
			}
			return false;
		}
	}

	class Action extends XmlStructure {
		int     gesture, gestureRev;
		String  text;
		String  cmd;
		String  label;
		int     code;

		public Action() {
		}

		protected void parseAttributes(XmlResourceParser parser) throws XmlPullParserException, IOException {
			super.parseAttributes(parser);
			String s;
			int resId;

			resId = parser.getAttributeResourceValue(null, "gesture", -1);
			gesture = (resId >= 0) ? res.getInteger(resId) : parser.getAttributeIntValue(null, "gesture", -1);
			text = parser.getAttributeValue(null, "text");
			cmd = parser.getAttributeValue(null, "cmd");
			resId = parser.getAttributeResourceValue(null, "label", -1);
			label = (resId >= 0) ? res.getString(resId) : parser.getAttributeValue(null, "label");
			code = parser.getAttributeIntValue(null, "code", -1);
			if ((gesture < 0) && ((text != null) || (cmd != null) || (code >= 0)))
				gesture = res.getInteger(R.integer.tap);
			//Log.d(TAG, "Action.parseAttributes; this=" + this + ", gesture=" + gesture + ", code=" + code + ", text='" + nullSafe(text) + "', cmd='" + nullSafe(cmd) +"'");

			if (gesture < 0) {
				gestureRev = gesture;
			}
			else {
				gestureRev = 0;
				for (int g = gesture; g > 0; g /= 10) 
					gestureRev = (10 * gestureRev) + (g % 10);
			}


		}

		public int getGesture() {
			return gesture;
		}

		public int getGestureRev() {
			return gestureRev;
		}

		public int getCode() {
			return code;
		}

		public String getText() {
			return text;
		}

		public String getCmd() {
			return cmd;
		}

		public String getLabel() {
			if (label != null)
				return label;

			if (text != null)
				return text;

			if (cmd != null)
				return cmd; // TODO: localise

			// NOTE: actions with 'code=' shall also have 'label='
			return "<???>";
		}

		public String toString() {
			StringBuilder sb = new StringBuilder("Action(gesture=");
			sb.append(gesture);

			sb.append(", code=");
			sb.append(code);


			if (text != null) {
				sb.append(", text=");
				sb.append(text);
			}

			if (label != null) {
				sb.append(", label=");
				sb.append(label);
			}

			if (cmd != null) {
				sb.append(", cmd=");
				sb.append(cmd);
			}

			return sb.append(")").toString();
		}

		public void collectHelp(GestureHelpAdapter ghA) {
			if (gesture >= 0) {
				ghA.add(new GestureHelp(this, getLabel()));
			}
		}

	}

	class State extends Action {
		int                 mod;
		SparseArray<Action> actions = new SparseArray<Action>(12);

		public State() {
		}

		protected void parseAttributes(XmlResourceParser parser) throws XmlPullParserException, IOException {
			super.parseAttributes(parser);
			mod = res.getInteger(parser.getAttributeResourceValue(null, "mod", R.integer.normal));
			//Log.d(TAG, "State.parseAttributes; this=" + this + ", mod=" + mod);
		}

		protected boolean parseContent(XmlResourceParser parser) throws XmlPullParserException, IOException {
			if (super.parseContent(parser))
				return true;
			if (parser.getName().equals("Action")) {
				Action action = new Action();
				//Log.d(TAG, "State.parseContent; this=" + this + ", action=" + action);
				action.parse(parser);
				actions.put(action.getGesture(), action);
				return true;
			}
			return false;
		}

		public int getMod() {
			return mod;
		}

		public Action getAction(int gesture) {
			Action action = actions.get(gesture);
			//Log.d(TAG, "State; lookup gesture=" + gesture + ", action=" + action);
			return (action != null) ? action : this;
		}

		public void collectHelp(GestureHelpAdapter ghA) {
			int n = actions.size();
			for (int i = 0; i < n; ++i)
				actions.valueAt(i).collectHelp(ghA);
			if (actions.get(gesture) == null)
				super.collectHelp(ghA);
		}

	}

	class Script extends State {
		int         id;
		State[]     states = new State[res.getInteger(R.integer.shift_state_max)];

		public Script() {
		}

		protected void parseAttributes(XmlResourceParser parser) throws XmlPullParserException, IOException {
			super.parseAttributes(parser);
			id = res.getInteger(parser.getAttributeResourceValue(null, "id", R.integer.latin));
			//Log.d(TAG, "Script.parseAttributes; this=" + this + ", mod=" + mod);
		}

		protected boolean parseContent(XmlResourceParser parser) throws XmlPullParserException, IOException {
			if (super.parseContent(parser))
				return true;
			if (parser.getName().equals("State")) {
				State state = new State();
				//Log.d(TAG, "Script.parseContent; this=" + this + ", state=" + state);
				state.parse(parser);
				states[state.getMod()] = state;
				return true;
			}
			return false;
		}

		public int getId() {
			return id;
		}

		public State getState(int mod) {
			State state = states[mod];
			//Log.d(TAG, "Key; lookup mod=" + mod + ", state=" + state);
			return (state != null) ? state : this;
		}

		public void collectHelp(GestureHelpAdapter ghA) {
			State state = states[currentShiftState];
			if (state != null)
				state.collectHelp(ghA);
			else
				super.collectHelp(ghA);
		}
	}

	class Key extends Script {
		int         id;
		Script[]    scripts = new Script[res.getInteger(R.integer.script_max)];

		public Key() {
		}

		protected void parseAttributes(XmlResourceParser parser) throws XmlPullParserException, IOException {
			super.parseAttributes(parser);
			id = parser.getAttributeResourceValue(null, "id", -1);
			//Log.d(TAG, "Key.parseAttributes; this=" + this + ", id=" + id);
		}

		protected boolean parseContent(XmlResourceParser parser) throws XmlPullParserException, IOException {
			if (super.parseContent(parser))
				return true;
			if (parser.getName().equals("Script")) {
				Script script = new Script();
				//Log.d(TAG, "Key.parseContent; this=" + this + ", state=" + state);
				script.parse(parser);
				scripts[script.getId()] = script;
				return true;
			}
			return false;
		}

		public int getId() {
			return id;
		}

		public Script getScript(int id) {
			Script script = scripts[id];
			//Log.d(TAG, "Key; lookup id=" + id + ", script=" + script);
			return (script != null) ? script : this;
		}

		public void collectHelp(GestureHelpAdapter ghA) {
			Script script = scripts[currentScript];
			if (script != null)
				script.collectHelp(ghA);
			else
				super.collectHelp(ghA);
		}
	}

	class KeyMap extends Key {
		SparseArray<Key>    keys = new SparseArray<Key>(64);

		public KeyMap() {
		}

		public void parse(XmlResourceParser parser) throws XmlPullParserException, IOException {
			super.parse(parser);
			if (!tagName.contentEquals("KeyMap"))
				throw new XmlPullParserException("Expected <KeyMap>", parser, null);
		}

		protected void parseAttributes(XmlResourceParser parser) throws XmlPullParserException, IOException {
			super.parseAttributes(parser);
			String s = parser.getAttributeValue(null, "name");
			//Log.d(TAG, "KeyMap.parseAttributes; this=" + this + ", name='" + nullSafe(s) + "'");
		}

		protected boolean parseContent(XmlResourceParser parser) throws XmlPullParserException, IOException {
			if (super.parseContent(parser))
				return true;
			if (parser.getName().equals("Key")) {
				Key key = new Key();
				//Log.d(TAG, "KeyMap.parseContent; this=" + this + ", key=" + key);
				key.parse(parser);
				keys.put(key.getId(), key);
				return true;
			}
			return false;
		}

		public Key getKey(int id) {
			Key key = keys.get(id);
			//Log.d(TAG, "KeyMap; lookup id=" + id + ", key=" + key);
			return (key != null) ? key : this;
		}

		// NOTE: intentionally doesn't override collectHelp(GestureHelpAdapter ghA)
	}

	@Override public void onCreate() {
		String err = null;

		res = getResources();
		setTheme(R.style.Theme_GRKeyboard);
		inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mPrefs = getSharedPreferences(SHARED_PREFS_NAME, 0);
		mPrefs.registerOnSharedPreferenceChangeListener(this);
		currentShiftState = res.getInteger(R.integer.normal);

		super.onCreate();

		try {
			XmlResourceParser parser = getResources().getXml(R.xml.keyboard_mapping);
			while (parser.getEventType() == XmlResourceParser.START_DOCUMENT)
				parser.next();
			keyMap = new KeyMap();
			keyMap.parse(parser);
		}
		catch (XmlPullParserException e)    { err = e.getMessage(); }
		catch (java.io.IOException e)       { err = e.getMessage(); }

		if (err != null)
			Log.e(TAG, "Cannot load keyboard mapping: " + err);

		helpDialogBuilder = new AlertDialog.Builder(this);
		helpDialogBuilder.setTitle(R.string.possible_gestures);

		Iterator<String> prefKey = mPrefs.getAll().keySet().iterator();
		while (prefKey.hasNext())
			onSharedPreferenceChanged(mPrefs, prefKey.next());
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

	// Process a command
	public void execCmd(String cmd, int keyId) {
		Log.d(TAG, "execCmd('" + cmd + "')");
		InputConnection ic = getCurrentInputConnection();

		// ---- internal commands
		if (cmd.equals("hide"))
			requestHideSelf(0);
		else if (cmd.equals("switchIM"))
			ic.performContextMenuAction(android.R.id.switchInputMethod);
		else if (cmd.equals("showGestures"))
			showGestures(keyId);
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

	public float getRelativeKeyHeight() {
		return relativeKeyHeight;
	}

	public int getShiftState() {
		return currentShiftState;
	}

	private void setShiftState(int newState) {
		currentShiftState = newState;
		if (kv != null)
			kv.invalidate();
	}

	public int getScript() {
		return currentScript;
	}

	private void setScript(int newScript) {
		currentScript = newScript;
		if (kv != null)
			kv.invalidate();
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
		Log.d(TAG, "onSharedPreferenceChanged(..., '" + key + "')");
	}

	public Action getActionForKey(int keyId, int script, int shiftState, int gestureCode) {
		return keyMap.getKey(keyId).getScript(script).getState(shiftState).getAction(gestureCode);
	}

	public String getLabelForKey(int keyId) {
		Action a;
		String s;

		a = getActionForKey(keyId, currentScript, currentShiftState, 0);
		s = a.getLabel();
		if (s != null)
			return s;

		a = getActionForKey(keyId, currentScript, 0, 0);
		s = a.getLabel();
		if (s != null)
			return s;

		a = getActionForKey(keyId, 0, currentShiftState, 0);
		s = a.getLabel();
		if (s != null)
			return s;

		a = getActionForKey(keyId, 0, 0, 0);
		s = a.getLabel();
		if (s != null)
			return s;

		return "☹";
	}

	class GestureHelp {
		Action action;
		String text;

		GestureHelp(Action action, String text) {
			this.action = action;
			this.text = text;
		}

	}

	class GestureHelpAdapter extends ArrayAdapter<GestureHelp> {
		static final int layoutId = R.layout.gesture_help;
		static final int gestureViewId = R.id.gestureView;
		static final int gestureTextId = R.id.gestureText;
		HashSet<Integer> gestures = new HashSet<Integer>();

		public Comparator<GestureHelp> defaultComparator = new Comparator<GestureHelp>() {
			public int compare(GestureHelp lhs, GestureHelp rhs) {
				int gl = lhs.action.getGestureRev();
				int gr = rhs.action.getGestureRev();
				// handle special cases: tap, longtap
				if ((gl == 0) || (gr == 5))
					return -1;
				if ((gr == 0) || (gl == 5))
					return 1;

				while ((gl > 0) && (gr > 0)) {
					if ((gl % 10) < (gr % 10))
						return -1;
					if ((gl % 10) > (gr % 10))
						return 1;
					gl /= 10;
					gr /= 10;
				}
				if (gl < gr)
					return -1;
				if (gl > gr)
					return 1;
				return 0;
			}
		};

		class ViewHolder {
			GestureView gestureView;
			TextView gestureText;
		}

		public GestureHelpAdapter(Context context) {
			super(context, layoutId);
		}

		public void clear() {
			super.clear();
			gestures.clear();
		}

		public boolean containsGesture(int g) {
			return gestures.contains(g);
		}

		public void add(GestureHelp gh) {
			if (!gestures.contains(gh.action.gesture)) {
				super.add(gh);
				gestures.add(gh.action.gesture);
			}
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;

			if (convertView == null) {
				convertView = getLayoutInflater().inflate(layoutId, null, false);
				holder = new ViewHolder();
				holder.gestureView = (GestureView)convertView.findViewById(gestureViewId);
				holder.gestureText = (TextView)convertView.findViewById(gestureTextId);
				convertView.setTag(holder);
			}
			else {
				holder = (ViewHolder)convertView.getTag();
			}

			GestureHelp item = getItem(position);
			if ((item != null) && (holder != null)) {
				holder.gestureView.setGesture(item.action.gesture);
				holder.gestureText.setText(item.text);
			}
			return convertView;
		}
	}

	void showGestures(final int keyId) {
		final GestureHelpAdapter ghA = new GestureHelpAdapter(this);
		ghA.clear();

		Key key = keyMap.getKey(keyId);
		key.collectHelp(ghA);
		if (key != keyMap)
			keyMap.collectHelp(ghA);

		ghA.sort(ghA.defaultComparator);

		helpDialogBuilder.setAdapter(ghA,
			new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					performAction(ghA.getItem(which).action, keyId);
				}
			}
		);
		AlertDialog dialog = helpDialogBuilder.create();
		dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
		dialog.show();
	}

	void performAction(Action a, int keyId) {
		if (a.getCmd() != null)
			execCmd(a.getCmd(), keyId);
		else {
			if (a.getCode() >= 0)
				onKey(a.getCode());
			else if (a.getText() != null)
				onText(a.getText());
			else
				Log.d(TAG, "Empty action for this key;");

			if (currentShiftState != nextShiftState)
				setShiftState(nextShiftState);
		}
	}

	public void keyClicked(View keyview, int gestureCode) {
		if (keyview instanceof GRKey) {
			GRKey key = (GRKey)keyview;
			Log.d(TAG, "keyClicked('" + key.getText().toString() + "'), id=" + key.getId() + ", state=" + currentShiftState + ", gesture=" + gestureCode);

			if (gestureCode == 5) { // FIXME: propagation is buggy, hardwire it here for testing
				execCmd("showGestures", key.getId());
			}
			else {
				Action a = getActionForKey(key.getId(), currentScript, currentShiftState, gestureCode);
				performAction(a, key.getId());
			}
		}
	}
}

// vim: set ai si sw=4 ts=4 noet:
