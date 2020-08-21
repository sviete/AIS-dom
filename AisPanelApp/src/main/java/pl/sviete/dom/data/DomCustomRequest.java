package pl.sviete.dom.data;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.HttpHeaderParser;

public class DomCustomRequest extends Request<JSONObject> {

    private Listener<JSONObject> listener;
    private Map<String, String> heders;
    private String body;

    public DomCustomRequest(int method, String url, Map<String, String> heders, String body,
                            Listener<JSONObject> reponseListener, ErrorListener errorListener) {
        super(method, url, errorListener);
        this.listener = reponseListener;
        this.body = body;
        this.heders = heders;
    }

    public DomCustomRequest(int method, String url, String body,
                            Listener<JSONObject> reponseListener, ErrorListener errorListener) {
        super(method, url, errorListener);
        this.listener = reponseListener;
        this.body = body;
    }

    public Map<String, String> getHeaders() throws AuthFailureError {
        if (heders == null){
            Map<String, String> h = new HashMap<String, String>();
            h.put("Content-Type", "application/json; charset=UTF-8");
            return h;
        }
        return heders;
    }

    public byte[] getBody()
            throws com.android.volley.AuthFailureError {
        try {
            return body.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    };


    @Override
    protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
        try {
            String jsonString = new String(response.data,
                    HttpHeaderParser.parseCharset(response.headers));
            return Response.success(new JSONObject(jsonString),
                    HttpHeaderParser.parseCacheHeaders(response));
        } catch (UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        } catch (JSONException je) {
            return Response.error(new ParseError(je));
        }
    }

    @Override
    protected void deliverResponse(JSONObject response) {
        // TODO Auto-generated method stub
        listener.onResponse(response);
    }
}