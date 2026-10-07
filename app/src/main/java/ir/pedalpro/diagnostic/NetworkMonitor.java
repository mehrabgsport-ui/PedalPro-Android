package ir.pedalpro.diagnostic;

public class NetworkMonitor {

    public NetworkMonitor(android.content.Context context) {
        DiagnosticLogger.init(context);
    }

    public void logRequest(String endpoint, int statusCode, long durationMs) {
        DiagnosticLogger.log("NETWORK", endpoint + " status=" + statusCode + " duration=" + durationMs);
    }

    public void logFailure(String endpoint, String error) {
        DiagnosticLogger.log("NETWORK_ERROR", endpoint + " error=" + error);
    }
}
