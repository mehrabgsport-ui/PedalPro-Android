package ir.pedalpro.diagnostic;

public class EventTracker {

    public EventTracker(android.content.Context context) {
        DiagnosticLogger.init(context);
    }

    public void track(String event, String details) {
        DiagnosticLogger.log("EVENT", event + " | " + details);
    }

    public void trackScreen(String screen) {
        track("SCREEN", screen);
    }

    public void trackAction(String action) {
        track("ACTION", action);
    }
}
