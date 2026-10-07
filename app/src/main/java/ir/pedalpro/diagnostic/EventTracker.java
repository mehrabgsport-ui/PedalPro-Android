package ir.pedalpro.diagnostic;

import android.content.Context;

public class EventTracker {
    private final DiagnosticLogger logger;

    public EventTracker(Context context) {
        logger = new DiagnosticLogger(context);
    }

    public void track(String event, String details) {
        logger.log("EVENT: " + event + " | " + details);
    }

    public void trackScreen(String screen) {
        track("SCREEN", screen);
    }

    public void trackAction(String action) {
        track("ACTION", action);
    }
}
