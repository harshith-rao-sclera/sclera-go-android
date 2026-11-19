package com.sclera.sclerago.domain.model

/** Id prefixes, so a row can be traced back to the kind of scan that found it. */
const val BLE_ID = "ble:"
const val NET_ID = "ip:"
const val AP_ID = "ap:"

/** Shown (and treated as "no name") when a device advertises nothing better than its address. */
const val UNKNOWN_DEVICE = "Unknown device"
