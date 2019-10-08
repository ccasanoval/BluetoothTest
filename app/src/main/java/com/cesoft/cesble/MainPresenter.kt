package com.cesoft.cesble

import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.*
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.ViewModel
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.media.AudioManager
import android.bluetooth.BluetoothHeadset
import android.content.IntentFilter
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.R.attr.name




//TODO: https://developer.android.com/reference/android/bluetooth/BluetoothHeadset

//TODO: comprobar si es headset y permitir el pareamiento y el uso...

//https://blog.usejournal.com/improve-recyclerview-performance-ede5cec6c5bf
class MainPresenter(private val view: View) : ViewModel() {

    companion object {
        private val TAG = MainPresenter::class.java.simpleName
        private const val REQUEST_ENABLE_BT = 6968
    }

    //abstract class View : View2, BroadcastReceiver()
    interface View {
        val app: Application
        val btnScanClassic: Button
        val btnScanLowEnergy: Button
        val btnPaired: Button
        val btnScanStop: FloatingActionButton
        val txtStatus: TextView
        val listDevices: RecyclerView
        val broadcastReceiver: BroadcastReceiver
        fun startActivityForResult(intent: Intent, requestCode: Int)
        fun alert(id: Int)
        fun alertDialog(title: String, message: String, onDismissListener: DialogInterface.OnDismissListener)
    }

    private var isScanning = false
    private var currentScanned = 0
    // if <uses-feature android:name="android.hardware.bluetooth_le" android:required=" F A L S E "/>
    private var isLowEnergyEnabled: Boolean = false


    private val textScanning = view.app.getString(R.string.bluetooth_scanning)

    private lateinit var viewAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
    private val dataSetClassic = ArrayList<BluetoothDevice>()
    private val dataSetLE = ArrayList<ScanResult>()


    private val pairedClickListener = android.view.View.OnClickListener {
        val adapter =  viewAdapter as BTDeviceAdapter
        val device = adapter.getItemAt(it.tag as Int)
        android.util.Log.e(TAG, "pairedClickListener----${it.tag}-------------------------$device")
        listenBluetoothProfileHeadset()

    }
    private val classicClickListener = android.view.View.OnClickListener {
        val adapter =  viewAdapter as BTDeviceAdapter
        val device = adapter.getItemAt(it.tag as Int)
        android.util.Log.e(TAG, "classicClickListener----${it.tag}-------------------------$device")
        askToPair(device)
    }
    private val lowEnergyClickListener = android.view.View.OnClickListener {
        val adapter =  viewAdapter as BTLEDeviceAdapter
        android.util.Log.e(TAG, "lowEnergyClickListener----${it.tag}-----------------------"+adapter.getItemAt(it.tag as Int))
    }

    init {
        isLowEnergyEnabled = view.app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        if( ! isLowEnergyEnabled)
            view.alert(R.string.bluetooth_low_energy_not_supported)

        stopScanUI()

        view.btnScanClassic.setOnClickListener {
            if (isBluetoothEnabled())
                startScanClassic()
            else
                view.alert(R.string.bluetooth_disabled)
        }
        view.btnScanLowEnergy.setOnClickListener {
            if (isBluetoothEnabled())
                startScanLE()
            else
                view.alert(R.string.bluetooth_disabled)
        }
        view.btnPaired.setOnClickListener {
            if (isBluetoothEnabled())
                showPairedDevices()
            else
                view.alert(R.string.bluetooth_disabled)
        }
        view.btnScanStop.setOnClickListener {
android.util.Log.e(TAG, "view.btnScanStop.setOnClickListener--------------------------------------------------------------------------------------------")
            if (isBluetoothEnabled())
                stopScanLE()
            else
                view.alert(R.string.bluetooth_disabled)
        }
    }

    fun onResume() {
        turnOnBluetooth()
    }

    private fun isBluetoothEnabled(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return adapter != null && adapter.isEnabled
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = view.app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    private fun turnOnBluetooth() {
        bluetoothAdapter?.let {bluetoothAdapter ->
            if (bluetoothAdapter.isDisabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                view.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                disableAllUI()
            } else if( ! isScanning) {
                view.btnScanClassic.isEnabled = true
            }
        } ?: run {
            disableAllUI()
        }
    }

    //----------------------------------------------------------------------------------------------
    // UI
    private fun disableAllUI() {
        view.btnScanClassic.isEnabled = false
        view.btnScanLowEnergy.isEnabled = false
        view.btnScanStop.isEnabled = false
    }
    private fun disableScanClassic() {
        view.btnScanStop.isEnabled = true
        view.btnScanClassic.isEnabled = false
        view.btnScanLowEnergy.isEnabled = isLowEnergyEnabled
    }
    private fun stopScanUI() {
        view.btnScanStop.isEnabled = false
        view.btnScanClassic.isEnabled = true
        view.btnScanLowEnergy.isEnabled = isLowEnergyEnabled
        view.txtStatus.text = ""
        isScanning = false
        //currentScanned = 0
    }



    //----------------------------------------------------------------------------------------------
    // CLASSIC SCAN
    //https://www.thedroidsonroids.com/blog/bluetooth-classic-vs-bluetooth-low-energy-on-android-hints-implementation-steps
    private interface BluetoothDiscoveryCallback {
        fun onDeviceFound(bluetoothDevice: BluetoothDevice)
        fun onDiscoveryFinished()
    }

    private fun scanDevices(context: Context, callback: BluetoothDiscoveryCallback) {
        val scanningBroadcastReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (BluetoothDevice.ACTION_FOUND == intent.action) {
                    val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
                    callback.onDeviceFound(device)
                }
                else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == intent.action) {
                    context.unregisterReceiver(this)
                    callback.onDiscoveryFinished()
                }
            }
        }

        val scanningItentFilter = IntentFilter()
        scanningItentFilter.addAction(BluetoothDevice.ACTION_FOUND)
        scanningItentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        context.registerReceiver(scanningBroadcastReceiver, scanningItentFilter)

        BluetoothAdapter.getDefaultAdapter().startDiscovery()
    }

    private fun startScanClassic() {

        stopScanLE()
        disableScanClassic()

        isScanning = true
        currentScanned = 0

        //viewAdapter = scanningAdapter
        view.txtStatus.text = textScanning

        scanDevices(view.app, object : BluetoothDiscoveryCallback {
            override fun onDeviceFound(bluetoothDevice: BluetoothDevice) {
                android.util.Log.e(TAG, "onDeviceFound---------------------------------------------------$bluetoothDevice "
                        +" : "+bluetoothDevice.name
                        +" : "+bluetoothDevice.bluetoothClass
                        +" : "+bluetoothDevice.bluetoothClass.deviceClass       //AUDIO_VIDEO_WEARABLE_HEADSET
                        +" : "+bluetoothDevice.bluetoothClass.majorDeviceClass  //1024 = 0x400
                        +" : "+bluetoothDevice.type)

                if(bluetoothDevice.name == null || bluetoothDevice.name.isEmpty())return
                if(dataSetClassic.contains(bluetoothDevice))return

                //if(viewAdapter.itemCount == 1 && viewAdapter.getItemAt(0) == view.app.getString(R.string.bluetooth_scanning)) {
                if(currentScanned == 0) {
                    dataSetClassic.clear()
                    dataSetClassic.add(bluetoothDevice)
                    viewAdapter = BTDeviceAdapter(dataSetClassic, classicClickListener) as RecyclerView.Adapter<RecyclerView.ViewHolder>
                    view.listDevices.adapter = viewAdapter
                }
                else {
                    dataSetClassic.add(bluetoothDevice)
                    (viewAdapter as BTDeviceAdapter).add(bluetoothDevice)
                    viewAdapter.notifyDataSetChanged()
                }
                currentScanned++
            }
            override fun onDiscoveryFinished() {
                android.util.Log.e(TAG, "onDiscoveryFinished---------------------------------------------------")
                stopScanUI()
            }
        })
    }


    //----------------------------------------------------------------------------------------------
    // LOW ENERGY SCAN
    private fun startScanLE() {

        stopScanLE()
        view.btnScanStop.isEnabled = true
        view.btnScanClassic.isEnabled = true
        view.btnScanLowEnergy.isEnabled = false
        isScanning = true

        //viewAdapter = scanningAdapter
        view.txtStatus.text = textScanning

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

        bluetoothAdapter?.bluetoothLeScanner?.startScan(filters, settings, callbackStartLowEnergy)//filters is mandatory for Android 9!!!
        //bluetoothAdapter?.bluetoothLeScanner?.startScan(null, settings, callbackStart)
        //bluetoothAdapter?.bluetoothLeScanner?.startScan(callbackStart)
    }
    //----------------------------------------------------------------------------------------------
    private val callbackStartLowEnergy = object: ScanCallback() {
        private val TAG = "callbackStart"
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            android.util.Log.e(TAG, "onScanResult----------callbackType=$callbackType result=$result ")
            result.scanRecord?.deviceName?.let {
                viewAdapter = BTLEDeviceAdapter(arrayListOf(result), lowEnergyClickListener) as RecyclerView.Adapter<RecyclerView.ViewHolder>
                view.listDevices.adapter = viewAdapter
                currentScanned++
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

            viewAdapter = BTLEDeviceAdapter(ArrayList(sortedResults), lowEnergyClickListener) as RecyclerView.Adapter<RecyclerView.ViewHolder>
            view.listDevices.adapter = viewAdapter

            //android.util.Log.e(TAG, "onBatchScanResults------${a.size}----results=$results ")
            //android.util.Log.e(TAG, "onBatchScanResults-- A ----${results.size}----results=$results ")//deviceName=((?!null).)
            android.util.Log.e(TAG, "onBatchScanResults-- B$nDeleteCurrentResults- ----${filteredResults.size}----results=$filteredResults ")//deviceName=((?!null).)
        }
        override fun onScanFailed(errorCode: Int) {
            android.util.Log.e(TAG, "onScanFailed---------errorCode=$errorCode  SCAN_FAILED_ALREADY_STARTED=$SCAN_FAILED_ALREADY_STARTED")
            view.txtStatus.text = "onScanFailed errorCode=$errorCode"
            view.listDevices.adapter = viewAdapter
        }
    }

    private fun stopScanLE() {
        if(isLowEnergyEnabled)
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(callbackStartLowEnergy)
        stopScanUI()
    }

    //----------------------------------------------------------------------------------------------
    // PAIRED DEVICES
    //----------------------------------------------------------------------------------------------
    private fun showPairedDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices = adapter.bondedDevices
        dataSetClassic.clear()
        dataSetClassic.addAll(pairedDevices)
        viewAdapter = BTDeviceAdapter(dataSetClassic, pairedClickListener) as RecyclerView.Adapter<RecyclerView.ViewHolder>
        view.listDevices.adapter = viewAdapter
    }
    private fun askToPair(device: BluetoothDevice) {
        android.util.Log.e(TAG, "askToPair------------------------------------------------")
        device.createBond()
    }
    //private fun askToUnpair(device: BluetoothDevice) {}


    //----------------------------------------------------------------------------------------------
    // PERMISSIONS
    //----------------------------------------------------------------------------------------------
    /*private fun checkPermissionsForBluetooth() {
        android.util.Log.e(TAG, "checkPermissionsForBLE---------------------------------------------------")
        if(view.app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            view.alertDialog(
                "This app needs location access",
                "Please grant location access so this app can detect beacons.",
                DialogInterface.OnDismissListener {
                    view.requestPermissions2(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_LOCATION)
                })
        }
        else
            turnOnBluetooth()
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_LOCATION -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    view.alertDialog(
                        "Functionality limited",
                        "Since location access has not been granted, this app will not be able to discover beacons when in the background.",
                        DialogInterface.OnDismissListener { })
                }
                return
            }
        }
    }*/



    //----------------------------------------------------------------------------------------------
    // HEADSET
    //----------------------------------------------------------------------------------------------
    private fun listenBluetoothProfileHeadset() {
        val bluetoothManager = view.app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter.getProfileProxy(view.app, object: BluetoothProfile.ServiceListener {
            override fun onServiceDisconnected(profile: Int) {
                android.util.Log.e(TAG, "onServiceDisconnected------------------------------------------------profile=$profile")
            }
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                //isHeadsetConnected = proxy!!.connectedDevices.size > 0
                android.util.Log.e(TAG, "onServiceConnected------------------------------------------------profile=$profile N=${proxy!!.connectedDevices.size}")
            }

        }, BluetoothProfile.HEADSET)

        view.app.registerReceiver(view.broadcastReceiver, IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED))
    }



}






///---------------------------------------------------------------------------------------------
/// SCANNING : https://github.com/NordicSemiconductor/Android-Scanner-Compat-Library
///---------------------------------------------------------------------------------------------
/*private fun startScan() {
    val settings = ScanSettings.Builder()
        .setLegacy(false)
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setReportDelay(5000)
        .setUseHardwareBatchingIfSupported(true)
        .build()
    val filters = ArrayList<ScanFilter>()
    //filters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid()).build())
    scanner.startScan(filters, settings, callbackStart)
    btn_stop_scan.isEnabled = true
    btn_start_scan.isEnabled = false
}

private fun stopScan() {
    scanner.stopScan(callbackStart)
    btn_stop_scan.isEnabled = false
    btn_start_scan.isEnabled = true
}

private val callbackStart = object: ScanCallback() {
    private val TAG = "callbackStart"
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        android.util.Log.e(TAG, "onScanResult----------callbackType=$callbackType result=$result ")
        result.scanRecord?.deviceName?.let { deviceName ->
            viewAdapter = BLEAdapter(Array(1) { deviceName })
            list_results.adapter = viewAdapter
        }
    }
    override fun onBatchScanResults(results: List<ScanResult>) {

        val a = results.filter { it.scanRecord?.deviceName != null }
        viewAdapter = BLEAdapter(Array(a.size) { i -> a[i].scanRecord!!.deviceName!!})
        list_results.adapter = viewAdapter

        //android.util.Log.e(TAG, "onBatchScanResults------${a.size}----results=$results ")
        android.util.Log.e(TAG, "onBatchScanResults-- A ----${results.size}----results=$results ")//deviceName=((?!null).)
        android.util.Log.e(TAG, "onBatchScanResults-- B ----${a.size}----results=$a ")//deviceName=((?!null).)
    }
    override fun onScanFailed(errorCode: Int) {
        android.util.Log.e(TAG, "onScanFailed----------errorCode=$errorCode")
        viewAdapter = BLEAdapter(Array(1) {"onScanFailed errorCode=$errorCode"})
        list_results.adapter = viewAdapter
    }
}

///---------------------------------------------------------------------------------------------
/// BLE : https://github.com/NordicSemiconductor/Android-BLE-Library
///---------------------------------------------------------------------------------------------
*/