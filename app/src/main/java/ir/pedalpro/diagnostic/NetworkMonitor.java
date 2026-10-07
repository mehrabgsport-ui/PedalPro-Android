package ir.pedalpro.diagnostic;

public class NetworkMonitor {
    private final DiagnosticLogger logger;

    public NetworkMonitor(android.content.Context context) {
        logger = new DiagnosticLogger(context);
    }

    public void logRequest(String endpoint, int statusCode, long durationMs) {
        logger.log("NETWORK | " + endpoint + " status=" + statusCode + " duration=" + durationMs);
    }

    public void logFailure(String endpoint, String error) {
        logger.log("NETWORK_ERROR | " + endpoint + " error=" + error);
    }
}
