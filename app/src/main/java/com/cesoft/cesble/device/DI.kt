package com.cesoft.cesble.device

import org.koin.dsl.module

val bluetoothDIModule = module {
    single { Bluetooth() }
    single { BluetoothClassic() }
    single { BluetoothLE() }
}