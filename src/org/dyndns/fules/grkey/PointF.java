package org.dyndns.fules.grkey;

class PointF {
	public static final float PI = (float)Math.PI;

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

	public float abs2() {
		return x*x + y*y;
	}
}

