package ir.pedalpro.app.nativeapp;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

/** Bicycle-only routing contract; a car route is never accepted as a fallback. */
final class CyclingDirectionsClient {
    static final class Route {
        final JSONArray points;
        final double distanceMeters;
        final long durationSeconds;
        final String instruction;
        Route(JSONArray points,double metres,long seconds,String instruction){
            this.points=points;distanceMeters=metres;durationSeconds=seconds;this.instruction=instruction;
        }
    }
    private CyclingDirectionsClient(){}
    static Route fetch(PedalProApiClient api,double fromLat,double fromLng,double toLat,double toLng) throws Exception {
        if(!valid(fromLat,fromLng)||!valid(toLat,toLng))
            throw new IllegalArgumentException("مختصات مسیر دوچرخه معتبر نیست.");
        Map<String,Object> query=new HashMap<>();
        query.put("from_lat",fromLat);query.put("from_lng",fromLng);
        query.put("to_lat",toLat);query.put("to_lng",toLng);
        JSONObject data=api.get("pp_cycling_directions",query);
        if(!"cycling-regular".equals(data.optString("profile")))
            throw new IllegalStateException("پاسخ مسیریابی مخصوص دوچرخه نیست.");
        JSONArray all=data.optJSONArray("points");
        if(all==null||all.length()<2)throw new IllegalStateException("مسیر دوچرخه پیدا نشد.");
        JSONArray accepted=new JSONArray();
        for(int i=0;i<all.length();i++){
            JSONObject p=all.optJSONObject(i);if(p==null)continue;
            double lat=p.optDouble("lat",Double.NaN),lng=p.optDouble("lng",Double.NaN);
            if(valid(lat,lng))accepted.put(new JSONObject().put("lat",lat).put("lng",lng));
        }
        if(accepted.length()<2)throw new IllegalStateException("مسیر دوچرخه ناقص است.");
        JSONArray steps=data.optJSONArray("steps");
        JSONObject step=steps==null?null:steps.optJSONObject(0);
        return new Route(accepted,Math.max(0,data.optDouble("distance_m",0)),
                Math.max(0,data.optLong("duration_s",0)),step==null?"":step.optString("instruction",""));
    }
    private static boolean valid(double lat,double lng){
        return Double.isFinite(lat)&&Double.isFinite(lng)&&Math.abs(lat)<=85&&Math.abs(lng)<=180;
    }
}
