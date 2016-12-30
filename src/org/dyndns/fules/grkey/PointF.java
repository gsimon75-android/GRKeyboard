package org.dyndns.fules.grkey;

class PointF {
	float x, y;

	PointF() {
		this(0, 0);
	}

	PointF(float x, float y) {
		this.x = x;
		this.y = y;
	}

	public final String toString() {
		return "(" + x + ", " + y + ")";
	}
}

