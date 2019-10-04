package com.cesoft.cesble

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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


//TODO: https://developer.android.com/reference/android/bluetooth/BluetoothHeadset

//TODO: comprobar si es headset y permitir el pareamiento y el uso...

class MainPresenter(private val view: View) : ViewModel() {

    companion object {
        private val TAG = MainPresenter::class.java.simpleName
        private const val PERMISSION_REQUEST_COARSE_LOCATION = 6969
        private const val REQUEST_ENABLE_BT = 6968
    }

    interface View {
        val app: Application
        val btnScanClassic: Button
        val btnScanLowEnergy: Button
        val btnScanStop: Button
        val txtStatus: TextView
        val listDevices: RecyclerView
        fun startActivityForResult(intent: Intent, requestCode: Int)
        fun requestPermissions2(permissions: Array<String>, requestCode: Int)
        fun alert(id: Int)
        fun alertDialog(title: String, message: String, onDismissListener: DialogInterface.OnDismissListener)
    }

    private var isScanning = false
    private var currentScanned = 0
    // if <uses-feature android:name="android.hardware.bluetooth_le" android:required=" F A L S E "/>
    private var isLowEnergyEnabled: Boolean = false


    private val textScanning = view.app.getString(R.string.bluetooth_scanning)

    private lateinit var viewAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>//BTDeviceAdapter


    private val classicClickListener = android.view.View.OnClickListener {
        val adapter =  viewAdapter as BTDeviceAdapter
        android.util.Log.e(TAG, "classicClickListener----${it.tag}-------------------------"+adapter.getItemAt(it.tag as Int))
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
        view.btnScanStop.setOnClickListener {
            if (isBluetoothEnabled())
                stopScanLE()
            else
                view.alert(R.string.bluetooth_disabled)
        }

        view.requestPermissions2(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_COARSE_LOCATION)
    }

    fun onResume() {
        checkPermissionsForBluetooth()
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
                disableScanStopButtons()
            } else if( ! isScanning) {
                view.btnScanClassic.isEnabled = true
            }
        } ?: run {
            disableScanStopButtons()
        }
    }
    private fun disableScanStopButtons() {
        view.btnScanClassic.isEnabled = false
        view.btnScanLowEnergy.isEnabled = false
        view.btnScanStop.isEnabled = false
    }

    //----------------------------------------------------------------------------------------------
    // CLASSIC SCAN
    //https://www.thedroidsonroids.com/blog/bluetooth-classic-vs-bluetooth-low-energy-on-android-hints-implementation-steps
    private fun startScanClassic() {

        stopScanLE()
        view.btnScanStop.isEnabled = true
        view.btnScanClassic.isEnabled = false
        view.btnScanLowEnergy.isEnabled = isLowEnergyEnabled
        isScanning = true
        currentScanned = 0

        //viewAdapter = scanningAdapter
        view.txtStatus.text = textScanning

        scanDevices(view.app, object : BluetoothDiscoveryCallback {
            override fun onDeviceFound(bluetoothDevice: BluetoothDevice) {
                android.util.Log.e(TAG, "onDeviceFound---------------------------------------------------$bluetoothDevice "+bluetoothDevice.name)
                if(bluetoothDevice.name.isEmpty())return
                //if(viewAdapter.itemCount == 1 && viewAdapter.getItemAt(0) == view.app.getString(R.string.bluetooth_scanning)) {
                if(currentScanned == 0) {
                    viewAdapter = BTDeviceAdapter(arrayListOf(bluetoothDevice), classicClickListener) as RecyclerView.Adapter<RecyclerView.ViewHolder>
                    view.listDevices.adapter = viewAdapter
                }
                else {
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
    private fun stopScanUI() {
        view.btnScanStop.isEnabled = false
        view.btnScanClassic.isEnabled = true
        view.btnScanLowEnergy.isEnabled = isLowEnergyEnabled
        view.txtStatus.text = ""
        isScanning = false
        //currentScanned = 0
    }
    private fun stopScanLE() {
        if(isLowEnergyEnabled)
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(callbackStartLowEnergy)
        stopScanUI()
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

            for(item in results)
                android.util.Log.e(TAG, "onBatchScanResults-- Z:"+item.scanRecord?.deviceName+", "+item.device.name+", "+item.device.address+", "+item.device.type+", "+item.device.bluetoothClass)
            for(item in filteredResults)
                android.util.Log.e(TAG, "onBatchScanResults-- C:"+item.scanRecord?.deviceName+", "+item.device.name+", "+item.device.address+", "+item.device.type+", "+item.device.bluetoothClass)

            viewAdapter = BTLEDeviceAdapter(ArrayList(filteredResults), lowEnergyClickListener) as RecyclerView.Adapter<RecyclerView.ViewHolder>
            view.listDevices.adapter = viewAdapter

            //android.util.Log.e(TAG, "onBatchScanResults------${a.size}----results=$results ")
            //android.util.Log.e(TAG, "onBatchScanResults-- A ----${results.size}----results=$results ")//deviceName=((?!null).)
            android.util.Log.e(TAG, "onBatchScanResults-- B$nDeleteCurrentResults- ----${filteredResults.size}----results=$filteredResults ")//deviceName=((?!null).)
        }
        override fun onScanFailed(errorCode: Int) {
            android.util.Log.e(TAG, "onScanFailed---------errorCode=$errorCode  SCAN_FAILED_ALREADY_STARTED=$SCAN_FAILED_ALREADY_STARTED")
            //viewAdapter = BTDeviceAdapter(getArrayOfString("onScanFailed errorCode=$errorCode"), nullClickListener)
            view.txtStatus.text = "onScanFailed errorCode=$errorCode"
            view.listDevices.adapter = viewAdapter
        }
    }


    private fun checkPermissionsForBluetooth() {
        android.util.Log.e("Main", "checkPermissionsForBLE---------------------------------------------------")
        if(view.app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            view.alertDialog(
                "This app needs location access",
                "Please grant location access so this app can detect beacons.",
                DialogInterface.OnDismissListener {
                    view.requestPermissions2(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_COARSE_LOCATION)
                })
        }
        else
            turnOnBluetooth()
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_COARSE_LOCATION -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    view.alertDialog(
                        "Functionality limited",
                        "Since location access has not been granted, this app will not be able to discover beacons when in the background.",
                        DialogInterface.OnDismissListener { })
                }
                return
            }
        }
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
    private interface BluetoothDiscoveryCallback {
        fun onDeviceFound(bluetoothDevice: BluetoothDevice)
        fun onDiscoveryFinished()
    }

}