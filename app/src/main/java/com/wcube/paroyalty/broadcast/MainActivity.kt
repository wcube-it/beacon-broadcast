package com.wcube.paroyalty.broadcast

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.Locale


class MainActivity: AppCompatActivity() {
    val ACTION_UUID_SERVICE: String = "${BroadcastService::class.java.name}_UUIDBROADCAST"
    private lateinit var broadcastingText: TextView;

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("beacon", "App initiated")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
          broadcastingText = this.findViewById<TextView>(R.id.broadcasting);


        // ask for bluetooth permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT))
        }
        else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(enableBtIntent)
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val uuid = intent.getStringExtra("UUID")
                    if (uuid != null && broadcastingText != null) {
                        broadcastingText.setText(uuid.uppercase(Locale.ROOT))
                    }
                }
            }, IntentFilter(ACTION_UUID_SERVICE)
        )


        // start service
        val serviceIntent = Intent(this, BroadcastService::class.java)
        startService(serviceIntent)
    }

    private var requestBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // start service
            val serviceIntent = Intent(this, BroadcastService::class.java)
            startService(serviceIntent)
        }
    }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d("beacon", "${it.key} = ${it.value}")
            }
        }
}