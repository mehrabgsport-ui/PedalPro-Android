package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;

import ir.pedalpro.app.BuildConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Native Neshan SDK map + existing native GPS overlay. Never instantiates a WebView.
 * The optional Neshan SDK requires an Android licence for this signing certificate.
 * Without that licensed SDK, the existing native XYZ map remains available.
 */
public final class PedalNeshanMapSurface extends FrameLayout {
    private static final String TAG = "PedalNeshanMap";
    private final NativeRouteView overlay;
    private View neshanMap;
    private double lastLat = Double.NaN, lastLng = Double.NaN;
    private int lastZoom = -1;
    private boolean trafficEnabled = true;
    private boolean nightStyle = true;

    public PedalNeshanMapSurface(Context context) {
        super(context);
        setClipChildren(true);
        setClipToPadding(true);
        setBackgroundColor(Color.rgb(5,21,33));

        View sdk = BuildConfig.PEDALPRO_NESHAN_ANDROID_ENABLED ? createNeshanSdk(context) : null;
        if (sdk != null) {
            neshanMap = sdk;
            addView(sdk, new FrameLayout.LayoutParams(-1,-1));
        }
        overlay = new NativeRouteView(context);
        if (neshanMap != null) {
            overlay.setExternalBasemap(true);
            overlay.setCameraListener(this::syncCamera);
        }
        addView(overlay, new FrameLayout.LayoutParams(-1,-1));
        buildControls(context);
    }

    private View createNeshanSdk(Context context) {
        try {
            Class<?> clazz = Class.forName("org.neshan.mapsdk.MapView");
            Object raw = null;
            for (Constructor<?> ctor : clazz.getConstructors()) {
                Class<?>[] types = ctor.getParameterTypes();
                if (types.length == 1 && Context.class.isAssignableFrom(types[0])) {
                    raw = ctor.newInstance(context);
                    break;
                }
                if (types.length == 2 && Context.class.isAssignableFrom(types[0])
                        && android.util.AttributeSet.class.isAssignableFrom(types[1])) {
                    raw = ctor.newInstance(context,null);
                    break;
                }
            }
            if (!(raw instanceof View)) throw new IllegalStateException("Neshan MapView unavailable");
            View view = (View) raw;
            invoke(view, "setTrafficEnabled", trafficEnabled);
            updateMapStyle(view, nightStyle);
            return view;
        } catch (Throwable e) {
            Log.w(TAG, "Neshan Android SDK unavailable; using built-in native map tiles", e);
            return null;
        }
    }

    private void syncCamera(double lat,double lng,int zoom) {
        if (neshanMap == null) return;
        if (Math.abs(lat-lastLat) < .0000005 && Math.abs(lng-lastLng) < .0000005
                && zoom == lastZoom) return;
        try {
            Class<?> latLng = Class.forName("org.neshan.common.model.LatLng");
            Object coordinate = null;
            for (Constructor<?> ctor : latLng.getConstructors()) {
                Class<?>[] p=ctor.getParameterTypes();
                if(p.length==2 && numeric(p[0]) && numeric(p[1])) {
                    coordinate=ctor.newInstance(number(p[0],lat),number(p[1],lng));
                    break;
                }
            }
            if (coordinate == null) throw new IllegalStateException("Neshan LatLng missing");
            Method move = findMethod(neshanMap.getClass(),"moveCamera",2);
            if (move == null) throw new IllegalStateException("Neshan camera method missing");
            move.invoke(neshanMap,coordinate,number(move.getParameterTypes()[1],0));
            Method setZoom=findMethod(neshanMap.getClass(),"setZoom",2);
            if(setZoom!=null) setZoom.invoke(neshanMap,number(setZoom.getParameterTypes()[0],zoom),
                    number(setZoom.getParameterTypes()[1],0));
            lastLat=lat;lastLng=lng;lastZoom=zoom;
        } catch (Throwable e) {
            Log.w(TAG,"Neshan SDK camera failed; falling back to XYZ tiles",e);
            fallbackToTiles();
        }
    }

    private void fallbackToTiles() {
        View old=neshanMap;
        if(old==null)return;
        neshanMap=null;
        overlay.setCameraListener(null);
        overlay.setExternalBasemap(false);
        removeView(old);
        overlay.invalidate();
    }

    private static boolean numeric(Class<?> cls) {
        return cls==double.class||cls==float.class||cls==int.class||cls==long.class
                ||cls==Double.class||cls==Float.class||cls==Integer.class||cls==Long.class;
    }

    private static Object number(Class<?> cls,double n) {
        if(cls==float.class||cls==Float.class)return (float)n;
        if(cls==int.class||cls==Integer.class)return (int)Math.round(n);
        if(cls==long.class||cls==Long.class)return Math.round(n);
        return n;
    }

    private static Method findMethod(Class<?> type,String name,int count) {
        for(Method m:type.getMethods())if(m.getName().equals(name)&&m.getParameterCount()==count)return m;
        return null;
    }

    private static void invoke(Object obj,String name,boolean value) {
        try {
            Method m=obj.getClass().getMethod(name,boolean.class);
            m.invoke(obj,value);
        }catch(Exception e){Log.w(TAG,"Neshan setting unavailable: "+name);}
    }

    private void updateMapStyle(View view,boolean night) {
        try {
            Class<?> styles=Class.forName("org.neshan.mapsdk.style.NeshanMapStyle");
            int style=styles.getField(night?"NESHAN_NIGHT":"STANDARD_DAY").getInt(null);
            Method method=view.getClass().getMethod("setMapStyle",int.class);
            method.invoke(view,style);
        }catch(Throwable e){Log.w(TAG,"Neshan map-style method unavailable",e);}
    }

    private void buildControls(Context context) {
        LinearLayout controls=new LinearLayout(context);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(5),dp(5),dp(5),dp(5));
        controls.setBackground(panelBackground());
        controls.addView(control("+","بزرگ‌نمایی نقشه",()->overlay.zoomBy(1)));
        controls.addView(control("−","کوچک‌نمایی نقشه",()->overlay.zoomBy(-1)));
        controls.addView(control("◎","نمایش کامل مسیر",overlay::recenter));
        if (neshanMap != null) {
            controls.addView(control("☾","تغییر حالت روز و شب نشان",()->{
                nightStyle=!nightStyle;
                updateMapStyle(neshanMap,nightStyle);
            }));
            controls.addView(control("≋","نمایش یا مخفی‌کردن ترافیک نشان",()->{
                trafficEnabled=!trafficEnabled;
                invoke(neshanMap,"setTrafficEnabled",trafficEnabled);
            }));
        }
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(44),-2,Gravity.TOP|Gravity.END);
        params.setMargins(0,dp(10),dp(10),0);
        addView(controls,params);
    }

    private View control(String label,String description,Runnable click) {
        TextView button=new TextView(getContext());
        button.setText(label);
        button.setTextSize(23);
        button.setTextColor(Color.rgb(239,248,251));
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setClickable(true);
        button.setOnClickListener(v->click.run());
        button.setBackground(panelBackground());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(34),dp(38));
        lp.setMargins(0,dp(2),0,dp(2));
        button.setLayoutParams(lp);
        return button;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.argb(229,6,28,45));
        g.setCornerRadius(dp(13));
        g.setStroke(dp(1),Color.argb(75,164,235,244));
        return g;
    }

    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}

    public boolean isNeshanEnabled(){return neshanMap!=null;}
    public void setPoints(JSONArray rows){overlay.setPoints(rows);}
    public void appendPoints(JSONArray rows){overlay.appendPoints(rows);}
    public void setCurrent(double lat,double lng){overlay.setCurrent(lat,lng);}
    public void setTarget(double lat,double lng){overlay.setTarget(lat,lng);}
    public void setNavigationRoute(JSONArray rows){overlay.setNavigationRoute(rows);}
    public void clearNavigationRoute(){overlay.clearNavigationRoute();}
    public void clearTarget(){overlay.clearTarget();}
    public int pointCount(){return overlay.pointCount();}
}
