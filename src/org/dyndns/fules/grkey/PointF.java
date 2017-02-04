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

    PointF(PointF other) {
        x = other.x;
        y = other.y;
    }

    public final String toString() {
        return "(" + x + ", " + y + ")";
    }

    public float abs2() {
        return x*x + y*y;
    }

    public void add(PointF other) {
        x += other.x;
        y += other.y;
    }

    public void subtract(PointF other) {
        x -= other.x;
        y -= other.y;
    }

    public void multiply(float r) {
        x *= r;
        y *= r;
    }

    public void normalise() {
        float l = (float)Math.sqrt(abs2());
        if (l != 0) {
            x /= l;
            y /= l;
        }
    }

    public void makeLength(float r) {
        float l = (float)Math.sqrt(abs2());
        if (l != 0) {
            x = x * r / l;
            y = y * r / l;
        }
    }

}

// vim: set ts=4 sw=4 et:
