package ir.pedalpro.diagnostic;

public class RideDiagnosticBridge {
    private final DiagnosticLogger logger;

    public RideDiagnosticBridge(android.content.Context context) {
        logger = new DiagnosticLogger(context);
    }

    public void rideEvent(String name, String value) {
        logger.log("RIDE: " + name + " = " + value);
    }

    public void gpsState(String state) {
        logger.log("GPS_STATE: " + state);
    }
}
