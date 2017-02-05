package org.dyndns.fules.grkey;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class GestureView extends View {
    // absolute and relative attributes
    float outlineWidth, outlinePct;
    float lineWidth, linePct;
    float circleRadius, circlePct;
    float arrowLength, arrowPct;
    float arrowCos, arrowSin;

    // paints for line and outline, stroke and fill
    Paint paintOutlineStroke, paintLineStroke;
    Paint paintOutlineFill, paintLineFill;

    // the gesture to display
    int gesture;

    // cached coordinates
    PointF[] points;
    PointF arrow1, arrow2;

    public GestureView(Context context) {
        this(context, null);
    }

    public GestureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        int res;
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GestureView);

        // gesture
        gesture = a.getInteger(R.styleable.GestureView_gesture, 0);

        // stroke colour
		res = a.getResourceId(R.styleable.GestureView_foreground, -1);
        int lineColour = (res != -1) ? getResources().getColor(res) : a.getInteger(R.styleable.GestureView_foreground, Color.RED);

        // outline colour
		res = a.getResourceId(R.styleable.GestureView_outline, -1);
        int outlineColour = (res != -1) ? getResources().getColor(res) : a.getInteger(R.styleable.GestureView_outline, Color.YELLOW);

        // -- absolute-relative logic for circleRadius
        outlineWidth = a.getDimension(R.styleable.GestureView_outlineWidth, 0);
        if (outlineWidth <= 0) {
            outlinePct = a.getFloat(R.styleable.GestureView_outlinePct, 0);
            if (outlinePct > 0) // meaningful relative -> use it
                outlinePct /= 100.0f;
            else
                outlineWidth = 6.0f;
        }

        // -- absolute-relative logic for circleRadius
        circleRadius = a.getDimension(R.styleable.GestureView_circleRadius, 0);
        if (circleRadius <= 0) {
            circlePct = a.getFloat(R.styleable.GestureView_circlePct, 0);
            if (circlePct > 0) // meaningful relative -> use it
                circlePct /= 100.0f;
            else
                circleRadius = 9.0f;
        }

        // -- absolute-relative logic for lineWidth
        lineWidth = a.getDimension(R.styleable.GestureView_lineWidth, 0);
        if (lineWidth <= 0) {
            linePct = a.getFloat(R.styleable.GestureView_linePct, 0);
            if (linePct > 0) // meaningful relative -> use it
                linePct /= 100.0f;
            else
                lineWidth = 6.0f;
        }

        // -- absolute-relative logic for arrowLength
        arrowLength = a.getDimension(R.styleable.GestureView_arrowLength, 0);
        if (arrowLength <= 0) {
            arrowPct = a.getFloat(R.styleable.GestureView_arrowPct, 0);
            if (arrowPct > 0) // meaningful relative -> use it
                arrowPct /= 100.0f;
            else
                arrowLength = 20.0f;
        }

        float arrowDegrees = a.getFloat(R.styleable.GestureView_arrowDegrees, 25) * PointF.PI / 180.0f;
        arrowCos = (float)Math.cos(arrowDegrees);
        arrowSin = (float)Math.sin(arrowDegrees);

        paintLineStroke = new Paint();
        paintLineStroke.setColor(lineColour);
        paintLineStroke.setStyle(Paint.Style.STROKE);

        paintOutlineStroke = new Paint();
        paintOutlineStroke.setColor(outlineColour);
        paintOutlineStroke.setStyle(Paint.Style.STROKE);

        paintLineFill = new Paint();
        paintLineFill.setColor(lineColour);
        paintLineFill.setStyle(Paint.Style.FILL);

        paintOutlineFill = new Paint();
        paintOutlineFill.setColor(outlineColour);
        paintOutlineFill.setStyle(Paint.Style.FILL);
    }

    int getGesture() {
        return gesture;
    }

    void setGesture(int g) {
        if (g < 0)
            g = 0;

        if (gesture != g) {
            gesture = g;
            points = null;
            invalidate();
        }
    }

    void recalculatePoints() {
        float drift = 0.1f;
        int gesturesLength = 0;
        int gestureRev = 0;
        for (int g = gesture; g > 0; g /= 10) {
            if ((g % 10) == 5)
                continue;
            gesturesLength++;
            gestureRev = (10 * gestureRev) + (g % 10);
        }

        points = new PointF[1 + gesturesLength];

        points[0] = new PointF(0, 0);
        PointF pMin = new PointF(points[0]);
        PointF pMax = new PointF(points[0]);
        int lastGesture = 10;
        for (int i = 0; i < gesturesLength; ++i) {
            int g = gestureRev % 10;
            float dx = 0, dy = 0;
            switch (g) {
                case 1: dx = -1; dy = +1; break;
                case 2: dx =  0; dy = +1; break;
                case 3: dx = +1; dy = +1; break;
                case 4: dx = -1; dy =  0; break;
                case 6: dx = +1; dy =  0; break;
                case 7: dx = -1; dy = -1; break;
                case 8: dx =  0; dy = -1; break;
                case 9: dx = +1; dy = -1; break;
            }
            if (lastGesture == (10 - g)) {
                float q = dx * 0.1f;
                dx += dy*0.1f;
                dy -= q;
            }
            lastGesture = g;
            if ((dx != 0) || (dy != 0))
                points[i+1] = new PointF(points[i].x + dx, points[i].y + dy);
            if (pMin.x > points[i+1].x) pMin.x = points[i+1].x;
            if (pMin.y > points[i+1].y) pMin.y = points[i+1].y;
            if (pMax.x < points[i+1].x) pMax.x = points[i+1].x;
            if (pMax.y < points[i+1].y) pMax.y = points[i+1].y;
            gestureRev /= 10;
        }
        pMax.subtract(pMin);

        PointF size = new PointF(getWidth() - getPaddingLeft() - getPaddingRight(), getHeight() - getPaddingTop() - getPaddingBottom());

        float qHorizontal = (pMax.x > 0) ? (size.x / pMax.x) : size.x;
        float qVertical = (pMax.y > 0) ? (size.y / pMax.y) : size.y;
        float q = (qHorizontal < qVertical) ? qHorizontal : qVertical;

        if (outlinePct != 0)
            outlineWidth = q * outlinePct;
        if (linePct != 0)
            lineWidth = q * linePct;
        if (circlePct != 0)
            circleRadius = q * circlePct;
        if (arrowPct != 0)
            arrowLength = q * arrowPct;

        PointF paddingLT = new PointF(getPaddingLeft() + (size.x - q*pMax.x)/2,
                                      getPaddingTop() + (size.y - q*pMax.y)/2);

        for (PointF p : points) {
            p.subtract(pMin);
            p.multiply(q);
            p.add(paddingLT);
        }

        if (gesturesLength > 0) {
            arrow1 = new PointF(points[gesturesLength-1]);
            arrow1.subtract(points[gesturesLength]);
            arrow1.makeLength(arrowLength);
            arrow2 = new PointF(arrow1);
            { // rotate by +arrowDegrees
                float t  =   arrow1.x * arrowCos - arrow1.y * arrowSin;
                arrow1.y =   arrow1.x * arrowSin + arrow1.y * arrowCos;
                arrow1.x = t;
            }
            { // rotate by -arrowDegrees
                float t  =   arrow2.x * arrowCos + arrow2.y * arrowSin;
                arrow2.y = - arrow2.x * arrowSin + arrow2.y * arrowCos;
                arrow2.x = t;
            }
            arrow1.add(points[gesturesLength]);
            arrow2.add(points[gesturesLength]);
        }
        else {
            arrow1 = null;
            arrow2 = null;
        }

        // update the stroke widths
        paintLineStroke.setStrokeWidth(lineWidth);
        paintOutlineStroke.setStrokeWidth(lineWidth + 2*outlineWidth);
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if ((w != oldw) || (h != oldh))
            recalculatePoints();
    }

    public void onDraw(Canvas canvas) {
        if (points == null)
            recalculatePoints();

        int n = points.length;
        float circleR = circleRadius * ((gesture == 5) ? 2 : 1);

        // start circle outline
        canvas.drawCircle(points[0].x, points[0].y, circleR + outlineWidth, paintOutlineFill);

        if (n > 2) {
            // 1st line segment outline and stroke
            canvas.drawLine(points[0].x, points[0].y, points[1].x, points[1].y, paintOutlineStroke);
            canvas.drawLine(points[0].x, points[0].y, points[1].x, points[1].y, paintLineStroke);
            // start circle stroke
            canvas.drawCircle(points[0].x, points[0].y, circleR, paintLineFill);
        }

        // subsequent line segments: joining circle, outline, stroke
        for (int i = 2; i < n-1; ++i) {
            canvas.drawCircle(points[i-1].x, points[i-1].y, (lineWidth / 2) + outlineWidth, paintOutlineFill);
            canvas.drawLine(points[i-1].x, points[i-1].y, points[i].x, points[i].y, paintOutlineStroke);
            canvas.drawCircle(points[i-1].x, points[i-1].y, lineWidth / 2, paintLineFill);
            canvas.drawLine(points[i-1].x, points[i-1].y, points[i].x, points[i].y, paintLineStroke);
        }

        if (n > 1) {
            // end arrow outline
            canvas.drawLine(points[n-1].x, points[n-1].y, arrow1.x, arrow1.y, paintOutlineStroke);
            canvas.drawLine(points[n-1].x, points[n-1].y, arrow2.x, arrow2.y, paintOutlineStroke);
            canvas.drawCircle(arrow1.x, arrow1.y, (lineWidth / 2) + outlineWidth, paintOutlineFill);
            canvas.drawCircle(arrow2.x, arrow2.y, (lineWidth / 2) + outlineWidth, paintOutlineFill);
            canvas.drawCircle(points[n-1].x, points[n-1].y, (lineWidth / 2) + outlineWidth, paintOutlineFill);

            // last line segment outline and stroke
            canvas.drawCircle(points[n-2].x, points[n-2].y, (lineWidth / 2) + outlineWidth, paintOutlineFill);
            canvas.drawLine(points[n-2].x, points[n-2].y, points[n-1].x, points[n-1].y, paintOutlineStroke);
            canvas.drawCircle(points[n-2].x, points[n-2].y, lineWidth / 2, paintLineFill);
            canvas.drawLine(points[n-2].x, points[n-2].y, points[n-1].x, points[n-1].y, paintLineStroke);
        }

        if (n <= 2)
            // start circle stroke
            canvas.drawCircle(points[0].x, points[0].y, circleR, paintLineFill);

        if (n > 1) {
            // end arrow strokes and tip
            canvas.drawLine(points[n-1].x, points[n-1].y, arrow1.x, arrow1.y, paintLineStroke);
            canvas.drawLine(points[n-1].x, points[n-1].y, arrow2.x, arrow2.y, paintLineStroke);
            canvas.drawCircle(points[n-1].x, points[n-1].y, lineWidth / 2, paintLineFill);
        }
    }

}

// vim: set ts=4 sw=4 et:
