package ir.pedalpro.app;

import android.app.Application;

public class PedalProApp extends Application {
    @Override public void onCreate() {
        super.onCreate();

        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                DiagnosticLogger.log(
                        getApplicationContext(),
                        "uncaught",
                        "thread=" + (thread == null ? "unknown" : thread.getName()),
                        error);
            } catch (Throwable ignored) { }

            if (previous != null) previous.uncaughtException(thread, error);
        });

        DiagnosticLogger.log(getApplicationContext(), "app", "application start");
        MainThreadWatchdog.start(getApplicationContext());
        FirebaseConfigManager.sync(getApplicationContext());
    }
}
