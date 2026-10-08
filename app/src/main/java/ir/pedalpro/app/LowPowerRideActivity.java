package ir.pedalpro.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LowPowerRideActivity extends Activity {
    private TextView speed;
    private TextView distance;
    private TextView time;
    private boolean paused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        time = createText("00:00:00");
        speed = createText("0 km/h");
        distance = createText("0.00 km");

        root.addView(time);
        root.addView(speed);
        root.addView(distance);
        setContentView(root);
    }

    private TextView createText(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(32);
        return t;
    }

    public void setPaused(boolean value) {
        paused = value;
        if (paused) speed.setText("0 km/h");
    }
}
