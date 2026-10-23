package ir.pedalpro.app.nativeapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/** Dedicated native map selector; never starts or modifies GPS tracking. */
public final class NativeGroupPointPicker {
    public interface Listener{void selected(double latitude,double longitude);}
    private NativeGroupPointPicker(){}
    public static void show(Activity activity,Listener callback){
        LinearLayout shell=new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(12,10,12,10);
        TextView status=new TextView(activity);
        status.setText("روی نقشه، محل شروع برنامه را لمس کنید.");
        status.setTextColor(android.graphics.Color.WHITE);
        status.setTextSize(13);
        shell.addView(status);
        NativeRouteView map=new NativeRouteView(activity);
        shell.addView(map,new LinearLayout.LayoutParams(-1,Math.round(activity.getResources().getDisplayMetrics().density*350)));
        final double[] chosen={Double.NaN,Double.NaN};
        double startLat=32.4279,startLng=53.6880;
        try{
            if(activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
                ||activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                LocationManager manager=(LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
                Location last=manager==null?null:manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if(last==null&&manager!=null)last=manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if(last!=null&&Double.isFinite(last.getLatitude())&&Double.isFinite(last.getLongitude())){
                    startLat=last.getLatitude();startLng=last.getLongitude();
                }
            }
        }catch(SecurityException ignored){}
        // Center is not an accepted selection until the user explicitly taps.
        map.setTarget(startLat,startLng);
        map.setOnMapTapListener((lat,lng)->{
            chosen[0]=lat;chosen[1]=lng;
            map.setTarget(lat,lng);
            status.setText(String.format(Locale.US,"%.6f, %.6f",lat,lng));
        });
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("انتخاب موقعیت شروع")
                .setView(shell)
                .setNegativeButton("انصراف",null)
                .setPositiveButton("تأیید",(d,w)->{})
                .create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(confirm->{
            if(!Double.isFinite(chosen[0])||!Double.isFinite(chosen[1])){
                status.setText("برای تأیید، ابتدا نقطه‌ای را روی نقشه انتخاب کنید.");
                return;
            }
            callback.selected(chosen[0],chosen[1]);
            dialog.dismiss();
        }));
        dialog.show();
    }
}
