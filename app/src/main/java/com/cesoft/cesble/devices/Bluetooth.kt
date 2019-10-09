package com.cesoft.cesble.devices

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.cesoft.cesble.App
import org.koin.dsl.module


val globalModule = module {
    single { Bluetooth(App.instance) }
    single { BluetoothClassic() }
    single { BluetoothLE() }
}
class Bluetooth(val context: Context) {

    //private val appContext: Context by injec()  TODO

    //private val bluetoothAdapter2 = BluetoothAdapter.getDefaultAdapter()
    val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    val isEnabled: Boolean
        get() = bluetoothAdapter != null && bluetoothAdapter!!.isEnabled
    val isDisabled: Boolean
        get() = ! isEnabled

}