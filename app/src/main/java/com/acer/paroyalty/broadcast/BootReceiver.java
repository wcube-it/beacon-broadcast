package com.acer.paroyalty.broadcast;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
                if(Build.VERSION.SDK_INT >25){
                    Intent intent1 = new Intent();
                    intent1.setClass(context, PaRoyaltyBroadCastService.class);
                    context.startForegroundService(intent1);
                } else {
                    Intent intent1 = new Intent();
                    intent1.setClass(context, PaRoyaltyBroadCastService.class);
                    context.startService(intent1);
                }
                break;
            case BluetoothAdapter.ACTION_STATE_CHANGED: {

                break;
            }
        }
    }

}
