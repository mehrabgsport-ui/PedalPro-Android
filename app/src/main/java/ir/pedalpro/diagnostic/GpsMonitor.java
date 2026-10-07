package ir.pedalpro.diagnostic;

public class GpsMonitor {

    public GpsMonitor(android.content.Context context) {
        DiagnosticLogger.init(context);
    }

    public void logLocationStatus(boolean enabled, float accuracy, long delayMs) {
        DiagnosticLogger.log("GPS", "enabled=" + enabled + " accuracy=" + accuracy + " delay=" + delayMs);
    }
}
