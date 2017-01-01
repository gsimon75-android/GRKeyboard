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
import android.util.AttributeSet;
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

    SparseArray<Key>            keys = new SparseArray<Key>(64);

    int                         currentState;

    class Key {

        class State {

            class Action {
                int                 gesture;
                String              text;
                String              cmd;
                int                 code;

                public Action(XmlResourceParser parser) throws XmlPullParserException, IOException {
                    String s;
                    int resId;

                    // parse and skip opening tag
                    if ((parser.getEventType() != XmlResourceParser.START_TAG) || !parser.getName().contentEquals("Action"))
                        throw new XmlPullParserException("Expected <Action>", parser, null);

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

                    parser.nextTag();

                    // check and skip closing tag
                    if (!parser.getName().contentEquals("Action"))
                        throw new XmlPullParserException("Expected </Action>", parser, null);
                    parser.nextTag();
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

            int                 id;
            SparseArray<Action> actions = new SparseArray<Action>(12);

            public State(XmlResourceParser parser) throws XmlPullParserException, IOException {
                String s;

                // parse and skip opening tag
                if ((parser.getEventType() != XmlResourceParser.START_TAG) || !parser.getName().contentEquals("State"))
                    throw new XmlPullParserException("Expected <State>", parser, null);

                id = res.getInteger(parser.getAttributeResourceValue(null, "id", R.integer.all));

                parser.nextTag();

                // parse contents
                while (parser.getEventType() != XmlResourceParser.END_TAG) {
                    Action action = new Action(parser);
                    actions.put(action.getGesture(), action);
                }

                // check and skip closing tag
                if (!parser.getName().contentEquals("State"))
                    throw new XmlPullParserException("Expected </State>", parser, null);
                parser.nextTag();
            }

            public int getId() {
                return id;
            }

            public Action getAction(int gesture) {
                return actions.get(gesture);
            }
        }

        int                 id;
        State[]             states = new State[res.getInteger(R.integer.shift_state_max)];

        public Key(XmlResourceParser parser) throws XmlPullParserException, IOException {
            String s;

            // parse and skip opening tag
            if ((parser.getEventType() != XmlResourceParser.START_TAG) || !parser.getName().contentEquals("Key"))
                throw new XmlPullParserException("Expected <Key>", parser, null);

            {
                int n = parser.getAttributeCount();
                for (int i = 0; i < n; ++i) {
                    Log.d(TAG, "<Key> attribute[" + i + "] = { ns='" + parser.getAttributeNamespace(i) + "', name='" + parser.getAttributeName(i) + "', value='" + parser.getAttributeValue(i) + "' }");
                }
            }

            /*s = parser.getAttributeValue(NS_ANDROID, "id");
            if (s == null)
                throw new XmlPullParserException("<Key> must have 'id' attribute", parser, null);
            id = Integer.parseInt(s);*/
            id = parser.getAttributeResourceValue(NS_ANDROID, "id", -1);
            Log.d(TAG, "<Key> id=" + id);

            parser.nextTag();

            // parse contents
            while (parser.getEventType() != XmlResourceParser.END_TAG) {
                State state = new State(parser);
                states[state.getId()] = state;
            }

            // check and skip closing tag
            if (!parser.getName().contentEquals("Key"))
                throw new XmlPullParserException("Expected </Key>", parser, null);
            parser.nextTag();
        }

        public int getId() {
            return id;
        }

        public State getState(int state) {
            return states[state];
        }

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


    void parseKeycodes(int resId) throws XmlPullParserException, java.io.IOException {
        String s;
        keys.clear();
        
        XmlResourceParser parser = getResources().getXml(resId);
        while (parser.getEventType() == XmlResourceParser.START_DOCUMENT)
            parser.next();

        // parse and skip opening tag
        if ((parser.getEventType() != XmlResourceParser.START_TAG) || !parser.getName().contentEquals("GRKeyboard"))
            throw new XmlPullParserException("Expected <GRKeyboard>", parser, null);

        s = parser.getAttributeValue(null, "name");
        if (s != null)
            Log.i(TAG, "Loading keyboard '" + s + "'");

        parser.nextTag();

        // parse contents
        while (parser.getEventType() != XmlResourceParser.END_TAG) {
            Key k = new Key(parser);
            keys.put(k.getId(), k);
        }

        // check and skip closing tag
        if (!parser.getName().contentEquals("GRKeyboard"))
            throw new XmlPullParserException("Expected </GRKeyboard>", parser, null);
        //parser.nextTag();
        parser.next();
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

        currentState = res.getInteger(R.integer.normal);

        try {
            parseKeycodes(R.xml.default_latin);
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
        }*/
    }

    public void keyClicked(View keyview, int gestureCode) {
        if (keyview instanceof TextView) {
            TextView tv = (TextView)keyview;
            Log.d(TAG, "keyClicked('" + tv.getText().toString() + "'), code=" + gestureCode);

            // find the key in the key-to-code table
            Key k = keys.get(tv.getId());
            if (k == null) {
                Log.d(TAG, "No conversion rule for this key;");
                return;
            }

            // find the current and the default states for that key
            Key.State currentSt = k.getState(currentState);
            Key.State defaultSt = k.getState(res.getInteger(R.integer.all));
            Key.State.Action a = null;

            // find the most appropriate action
            if (currentSt != null)
                a = currentSt.getAction(gestureCode);

            if ((a == null) && (defaultSt != null))
                a = defaultSt.getAction(gestureCode);

            if ((a == null) && (currentSt != null))
                a = currentSt.getAction(res.getInteger(R.integer.tap));

            if ((a == null) && (defaultSt != null))
                a = defaultSt.getAction(res.getInteger(R.integer.tap));

            if (a == null) {
                Log.d(TAG, "No action for this key;");
                return;
            }

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
