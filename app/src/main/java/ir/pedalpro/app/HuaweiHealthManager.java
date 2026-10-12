package ir.pedalpro.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.huawei.hmf.tasks.Task;
import com.huawei.hms.hihealth.data.Scopes;
import com.huawei.hms.support.api.entity.auth.Scope;
import com.huawei.hms.support.hwid.HuaweiIdAuthManager;
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParams;
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParamsHelper;
import com.huawei.hms.support.hwid.result.AuthHuaweiId;
import com.huawei.hms.support.hwid.service.HuaweiIdAuthService;
import com.huawei.hihealth.error.HiHealthError;
import com.huawei.hihealthkit.data.HiHealthExtendScope;
import com.huawei.hihealthkit.data.store.HiHealthDataStore;
import com.huawei.hihealthkit.data.store.HiRealTimeListener;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class HuaweiHealthManager {
    public static final int REQ_HUAWEI_SIGN_IN = 12131;

    private final MainActivity activity;
    private HuaweiIdAuthService authService;
    private boolean running = false;

    HuaweiHealthManager(MainActivity activity) {
        this.activity = activity;
    }

    boolean isConfigured() {
        String id = BuildConfig.HUAWEI_APP_ID;
        return id != null && !id.trim().isEmpty() && !"0".equals(id.trim());
    }

    void connectAndStart() {
        if (!isConfigured()) {
            emit("config_required", "Huawei App ID هنوز در Build تنظیم نشده است.");
            return;
        }
        if (!isHuaweiHealthInstalled()) {
            emit("health_missing", "Huawei Health روی گوشی نصب نیست.");
            return;
        }
        List<Scope> scopes = new ArrayList<>();
        scopes.add(new Scope(Scopes.HEALTHKIT_HEARTRATE_READ));
        scopes.add(new Scope(HiHealthExtendScope.HEALTHKIT_EXTEND_REALTIME_HEART_READ));

        HuaweiIdAuthParams params = new HuaweiIdAuthParamsHelper(HuaweiIdAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
                .setIdToken()
                .setAccessToken()
                .setScopeList(scopes)
                .createParams();

        authService = HuaweiIdAuthManager.getService(activity, params);
        Task<AuthHuaweiId> task = authService.silentSignIn();
        task.addOnSuccessListener(account -> startRealtimeHeartRate())
                .addOnFailureListener(e -> {
                    try {
                        Intent i = authService.getSignInIntent();
                        activity.startActivityForResult(i, REQ_HUAWEI_SIGN_IN);
                    } catch (Throwable t) {
                        emit("auth_failed", "ورود به حساب Huawei آغاز نشد.");
                    }
                });
    }

    void handleActivityResult(int requestCode, Intent data) {
        if (requestCode != REQ_HUAWEI_SIGN_IN) return;
        if (data == null) {
            emit("auth_cancelled", "ورود Huawei لغو شد.");
            return;
        }
        Task<AuthHuaweiId> result = HuaweiIdAuthManager.parseAuthResultFromIntent(data);
        result.addOnSuccessListener(account -> startRealtimeHeartRate())
                .addOnFailureListener(e -> emit("auth_failed", "مجوز Huawei Health صادر نشد."));
    }

    void openHuaweiHealth() {
        try {
            Intent i = activity.getPackageManager().getLaunchIntentForPackage("com.huawei.health");
            if (i != null) {
                activity.startActivity(i);
                return;
            }
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://appgallery.huawei.com/app/C10414141")));
        } catch (Throwable e) {
            emit("health_open_failed", "باز کردن Huawei Health ممکن نشد.");
        }
    }

    void openHuaweiHealthSync() {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("huaweischeme://healthapp/router/sync")));
        } catch (Throwable e) {
            openHuaweiHealth();
        }
    }

    void stop() {
        if (!running) return;
        try {
            HiHealthDataStore.stopReadingHeartRate(activity, new HiRealTimeListener() {
                @Override public void onResult(int state) {
                    running = false;
                    emit("stopped", state == HiHealthError.SUCCESS ? "دریافت ضربان Huawei متوقف شد." : "توقف Huawei Health با خطا انجام شد.");
                }
                @Override public void onChange(int resultCode, String value) { }
            });
        } catch (Throwable e) {
            running = false;
        }
    }

    String statusJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("configured", isConfigured());
            o.put("installed", isHuaweiHealthInstalled());
            o.put("running", running);
            o.put("appId", isConfigured() ? "configured" : "missing");
        } catch (Throwable ignored) { }
        return o.toString();
    }

    private boolean isHuaweiHealthInstalled() {
        try {
            activity.getPackageManager().getPackageInfo("com.huawei.health", 0);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private void startRealtimeHeartRate() {
        try {
            HiHealthDataStore.startReadingHeartRate(activity, new HiRealTimeListener() {
                @Override public void onResult(int state) {
                    if (state == HiHealthError.SUCCESS) {
                        running = true;
                        emit("running", "Huawei Health متصل شد؛ ضربان زنده در حال دریافت است.");
                    } else if (state == HiHealthError.ERR_DEVICE_NOT_CONNECTED) {
                        emit("watch_not_connected", "ساعت به Huawei Health متصل نیست.");
                    } else if (state == HiHealthError.ERR_PERMISSION_EXCEPTION) {
                        emit("permission_required", "مجوز Heart Rate در Huawei Health داده نشده است.");
                    } else if (state == HiHealthError.ERR_BETA_SCOPE_EXCEPTION) {
                        emit("scope_not_approved", "مجوز آزمایشی Real-time Heart Rate در Huawei Developer فعال نشده است.");
                    } else {
                        emit("start_failed", "شروع دریافت ضربان Huawei ناموفق بود. کد: " + state);
                    }
                }

                @Override public void onChange(int resultCode, String value) {
                    if (resultCode != HiHealthError.SUCCESS || value == null) return;
                    try {
                        JSONObject j = new JSONObject(value);
                        int bpm = j.optInt("hr_info", 0);
                        long ts = j.optLong("time_info", System.currentTimeMillis());
                        int confidence = j.optInt("heartRateCredibility", -3);
                        if (bpm >= 30 && bpm <= 240) {
                            activity.onHuaweiHeartRate(bpm, ts, confidence);
                        }
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable e) {
            emit("start_exception", "Huawei Health SDK آماده اجرا نیست.");
        }
    }

    private void emit(String state, String message) {
        activity.runOnUiThread(() -> {
            try { Toast.makeText(activity, message, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) { }
            activity.emitHuaweiHealthStatus(state, message, running);
        });
    }
}
