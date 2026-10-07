package ir.pedalpro.diagnostic;

public class GpsMonitor {
    private final DiagnosticLogger logger;

    public GpsMonitor(android.content.Context context) {
        logger = new DiagnosticLogger(context);
    }

    public void logLocationStatus(boolean enabled, float accuracy, long delayMs) {
        logger.log("GPS | enabled=" + enabled + " accuracy=" + accuracy + " delay=" + delayMs);
    }
}
