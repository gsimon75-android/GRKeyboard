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
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.inputmethodservice.KeyboardView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.R.id;
import java.io.IOException;
import java.util.Iterator;
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

    View                        kv;                 // the current layout view

    int                         lastOrientation = -1;

    ExtractedTextRequest        etreq = new ExtractedTextRequest();
    int                         selectionStart = -1, selectionEnd = -1;

    KeyMap                      keyMap;

    int                         currentShiftState;

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
            return false;
        }
    }

    class Action extends XmlStructure {
        int                 gesture;
        String              text;
        String              cmd;
        int                 code;

        public Action() {
        }

        protected void parseAttributes(XmlResourceParser parser) throws XmlPullParserException, IOException {
            super.parseAttributes(parser);
            String s;
            int resId;

            resId = parser.getAttributeResourceValue(null, "gesture", -1);
            if (resId >= 0) {
                gesture = res.getInteger(resId);
            }
            else {
                gesture = parser.getAttributeIntValue(null, "gesture", -1);
                if (gesture < 0) 
                    gesture = res.getInteger(R.integer.tap);
            }
            text = parser.getAttributeValue(null, "text");
            cmd = parser.getAttributeValue(null, "cmd");
            code = parser.getAttributeIntValue(null, "code", -1);
            //Log.d(TAG, "Action.parseAttributes; this=" + this + ", gesture=" + gesture + ", code=" + code + ", text='" + nullSafe(text) + "', cmd='" + nullSafe(cmd) +"'");
        }

        public int getGesture() {
            return gesture;
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
    }

    class Key extends State {
        int                 id;
        State[]             states = new State[res.getInteger(R.integer.shift_state_max)];

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
            if (parser.getName().equals("State")) {
                State state = new State();
                //Log.d(TAG, "Key.parseContent; this=" + this + ", state=" + state);
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
    }

    @Override public void onCreate() {
        String err = null;

        Log.d(TAG, "onCreate;");
        res = getResources();
        setTheme(R.style.Theme_GRKeyboard);
        inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPrefs = getSharedPreferences(SHARED_PREFS_NAME, 0);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        super.onCreate();

        currentShiftState = res.getInteger(R.integer.normal);

        try {
            XmlResourceParser parser = getResources().getXml(R.xml.default_latin);
            while (parser.getEventType() == XmlResourceParser.START_DOCUMENT)
                parser.next();
            keyMap = new KeyMap();
            keyMap.parse(parser);
        }
		catch (XmlPullParserException e)	{ err = e.getMessage(); }
		catch (java.io.IOException e)		{ err = e.getMessage(); }

		if (err != null)
            Log.e(TAG, "Cannot load keyboard mapping: " + err);
    }

    @Override public AbstractInputMethodService.AbstractInputMethodImpl onCreateInputMethodInterface() {
        Log.d(TAG, "onCreateInputMethodInterface;");
        etreq.hintMaxChars = etreq.hintMaxLines = 0;
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
        Log.v(TAG, "onCreateInputView; w=" + String.valueOf(metrics.widthPixels) + ", h=" + String.valueOf(metrics.heightPixels));

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
    public void execCmd(String cmd) {
        Log.d(TAG, "execCmd('" + cmd + "')");
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
        else if (cmd.equals("hide"))
            requestHideSelf(0);
        else if (cmd.equals("normal"))
            setShiftState(res.getInteger(R.integer.normal));
        else if (cmd.equals("shift"))
            setShiftState(currentShiftState | res.getInteger(R.integer.shift));
        else if (cmd.equals("arabic"))
            setShiftState(res.getInteger(R.integer.arabic));
        else if (cmd.equals("ctrl"))
            setShiftState(currentShiftState | res.getInteger(R.integer.ctrl));
        else
            Log.w(TAG, "Unknown cmd '" + cmd + "'");
    }

    public int getShiftState() {
        return currentShiftState;
    }

    private void setShiftState(int newState) {
        currentShiftState = newState;
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
        }*/
    }

    public void keyClicked(View keyview, int gestureCode) {
        if (keyview instanceof TextView) {
            TextView tv = (TextView)keyview;
            Log.d(TAG, "keyClicked('" + tv.getText().toString() + "'), id=" + tv.getId() + ", state=" + currentShiftState + ", gesture=" + gestureCode);

            // find the most appropriate action and process it
            Action a = keyMap.getKey(tv.getId()).getState(currentShiftState).getAction(gestureCode);

            /*Key k = keyMap.getKey(tv.getId());
            State st = k.getState(currentShiftState);
            Action a = st.getAction(gestureCode);
            Log.d(TAG, "keyClicked; k=" + k + ", st=" + st + ", a=" + a);*/

            if (a.getCode() >= 0)
                onKey(a.getCode());
            else if (a.getText() != null)
                onText(a.getText());
            else if (a.getCmd() != null)
                execCmd(a.getCmd());
            else
                Log.d(TAG, "Empty action for this key;");

        }
        else {
            Log.d(TAG, "keyClicked(...)");
        }
    }
}

// vim: set ai si sw=4 ts=4 et:
