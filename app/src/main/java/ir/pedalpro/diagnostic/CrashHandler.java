package ir.pedalpro.diagnostic;

import android.content.Context;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        DiagnosticLogger.init(context);
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        DiagnosticLogger.log("CRASH", throwable.toString());
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }
}
