package ir.pedalpro.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

public final class MainThreadWatchdog {
    private static volatile boolean started = false;

    private MainThreadWatchdog() {}

    public static synchronized void start(Context context) {
        if (started || context == null) return;
        started = true;
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());

        Thread watcher = new Thread(() -> {
            final long[] lastAck = {SystemClock.uptimeMillis()};
            long lastReport = 0L;

            while (true) {
                long postedAt = SystemClock.uptimeMillis();
                try {
                    main.post(() -> lastAck[0] = SystemClock.uptimeMillis());
                } catch (Throwable ignored) { }

                try { Thread.sleep(1000L); }
                catch (InterruptedException e) { return; }

                long now = SystemClock.uptimeMillis();
                long blockedFor = now - Math.max(lastAck[0], postedAt);
                if (blockedFor >= 2500L && now - lastReport >= 5000L) {
                    lastReport = now;
                    try {
                        Thread mainThread = Looper.getMainLooper().getThread();
                        StackTraceElement[] stack = mainThread.getStackTrace();
                        StringBuilder sb = new StringBuilder();
                        sb.append("main_thread_blocked_ms=").append(blockedFor).append("\n");
                        int max = Math.min(stack == null ? 0 : stack.length, 32);
                        for (int i = 0; i < max; i++) sb.append(stack[i]).append("\n");
                        DiagnosticLogger.log(app, "anr_watch", sb.toString());
                    } catch (Throwable ignored) { }
                }
            }
        }, "PedalPro-MainWatchdog");
        watcher.setDaemon(true);
        watcher.start();
    }
}
