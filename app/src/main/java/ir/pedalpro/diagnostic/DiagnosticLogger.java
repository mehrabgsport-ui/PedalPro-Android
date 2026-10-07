package ir.pedalpro.diagnostic;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DiagnosticLogger {

    private static File logFile;

    private DiagnosticLogger() {
    }

    // Compatibility constructor for old code
    public DiagnosticLogger(Context context) {
        init(context);
    }

    public static void init(Context context) {
        logFile = new File(
                context.getFilesDir(),
                "pedalpro_diagnostic.log"
        );

        log(
                "SYSTEM",
                "Started " + Build.MODEL + " Android " + Build.VERSION.RELEASE
        );
    }

    // New API
    public static void log(String category, String message) {
        if (logFile == null) return;

        String time = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());

        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.append(time)
                    .append(" | ")
                    .append(category)
                    .append(" | ")
                    .append(message)
                    .append("\n");

        } catch (IOException ignored) {
        }
    }

    // Old API compatibility
    public void log(String message) {
        log("GENERAL", message);
    }

    public static File getReportFile() {
        return logFile;
    }
}
