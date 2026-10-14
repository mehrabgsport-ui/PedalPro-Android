package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.WeakHashMap;
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
    // Logical display pixels, not the 256px provider bitmap. RC6 downloaded more
    // than a screenful of high-zoom raster tiles on high-density devices.
    static final int TILE = 512;
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Bitmap> MEMORY = Collections.synchronizedMap(
            new LinkedHashMap<String, Bitmap>(56, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> item) {
                    return size() > 56;
                }
            });
    private static final Set<String> PENDING = Collections.synchronizedSet(new HashSet<>());
    private static final Set<View> WATCHERS = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<View,Boolean>()));
    private static final Map<String, Long> RETRY_AFTER = Collections.synchronizedMap(new LinkedHashMap<>());
    private static volatile boolean hostRejected = false;
    private static final Paint IMAGE = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private static final Paint CAPTION = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final long CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final String TILE_URL = BuildConfig.PEDALPRO_MAP_TILE_TEMPLATE;
    private static final String CREDIT = BuildConfig.PEDALPRO_MAP_ATTRIBUTION;
    private static final String CACHE_SCOPE = Integer.toHexString(TILE_URL.hashCode());

    private PedalMapTiles() {}

    static double worldX(double lng, int zoom) {
        return (lng + 180.0) / 360.0 * TILE * (1 << zoom);
    }

    static double worldY(double lat, int zoom) {
        double phi = Math.toRadians(Math.max(-85.05112878, Math.min(85.05112878, lat)));
        return (1 - Math.log(Math.tan(phi) + 1.0 / Math.cos(phi)) / Math.PI) / 2.0 * TILE * (1 << zoom);
    }


    static void draw(View target, Canvas c, double centerLat, double centerLng, int zoom) {
        draw(target,c,centerLat,centerLng,zoom,1f,0f,null);
    }

    static void draw(View target, Canvas c, double centerLat, double centerLng, int zoom,
                     float overscan) {
        draw(target,c,centerLat,centerLng,zoom,overscan,0f,null);
    }

    static void draw(View target, Canvas c, double centerLat, double centerLng, int zoom,
                     float overscan,float bearingDegrees) {
        draw(target,c,centerLat,centerLng,zoom,overscan,bearingDegrees,null);
    }

    static void draw(View target,Canvas c,double centerLat,double centerLng,int zoom,
                     float overscan,float bearingDegrees,Matrix inverseProjection) {
        if(!Double.isFinite(centerLat)||!Double.isFinite(centerLng))return;
        WATCHERS.add(target);
        float w=target.getWidth(),h=target.getHeight();
        if(w<1||h<1)return;
        double worldX=worldX(centerLng,zoom),worldY=worldY(centerLat,zoom);
        double left=worldX-w*.5,top=worldY-h*.5;
        // Exact inverse-projected viewport coverage (plus fractional-tile margin).
        // This remains correct with heading rotation AND true perspective pitch.
        float[] corners={0,0,w,0,w,h,0,h};
        if(inverseProjection!=null)inverseProjection.mapPoints(corners);
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;
        double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        for(int i=0;i<corners.length;i+=2){
            minX=Math.min(minX,corners[i]);maxX=Math.max(maxX,corners[i]);
            minY=Math.min(minY,corners[i+1]);maxY=Math.max(maxY,corners[i+1]);
        }
        if(!Double.isFinite(minX)||!Double.isFinite(maxX)||!Double.isFinite(minY)
                ||!Double.isFinite(maxY))return;
        final double margin=20d;
        int firstX=(int)Math.floor((left+minX-margin)/TILE);
        int lastX=(int)Math.floor((left+maxX+margin)/TILE);
        int firstY=(int)Math.floor((top+minY-margin)/TILE);
        int lastY=(int)Math.floor((top+maxY+margin)/TILE);
        int n=1<<zoom;
        // A malicious/broken transform must never schedule a giant tile set.
        if(lastX-firstX>16||lastY-firstY>16)return;
        ArrayList<int[]> needed=new ArrayList<>();
        int loaded=0;
        final double midX=worldX/TILE,midY=worldY/TILE;
        for(int y=firstY;y<=lastY;y++){
            if(y<0||y>=n)continue;
            for(int x=firstX;x<=lastX;x++){
                int wrappedX=((x%n)+n)%n;
                String id=zoom+"_"+wrappedX+"_"+y;
                float drawX=(float)(x*TILE-left),drawY=(float)(y*TILE-top);
                RectF rect=new RectF(drawX,drawY,drawX+TILE,drawY+TILE);
                Bitmap tile=MEMORY.get(id);
                if(tile!=null&&!tile.isRecycled()){
                    c.drawBitmap(tile,null,rect,IMAGE);
                    loaded++;
                }else{
                    boolean placeholder=false;
                    // Parent pyramid retains street imagery during zoom & network
                    // transitions rather than swapping to an empty navy viewport.
                    for(int level=1;level<=2&&zoom-level>=0;level++){
                        int parts=1<<level;
                        Bitmap parent=MEMORY.get((zoom-level)+"_"+(wrappedX/parts)+"_"+(y/parts));
                        if(parent==null||parent.isRecycled())continue;
                        int width=parent.getWidth(),height=parent.getHeight();
                        int sx=wrappedX%parts,sy=y%parts;
                        Rect crop=new Rect(sx*width/parts,sy*height/parts,
                                (sx+1)*width/parts,(sy+1)*height/parts);
                        c.drawBitmap(parent,crop,rect,IMAGE);
                        placeholder=true;
                        break;
                    }
                    if(placeholder)loaded++;
                    needed.add(new int[]{wrappedX,y,x});
                }
            }
        }
        // Queue nearest-to-camera tiles first. RC6 requested top-left tiles first,
        // making the rider location the last region to become visible.
        needed.sort(Comparator.comparingDouble(pt->
                Math.pow(pt[2]+.5-midX,2)+Math.pow(pt[1]+.5-midY,2)));
        for(int[] pt:needed){
            if(PENDING.size()>=18)break;
            queue(target,zoom+"_"+pt[0]+"_"+pt[1],zoom,pt[0],pt[1]);
        }
        if(loaded==0) {
            CAPTION.setTextSize(target.getResources().getDisplayMetrics().scaledDensity*12f);
            CAPTION.setColor(Color.rgb(199,215,224));
            c.drawText(hostRejected?"سرویس نقشه پاسخ نمی‌دهد":"در حال بارگذاری نقشه…",
                    18f,h*.55f,CAPTION);
        }
    }

    /** Draw after restoring the rotated map canvas, so attribution stays legible. */
    static void drawAttribution(View target, Canvas c) {
        CAPTION.setTextSize(target.getResources().getDisplayMetrics().scaledDensity * 10f);
        CAPTION.setColor(Color.WHITE);
        CAPTION.setShadowLayer(3, 0, 1, Color.BLACK);
        String credit = CREDIT == null ? "" : CREDIT;
        c.drawText(credit, Math.max(10f, target.getWidth() - CAPTION.measureText(credit) - 10f),
                target.getHeight() - 24f, CAPTION);
        CAPTION.clearShadowLayer();
    }

    private static void queue(View target, String id, int zoom, int x, int y) {
        if (TILE_URL == null || !TILE_URL.startsWith("https://")) return;
        Long retry = RETRY_AFTER.get(id);
        if (retry != null && retry > System.currentTimeMillis()) return;
        if (PENDING.size() >= 18 || !PENDING.add(id)) return;
        Context app = target.getContext().getApplicationContext();
        IO.execute(() -> {
            try {
                File folder = new File(app.getCacheDir(), "pedalpro-map-tiles-" + CACHE_SCOPE);
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
                    MAIN.post(()->{
                        // Re-render all still-visible map surfaces, not only the view
                        // that originally requested the tile (which may be detached).
                        synchronized(WATCHERS){
                            for(View view:WATCHERS)
                                if(view!=null&&view.isAttachedToWindow())
                                    view.postInvalidateOnAnimation();
                        }
                    });
                }
            } catch (Exception ignored) {
                // Keep GPS route visible even when the tile host is offline.
                RETRY_AFTER.put(id, System.currentTimeMillis() + 15_000L);
            } finally { PENDING.remove(id); }
        });
    }
}
