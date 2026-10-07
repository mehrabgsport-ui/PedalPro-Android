package ir.pedalpro.diagnostic;

public class DebugModeManager {
    private static boolean enabled = false;

    public static void enable() {
        enabled = true;
    }

    public static boolean isEnabled() {
        return enabled;
    }
}
