package ir.pedalpro.diagnostic;

import android.content.Context;

public class DiagnosticInitializer {
    public static void init(Context context) {
        DiagnosticLogger logger = new DiagnosticLogger(context);
        logger.log("Diagnostic Engine Started");
    }
}
