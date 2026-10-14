package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Native PedalPro API transport with persistent PHP session/remember cookies and CSRF.
 * This client never renders website HTML.
 */
public final class PedalProApiClient {
    public static final String BASE_URL = "https://pedalpro.ir/api.php";

    private final SessionStore session;
    private final Context context;

    public PedalProApiClient(Context context) {
        this.context = context.getApplicationContext();
        session = new SessionStore(this.context);
    }

    public JSONObject get(String action) throws Exception {
        return request("GET", action, null, true);
    }

    public JSONObject get(String action, java.util.Map<String, ?> params) throws Exception {
        StringBuilder q = new StringBuilder(action);
        if (params != null && !params.isEmpty()) {
            boolean first = true;
            for (java.util.Map.Entry<String, ?> e : params.entrySet()) {
                if (e.getValue() == null) continue;
                q.append(first ? "&" : "&")
                 .append(java.net.URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8.name()))
                 .append("=")
                 .append(java.net.URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8.name()));
                first = false;
            }
        }
        return request("GET", q.toString(), null, true, true);
    }

    public JSONObject post(String action, JSONObject body) throws Exception {
        return request("POST", action, body == null ? new JSONObject() : body, true);
    }

    public JSONObject postMultipart(String action, java.util.Map<String,String> fields,
                                    Uri fileUri, String fileField, String fileName, String mimeType) throws Exception {
        return postMultipartRetried(action,fields,fileUri,fileField,fileName,mimeType,false);
    }

    private JSONObject postMultipartRetried(String action, java.util.Map<String,String> fields,
                                    Uri fileUri, String fileField, String fileName, String mimeType,
                                    boolean retried419) throws Exception {
        String boundary="----PedalPro"+System.currentTimeMillis();
        HttpURLConnection c=null;
        try{
            URL url=new URL(BASE_URL+"?action="+java.net.URLEncoder.encode(action,StandardCharsets.UTF_8.name()));
            c=(HttpURLConnection)url.openConnection();
            c.setConnectTimeout(12000);c.setReadTimeout(30000);c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Accept","application/json");
            c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
            String cookie=session.cookie();if(!cookie.isBlank())c.setRequestProperty("Cookie",cookie);
            String csrf=session.csrf();if(!csrf.isBlank())c.setRequestProperty("X-CSRF-Token",csrf);
            try(DataOutputStream out=new DataOutputStream(c.getOutputStream())){
                if(fields!=null)for(java.util.Map.Entry<String,String> e:fields.entrySet()){
                    writeTextPart(out,boundary,e.getKey(),e.getValue()==null?"":e.getValue());
                }
                if(fileUri!=null){
                    String safeName=(fileName==null||fileName.isBlank())?"image.jpg":fileName;
                    String type=(mimeType==null||mimeType.isBlank())?"image/jpeg":mimeType;
                    out.writeBytes("--"+boundary+"\r\n");
                    out.writeBytes("Content-Disposition: form-data; name=\""+fileField+"\"; filename=\""+safeName.replace("\"","")+"\"\r\n");
                    out.writeBytes("Content-Type: "+type+"\r\n\r\n");
                    try(InputStream in=context.getContentResolver().openInputStream(fileUri)){
                        if(in==null)throw new IllegalStateException("فایل انتخاب‌شده قابل خواندن نیست.");
                        byte[] buf=new byte[16384];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
                    }
                    out.writeBytes("\r\n");
                }
                out.writeBytes("--"+boundary+"--\r\n");out.flush();
            }
            int status=c.getResponseCode();captureCookies(c);
            String raw=readBody(status>=400?c.getErrorStream():c.getInputStream());
            JSONObject json=new JSONObject(raw==null||raw.isBlank()?"{}":raw);
            String responseCsrf=json.optString("csrf","");
            if(!responseCsrf.isEmpty())session.saveCsrf(responseCsrf);
            // CSRF is checked before the PHP upload handler; retry once is safe.
            if(status==419&&!retried419){
                bootstrap();
                return postMultipartRetried(action,fields,fileUri,fileField,fileName,mimeType,true);
            }
            if(status<200||status>=300||!json.optBoolean("ok",true))throw new IllegalStateException(json.optString("error","HTTP "+status));
            return json;
        }finally{if(c!=null)c.disconnect();}
    }

    public JSONObject postMultipartChunk(
            String action,
            java.util.Map<String,String> fields,
            Uri fileUri,
            String fileField,
            String fileName,
            String mimeType,
            long offset,
            long length) throws Exception {
        return postMultipartChunkRetried(action,fields,fileUri,fileField,fileName,mimeType,offset,length,false);
    }

    private JSONObject postMultipartChunkRetried(
            String action,
            java.util.Map<String,String> fields,
            Uri fileUri,
            String fileField,
            String fileName,
            String mimeType,
            long offset,
            long length,
            boolean retried419) throws Exception {
        String boundary="----PedalProChunk"+System.currentTimeMillis();
        HttpURLConnection c=null;
        try{
            URL url=new URL(BASE_URL+"?action="+java.net.URLEncoder.encode(action,StandardCharsets.UTF_8.name()));
            c=(HttpURLConnection)url.openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(45000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setChunkedStreamingMode(64*1024);
            c.setRequestProperty("Accept","application/json");
            c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
            String cookie=session.cookie();if(!cookie.isBlank())c.setRequestProperty("Cookie",cookie);
            String csrf=session.csrf();if(!csrf.isBlank())c.setRequestProperty("X-CSRF-Token",csrf);

            try(DataOutputStream out=new DataOutputStream(c.getOutputStream())){
                if(fields!=null)for(java.util.Map.Entry<String,String> e:fields.entrySet()){
                    writeTextPart(out,boundary,e.getKey(),e.getValue()==null?"":e.getValue());
                }

                String safeName=(fileName==null||fileName.isBlank())?"chunk.part":fileName.replace(Character.toString((char)34),"");
                String type=(mimeType==null||mimeType.isBlank())?"application/octet-stream":mimeType;
                out.writeBytes("--"+boundary+"\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\""+fileField+"\"; filename=\""+safeName+"\"\r\n");
                out.writeBytes("Content-Type: "+type+"\r\n\r\n");

                try(InputStream in=context.getContentResolver().openInputStream(fileUri)){
                    if(in==null)throw new IllegalStateException("فایل انتخاب‌شده قابل خواندن نیست.");
                    long skipped=0;
                    while(skipped<offset){
                        long n=in.skip(offset-skipped);
                        if(n>0){skipped+=n;continue;}
                        if(in.read()==-1)throw new IllegalStateException("بخش فایل قابل خواندن نیست.");
                        skipped++;
                    }
                    byte[] buf=new byte[64*1024];
                    long remaining=Math.max(0,length);
                    while(remaining>0){
                        int n=in.read(buf,0,(int)Math.min(buf.length,remaining));
                        if(n<0)break;
                        out.write(buf,0,n);
                        remaining-=n;
                    }
                    if(remaining>0)throw new IllegalStateException("بخش فایل ناقص خوانده شد.");
                }

                out.writeBytes("\r\n--"+boundary+"--\r\n");
                out.flush();
            }

            int status=c.getResponseCode();
            captureCookies(c);
            String raw=readBody(status>=400?c.getErrorStream():c.getInputStream());
            JSONObject json=new JSONObject(raw==null||raw.isBlank()?"{}":raw);
            String responseCsrf=json.optString("csrf","");
            if(!responseCsrf.isBlank())session.saveCsrf(responseCsrf);
            if(status==419&&!retried419){
                bootstrap();
                return postMultipartChunkRetried(action,fields,fileUri,fileField,fileName,mimeType,offset,length,true);
            }
            if(status<200||status>=300||!json.optBoolean("ok",true)){
                throw new IllegalStateException(json.optString("error","HTTP "+status));
            }
            return json;
        }finally{
            if(c!=null)c.disconnect();
        }
    }

    private void writeTextPart(DataOutputStream out,String boundary,String name,String value)throws Exception{
        out.writeBytes("--"+boundary+"\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\""+name+"\"\r\n\r\n");
        out.write(value.getBytes(StandardCharsets.UTF_8));out.writeBytes("\r\n");
    }

    public JSONObject bootstrap() throws Exception {
        JSONObject data = get("bootstrap");
        String csrf = data.optString("csrf", "");
        if (!csrf.isBlank()) session.saveCsrf(csrf);
        JSONObject user = data.optJSONObject("user");
        session.saveUserJson(user == null ? "" : user.toString());
        return data;
    }

    public JSONObject login(String username, String password, boolean remember) throws Exception {
        // Obtain a fresh PHP session + CSRF before auth_login.
        bootstrap();
        JSONObject body = new JSONObject()
                .put("username", username == null ? "" : username.trim())
                .put("password", password == null ? "" : password)
                .put("remember", remember);
        JSONObject result = post("auth_login", body);
        bootstrap();
        return result;
    }

    public void logout() throws Exception {
        try { post("auth_logout", new JSONObject()); }
        finally { session.clear(); }
    }

    private JSONObject request(String method, String action, JSONObject body, boolean retry419) throws Exception {
        return request(method, action, body, retry419, false);
    }

    private JSONObject request(String method, String action, JSONObject body, boolean retry419, boolean actionContainsQuery) throws Exception {
        HttpURLConnection c = null;
        try {
            String query;
            if (actionContainsQuery) {
                int amp = action.indexOf('&');
                String name = amp >= 0 ? action.substring(0, amp) : action;
                String rest = amp >= 0 ? action.substring(amp) : "";
                query = "action=" + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8.name()) + rest;
            } else {
                query = "action=" + java.net.URLEncoder.encode(action, StandardCharsets.UTF_8.name());
            }
            URL url = new URL(BASE_URL + "?" + query);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(18000);
            c.setRequestMethod(method);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Cache-Control", "no-cache");

            String cookie = session.cookie();
            if (!cookie.isBlank()) c.setRequestProperty("Cookie", cookie);

            if (!"GET".equals(method)) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                String csrf = session.csrf();
                if (!csrf.isBlank()) c.setRequestProperty("X-CSRF-Token", csrf);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
            }

            int status = c.getResponseCode();
            captureCookies(c);
            String raw = readBody(status >= 400 ? c.getErrorStream() : c.getInputStream());
            JSONObject json = new JSONObject(raw == null || raw.isBlank() ? "{}" : raw);

            String csrf = json.optString("csrf", "");
            if (!csrf.isBlank()) session.saveCsrf(csrf);

            if (status == 419 && retry419) {
                bootstrap();
                return request(method, action, body, false, actionContainsQuery);
            }
            if (status < 200 || status >= 300 || json.optBoolean("ok", true) == false) {
                throw new IllegalStateException(json.optString("error", "HTTP " + status));
            }
            return json;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void captureCookies(HttpURLConnection c) {
        Map<String, List<String>> headers = c.getHeaderFields();
        if (headers == null) return;
        StringBuilder merged = new StringBuilder();
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() == null || !"set-cookie".equalsIgnoreCase(e.getKey())) continue;
            for (String line : e.getValue()) {
                if (line == null || line.isBlank()) continue;
                String pair = line.split(";", 2)[0].trim();
                if (pair.isBlank()) continue;
                if (merged.length() > 0) merged.append("; ");
                merged.append(pair);
            }
        }
        if (merged.length() > 0) {
            String previous = session.cookie();
            session.saveCookie(mergeCookiePairs(previous, merged.toString()));
        }
    }

    private String mergeCookiePairs(String oldCookie, String incoming) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        addCookieString(map, oldCookie);
        addCookieString(map, incoming);
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (out.length() > 0) out.append("; ");
            out.append(e.getKey()).append("=").append(e.getValue());
        }
        return out.toString();
    }

    private void addCookieString(java.util.LinkedHashMap<String, String> map, String cookie) {
        if (cookie == null) return;
        for (String part : cookie.split(";")) {
            String p = part.trim();
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            map.put(p.substring(0, eq).trim(), p.substring(eq + 1).trim());
        }
    }

    private String readBody(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) out.append(line);
        }
        return out.toString();
    }
}
