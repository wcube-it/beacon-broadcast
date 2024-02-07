package com.acer.paroyalty.broadcast;


import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.acer.paroyalty.AdData;
import com.acer.paroyalty.AdDataCallBack;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;


public class PaRoyaltyBroadCastService extends Service implements AdDataCallBack {

    ParoyaltyManager manager;
    private String TAG = "PaRoyaltyBroadCastService";
    BluetoothLeAdvertiser advertiser;
    boolean isFirstTime = true;
    String mUUID = "0dd70a1c-bc7c-11ec-8422-0242ac120002";
    int mStoreId = 100;
    String mInitFilePath = "Android/data/com.acer.paroyalty.broadcast/files/init/init.json";
    int periodUpdate = 3 * 60 * 1000;
    AdvertiseCallback mAdvertiseCallback = null;
    private final int MSG_ID_BROADCAST = 0;
    private final int MSG_ID_STOPBROADCAST = 1;
    private  int mStopBroadCastTime = 6000;
    private int mResumeBroadCastTime = 10;
    private Logger mLogger = LoggerFactory.getLogger("PaRoyaltyBroadCastService");

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            mLogger.debug("handleMessage:" + msg.what);
            switch (msg.what) {
                case MSG_ID_BROADCAST :
                    if (getBluetoothStatus()) {
                        startBroadCast(mUUID);
                        Long time = Math.min(getUpdateTime(), mStopBroadCastTime * 1000);
                        mLogger.debug("sendEmptyMessageDelayed  MSG_ID_BROADCAST:" + time);
                        mHandler.sendEmptyMessageDelayed(MSG_ID_STOPBROADCAST, time);
                    } else {
                        enableBluetooth();
                        mHandler.sendEmptyMessageDelayed(MSG_ID_BROADCAST, 5*1000);
                    }
                    break;
                case MSG_ID_STOPBROADCAST :
                    stopBroadCast();
                    mLogger.debug("sendEmptyMessageDelayed  MSG_ID_STOPBROADCAST");
                    mHandler.sendEmptyMessageDelayed(MSG_ID_BROADCAST, mResumeBroadCastTime * 1000);
                    break;
                default :
                    break;
            }
        }
    };


    private long getUpdateTime() {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            Date currentLocalTime = cal.getTime();
            DateFormat date = new SimpleDateFormat("mm:ss");
            date.setTimeZone(TimeZone.getTimeZone("GMT"));
            String localTime = date.format(currentLocalTime);
            int min = Integer.parseInt(localTime.split(":")[0]);
            int sec = Integer.parseInt(localTime.split(":")[1]);
            long delayedTime = (60 * (59 - min) + (60 - sec)) * 1000;
            return delayedTime;
    }

    Handler handler = new Handler();


    private Runnable getUuidRunnable = new Runnable() {
        public void run() {
            mLogger.debug("getUuidRunnable");
            // scheduled another events to be in 10 seconds later
            readUUID();
        }
    };

    private void readUUID() {
        mLogger.debug("function readUUID");
        manager.getUUID(new ParoyaltyManager.BroadCastInfoCallBack() {
            @Override
            public void onResult(boolean success, ParoyaltyManager.BroadCastInfo info) {
                Log.v(TAG, "onResult:" + success);
                mLogger.debug("onResult:" + success);
                if (success) {
                    mUUID = info.uuid;
                    mStoreId = info.storeId;
                    mStopBroadCastTime = info.stopBroadcast;
                    mResumeBroadCastTime = info.resumBroadcast;
                    mLogger.debug("mUUID:" + mUUID);
                    mLogger.debug("mStoreId:" + mStoreId);
                    mHandler.sendEmptyMessage(MSG_ID_BROADCAST);
                } else {
                    mLogger.debug("retry");
                    handler.postDelayed(getUuidRunnable, 1 * 60 * 1000);
                }
            }
        });
    }


    public PaRoyaltyBroadCastService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());

        PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT);
        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent);
        mLogger.debug("onTaskRemoved");
        super.onTaskRemoved(rootIntent);
    }


    @Override
    public void onCreate() {
        super.onCreate();
        registerBroadcastReceivers();
        startRunningInForeground();
        if (!getBluetoothStatus()) {
            enableBluetooth();
        }

        mLogger.debug("onCreate");
        manager = new ParoyaltyManager(this, 0);
        readUUID();
        //startBroadCast(mUUID);

    }


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        // Bluetooth has been turned off;
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        // Bluetooth is turning off;
                        break;
                    case BluetoothAdapter.STATE_ON:
                        // Bluetooth is on
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        // Bluetooth is turning on
                        break;
                }
            }
        }
    };

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

    private void stopBroadCast() {
        mLogger.debug("stopBroadCast");
        if (advertiser != null && mAdvertiseCallback != null) {
            advertiser.stopAdvertising(mAdvertiseCallback);
        }
    }


    private void startBroadCast(String uuid) {
        mLogger.debug("startBroadCast:" + uuid);

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        Date currentLocalTime = cal.getTime();

        DateFormat date = new SimpleDateFormat("MM-dd-HH");
        date.setTimeZone(TimeZone.getTimeZone("GMT"));
        String localTime = date.format(currentLocalTime);
        int day = Integer.parseInt(localTime.split("-")[1]);
        int month = Integer.parseInt(localTime.split("-")[0]);
        int hours = Integer.parseInt(localTime.split("-")[2]);
        mLogger.debug("hours=" +  hours + ",day=" + day + ",month=" +month);
        int orignBitData = (hours << 2) + (day << 7) + (month << 12);

        int bit8_p1 = orignBitData >> 8;
        int bit8_p2 = orignBitData & 0x000000FF;
        int xor8bit = bit8_p1 ^ bit8_p2;

        int bit4_p1 = xor8bit >> 4;
        int bit4_p2 = xor8bit & 0x0000000F;
        int xor4bit = bit4_p1 ^ bit4_p2;


        int bit2_p1 = xor4bit >> 2;
        int bit2_p2 = xor4bit & 0x00000003;
        int xor2bit = bit2_p1 ^ bit2_p2;

        int major = orignBitData + xor2bit;
        Log.v(TAG, "major=" + major);
        mLogger.debug("major=" + major);

        byte[] uuidByte = hexToByteArray(uuid.replace("-", ""));
        byte[] preData = {(byte) 0x02, (byte) 0x15};
        byte[] postData = {(byte) (major >> 8), (byte) (major), (byte) (mStoreId >> 8), (byte) (mStoreId), (byte) 0xC5};


        byte[] payload = new byte[23];
        System.arraycopy(preData, 0, payload, 0, preData.length);
        System.arraycopy(uuidByte, 0, payload, preData.length, uuidByte.length);
        System.arraycopy(postData, 0, payload, preData.length + uuidByte.length, postData.length);


        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        dataBuilder.addManufacturerData(0x004C, payload); // 0x004c is for Apple inc.
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();

        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
        settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        settingsBuilder.setConnectable(false);
        advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        mAdvertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
            }
        };
        mLogger.debug("start broadcast");
        advertiser.startAdvertising(settingsBuilder.build(), dataBuilder.build(), mAdvertiseCallback);


    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void startRunningInForeground() {

        if (Build.VERSION.SDK_INT >= 26) {
            if (Build.VERSION.SDK_INT > 26) {
                String CHANNEL_ONE_ID = "com.acer.test.paroyalty";
                String CHANNEL_ONE_NAME = "Screen service";
                NotificationChannel notificationChannel = null;
                notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
                        CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_MIN);
                notificationChannel.enableLights(true);
                notificationChannel.setLightColor(Color.RED);
                notificationChannel.setShowBadge(true);
                notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.createNotificationChannel(notificationChannel);
                }

                Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_background);
                Notification notification = new Notification.Builder(getApplicationContext())
                        .setChannelId(CHANNEL_ONE_ID)
                        .setContentTitle("test")
                        .setContentText("test")
                        .setSmallIcon(R.drawable.ic_launcher_background)
                        .setLargeIcon(icon)
                        .build();


                startForeground(101, notification);
            } else {
                startForeground(101, updateNotification());

            }
        }
        //if less than version 26
        else {
            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle("Paroyalty")
                    .setContentText("Paroyalty Service")
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setOngoing(true).build();

            startForeground(101, notification);
        }
    }

    private Notification updateNotification() {
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, StartServiceActivity.class), 0);
        return new NotificationCompat.Builder(this)
                .setContentTitle("test")
                .setTicker("test")
                .setContentText("test")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentIntent(pendingIntent)
                .setOngoing(true).build();
    }

    private void registerBroadcastReceivers() {
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, screenFilter);
    }

    private void storeInternally(String screen_on) {
        Log.v("test", "screen:" + screen_on);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mLogger.debug("onDestroy");
        unregisterReceiver(mReceiver);
        if (manager != null) {
            manager.release();
        }
    }

    @Override
    public void onReceiveAdData(AdData[] adData) {
        mLogger.debug("onReceiveAdData " + "url:" + adData[0].url);
        Toast.makeText(this, adData[0].url, Toast.LENGTH_LONG).show();
    }

    private void enableBluetooth() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()){
            mBluetoothAdapter.enable();
        }
    }

    private boolean getBluetoothStatus(){
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter.isEnabled();
    }
}

