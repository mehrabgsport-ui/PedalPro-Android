package ir.pedalpro.diagnostic;

public class PerformanceMonitor {

    public PerformanceMonitor(android.content.Context context) {
        DiagnosticLogger.init(context);
    }

    public void logMemory(long usedBytes) {
        DiagnosticLogger.log("PERFORMANCE", "memory=" + usedBytes);
    }

    public void logFreeze(long durationMs, String screen) {
        DiagnosticLogger.log("UI_FREEZE", "screen=" + screen + " duration=" + durationMs);
    }
}
