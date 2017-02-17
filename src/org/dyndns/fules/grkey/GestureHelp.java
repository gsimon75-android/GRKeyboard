package org.dyndns.fules.grkey;
import org.dyndns.fules.grkey.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.util.Comparator;
import java.util.HashSet;

class GestureHelp {
	KeyMapping.Action action;
	String text;

	GestureHelp(KeyMapping.Action action, String text) {
		this.action = action;
		this.text = text;
	}

	static class Adapter extends ArrayAdapter<GestureHelp> {
		static final int layoutId = R.layout.gesture_help;
		static final int gestureViewId = R.id.gestureView;
		static final int gestureTextId = R.id.gestureText;
		HashSet<Integer> gestures = new HashSet<Integer>();
		final LayoutInflater inflater;

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

		public Adapter(Context context) {
			super(context, layoutId);
			inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
				convertView = inflater.inflate(layoutId, null, false);
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

}

// vim: set ai si sw=4 ts=4 noet:
