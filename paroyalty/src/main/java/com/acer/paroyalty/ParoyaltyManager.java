package com.acer.paroyalty;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
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
    static final String PATH_VERIFY_LICENSE = "paroyaltyApi/api/V1/license/verify";
    static final String PATH_GET_ADDATA = "paroyaltyApi/api/V1/ad/getaddata";
    static final String PATH_TRACK_LOG = "paroyaltyApi/api/V1/ad/tracklog";
    static final String SERVER_USERNAME = "aiSage2020";
    static final String SERVER_PASSWORD = "aiSage2020";
    static final String TAG = "ParoyaltyManager";


    boolean mIsDev = false;
    RequestQueue mVolleyQueue;
    String mCurrntUUID;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBleScanner;
    boolean mIsScaning = false;
    long mLastGetAdTime = 0;
    int mLastStoreId = 0;
    boolean isVerifyBlePackage = false;
    String mAppId;
    AdDataCallBack mAdDataCallBack;
    long mTimePerios = 5 * 60 * 1000;
    boolean isPassLicenseVerfiy = false;
    long mLastShowReceiveLog = 0;


    public ParoyaltyManager(Context context) {
        mContext = context;
        mVolleyQueue = Volley.newRequestQueue(mContext);
    }

    public void initSDK(int environment, String appid, String licenseKey, final LicenseVerifyCallBack callBack) {
        if (environment == 0) {
            mIsDev = true;
        }
        mAppId = appid;
        JSONObject postparams = new JSONObject();
        try {
            postparams.put("appId", appid);
            postparams.put("appKey", licenseKey);
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
                                    callBack.onVerifyResult(Status.LICENSE_EXPIRED,"");
                                } else {
                                    mCurrntUUID = response.getString("uuid");
                                    isPassLicenseVerfiy = true;
                                    callBack.onVerifyResult(Status.SUCCESS, mCurrntUUID);
                                    Log.v(TAG, "verify PASS");
                                }
                            } else if (errorCode == -1) {
                                callBack.onVerifyResult(Status.LICENSE_ERROR,"");
                                Log.v(TAG, "LICENSE_ERROR");
                            } else {
                                callBack.onVerifyResult(Status.OTHERS_ERROR,"");
                                Log.v(TAG, "OTHERS_ERROR");
                            }
                        } catch (JSONException e) {
                            callBack.onVerifyResult(Status.OTHERS_ERROR, "");
                            Log.v(TAG, "init sdk json exception");
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        callBack.onVerifyResult(Status.NETWROK_ERROR, "");
                        Log.v(TAG, "Volley error");

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

    public int startReceiver() {
        if (!isPassLicenseVerfiy) {
            return -1;
        }
        if (mBluetoothAdapter == null) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        mIsScaning = true;

        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        mBleScanner = mBluetoothAdapter.getBluetoothLeScanner();
        ScanFilter.Builder builder1 = new ScanFilter.Builder();
        ScanFilter filter = builder1.build();
        //final List<ScanFilter> filters = Collections.singletonList(new ScanFilter.Builder().build());
        List<ScanFilter> filters_v2 = new ArrayList<>();
//
        byte[] uuidByte = hexToByteArray(mCurrntUUID.replace("-", ""));


        byte[] preData = {(byte) 0x02, (byte) 0x15};
        byte[] postData = {(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04};


        byte[] payload = new byte[22];
        System.arraycopy(preData, 0, payload, 0, preData.length);
        System.arraycopy(uuidByte, 0, payload, preData.length, uuidByte.length);
        System.arraycopy(postData, 0, payload, preData.length + uuidByte.length, postData.length);
        Log.v(TAG, "payload=" + ByteTools.bytesToHex(payload));

        byte[] payloadMask = {(byte) 0xFF, (byte) 0xFF, // this makes it a iBeacon
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, // uuid
                (byte) 0x00, (byte) 0x00,  // Major
                (byte) 0x00, (byte) 0x00}; // Minor
        ScanFilter scanFilter = new ScanFilter.Builder()
                .setManufacturerData(76, payload, payloadMask)
                .build();
        filters_v2.add(scanFilter);

        mBleScanner.startScan(filters_v2, builder.build(), mScanCallback);

        return 0;
    }

    public void setGetAdDataPeriod(int sec) {
        mTimePerios = sec * 1000;

    }

    public void stopReceiver() {
        if (mBleScanner != null) {
            mBleScanner.stopScan(mScanCallback);
        }
    }

    public void release() {
        mContext = null;
    }

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            //Log.v(TAG, "receive info");
            if (System.currentTimeMillis() > mLastShowReceiveLog + 5*1000) {
                Log.v(TAG, "receive info");
                mLastShowReceiveLog = System.currentTimeMillis();
            }
            //Log.v("beacon", "rawdata=" + ByteTools.bytesToHexWithSpaces(result.getScanRecord().getBytes()));
            parseResult(result);
        }

        public void onBatchScanResults(List<ScanResult> results) {
        }

        public void onScanFailed(int errorCode) {
        }
    };

    private void parseResult(ScanResult result) {
        long currentTime = System.currentTimeMillis();
        if (currentTime < (mLastGetAdTime + mTimePerios)) {
            return;
        }
        Log.v(TAG, "parseResult");
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        Date currentLocalTime = cal.getTime();

        DateFormat date = new SimpleDateFormat("MM-dd-HH");
        date.setTimeZone(TimeZone.getTimeZone("GMT"));
        String localTime = date.format(currentLocalTime);
        int day = Integer.parseInt(localTime.split("-")[1]);
        int month = Integer.parseInt(localTime.split("-")[0]);
        int hours = Integer.parseInt(localTime.split("-")[2]);
        //Log.v(TAG, "hours=" + (hours << 2));
        //Log.v(TAG, "day=" + (hours << 7));
        //Log.v(TAG, "month=" + (month << 12));
        int orignBitData = (hours << 2) + (day << 7) + (month << 12);
        //Log.v(TAG, "orignBitData=" + orignBitData);

        int bit8_p1 = orignBitData >> 8;
        int bit8_p2 = orignBitData & 0x000000FF;
        //Log.v(TAG, "bit8_p1=" + bit8_p1);
        //Log.v(TAG, "bit8_p2=" + bit8_p2);
        int xor8bit = bit8_p1 ^ bit8_p2;
        //Log.v(TAG, "xor8bit=" + xor8bit);

        int bit4_p1 = xor8bit >> 4;
        int bit4_p2 = xor8bit & 0x0000000F;
        int xor4bit = bit4_p1 ^ bit4_p2;
        Log.v(TAG, "xor4bit=" + xor4bit);

        int bit2_p1 = xor4bit >> 2;
        int bit2_p2 = xor4bit & 0x00000003;
        int xor2bit = bit2_p1 ^ bit2_p2;
        //Log.v(TAG, "xor2bit=" + xor2bit);

        //Log.v(TAG, "month=" + month + ",day=" + day + ", hours=" + hours);
        int major = 0x00 << 24 | 0x00 << 16 | (result.getScanRecord().getBytes()[25] & 0xff) << 8 | (result.getScanRecord().getBytes()[26] & 0xff);
        //Log.v("test", "major=" + major);
        int storeid = 0x00 << 24 | 0x00 << 16 | (result.getScanRecord().getBytes()[27] & 0xff) << 8 | (result.getScanRecord().getBytes()[28] & 0xff);
        Log.v(TAG, "storeid=" + storeid);
        int checkBit = major & 0x00000003;
        if (!isVerifyBlePackage || checkBit == xor2bit) {
            getAdData(storeid);
        }
    }

    private String getSerialId() {
        return Installation.id(mContext);
    }

    private void getAdData(int storeid) {
        if (mAdDataCallBack == null) {
            return;
        }
        mLastGetAdTime = System.currentTimeMillis();
        String url = (mIsDev ? DEV_HOST : PRODUCTION_HOST) + PATH_GET_ADDATA;
        JSONObject postparams = new JSONObject();

        try {
            postparams.put("appId", mAppId);
            postparams.put("deviceId", getSerialId());
            Log.v(TAG, "getSerialId=" + getSerialId());
            String currentHours = new SimpleDateFormat("HH", Locale.getDefault()).format(new Date());
            postparams.put("hours", Integer.parseInt(currentHours));
            postparams.put("storeId", storeid);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.POST,
                url, postparams,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            int errorCode = response.getInt("errorCode");
                            if (errorCode == 0 & response.getJSONArray("adData").length() > 0) {
                                Log.v(TAG, response.toString());
                                JSONArray array = response.getJSONArray("adData");
                                AdData[] adData = new AdData[array.length()];
                                for (int i = 0; i < array.length(); i++) {
                                    adData[i] = AdData.parseFromJson(array.getJSONObject(i));
                                }
                                mAdDataCallBack.onReceiveAdData(adData);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.v(TAG, "volley error:" + error.toString());
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return createBasiAuthUser(SERVER_USERNAME, SERVER_PASSWORD);
            }
        };
        mVolleyQueue.add(jsonObjReq);

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

    public void registerAdDataCallBack(AdDataCallBack callBack) {
        mAdDataCallBack = callBack;
    }

    public void clickAd(String token, final ClickAdCallBack callBack) {
        if (!isPassLicenseVerfiy) {
            callBack.onClickAdResult(-1, "Verify license fail");
            return;
        }
        JSONObject postparams = new JSONObject();
        try {
            postparams.put("trackToken", token);
            postparams.put("timestamp", System.currentTimeMillis() / 1000);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        //Log.v("test", postparams.toString());
        String url = (mIsDev ? DEV_HOST : PRODUCTION_HOST) + PATH_TRACK_LOG;
        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.POST,
                url, postparams,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.v(TAG, "click:" + response);
                        try {
                            int errorCode = response.getInt("errorCode");
                            if (errorCode == 0) {
                                callBack.onClickAdResult(0, "");
                            } else {
                                callBack.onClickAdResult(-1, "server error");
                            }
                        } catch (JSONException e) {
                            callBack.onClickAdResult(-1, "Jsonobject error");
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        callBack.onClickAdResult(-1, "VolleyError:" + error.toString());
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return createBasiAuthUser(SERVER_USERNAME, SERVER_PASSWORD);
            }
        };
        mVolleyQueue.add(jsonObjReq);
    }
}
