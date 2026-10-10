package ir.pedalpro.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class MainThreadWatchdog {
    private static volatile boolean started = false;

    private MainThreadWatchdog() {}

    public static synchronized void start(Context context) {
        if (started || context == null) return;
        started = true;
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        AtomicBoolean pingPending = new AtomicBoolean(false);
        AtomicLong pingSentAt = new AtomicLong(0L);

        Thread watcher = new Thread(() -> {
            long lastReport = 0L;

            while (!Thread.currentThread().isInterrupted()) {
                long now = SystemClock.uptimeMillis();
                if (pingPending.compareAndSet(false, true)) {
                    pingSentAt.set(now);
                    try {
                        main.post(() -> pingPending.set(false));
                    } catch (Throwable e) {
                        pingPending.set(false);
                    }
                }

                try { Thread.sleep(1000L); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                now = SystemClock.uptimeMillis();
                long sentAt = pingSentAt.get();
                long blockedFor = pingPending.get() && sentAt > 0L ? now - sentAt : 0L;
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
