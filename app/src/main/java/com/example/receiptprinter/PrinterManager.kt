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
    /** إرسال حزماً صغيرة (128 بايت) مع انتظار بعد كل حزمة وبعد كل شريحة */
    suspend fun sendUnits(units: List<ByteArray>, chunkDelay: Int, unitDelay: Int) =
        withContext(Dispatchers.IO) {
            val os = socket?.outputStream ?: throw Exception("الطابعة غير متصلة")
            delay(400)
            val chunk = 128
            for (u in units) {
                var off = 0
                while (off < u.size) {
                    val n = minOf(chunk, u.size - off)
                    os.write(u, off, n)
                    os.flush()
                    off += n
                    if (chunkDelay > 0) delay(chunkDelay.toLong())
                }
                if (unitDelay > 0) delay(unitDelay.toLong())
            }
            delay(300)
        }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null; address = null
    }
}
