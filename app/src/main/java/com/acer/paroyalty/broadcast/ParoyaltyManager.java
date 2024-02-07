package com.acer.paroyalty.broadcast;


import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;


import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class ParoyaltyManager {
    Context mContext;
    static final String DEV_HOST = "https://paroyalty-dtp.acervcon.com/";
    static final String PRODUCTION_HOST = "https://paroyalty-dtp.acervcon.com/";
    static final String PATH_GET_STOREID = "paroyaltyApi/api/V1/player/getstoreid";
    static final String PATH_VERIFY_LICENSE = "paroyaltyApi/api/V1/license/verify";
    static final String PATH_GET_ADDATA = "paroyaltyApi/api/V1/ad/getaddata";
    static final String PATH_TRACK_LOG = "paroyaltyApi/api/V1/ad/tracklog";
    static final String SERVER_USERNAME = "aiSage2020";
    static final String SERVER_PASSWORD = "aiSage2020";
    static final String TAG = "ParoyaltyManager";
    String mInitFilePath = "Android/data/com.acer.paroyalty.broadcast/files/init/init.json";


    boolean mIsDev = false;
    RequestQueue mVolleyQueue;


    long mTimePerios = 5 * 60 * 1000;
    boolean isPassLicenseVerfiy = false;
    long mLastShowReceiveLog = 0;
    Handler handler = new Handler();

    private Runnable getUuidRunnable = new Runnable() {
        public void run() {
            Log.v(TAG, "getUuidRunnable");
        }
    };

    public ParoyaltyManager(Context context, int environment) {
        mContext = context;
        mVolleyQueue = Volley.newRequestQueue(mContext);
        if (environment == 0) {
            mIsDev = true;
        }
    }

    public void getUUID(final BroadCastInfoCallBack callBack) {
        File file = new File(Environment.getExternalStorageDirectory(), mInitFilePath);
        Log.v(TAG, "file path:" + file.getAbsolutePath());
        if(!file.exists()) {
            Log.v(TAG, "file is null");
            callBack.onResult(false, null);
        } else {
            InputStream inputStream = null;
            try {
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                String content="";
                while ((line = br.readLine()) != null) {
                    content = content + line;
                }
                br.close();
                Log.v(TAG, "content="+ content);
                JSONObject projectJson = new JSONObject(content);
                String appId = projectJson.getString("appId");
                String appKey = projectJson.getString("appKey");
                JSONObject postparams = new JSONObject();
                try {
                    postparams.put("appId", appId);
                    postparams.put("appKey", appKey);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.v("test", postparams.toString());
                String url = (mIsDev ? DEV_HOST : PRODUCTION_HOST) + PATH_VERIFY_LICENSE;
                JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.POST,
                        url, postparams,
                        new Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {
                                try {
                                    int errorCode = response.getInt("errorCode");
                                    if (errorCode == 0) {
                                        Long time = System.currentTimeMillis();
                                        if (time < response.getLong("expirationDate")) {
                                            callBack.onResult(false, null);
                                        } else {
                                            BroadCastInfo info  = new BroadCastInfo();
                                            info.uuid = response.getString("uuid");
                                            info.expirationDate = response.getLong("expirationDate");
                                            info.resumBroadcast = response.getInt("resumBroadcast");
                                            info.stopBroadcast = response.getInt("stopBroadcast");
                                            getStoreId(callBack, info);
                                        }
                                    } else {
                                        callBack.onResult(false, null);
                                    }
                                } catch (JSONException e) {
                                    callBack.onResult(false, null);
                                    Log.v(TAG, "init sdk json exception");
                                    e.printStackTrace();
                                }
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                callBack.onResult(false, null);
                                Log.v(TAG, "Volley error");

                            }
                        }) {
                    @Override
                    public Map<String, String> getHeaders() throws AuthFailureError {
                        return createBasiAuthUser(SERVER_USERNAME, SERVER_PASSWORD);
                    }
                };
                mVolleyQueue.add(jsonObjReq);

            } catch (Exception e) {
                callBack.onResult(false, null);
            }
        }


    }


    public void getStoreId(final BroadCastInfoCallBack callBack, final BroadCastInfo info) {
        JSONObject postparams = new JSONObject();
        try {
            postparams.put("boxId",getSerialId() );
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.v(TAG, "getSerialId=" + getSerialId());
        String url = (mIsDev ? DEV_HOST : PRODUCTION_HOST) + PATH_GET_STOREID;
        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.POST,
                url, postparams,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            int errorCode = response.getInt("errorCode");
                            if (errorCode == 0) {
                                info.storeId = response.getInt("storeId");
                                callBack.onResult(true, info);
                            } else {
                                callBack.onResult(false, null);
                            }
                        } catch (JSONException e) {
                            callBack.onResult(false, null);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        callBack.onResult(false, null);

                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return createBasiAuthUser(SERVER_USERNAME, SERVER_PASSWORD);
            }
        };
        mVolleyQueue.add(jsonObjReq);

    }



    private Map<String, String> createBasiAuthUser(String user, String pass) {
        Map<String, String> headerMap = new HashMap<String, String>();
        String creadentials = user + ":" + pass;
        String base64Credentials = Base64.encodeToString(creadentials.getBytes(), Base64.NO_WRAP);
        headerMap.put("Authorization", "Basic " + base64Credentials);
        return headerMap;
    }




    private String getSerialId() {
        return Build.SERIAL;
    }




    public static byte[] hexToByteArray(String hex) {
        hex = hex.length() % 2 != 0 ? "0" + hex : hex;
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            int index = i * 2;
            int v = Integer.parseInt(hex.substring(index, index + 2), 16);
            b[i] = (byte) v;
        }
        return b;
    }

    public void release() {
    }


    public class BroadCastInfo {
        String uuid;
        Long expirationDate;
        int storeId;
        int resumBroadcast;
        int stopBroadcast;

    }

    public interface BroadCastInfoCallBack {
        public void onResult(boolean success, BroadCastInfo info);
    }
}
