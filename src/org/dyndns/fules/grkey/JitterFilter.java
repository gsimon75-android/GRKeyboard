package org.dyndns.fules.grkey;

import android.util.Log;
import java.util.Vector;
import java.util.Iterator;

public class JitterFilter {
	PointF		last;
	float		jitterLimit2, currentLimit2;
	Vector<PointF>	input = new Vector<PointF>();

	public JitterFilter() {
		this(4.0f);
	}

	public JitterFilter(float limit) {
		jitterLimit2 = limit*limit;
		clear();
	}

	public void clear() {
		input.clear();
		currentLimit2 = jitterLimit2 * 4.0f/9.0f;
	}

	public void stop() {
		last = new PointF();
		int n = input.size();
		if (n > 0) {
			for (PointF p : input) {
				last.x += p.x;
				last.y += p.y;
			}
			last.x /= n;
			last.y /= n;
			input.clear();
		}
	}

	public boolean add(float x, float y) {
		return add(new PointF(x, y));
	}

	public boolean add(PointF p) {
		if (input.isEmpty()) {
			last = p;
			input.addElement(p);
			return false;
		}

		float dx = p.x - last.x;
		float dy = p.y - last.y;
		if ((dx*dx + dy*dy) < currentLimit2) {
			input.addElement(p);
			return false;
		}

		stop();
		currentLimit2 = jitterLimit2;
		input.addElement(p);
		return true;
	}

	public PointF getLast() {
		return last;
	}

	public float getLastX() {
		return last.x;
	}

	public float getLastY() {
		return last.y;
	}

}

// vim: set ai si sw=8 ts=8 noet:
