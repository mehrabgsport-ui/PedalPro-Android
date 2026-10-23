package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

/** Native equivalent of the Web 4.7.46 body-weight history chart. */
final class NativeWeightChartView extends View {
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint point = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private JSONArray rows = new JSONArray();

    NativeWeightChartView(Context context) {
        super(context);
        axis.setColor(Color.rgb(41, 67, 92));
        axis.setStrokeWidth(dp(1));
        line.setColor(Color.rgb(46, 230, 166));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(4));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        point.setColor(Color.rgb(43, 146, 255));
        point.setStyle(Paint.Style.FILL);
        label.setColor(Color.rgb(159, 178, 200));
        label.setTextSize(dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(92, 6, 21, 34));
        bg.setCornerRadius(dp(16));
        bg.setStroke(Math.max(1, Math.round(dp(1))), Color.argb(38, 255, 255, 255));
        setBackground(bg);
    }

    void setRows(JSONArray value) {
        rows = value == null ? new JSONArray() : value;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float p = dp(28);
        if (w <= p * 2 || h <= p * 2) return;

        if (rows.length() < 2) {
            canvas.drawText("با ثبت حداقل دو وزن، نمودار نمایش داده می‌شود.", dp(14), h / 2f, label);
            return;
        }

        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        int valid = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i);
            if (r == null) continue;
            double v = r.optDouble("weight_kg", Double.NaN);
            if (!Double.isFinite(v) || v <= 0) continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
            valid++;
        }
        if (valid < 2 || !Double.isFinite(min) || !Double.isFinite(max)) {
            canvas.drawText("با ثبت حداقل دو وزن، نمودار نمایش داده می‌شود.", dp(14), h / 2f, label);
            return;
        }

        min -= 1d;
        max += 1d;
        double range = Math.max(1d, max - min);
        canvas.drawLine(p, h - p, w - p, h - p, axis);

        Path path = new Path();
        boolean first = true;
        int plotted = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i);
            if (r == null) continue;
            double v = r.optDouble("weight_kg", Double.NaN);
            if (!Double.isFinite(v) || v <= 0) continue;
            float x = p + (w - 2 * p) * (plotted / (float)Math.max(1, valid - 1));
            float y = h - p - (float)((v - min) / range) * (h - 2 * p);
            if (first) { path.moveTo(x, y); first = false; }
            else path.lineTo(x, y);
            canvas.drawCircle(x, y, dp(5), point);
            plotted++;
        }
        canvas.drawPath(path, line);

        canvas.drawText(String.format(java.util.Locale.US, "%.1f kg", max - 1d), p, dp(17), label);
        canvas.drawText(String.format(java.util.Locale.US, "%.1f kg", min + 1d), p, h - dp(8), label);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
