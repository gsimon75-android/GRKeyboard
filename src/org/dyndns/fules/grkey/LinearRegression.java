package org.dyndns.fules.grkey;

import android.util.Log;
import java.util.Vector;
import java.util.Iterator;

public class LinearRegression {

	public static final float PI = (float)Math.PI;
	Vector<PointF>	input = new Vector<PointF>();

	float		qualityThreshold;
	PointF		center = new PointF();
	PointF		sum = new PointF();
	float		angle = 0;
	float		quality = 0;
	PointF		deviation = new PointF();

	public LinearRegression(float qualityThreshold) {
		this.qualityThreshold = qualityThreshold;
		clear();
	}

	public void clear() {
		input.clear();
		center.x = center.y = 0;
		sum.x = sum.y = 0;
		angle = quality = 0;
		deviation.x = deviation.y = 0;
	}

	public void clearButLast() {
		PointF last = null;
		if (!input.isEmpty())
			last = input.lastElement();
		clear();
		if (last != null)
			add(last);
	}

	public boolean add(float x, float y) {
		return add(new PointF(x, y));
	}
	public boolean add(PointF p) {
		int n = input.size();

		// check if we try to 'go backwards'
		// FIXME: this does not belong here
		if (n >= 2) {
			PointF pN1 = input.get(n - 1);
			PointF pN2 = input.get(n - 2);

			// 'Backwards' means that the proposed new vector (p-pN1) forms
			// an acute angle with the (back-pointing) last vector (pN2-pN1),
			// that is, the cosine of this angle is positive, which means
			// the scalar product is positive.
			if (((p.x - pN1.x)*(pN2.x - pN1.x) + (p.y - pN1.y)*(pN2.y - pN1.y)) > 0)
				return false;
		}

		// NOTE: we do not yet add the item to the input series, but calculate 
		// the momenta as if it were added

		// calculate the (would-be) center-of-mass
		float cx = (sum.x + p.x) / (n + 1);
		float cy = (sum.y + p.y) / (n + 1);

		// calculate the (would be) 2nd-order momenta
		float dx = p.x - cx;
		float dy = p.y - cy;
		float xx = dx*dx, yy = dy*dy, xy = dx*dy;
		for (PointF pp : input) {
			dx = pp.x - cx;
			dy = pp.y - cy;

			xx += dx*dx;
			yy += dy*dy;
			xy += dx*dy;
		}

		float d = (float)Math.sqrt(4*xy*xy + ((yy - xx)*(yy - xx)));
		/* NOTE: the total squared deviation would be:
		   	Hx = (xx + yy + d) / 2
			Hy = (xx + yy - d) / 2
		From this we can derive a 'quality' factor that spans [0..1],
		0 meaning totally incoherent data, 1 meaning strict linear dependence. */
		if (n == 0)
			quality = 1;
		else if (((xx + yy) <= 0) || ((xx == yy) && (xy == 0))) // all points are identical
			quality = 0;
		else
			quality = d / (xx + yy);

		if (quality < qualityThreshold) {
			// quality would drop too much, return without touching the data
			return false;
		}

		// quality stays ok, so add the new item to the input series
		input.addElement(p);
		sum.x += p.x;
		sum.y += p.y;
		center.x = cx;
		center.y = cy;

		// calculate the angle of the series
		if (xx != yy) {
			angle = -(float)Math.atan(2*xy / (yy - xx)) / 2;
			/* NOTE: atan() has a periodicity of PI, so atan()/2 has PI/2, so
			   although in the -PI/4..PI/4 region we get the correct result,
			   outside of it we get an angle perpendicular to the correct one.

			   In addition to this, we get the angle of the series without
			   considering the direction of it, so it must also be 'turned
			   backwards' if needed */

			if (yy > xx) {
				// the correct angle is perpendicular to 'angle', and
				// the direction must also be considered
				angle += (p.y > input.firstElement().y) ? PI / 2 : - PI / 2;
			}
			else if (p.x < input.firstElement().x) {
				// 'angle' is correct, but it must be 'turned backwards'
				angle += (angle < 0) ? PI : - PI;
			}
		}
		else if (xy > 0) {
			angle = (p.x > input.firstElement().x) ? PI / 4 : -3 * PI / 4;
		}
		else if (xy < 0) {
			angle = (p.x > input.firstElement().x) ? - PI / 4 : 3 * PI / 4;
		}

		// calculate the squared deviation (x means trend-wise span, y is perpendicular to it)
		deviation.x = (float)Math.sqrt((xx + yy + d) / 2);
		deviation.y = (float)Math.sqrt((xx + yy - d) / 2);

		return true;
	}

	public float getAngle() { // NOTE: returns [-PI .. PI)
		return angle;
	}

	public float getQuality() {
		return quality;
	}

	public PointF getDeviation() {
		return deviation;
	}

	public PointF getCenter() {
		return center;
	}

}

// vim: set ai si sw=8 ts=8 noet:
