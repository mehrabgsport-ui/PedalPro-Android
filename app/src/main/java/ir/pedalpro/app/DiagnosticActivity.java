package ir.pedalpro.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class DiagnosticActivity extends Activity {
    private TextView logView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("PedalPro Diagnostics");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.setBackgroundColor(Color.rgb(10, 15, 20));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        Button copy = button("کپی");
        Button share = button("اشتراک");
        Button refresh = button("بروزرسانی");
        Button clear = button("پاک‌کردن");
        Button sensor = button("سنسور");
        actions.addView(copy);
        actions.addView(share);
        actions.addView(refresh);
        actions.addView(clear);
        actions.addView(sensor);
        root.addView(actions);

        ScrollView scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextColor(Color.WHITE);
        logView.setTextSize(12f);
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(4), dp(12), dp(4), dp(20));
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        refresh();

        copy.setOnClickListener(v -> {
            String text = DiagnosticLogger.dump(this);
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("PedalPro Diagnostic", text));
            Toast.makeText(this, "لاگ‌ها کپی شد", Toast.LENGTH_SHORT).show();
        });

        share.setOnClickListener(v -> {
            String text = DiagnosticLogger.dump(this);
            if (text.length() > 350000) text = text.substring(text.length() - 350000);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "PedalPro Diagnostic");
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, "ارسال لاگ"));
        });

        refresh.setOnClickListener(v -> refresh());

        clear.setOnClickListener(v -> {
            DiagnosticLogger.clear(this);
            DiagnosticLogger.log(this, "diagnostic", "log cleared by user");
            refresh();
        });

        sensor.setOnClickListener(v ->
                startActivity(new Intent(this, BikeSensorActivity.class)));
    }

    private void refresh() {
        logView.setText(DiagnosticLogger.dump(this));
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
