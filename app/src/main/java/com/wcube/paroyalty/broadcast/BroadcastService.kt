package com.wcube.paroyalty.broadcast

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.Nullable
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.UUID

class BroadcastService : Service() {
    val ACTION_UUID_SERVICE: String = "${BroadcastService::class.java.name}_UUIDBROADCAST"

    private lateinit var mBluetoothAdapter: BluetoothAdapter;
    private lateinit var advertiser: BluetoothLeAdvertiser;
    private var mAdvertiseCallback: AdvertiseCallback? = null
    private var mStoreId = 100
    private var started = false;
    private val randomUUID = UUID.randomUUID()

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= 31) {
            val bluetoothManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }else{
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (mBluetoothAdapter !== null) {
            if (mBluetoothAdapter.isEnabled()) {
                Log.d("beacon", "BluetoothAdapter enabled")
                registerBeaconBroadcast()
            } else {
                Log.d("beacon", "BluetoothAdapter disabled")
                // bluetooth disabled (opening app will enable bluetooth)
                try {
                    mBluetoothAdapter.enable()
                    registerBeaconBroadcast()
                } catch (e: Exception) {
                    Log.d("beacon", "something went wrong")
                }
            }
        }

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()

        if (advertiser != null) {
            started = false;
            var mAdvertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    super.onStartSuccess(settingsInEffect)
                    Log.d("beacon", "beacon stop success:");
                }

                override fun onStartFailure(errorCode: Int) {
                    super.onStartFailure(errorCode)
                    Log.d("beacon", "beacon stop failure:"+errorCode)
                }
            }
            advertiser.stopAdvertising(mAdvertiseCallback)
        }
    }

    @Nullable
    override fun onBind(intent: Intent?): IBinder? {
        return null // Return null if the service does not support binding
    }

    private fun registerBeaconBroadcast() {
        Log.d("beacon", "start to deliver beacon")

        // TODO: get data from sdcard
        if (started) return;

        // inform main activity
        val intent = Intent(ACTION_UUID_SERVICE)
        intent.putExtra("UUID", randomUUID.toString())
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        startBroadCast(randomUUID.toString())
    }

    @SuppressLint("MissingPermission")
    private fun startBroadCast(uuid: String) {
        var builder = DataBuilder(uuid, mStoreId)
        advertiser = mBluetoothAdapter.bluetoothLeAdvertiser
        mAdvertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                started = true;
                Log.d("beacon", "beacon start success delivering "+ uuid);
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.d("beacon", "beacon start failure:"+errorCode)
            }
        }

        Log.d("beacon", "start broadcast")
        advertiser.startAdvertising(
            builder.getSetting(),
            builder.getData(),
            mAdvertiseCallback
        )
    }
}