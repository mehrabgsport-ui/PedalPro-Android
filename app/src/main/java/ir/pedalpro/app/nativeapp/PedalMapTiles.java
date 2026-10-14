package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import ir.pedalpro.app.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native XYZ raster tile backdrop; no WebView, no GPS collection.
 * Production operators should configure a licensed, Android-authorized XYZ provider.
 * Public OSM tiles are a limited development fallback; cache and attribution are mandatory.
 */
final class PedalMapTiles {
    static final int TILE = 256;
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Bitmap> MEMORY = Collections.synchronizedMap(
            new LinkedHashMap<String, Bitmap>(72, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> item) {
                    return size() > 72;
                }
            });
    private static final Set<String> PENDING = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, Long> RETRY_AFTER = Collections.synchronizedMap(new LinkedHashMap<>());
    private static volatile boolean hostRejected = false;
    private static final Paint IMAGE = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private static final Paint CAPTION = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final long CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final String TILE_URL = BuildConfig.PEDALPRO_MAP_TILE_TEMPLATE;
    private static final String CREDIT = BuildConfig.PEDALPRO_MAP_ATTRIBUTION;

    private PedalMapTiles() {}

    static double worldX(double lng, int zoom) {
        return (lng + 180.0) / 360.0 * TILE * (1 << zoom);
    }

    static double worldY(double lat, int zoom) {
        double phi = Math.toRadians(Math.max(-85.05112878, Math.min(85.05112878, lat)));
        return (1 - Math.log(Math.tan(phi) + 1.0 / Math.cos(phi)) / Math.PI) / 2.0 * TILE * (1 << zoom);
    }

    static void draw(View target, Canvas c, double centerLat, double centerLng, int zoom) {
        if (!Double.isFinite(centerLat) || !Double.isFinite(centerLng)) return;
        float w = target.getWidth(), h = target.getHeight();
        double worldX = worldX(centerLng, zoom), worldY = worldY(centerLat, zoom);
        double left = worldX - w / 2.0, top = worldY - h / 2.0;
        int firstX = (int) Math.floor(left / TILE);
        int firstY = (int) Math.floor(top / TILE);
        int lastX = (int) Math.floor((left + w) / TILE);
        int lastY = (int) Math.floor((top + h) / TILE);
        int n = 1 << zoom;
        int available = 0;
        for (int ty = firstY; ty <= lastY; ty++) {
            if (ty < 0 || ty >= n) continue;
            for (int tx = firstX; tx <= lastX; tx++) {
                int wrappedX = ((tx % n) + n) % n;
                String id = zoom + "_" + wrappedX + "_" + ty;
                Bitmap bmp = MEMORY.get(id);
                if (bmp == null) {
                    queue(target, id, zoom, wrappedX, ty);
                    continue;
                }
                if (bmp.isRecycled()) continue;
                available++;
                float x = (float) (tx * TILE - left);
                float y = (float) (ty * TILE - top);
                c.drawBitmap(bmp, null, new RectF(x, y, x + TILE, y + TILE), IMAGE);
            }
        }
        CAPTION.setTextSize(target.getResources().getDisplayMetrics().scaledDensity * 10f);
        if (available == 0) {
            CAPTION.setColor(Color.rgb(199, 215, 224));
            c.drawText(hostRejected ? "دسترسی به سرویس نقشه رد شد؛ تنظیمات API را بررسی کنید" :
                    "در حال دریافت نقشه زمینه…", 16f, h / 2f, CAPTION);
        }
        // Mandatory per-tile provider attribution. Other providers may require a different credit.
        CAPTION.setColor(Color.WHITE);
        CAPTION.setShadowLayer(3, 0, 1, Color.BLACK);
        String credit = CREDIT == null ? "" : CREDIT;
        c.drawText(credit, Math.max(10f, w - CAPTION.measureText(credit) - 10f), h - 24f, CAPTION);
        CAPTION.clearShadowLayer();
    }

    private static void queue(View target, String id, int zoom, int x, int y) {
        if (TILE_URL == null || !TILE_URL.startsWith("https://")) return;
        Long retry = RETRY_AFTER.get(id);
        if (retry != null && retry > System.currentTimeMillis()) return;
        if (!PENDING.add(id)) return;
        Context app = target.getContext().getApplicationContext();
        IO.execute(() -> {
            try {
                File folder = new File(app.getCacheDir(), "pedalpro-map-tiles");
                if (!folder.exists() && !folder.mkdirs()) return;
                File file = new File(folder, id + ".png");
                Bitmap bitmap = null;
                if (file.isFile() && System.currentTimeMillis() - file.lastModified() < CACHE_AGE_MS) {
                    bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                }
                if (bitmap == null) {
                    String url = TILE_URL.replace("{z}", String.valueOf(zoom))
                            .replace("{x}", String.valueOf(x)).replace("{y}", String.valueOf(y));
                    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setConnectTimeout(7500);
                    connection.setReadTimeout(8000);
                    connection.setRequestProperty("User-Agent", "PedalPro-Android/2.0 (+https://pedalpro.ir)");
                    try {
                        int status = connection.getResponseCode();
                        if (status == 401 || status == 403) hostRejected = true;
                        if (status != 200) RETRY_AFTER.put(id,
                                System.currentTimeMillis() + (status == 401 || status == 403 || status == 429 ? 60_000L : 15_000L));
                        if (status == 200) {
                            String mime = connection.getContentType();
                            if (mime != null && mime.startsWith("image/")) {
                                File temp = new File(folder, id + ".tmp");
                                try (InputStream in = connection.getInputStream();
                                     FileOutputStream out = new FileOutputStream(temp)) {
                                    byte[] buf = new byte[8192];
                                    int len;
                                    long total = 0;
                                    while ((len = in.read(buf)) != -1 && (total += len) <= 1024 * 1024) {
                                        out.write(buf, 0, len);
                                    }
                                }
                                if (temp.length() > 0 && temp.length() <= 1024 * 1024) {
                                    bitmap = BitmapFactory.decodeFile(temp.getAbsolutePath());
                                    if (bitmap != null && !temp.renameTo(file)) temp.delete();
                                } else temp.delete();
                            }
                        }
                    } finally { connection.disconnect(); }
                }
                if (bitmap != null) {
                    MEMORY.put(id, bitmap);
                    RETRY_AFTER.remove(id);
                    hostRejected = false;
                    if (target.isAttachedToWindow()) MAIN.post(target::postInvalidateOnAnimation);
                }
            } catch (Exception ignored) {
                // Keep GPS route visible even when the tile host is offline.
                RETRY_AFTER.put(id, System.currentTimeMillis() + 15_000L);
            } finally { PENDING.remove(id); }
        });
    }
}
