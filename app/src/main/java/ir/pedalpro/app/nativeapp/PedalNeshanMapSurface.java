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

/**
 * True Neshan MapLibre native map when the official MOBILE key is configured at
 * build time. Otherwise, an explicitly labelled native XYZ diagnostic fallback.
 * This display does not change TrackingService, GPS processing or recorded data.
 */
public final class PedalNeshanMapSurface extends FrameLayout {
    private static final String TAG="PedalNeshanMap";
    private final NativeRouteView overlay;
    private NeshanMapBridge sdkBridge;
    private View sdkMap;
    private LinearLayout mapControls;
    private TextView navigationControl;
    private boolean night=true;
    private boolean traffic;
    private boolean alternateTiles;
    private boolean amoled;

    public PedalNeshanMapSurface(Context context) {
        super(context);
        setClipChildren(true);
        setClipToPadding(true);
        setBackgroundColor(Color.rgb(8,25,39));
        if(BuildConfig.PEDALPRO_NESHAN_ANDROID_ENABLED){
            try {
                // No legacy Neshan library or license file. The module is present
                // only for a release explicitly configured with a MOBILE key.
                Class<?> sdk=Class.forName(
                        "ir.pedalpro.app.nativeapp.NeshanMapLibreBridge");
                sdkBridge=(NeshanMapBridge)sdk.getConstructor(Context.class)
                        .newInstance(context);
                sdkMap=sdkBridge.view();
                addView(sdkMap,new FrameLayout.LayoutParams(-1,-1));
            }catch(Throwable error) {
                sdkBridge=null;sdkMap=null;
                Log.e(TAG,"Neshan MapLibre native renderer could not start: "+
                        error.getClass().getSimpleName());
            }
        }
        overlay=new NativeRouteView(context);
        amoled=context.getSharedPreferences("pedalpro_map_display",Context.MODE_PRIVATE)
                .getBoolean("amoled_enabled",false);
        overlay.setAmoledBatterySaver(amoled);
        if(sdkBridge!=null)sdkBridge.setAmoled(amoled);
        // A projected Canvas track cannot overlay a tilted MapLibre camera.
        // The native bridge owns both vector polylines in the genuine SDK.
        if(sdkBridge!=null){
            overlay.setExternalBasemap(true);
            overlay.setVisibility(View.GONE);
        }
        addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        buildControls();
        if(sdkBridge==null){
            TextView warning=new TextView(context);
            warning.setText("نقشه موقت · کلید MOBILE نشان در Build تنظیم نشده");
            warning.setTextColor(Color.WHITE);warning.setTextSize(10);
            warning.setGravity(Gravity.CENTER);
            warning.setBackground(panelBackground());
            FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-2,dp(32),
                    Gravity.CENTER_HORIZONTAL|Gravity.TOP);
            params.topMargin=dp(12);
            addView(warning,params);
        }
    }

    private void buildControls(){
        LinearLayout controls=new LinearLayout(getContext());
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(5),dp(5),dp(5),dp(5));
        controls.setBackground(panelBackground());
        mapControls=controls;
        controls.addView(control("+","بزرگ‌نمایی",()->zoomBy(1)));
        controls.addView(control("−","کوچک‌نمایی",()->zoomBy(-1)));
        controls.addView(control("◎","بازگشت به موقعیت رکاب‌زن",this::recenter));
        navigationControl=(TextView)control("↑","تغییر جهت نقشه",this::toggleOrientation);
        controls.addView(navigationControl);
        controls.addView(control("▣","فعال یا غیرفعال کردن ذخیره باتری AMOLED",this::toggleBatterySaver));
        if(sdkBridge!=null){
            controls.addView(control("☾","تغییر نقشه روز و شب نشان",()->{
                night=!night;
                sdkBridge.setNight(night);
            }));
        }
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(44),-2,
                Gravity.TOP|Gravity.END);
        lp.setMargins(0,dp(10),dp(10),0);
        addView(controls,lp);
        refreshOrientationLabel();
    }

    private View control(String caption,String description,Runnable click){
        TextView view=new TextView(getContext());
        view.setText(caption);view.setTextSize(23);view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);view.setContentDescription(description);
        view.setClickable(true);view.setFocusable(true);
        view.setOnClickListener(v->click.run());
        view.setBackground(panelBackground());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(34),dp(38));
        lp.setMargins(0,dp(2),0,dp(2));view.setLayoutParams(lp);
        return view;
    }

    private GradientDrawable panelBackground(){
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(Color.argb(225,6,28,45));
        drawable.setCornerRadius(dp(13));
        drawable.setStroke(dp(1),Color.argb(75,164,235,244));
        return drawable;
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}

    public void setFullRideMode(boolean enabled){
        if(mapControls==null)return;
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(44),-2,
                enabled?Gravity.BOTTOM|Gravity.START:Gravity.TOP|Gravity.END);
        if(enabled)lp.setMargins(dp(12),0,0,dp(218));
        else lp.setMargins(0,dp(10),dp(10),0);
        mapControls.setLayoutParams(lp);
        mapControls.setElevation(dp(9));
    }

    private void toggleBatterySaver(){
        amoled=!amoled;
        getContext().getSharedPreferences("pedalpro_map_display",Context.MODE_PRIVATE)
                .edit().putBoolean("amoled_enabled",amoled).apply();
        overlay.setAmoledBatterySaver(amoled);
        if(sdkBridge!=null)sdkBridge.setAmoled(amoled);
        android.widget.Toast.makeText(getContext(),
                amoled?"حالت ذخیره باتری نقشه فعال شد":"حالت ذخیره باتری نقشه خاموش شد",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private void toggleOrientation(){
        if(!overlay.isNavigationMode())overlay.setNavigationMode(true);
        else overlay.setHeadingUpEnabled(!overlay.isHeadingUpEnabled());
        applyCameraMode();
    }

    private void refreshOrientationLabel(){
        if(navigationControl==null)return;
        boolean on=overlay.isNavigationMode(),heading=overlay.isHeadingUpEnabled();
        navigationControl.setText(!on?"↗":heading?"↑":"N");
        navigationControl.setContentDescription(!on?"شروع حالت دنبال‌کردن":
                heading?"جهت حرکت بالا؛ تغییر به شمال بالا":
                        "شمال بالا؛ تغییر به جهت حرکت بالا");
    }

    private void applyCameraMode(){
        refreshOrientationLabel();
        if(sdkBridge!=null)sdkBridge.setNavigation(overlay.isNavigationMode(),
                overlay.isHeadingUpEnabled(),overlay.getHeadingDegrees(),
                overlay.hasHeadingDegrees());
    }

    private void zoomBy(int value){
        if(sdkBridge!=null)sdkBridge.zoomBy(value);
        else overlay.zoomBy(value);
    }
    private void recenter(){
        overlay.recenter();
        if(!overlay.isNavigationMode())overlay.setNavigationMode(true);
        if(sdkBridge!=null)sdkBridge.recenter();
        applyCameraMode();
    }

    public void setNavigationMode(boolean enabled){
        overlay.setNavigationMode(enabled);
        applyCameraMode();
    }
    public void setHeadingDegrees(float heading){
        overlay.setHeadingDegrees(heading);
        applyCameraMode();
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
    /** Explicit fallback when Neshan vector tiles are unavailable on a device. */
    public void useAlternativeTiles(boolean enabled){
        alternateTiles=enabled;
        if(sdkMap!=null)sdkMap.setVisibility(enabled?View.GONE:View.VISIBLE);
        overlay.setVisibility(enabled||sdkBridge==null?View.VISIBLE:View.GONE);
        overlay.setExternalBasemap(!enabled&&sdkBridge!=null);
    }
    public void setNearbyRiders(JSONArray riders){
        if(sdkBridge!=null)sdkBridge.setNearbyRiders(riders);
        else overlay.setNearbyRiders(riders);
    }
    public void focusOn(double lat,double lng){
        if(sdkBridge!=null)sdkBridge.focusOn(lat,lng);
        else overlay.focusOn(lat,lng);
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
