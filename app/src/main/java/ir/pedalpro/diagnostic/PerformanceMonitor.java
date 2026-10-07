package ir.pedalpro.diagnostic;

public class PerformanceMonitor {
    private final DiagnosticLogger logger;

    public PerformanceMonitor(android.content.Context context) {
        logger = new DiagnosticLogger(context);
    }

    public void logMemory(long usedBytes) {
        logger.log("PERFORMANCE | memory=" + usedBytes);
    }

    public void logFreeze(long durationMs, String screen) {
        logger.log("UI_FREEZE | screen=" + screen + " duration=" + durationMs);
    }
}
