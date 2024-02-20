package com.wcube.paroyalty.broadcast

import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.util.Log
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class DataBuilder {
    private lateinit var dataBuilder: AdvertiseData.Builder
    private lateinit var settingsBuilder: AdvertiseSettings.Builder

    constructor(strUUID: String, storeId: Int) {
        setup(strUUID, storeId)
    }

    private fun hexToByteArray(hex: String): ByteArray {
        var hex = hex
        hex = if (hex.length % 2 != 0) "0$hex" else hex
        val b = ByteArray(hex.length / 2)
        for (i in b.indices) {
            val index = i * 2
            val v = hex.substring(index, index + 2).toInt(16)
            b[i] = v.toByte()
        }
        return b
    }

    fun setup(uuid: String, mStoreId: Int) {
        Log.d("beacon", "startBroadCast:$uuid")
        val cal: Calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        val currentLocalTime: Date = cal.getTime()
        val date: DateFormat = SimpleDateFormat("MM-dd-HH")
        date.setTimeZone(TimeZone.getTimeZone("GMT"))
        val localTime: String = date.format(currentLocalTime)
        val day =
            localTime.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].toInt()
        val month =
            localTime.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0].toInt()
        val hours =
            localTime.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[2].toInt()


        Log.d("beacon", "hours=$hours,day=$day,month=$month")
        val orignBitData = (hours shl 2) + (day shl 7) + (month shl 12)
        val bit8_p1 = orignBitData shr 8
        val bit8_p2 = orignBitData and 0x000000FF
        val xor8bit = bit8_p1 xor bit8_p2
        val bit4_p1 = xor8bit shr 4
        val bit4_p2 = xor8bit and 0x0000000F
        val xor4bit = bit4_p1 xor bit4_p2
        val bit2_p1 = xor4bit shr 2
        val bit2_p2 = xor4bit and 0x00000003
        val xor2bit = bit2_p1 xor bit2_p2
        val major = orignBitData + xor2bit

        Log.d("beacon", "major=$major")
        val uuidByte = hexToByteArray(uuid.replace("-", ""))
        val preData = byteArrayOf(0x02.toByte(), 0x15.toByte())
        val postData = byteArrayOf(
            (major shr 8).toByte(),
            major.toByte(),
            (mStoreId shr 8).toByte(),
            mStoreId.toByte(),
            0xC5.toByte()
        )

        val payload = ByteArray(23)
        System.arraycopy(preData, 0, payload, 0, preData.size)
        System.arraycopy(uuidByte, 0, payload, preData.size, uuidByte.size)
        System.arraycopy(postData, 0, payload, preData.size + uuidByte.size, postData.size)

        dataBuilder = AdvertiseData.Builder()
        dataBuilder.addManufacturerData(0x004C, payload) // 0x004c is for Apple inc.

        settingsBuilder = AdvertiseSettings.Builder()
        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        settingsBuilder.setConnectable(false)
    }

    fun getSetting(): AdvertiseSettings? {
        return settingsBuilder.build();
    }

    fun getData(): AdvertiseData? {
        return dataBuilder.build()
    }
}