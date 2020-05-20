package com.cesoft.cesble.device

import android.bluetooth.BluetoothDevice
import org.greenrobot.eventbus.EventBus

object ConnectedDevice {

    var classicDevice: BluetoothDevice? = null
    var classicStatus: Int = 0
        set(v) {
            field = v
            EventBus.getDefault().post(ClassicEvent(field))
        }
    class ClassicEvent(status: Int)

    var leDevice: BluetoothDevice? = null
    var leStatus: Int = 0
        set(v) {
            field = v
            EventBus.getDefault().post(LeEvent(field))
        }
    class LeEvent(status: Int)

    var scoAudioStatus: Int = 0
        set(v) {
            field = v
            EventBus.getDefault().post(ScoEvent(field))
        }
    class ScoEvent(status: Int)
}