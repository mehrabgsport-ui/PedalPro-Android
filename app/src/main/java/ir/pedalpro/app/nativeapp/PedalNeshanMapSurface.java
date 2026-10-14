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
    private NeshanMapBridge sdkBridge;
    private TextView providerStatus;
    private LinearLayout mapControls;
    private TextView navigationControl;
    private double lastLat = Double.NaN, lastLng = Double.NaN;
    private int lastZoom = -1;
    private boolean trafficEnabled = true;
    private boolean nightStyle = true;
    private float lastSdkBearing=Float.NaN;
    private boolean lastSdkNavigationMode;

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
            // Let the authentic Neshan SDK render roads AND route geometry.
            // The custom Canvas overlay would capture native pan/pinch and cannot
            // project its vectors onto an independently pitched Neshan camera.
            overlay.setExternalBasemap(true);
            overlay.setVisibility(View.GONE);
        }
        addView(overlay, new FrameLayout.LayoutParams(-1,-1));
        buildControls(context);
        if(neshanMap==null) {
            providerStatus=new TextView(context);
            providerStatus.setText("نقشه موقت · SDK نشان نیازمند لایسنس");
            providerStatus.setTextSize(10f);
            providerStatus.setTextColor(Color.WHITE);
            providerStatus.setGravity(Gravity.CENTER);
            providerStatus.setBackground(panelBackground());
            FrameLayout.LayoutParams badge=new FrameLayout.LayoutParams(-2,dp(30),
                    Gravity.CENTER_HORIZONTAL|Gravity.TOP);
            badge.topMargin=dp(12);
            addView(providerStatus,badge);
        }
    }

    private View createNeshanSdk(Context context) {
        try {
            // Neshan's licensed implementation is compiled into the APK ONLY
            // when NESHAN_ANDROID_LICENCE_BASE64 is supplied to release CI.
            Class.forName("org.neshan.mapsdk.MapView");
            Class<?> clazz=Class.forName(
                    "ir.pedalpro.app.nativeapp.LicensedNeshanMapBridge");
            sdkBridge=(NeshanMapBridge)clazz.getConstructor(Context.class).newInstance(context);
            sdkBridge.setNight(nightStyle);
            return sdkBridge.view();
        } catch(Throwable e) {
            Log.w(TAG,"Licensed Neshan renderer unavailable: "+e.getClass().getSimpleName());
            sdkBridge=null;
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
            applySdkNavigationCamera();
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
        sdkBridge=null;
        overlay.setCameraListener(null);
        overlay.setExternalBasemap(false);
        overlay.setVisibility(View.VISIBLE);
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

    private void setSdkCameraFloat(String name,float value){
        if(neshanMap==null)return;
        try{
            Method method=findMethod(neshanMap.getClass(),name,2);
            if(method==null)throw new NoSuchMethodException(name);
            Class<?>[] types=method.getParameterTypes();
            method.invoke(neshanMap,number(types[0],value),number(types[1],.35f));
        }catch(Throwable e){Log.w(TAG,"Neshan navigation camera method unavailable: "+name);}
    }

    private void applySdkNavigationCamera(){
        if(sdkBridge!=null)
            sdkBridge.setNavigation(overlay.isNavigationMode(),overlay.isHeadingUpEnabled(),
                    overlay.getHeadingDegrees(),overlay.hasHeadingDegrees());
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
        mapControls=controls;
        controls.addView(control("+","بزرگ‌نمایی نقشه",()->zoomBy(1)));
        controls.addView(control("−","کوچک‌نمایی نقشه",()->zoomBy(-1)));
        controls.addView(control("◎","بازگشت به موقعیت رکاب‌زن",this::recenter));
        navigationControl=(TextView)control("↗","تغییر حالت نقشه ناویگیشن",this::toggleOrientation);
        controls.addView(navigationControl);
        if (neshanMap != null) {
            controls.addView(control("☾","تغییر حالت روز و شب نشان",()->{
                nightStyle=!nightStyle;
                if(sdkBridge!=null)sdkBridge.setNight(nightStyle);
            }));
            controls.addView(control("≋","نمایش یا مخفی‌کردن ترافیک نشان",()->{
                trafficEnabled=!trafficEnabled;
                if(sdkBridge!=null)sdkBridge.setTraffic(trafficEnabled);
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

    /** Presentation-only control arrangement for the full-screen riding map. */
    public void setFullRideMode(boolean enabled) {
        if(mapControls==null)return;
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(44),-2,
                enabled ? Gravity.BOTTOM|Gravity.START : Gravity.TOP|Gravity.END);
        if(enabled)lp.setMargins(dp(12),0,0,dp(218));
        else lp.setMargins(0,dp(10),dp(10),0);
        mapControls.setLayoutParams(lp);
        mapControls.setElevation(dp(9));
    }

    /** Orientation changes must never stop tracking or modify stored GPS. */
    private void toggleOrientation() {
        if(!overlay.isNavigationMode())overlay.setNavigationMode(true);
        else overlay.setHeadingUpEnabled(!overlay.isHeadingUpEnabled());
        refreshOrientationLabel();
        applySdkNavigationCamera();
    }

    private void zoomBy(int amount) {
        if(sdkBridge!=null)sdkBridge.zoomBy(amount);
        else overlay.zoomBy(amount);
    }

    private void recenter() {
        overlay.recenter();
        if(!overlay.isNavigationMode())overlay.setNavigationMode(true);
        if(sdkBridge!=null)sdkBridge.recenter();
        refreshOrientationLabel();
    }

    private void refreshOrientationLabel() {
        if(navigationControl==null)return;
        boolean follow=overlay.isNavigationMode();
        boolean heading=overlay.isHeadingUpEnabled();
        navigationControl.setText(!follow?"↗":heading?"↑":"N");
        navigationControl.setContentDescription(!follow?
                "فعال‌سازی دنبال‌کردن رکاب‌زن":
                heading?"Heading-Up فعال؛ تغییر به شمال بالا":
                        "North-Up فعال؛ تغییر به جهت حرکت بالا");
    }

    /** Navigation camera affects visual presentation only, not GPS/tracking. */
    public void setNavigationMode(boolean enabled) {
        overlay.setNavigationMode(enabled);
        refreshOrientationLabel();
        if(sdkBridge!=null)sdkBridge.setNavigation(enabled,overlay.isHeadingUpEnabled(),
                overlay.getHeadingDegrees(),overlay.hasHeadingDegrees());
        else applySdkNavigationCamera();
    }

    public void setHeadingDegrees(float bearing){
        overlay.setHeadingDegrees(bearing);
        if(sdkBridge!=null)applySdkNavigationCamera();
    }

    public boolean isNeshanEnabled(){return sdkBridge!=null;}
    public void setPoints(JSONArray rows){
        overlay.setPoints(rows);
        if(sdkBridge!=null)sdkBridge.setRecordedTrack(rows);
    }
    public void appendPoints(JSONArray rows){
        overlay.appendPoints(rows);
        if(sdkBridge!=null)sdkBridge.setRecordedTrack(overlay.exportPoints());
    }
    public void setCurrent(double lat,double lng){
        overlay.setCurrent(lat,lng);
        if(sdkBridge!=null)sdkBridge.setCurrent(lat,lng);
    }
    public void setTarget(double lat,double lng){
        overlay.setTarget(lat,lng);
        if(sdkBridge!=null)sdkBridge.setTarget(lat,lng);
    }
    public void setNavigationRoute(JSONArray rows){
        overlay.setNavigationRoute(rows);
        if(sdkBridge!=null)sdkBridge.setSuggestedRoute(rows);
    }
    public void clearNavigationRoute(){
        overlay.clearNavigationRoute();
        if(sdkBridge!=null)sdkBridge.setSuggestedRoute(null);
    }
    public void clearTarget(){
        overlay.clearTarget();
        if(sdkBridge!=null)sdkBridge.clearTarget();
    }
    public int pointCount(){return overlay.pointCount();}
}
