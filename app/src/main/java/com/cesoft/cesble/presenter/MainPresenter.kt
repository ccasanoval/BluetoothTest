package com.cesoft.cesble.presenter

import android.Manifest
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanResult
import android.content.*
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.ViewModel
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.content.IntentFilter
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.util.Log
import com.cesoft.cesble.adapter.BTDeviceAdapter
import com.cesoft.cesble.adapter.BTLEDeviceAdapter
import com.cesoft.cesble.R
import com.cesoft.cesble.adapter.BTViewHolder
import com.cesoft.cesble.device.*
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*
import kotlin.collections.ArrayList


//TODO: https://developer.android.com/reference/android/bluetooth/BluetoothHeadset

//TODO: comprobar si es headset y permitir el pareamiento y el uso...

//https://blog.usejournal.com/improve-recyclerview-performance-ede5cec6c5bf
class MainPresenter(private val view: View) : ViewModel(), KoinComponent {

    companion object {
        private val TAG = MainPresenter::class.java.simpleName
        private const val PERMISSION_REQUEST_LOCATION = 6969
        private const val REQUEST_ENABLE_BT = 6968
    }

    interface View {
        val app: Application
        val ctx: Context
        val btnScanClassic: Button
        val btnScanLowEnergy: Button
        val btnPaired: Button
        val btnScanStop: FloatingActionButton
        val txtStatus: TextView
        val listDevices: RecyclerView
        val broadcastReceiver: BroadcastReceiver
        fun startActivityForResult(intent: Intent, requestCode: Int)
        fun requestPermissions2(permissions: Array<String>, requestCode: Int)
        fun alert(id: Int)
        fun alertDialog(title: Int, message: Int, listener: YesNoListener?)
    }
    interface YesNoListener {
        fun onYes()
        fun onNo()
    }

    private val bluetooth : Bluetooth by inject()
    private val bluetoothClassic : BluetoothClassic by inject()
    private val bluetoothLE : BluetoothLE by inject()
    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null



    // if <uses-feature android:name="android.hardware.bluetooth_le" android:required=" F A L S E "/>
    private var isLowEnergyEnabled: Boolean = false
    private var isScanning = false
    private val textScanning = view.app.getString(R.string.bluetooth_scanning)
    private val textPaired = view.app.getString(R.string.bluetooth_paired_ok)
    private val textPairedError = view.app.getString(R.string.bluetooth_paired_error)

    private lateinit var viewAdapter: RecyclerView.Adapter<BTViewHolder>

    private var bluetoothSPP: SPPBluetooth

    //----------------------------------------------------------------------------------------------
    // ADAPTER CLICK LISTENERS
    private val pairedClickListener = android.view.View.OnClickListener {
        val adapter =  viewAdapter as BTDeviceAdapter
        val device = adapter.getItemAt(it.tag as Int)
        Log.e(TAG, "pairedClickListener----${it.tag}-------------------------$device")

        bluetooth.connect(device)

        listenBluetoothProfileHeadset()

        bluetooth.getProfileProxy()


        listenBluetoothProfileHeadset()
    }
    private val classicClickListener = android.view.View.OnClickListener {
        val adapter =  viewAdapter as BTDeviceAdapter
        val device = adapter.getItemAt(it.tag as Int)
        Log.e(TAG, "classicClickListener----${it.tag}-------------------------$device")
        askToPair(device)
    }
    private val lowEnergyClickListener = android.view.View.OnClickListener {
        val adapter =  viewAdapter as BTLEDeviceAdapter
        Log.e(TAG, "lowEnergyClickListener----${it.tag}-----------------------"+adapter.getItemAt(it.tag as Int))
    }

    init {
        checkPermissionsForBluetoothLowEnergy()

        isLowEnergyEnabled = view.app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        if( ! isLowEnergyEnabled)
            view.alert(R.string.bluetooth_low_energy_not_supported)

        enableAllUI()

        view.btnScanClassic.setOnClickListener {
            if (bluetooth.isEnabled)
                startScanClassic()
            else
                view.alert(R.string.bluetooth_disabled)
        }
        view.btnScanLowEnergy.setOnClickListener {
            if (bluetooth.isEnabled)
                startScanLE()
            else
                view.alert(R.string.bluetooth_disabled)
        }
        view.btnPaired.setOnClickListener {
            if (bluetooth.isEnabled)
                showPairedDevices()
            else
                view.alert(R.string.bluetooth_disabled)
        }
        view.btnScanStop.setOnClickListener {
            if (bluetooth.isEnabled) {
                Log.e(TAG, "stopScan---------------------------------------------")
                bluetoothClassic.stopScan()
                bluetoothLE.stopScan()
                stopScanUI()
            }
            else
                view.alert(R.string.bluetooth_disabled)
        }

        ///
        bluetoothSPP = SPPBluetooth(view.ctx)
    }

    fun onResume() {
        turnOnBluetooth()
    }

    private fun turnOnBluetooth() {
        if (bluetooth.isDisabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            view.startActivityForResult(enableBtIntent,
                REQUEST_ENABLE_BT
            )
            disableAllUI()
        }
        else if( ! isScanning) {//TODO clean
            view.btnScanClassic.isEnabled = true
        }
    }

    //----------------------------------------------------------------------------------------------
    // UI
    private fun disableAllUI() {
        view.btnScanClassic.isEnabled = false
        view.btnScanLowEnergy.isEnabled = false
        view.btnPaired.isEnabled = false
        view.btnScanStop.isEnabled = false
    }
    private fun enableAllUI() {
        view.btnScanClassic.isEnabled = true
        view.btnScanLowEnergy.isEnabled = true
        view.btnPaired.isEnabled = true
        view.btnScanStop.isEnabled = false
    }
    private fun stopScanUI() {
        view.btnScanStop.isEnabled = false
        view.btnScanClassic.isEnabled = true
        view.btnScanLowEnergy.isEnabled = isLowEnergyEnabled
        view.txtStatus.text = ""
    }
    private fun startScanClassicUI() {
        view.btnScanStop.isEnabled = true
        view.btnScanClassic.isEnabled = false
        view.btnPaired.isEnabled = true
        view.btnScanLowEnergy.isEnabled = true
        //
        view.txtStatus.text = textScanning
    }
    private fun startScanLEUI() {
        view.btnScanStop.isEnabled = true
        view.btnScanClassic.isEnabled = true
        view.btnPaired.isEnabled = true
        view.btnScanLowEnergy.isEnabled = false
        //
        view.txtStatus.text = textScanning
    }



    //----------------------------------------------------------------------------------------------
    // CLASSIC SCAN
    private fun startScanClassic() {
        bluetoothLE.stopScan()
        startScanClassicUI()
        isScanning = true

        var isNewScanClassic = true
        bluetoothClassic.startScan(object : BluetoothClassic.Callback {
            override fun onDeviceFound(bluetoothDevice: BluetoothDevice) {

                //if(::viewAdapter.isInitialized && viewAdapter is BTDeviceAdapter) {
                if(isNewScanClassic) {
                    viewAdapter = BTDeviceAdapter(arrayListOf(bluetoothDevice), classicClickListener)
                    isNewScanClassic = false
                }
                else if(::viewAdapter.isInitialized) {
                    (viewAdapter as BTDeviceAdapter).add(bluetoothDevice)
                    viewAdapter.notifyDataSetChanged()
                }
                view.listDevices.adapter = viewAdapter
            }
            override fun onDiscoveryFinished() {
                stopScanUI()
            }
        })
    }


    //----------------------------------------------------------------------------------------------
    // LOW ENERGY SCAN
    private fun startScanLE() {
        bluetoothClassic.stopScan()
        startScanLEUI()
        isScanning = true
        bluetoothLE.startScan(callback)
    }
    // Callback
    private val callback = object: BluetoothLE.Callback {
        override fun onBatchScanResults(results: List<ScanResult>) {
            viewAdapter = BTLEDeviceAdapter(results, lowEnergyClickListener)
            view.listDevices.adapter = viewAdapter
        }
        override fun onScanFailed(errorCode: Int) {
            view.txtStatus.text = "onScanFailed errorCode=$errorCode"
            view.listDevices.adapter = viewAdapter
        }
    }


    //----------------------------------------------------------------------------------------------
    // PAIRED DEVICES
    //----------------------------------------------------------------------------------------------
    private fun showPairedDevices() {
        bluetoothClassic.stopScan()
        bluetoothLE.stopScan()
        bluetooth.pairedDevices?.let {
            val dataSet = ArrayList<BluetoothDevice>(it)//TODO: simplify
            viewAdapter = BTDeviceAdapter(dataSet, pairedClickListener)
            view.listDevices.adapter = viewAdapter
        }
    }
    private fun askToPair(device: BluetoothDevice) {
        //TODO: ask user if wants to pair the device first?
        Log.e(TAG, "askToPair------------------------------------------------$device")
        view.alertDialog(R.string.bluetooth_pairing_tle, R.string.bluetooth_pairing_msg, object : YesNoListener {
            override fun onNo() {
                view.txtStatus.text = ""
            }
            override fun onYes() {
                if(device.createBond())
                    view.txtStatus.text = textPaired
                else
                    view.txtStatus.text = textPairedError
            }
        })
    }


    //----------------------------------------------------------------------------------------------
    // PERMISSIONS : BT Low Energy needs location access permission to scan for LE devices
    //----------------------------------------------------------------------------------------------
    private fun checkPermissionsForBluetoothLowEnergy() {
        Log.e("Main", "checkPermissionsForBLE---------------------------------------------------")
        if(view.app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            view.alertDialog(
                R.string.location_permission_tle,
                R.string.location_permission_msg,
                object : YesNoListener {
                    override fun onNo() {
                        view.alert(R.string.location_permission_err)
                    }
                    override fun onYes() {
                        view.requestPermissions2(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_LOCATION)
                    }

                })
        }
        else
            turnOnBluetooth()
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_LOCATION -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    view.alert(R.string.location_permission_err)
//                    view.alertDialog("Functionality limited","Since location access has not been granted, this app will not be able to discover beacons when in the background.",DialogInterface.OnDismissListener { })
                }
                return
            }
        }
    }


    //----------------------------------------------------------------------------------------------
    // HEADSET
    //----------------------------------------------------------------------------------------------
    private fun listenBluetoothProfileHeadset() {
        val bluetoothManager = view.app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter.getProfileProxy(view.app, object: BluetoothProfile.ServiceListener {
            override fun onServiceDisconnected(profile: Int) {
                Log.e(TAG, "onServiceDisconnected------------------------------------------------profile=$profile")
            }
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                //isHeadsetConnected = proxy!!.connectedDevices.size > 0
                Log.e(TAG, "onServiceConnected------------------------------------------------profile=$profile N=${proxy!!.connectedDevices.size}")
            }

        }, BluetoothProfile.HEADSET)

        view.app.registerReceiver(view.broadcastReceiver, IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED))
    }






    //----------------------------------------------------------------------------------------------
    // AINA BLE
    //----------------------------------------------------------------------------------------------
    fun ainBleTest() {
        bluetooth.pairedDevices?.let { pairedDevices ->
            if( ! pairedDevices.isEmpty()) {
                val device = pairedDevices.single()
                Log.e(TAG, "ainBleTest--------------------------------------${device.name} / ${device.address}")
                //device.fetchUuidsWithSdp()
                val bg: BluetoothGatt = device.connectGatt(
                    view.ctx,//view.app.applicationContext,
                    true,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE)
                for(service in bg.services) {
                    Log.e(TAG, "ainBleTest:service:------------------------------------${service.uuid}")
                }
            }
        }
    }
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.e(TAG, "onCharacteristicChanged--------------------***********------------------$characteristic")
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.e(TAG, "onConnectionStateChange--------------------------------------status=$status newState=$newState")
            val intentAction: String
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    //intentAction = ACTION_GATT_CONNECTED
                    //connectionState = STATE_CONNECTED
                    //broadcastUpdate(intentAction)
                    Log.e(TAG, "Connected to GATT server.--------------------------------------STATE_CONNECTED")
                    Log.e(TAG, "Attempting to start service discovery: " + bluetoothGatt?.discoverServices())

                    bluetoothGatt?.setCharacteristicNotification(characteristic, true)
                    val uuid: UUID = UUID.fromString("127FACE1-CB21-11E5-93D0-0002A5D5C51B")
                    val descriptor = characteristic?.getDescriptor(uuid)?.apply {
                        value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    }
                    bluetoothGatt?.writeDescriptor(descriptor)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    //intentAction = ACTION_GATT_DISCONNECTED
                    //connectionState = STATE_DISCONNECTED
                    //broadcastUpdate(intentAction)
                    Log.e(TAG, "Disconnected from GATT server.--------------------------------STATE_DISCONNECTED "+bluetoothGatt?.discoverServices())
                }
            }
        }
        // New services discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> Log.e(TAG, "onServicesDiscovered----------------------------------GATT_SUCCESS")
                else -> Log.e(TAG, "onServicesDiscovered received: $status -----------------------------------------------")
            }
        }

        // Result of a characteristic read operation
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.e(TAG, "onCharacteristicRead----------------------------------GATT_SUCCESS")
                }
            }
        }
    }






    //----------------------------------------------------------------------------------------------
    // SPP
    //----------------------------------------------------------------------------------------------
    fun onActivityResult(requestCode: Int, data: Intent?) {
        if (requestCode == SPPBluetoothState.REQUEST_CONNECT_DEVICE) {
            Log.e(TAG, "SPP:  onActivityResult ---------------------------------------REQUEST_CONNECT_DEVICE "+data.toString())
            bluetoothSPP.connect(data)
        }
        /*else if (requestCode == BluetoothState.REQUEST_ENABLE_BT) {
            Log.e(TAG, "SPP:  onActivityResult ---------------------------------------REQUEST_ENABLE_BT"+data.toString())
            bluetoothSPP.setupService()
            bluetoothSPP.startService(false)//BluetoothState.DEVICE_OTHER)
            setupSpp()
        }*/
    }
    fun setupSpp() {
        if( ! bluetoothSPP.isBluetoothAvailable) {
            Log.e(TAG, "SPP: isBluetoothAvailable == false ---------------------------------------")
            return
        }

        if( ! bluetoothSPP.isBluetoothEnabled) {
            bluetoothSPP.enable()
        }

        bluetoothSPP.setBluetoothConnectionListener(object : SPPBluetooth.BluetoothConnectionListener {
            override fun onDeviceConnected(name: String, address: String) {
                Log.e(TAG, "SPP: onDeviceConnected --------------------------------------------***")
            }
            override fun onDeviceDisconnected() {
                Log.e(TAG, "SPP: onDeviceDisconnected -----------------------------------------***")
            }

            override fun onDeviceConnectionFailed() {
                Log.e(TAG, "SPP: onDeviceConnectionFailed -------------------------------------***")
            }
        })

        bluetoothSPP.setBluetoothStateListener { state ->
            when (state) {
                SPPBluetoothState.STATE_CONNECTED         // Do something when successfully connected
                -> {
                    Log.e(TAG, "SPP: onServiceStateChanged ---------------------------------------STATE_CONNECTED")
                    Log.e(TAG, "SPP: onServiceStateChanged ---------------------------------------STATE_CONNECTED: "
                            +bluetoothSPP.isServiceAvailable+" : "
                            +bluetoothSPP.connectedDeviceAddress+" : "
                            +bluetoothSPP.serviceState+" : "//STATE_CONNECTED==3
                    )

                    //bluetoothSPP.send("", false)

                    /*Log.e(TAG, "SPP: ---------------------------------------pairedDeviceAddress.size="+bluetoothSPP.pairedDeviceAddress.size)
                    if(bluetoothSPP.pairedDeviceAddress.isNotEmpty()) {
                        for(device in bluetoothSPP.pairedDeviceAddress) {
                            Log.e(TAG, "SPP: ---------------------------------------device=$device")
                            bluetoothSPP.connect(device.toString())
                            break
                        }
                        //bt.connect(bt.pairedDeviceAddress[0]) --> Exception
                    }*/
                }
                SPPBluetoothState.STATE_CONNECTING -> {  // Do something while connecting
                    Log.e(TAG,"SPP: onServiceStateChanged ---------------------------------------STATE_CONNECTING")
                }
                SPPBluetoothState.STATE_LISTEN     -> { // Do something when device is waiting for connection
                    Log.e(TAG,"SPP: onServiceStateChanged ---------------------------------------STATE_LISTEN")
                }
                SPPBluetoothState.STATE_NONE       -> {// Do something when device don't have any connection
                    Log.e(TAG, "SPP: onServiceStateChanged ---------------------------------------STATE_NONE")
                }
            }
        }

        bluetoothSPP.setOnDataReceivedListener { data, message ->
            Log.e(TAG, "SPP:DataReceived: -------------********************************-------------data=$data  message=$message")
        }

        bluetoothSPP.setupService()
        bluetoothSPP.startService()


        Log.e(TAG, "SPP: ---------------------------------------pairedDeviceAddress.size="+bluetoothSPP.pairedDeviceAddress.size)
        if(bluetoothSPP.pairedDeviceAddress.isNotEmpty()) {
            for(device in bluetoothSPP.pairedDeviceAddress) {
                Log.e(TAG, "SPP: CONNECTING---------------------------------------device=$device")
                bluetoothSPP.connect(device.toString())
                break
            }
            //bt.connect(bt.pairedDeviceAddress[0]) --> Exception
        }
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