package com.cesoft.cesble.device

class DiskEvent(val type: Type, val freeSpace: Long) {
    enum class Type { DISK_AT_90 }
}