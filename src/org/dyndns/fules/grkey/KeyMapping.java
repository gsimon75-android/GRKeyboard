package org.dyndns.fules.grkey;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;
import android.util.SparseArray;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParserException;

public class KeyMapping {
	static final String         TAG = "GRKeyboard";
	Context                     context;
	Resources                   res;

	public static interface OnActionListener {
		abstract void onActionRequested(Action a);
	}

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
					XmlResourceParser np = context.getResources().getXml(resId);
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

		public String DisabledtoString() {
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

		public void collectHelp(GestureHelp.Adapter ghA) {
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
			return action;
		}

		public void collectHelp(GestureHelp.Adapter ghA) {
			int n = actions.size();
			for (int i = 0; i < n; ++i)
				actions.valueAt(i).collectHelp(ghA);
			super.collectHelp(ghA);
		}

		public Action getActionFor(int gestureCode) {
			Action a = getAction(gestureCode);
			if (a != null)
				return a;
			if (getGesture() == gestureCode)
				return this;
			return null;
		}

	}

	class Script extends State {
		int         id;
		State[]     states = new State[res.getInteger(R.integer.shift_state_max)];

		public Script() {
		}

		protected void parseAttributes(XmlResourceParser parser) throws XmlPullParserException, IOException {
			super.parseAttributes(parser);
			id = parser.getAttributeResourceValue(null, "id", -1);
			if (id >= 0)
				id = res.getInteger(id);
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
			//Log.d(TAG, "Script; lookup mod=" + mod + ", state=" + state);
			return state;
		}

		public void collectHelp(GestureHelp.Adapter ghA, int currentState) {
			State state = states[currentState];
			if (state != null)
				state.collectHelp(ghA);
			super.collectHelp(ghA);
		}

		public Action getActionFor(int shiftState, int gestureCode) {
			State st = getState(shiftState);
			if (st != null) {
				Action a = st.getActionFor(gestureCode);
				if (a != null)
					return a;
			}
			return super.getActionFor(gestureCode);
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
			return script;
		}

		public void collectHelp(GestureHelp.Adapter ghA, int currentScript, int currentState) {
			Script script = scripts[currentScript];
			if (script != null)
				script.collectHelp(ghA, currentState);
			super.collectHelp(ghA, currentState);
		}

		public Action getActionFor(int script, int shiftState, int gestureCode) {
			Script sc = getScript(script);
			if (sc != null) {
				Action a = sc.getActionFor(shiftState, gestureCode);
				if (a != null)
					return a;
			}
			return super.getActionFor(shiftState, gestureCode);
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
			return key;
		}

		public Action getActionFor(int keyId, int script, int shiftState, int gestureCode) {
			Key k = getKey(keyId);
			if (k != null) {
				Action a = k.getActionFor(script, shiftState, gestureCode);
				if (a != null)
					return a;
			}
			return super.getActionFor(script, shiftState, gestureCode);
		}

		// NOTE: intentionally doesn't override collectHelp(GestureHelp.Adapter ghA)
	}

	KeyMap        keyMap;

	public KeyMapping(Context context, int resId) {
		this.context = context;
		res = context.getResources();

		String err = null;
		try {
			XmlResourceParser parser = context.getResources().getXml(resId);
			while (parser.getEventType() == XmlResourceParser.START_DOCUMENT)
				parser.next();
			keyMap = new KeyMap();
			keyMap.parse(parser);
		}
		catch (XmlPullParserException e)    { err = e.getMessage(); }
		catch (java.io.IOException e)       { err = e.getMessage(); }

		if (err != null)
			Log.e(TAG, "Cannot load keyboard mapping: " + err);
	}

	void collectHelpForKey(GestureHelp.Adapter ghA, int keyId, int currentScript, int currentState) {
		Key key = keyMap.getKey(keyId);
		if (key != null)
			key.collectHelp(ghA, currentScript, currentState);
		if (key != keyMap)
			keyMap.collectHelp(ghA, currentScript, currentState);
	}

}
// vim: set ai si sw=4 ts=4 noet:
