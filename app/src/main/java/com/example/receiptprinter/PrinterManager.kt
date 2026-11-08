package com.example.receiptprinter

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

@SuppressLint("MissingPermission")
class PrinterManager(ctx: Context) {
    private val adapter: BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var socket: BluetoothSocket? = null
    private var address: String? = null
    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun bonded(): List<BluetoothDevice> = adapter?.bondedDevices?.toList() ?: emptyList()

    /** تخمين تلقائي للطابعة من الأجهزة المقترنة */
    fun guess(list: List<BluetoothDevice>): BluetoothDevice? {
        val keys = listOf("inner", "print", "pos", "mpt", "rpp", "pt-", "thermal", "bt")
        return list.firstOrNull { d ->
            d.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.IMAGING ||
                keys.any { (d.name ?: "").lowercase().contains(it) }
        }
    }

    /** تخمين العرض من الاسم؛ غير مضمون، يمكن تغييره يدوياً */
    fun guessPaper(d: BluetoothDevice?): Int =
        if ((d?.name ?: "").contains("80")) 576 else 384

    suspend fun connect(dev: BluetoothDevice) = withContext(Dispatchers.IO) {
        if (socket?.isConnected == true && address == dev.address) return@withContext
        close()
        adapter?.cancelDiscovery()
        val attempts = listOf<() -> BluetoothSocket>(
            { dev.createRfcommSocketToServiceRecord(spp) },
            { dev.createInsecureRfcommSocketToServiceRecord(spp) },
            { dev.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                .invoke(dev, 1) as BluetoothSocket }
        )
        var last: Exception? = null
        for (a in attempts) {
            try {
                val s = a(); s.connect(); socket = s; address = dev.address; return@withContext
            } catch (e: Exception) { last = e }
        }
        throw last ?: Exception("تعذر الاتصال")
    }

    /** إرسال على حزم صغيرة مع تأخير: هذا يمنع تلف البيانات والرموز الصينية */
    suspend fun send(data: ByteArray, delayMs: Int) = withContext(Dispatchers.IO) {
        val os = socket?.outputStream ?: throw Exception("الطابعة غير متصلة")
        var off = 0
        val chunk = 512
        while (off < data.size) {
            val n = minOf(chunk, data.size - off)
            os.write(data, off, n); os.flush(); off += n
            if (delayMs > 0) delay(delayMs.toLong())
        }
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null; address = null
    }
}
