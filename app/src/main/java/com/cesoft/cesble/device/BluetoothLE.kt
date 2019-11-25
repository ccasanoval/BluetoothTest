package com.cesoft.cesble.device

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import org.koin.core.KoinComponent
import org.koin.core.inject


class BluetoothLE : KoinComponent {

    companion object {
        private val TAG = BluetoothLE::class.java.simpleName
    }

    private val appContext: Context by inject()
    private val bluetooth : Bluetooth by inject()
    private var isLowEnergyEnabled: Boolean = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun startScan(callback: Callback?) {
        stopScan()
        this.callback = callback

        val ssb = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            //.setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setReportDelay(5000)
        //.setUseHardwareBatchingIfSupported(true)
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        //    ssb.setLegacy(false)

        val settings = ssb.build()

        val filter = ScanFilter.Builder().setDeviceName(null).build()
        val filters = ArrayList<ScanFilter>()
        filters.add(filter)

        bluetooth.adapter?.bluetoothLeScanner?.startScan(filters, settings, scanCallback)//filters is mandatory for Android 9!!!
        //bluetoothAdapter?.bluetoothLeScanner?.startScan(null, settings, callbackStart)
        //bluetoothAdapter?.bluetoothLeScanner?.startScan(callbackStart)
    }
    fun stopScan() {
        android.util.Log.e(TAG, "stopScan---------------------------------------------$isLowEnergyEnabled $callback ")
        if(isLowEnergyEnabled && callback != null) {
            android.util.Log.e(TAG, "stopScan--------------------------------------------6666-"+bluetooth.adapter?.bluetoothLeScanner)
            bluetooth.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            callback = null
        }
    }
    //----------------------------------------------------------------------------------------------
    interface Callback {
        fun onBatchScanResults(results: List<ScanResult>)
        fun onScanFailed(errorCode: Int)
    }
    private var callback: Callback? = null
    private val scanCallback = object: ScanCallback() {
        private val TAG = "scanCallback"
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            android.util.Log.e(TAG, "onScanResult----------callbackType=$callbackType result=$result ")
            result.scanRecord?.deviceName?.let {
                callback?.onBatchScanResults(listOf(result))
            }
        }
        override fun onBatchScanResults(results: List<ScanResult>) {

for(item in results)android.util.Log.e(TAG, "onBatchScanResults-- Z:"+item.scanRecord?.deviceName+", "+item.device.name+", "+item.device.address+", "+item.device.type+", "+item.device.bluetoothClass)

//            val filteredResults = results.filter { it.scanRecord?.deviceName != null }
//for(item in filteredResults)android.util.Log.e(TAG, "onBatchScanResults-- C:"+item.scanRecord?.deviceName+", "+item.device.name+", "+item.device.address+", "+item.device.type+", "+item.device.bluetoothClass)

//            val sortedResults = filteredResults.toSortedSet(Comparator { scanResult1: ScanResult, scanResult2: ScanResult ->
//                scanResult1.scanRecord!!.deviceName!!.compareTo(scanResult2.scanRecord!!.deviceName!!)
//            })
//            callback?.onBatchScanResults(sortedResults.toList())

            callback?.onBatchScanResults(results)
        }
        override fun onScanFailed(errorCode: Int) {
            android.util.Log.e(TAG, "onScanFailed---------errorCode=$errorCode  SCAN_FAILED_ALREADY_STARTED=$SCAN_FAILED_ALREADY_STARTED")
            callback?.onScanFailed(errorCode)
        }
    }

}