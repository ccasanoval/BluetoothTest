package com.cesoft.cesble.devices

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import org.koin.core.KoinComponent
import org.koin.core.inject


class BluetoothLE : KoinComponent {

    private val appContext: Context by inject()
    private val bluetooth : Bluetooth by inject()
    private var isLowEnergyEnabled: Boolean = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)


    private var callback: ScanCallback? = null
    fun startScan(callback: ScanCallback) {
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

        bluetooth.bluetoothAdapter?.bluetoothLeScanner?.startScan(filters, settings, callback)//filters is mandatory for Android 9!!!
        //bluetoothAdapter?.bluetoothLeScanner?.startScan(null, settings, callbackStart)
        //bluetoothAdapter?.bluetoothLeScanner?.startScan(callbackStart)
    }
    fun stopScan() {
        android.util.Log.e("BTLE", "stopScan---------------------------------------------$isLowEnergyEnabled $callback ")
        if(isLowEnergyEnabled && callback != null) {
            android.util.Log.e("BTLE", "stopScan--------------------------------------------6666-"+bluetooth.bluetoothAdapter?.bluetoothLeScanner)
            bluetooth.bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
            callback = null
        }
    }
    //----------------------------------------------------------------------------------------------
    /*private val callback = object: ScanCallback() {
        private val TAG = "callbackStart"
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            android.util.Log.e(TAG, "onScanResult----------callbackType=$callbackType result=$result ")
            result.scanRecord?.deviceName?.let {
//                viewAdapter = BTLEDeviceAdapter(arrayListOf(result), lowEnergyClickListener) as RecyclerView.Adapter<RecyclerView.ViewHolder>
//                view.listDevices.adapter = viewAdapter
//                currentScanned++
            }
        }
        private var nDeleteCurrentResults: Int = 5
        override fun onBatchScanResults(results: List<ScanResult>) {

            val filteredResults = results
                .filter { it.scanRecord?.deviceName != null }
            //.map { it.device }

            val sortedResults = filteredResults.toSortedSet(Comparator<ScanResult> { scanResult1: ScanResult, scanResult2: ScanResult ->
                scanResult1.scanRecord!!.deviceName!!.compareTo(scanResult2.scanRecord!!.deviceName!!)
            })

            for(item in results)
                android.util.Log.e(TAG, "onBatchScanResults-- Z:"+item.scanRecord?.deviceName+", "+item.device.name+", "+item.device.address+", "+item.device.type+", "+item.device.bluetoothClass)
            for(item in filteredResults)
                android.util.Log.e(TAG, "onBatchScanResults-- C:"+item.scanRecord?.deviceName+", "+item.device.name+", "+item.device.address+", "+item.device.type+", "+item.device.bluetoothClass)

//            viewAdapter = BTLEDeviceAdapter(ArrayList(sortedResults), lowEnergyClickListener) as RecyclerView.Adapter<RecyclerView.ViewHolder>
//            view.listDevices.adapter = viewAdapter

            //android.util.Log.e(TAG, "onBatchScanResults------${a.size}----results=$results ")
            //android.util.Log.e(TAG, "onBatchScanResults-- A ----${results.size}----results=$results ")//deviceName=((?!null).)
            android.util.Log.e(TAG, "onBatchScanResults-- B$nDeleteCurrentResults- ----${filteredResults.size}----results=$filteredResults ")//deviceName=((?!null).)
        }
        override fun onScanFailed(errorCode: Int) {
            android.util.Log.e(TAG, "onScanFailed---------errorCode=$errorCode  SCAN_FAILED_ALREADY_STARTED=$SCAN_FAILED_ALREADY_STARTED")
//            view.txtStatus.text = "onScanFailed errorCode=$errorCode"
//            view.listDevices.adapter = viewAdapter
        }
    }*/

}