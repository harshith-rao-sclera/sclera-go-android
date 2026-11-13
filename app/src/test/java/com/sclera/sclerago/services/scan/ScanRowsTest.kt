package com.sclera.sclerago.services.scan

import com.sclera.sclerago.domain.model.UNKNOWN_DEVICE
import com.sclera.sclerago.domain.validation.MAX_SCAN_TEXT
import com.sclera.sclerago.services.scan.model.BleDevice
import com.sclera.sclerago.services.scan.model.WifiAccessPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanRowsTest {
    private fun ble(name: String?, rssi: Int = -52, maker: String? = null) =
        BleDevice(address = "AA:BB:CC:DD:EE:FF", name = name, rssiDbm = rssi, manufacturerName = maker)

    @Test fun bleRowShowsKindSignalAndDistance() {
        val row = ble("TH-22 sensor").toRow()
        assertEquals("TH-22 sensor", row.name)
        assertEquals("BLE device · −52 dBm · ~1 m", row.sub)
        assertTrue(row.lvl in 1..4)
    }

    @Test fun unnamedBleDeviceIsUnknown() = assertEquals(UNKNOWN_DEVICE, ble(null).toRow().name)

    @Test fun bleNameIsSanitised() {
        val row = ble("‮tag\u0000\n" + "x".repeat(300)).toRow()
        assertEquals(MAX_SCAN_TEXT, row.name.length)
        assertTrue(row.name.none { it.isISOControl() || it == '‮' })
    }

    @Test fun manufacturerIsSanitisedInTheDetailLine() {
        val row = ble("Tag", maker = "Acme\r\nCorp").toRow()
        assertFalse(row.sub.contains('\n'))
        assertTrue(row.sub.startsWith("Acme Corp · "))
    }

    @Test fun farAwayDeviceIsCappedAt30mPlus() = assertTrue(ble("Tag", rssi = -120).toRow().sub.endsWith("30 m+"))

    @Test fun accessPointNameAndDetailAreSanitised() {
        val ap = WifiAccessPoint(ssid = "Guest\u0000\n" + "y".repeat(200), bssid = "F0:9F:C2:1C:3F:10", rssiDbm = -60, frequencyMHz = 5180, securityLabel = "WPA2")
        val row = ap.toRow()
        assertTrue(row.name.length <= MAX_SCAN_TEXT)
        assertTrue(row.name.none { it.isISOControl() })
        assertTrue(row.sub.startsWith("F0:9F:C2:1C:3F:10"))
        assertEquals(ap.signalBars.coerceIn(1, 4), row.lvl)
    }
}
