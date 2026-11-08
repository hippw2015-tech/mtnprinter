package com.example.receiptprinter

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { App() }
            }
        }
    }
}

@Composable
fun SliderRow(label: String, shown: String, v: Float, range: ClosedFloatingPointRange<Float>, on: (Float) -> Unit) {
    Column { Text("$label: $shown"); Slider(value = v, onValueChange = on, valueRange = range) }
}

@Composable
fun Chips(options: List<String>, sel: Int, on: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, t ->
            FilterChip(selected = sel == i, onClick = { on(i) }, label = { Text(t) })
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("p", 0) }
    val printer = remember { PrinterManager(ctx) }
    var s by remember { mutableStateOf(Store.load(prefs)) }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(listOf<BluetoothDevice>()) }
    var selected by remember { mutableStateOf<BluetoothDevice?>(null) }
    var src by remember { mutableStateOf<Bitmap?>(null) }
    var autoPaper by remember { mutableStateOf(false) }

    LaunchedEffect(s) { Store.save(prefs, s) }

    fun refresh() {
        devices = printer.bonded()
        val last = prefs.getString("last", null)
        selected = devices.firstOrNull { it.address == last } ?: printer.guess(devices)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 31)
            permLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
        else refresh()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            src = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
            status = "تم تحميل الصورة، اضغط استخراج النص"
        }
    }

    val preview = remember(text, s) {
        if (text.isBlank()) null else ReceiptRenderer.render(convertDigits(text, s.digits), s)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("طباعة الإيصالات", style = MaterialTheme.typography.headlineSmall)

        // 1) الصورة و OCR
        Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("اختيار صورة الإشعار") }
        src?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 220.dp))
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    busy = true; status = "جارٍ استخراج النص..."
                    try { text = withContext(Dispatchers.Default) { Ocr.run(ctx, it) }; status = "تم. راجع النص وصحّحه قبل الطباعة" }
                    catch (e: Exception) { status = "خطأ: ${e.message}" }
                    busy = false
                }
            }) { Text("استخراج النص (عربي + إنجليزي)") }
        }
        OutlinedTextField(text, { text = it }, label = { Text("النص (قابل للتعديل)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp))

        // 2) الطابعة
        Text("الطابعة", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refresh() }) { Text("تحديث") }
            OutlinedButton(onClick = {
                selected = printer.guess(devices)
                if (autoPaper) s = s.copy(paperDots = printer.guessPaper(selected))
            }) { Text("كشف تلقائي") }
        }
        devices.forEach { d ->
            FilterChip(selected = selected?.address == d.address,
                onClick = { selected = d; prefs.edit().putString("last", d.address).apply() },
                label = { Text("${d.name ?: "?"}  (${d.address})") })
        }
        Row { Checkbox(autoPaper, { autoPaper = it }); Text("تخمين المقاس من اسم الطابعة", Modifier.padding(top = 12.dp)) }
        Text("مقاس الورق")
        Chips(listOf("58 ملم", "80 ملم"), if (s.paperDots == 384) 0 else 1) { s = s.copy(paperDots = if (it == 0) 384 else 576) }

        // 3) الخط
        Text("التحكم بالخط", style = MaterialTheme.typography.titleMedium)
        SliderRow("حجم الخط", s.textSize.roundToInt().toString(), s.textSize, 14f..64f) { s = s.copy(textSize = it) }
        SliderRow("التغميق (عتبة السواد)", s.threshold.toString(), s.threshold.toFloat(), 60f..230f) { s = s.copy(threshold = it.roundToInt()) }
        SliderRow("المسافة بين الأسطر", "%.2f".format(s.lineSpacing), s.lineSpacing, 0.9f..2f) { s = s.copy(lineSpacing = it) }
        SliderRow("الهامش", s.margin.toString(), s.margin.toFloat(), 0f..30f) { s = s.copy(margin = it.roundToInt()) }
        Row { Checkbox(s.bold, { s = s.copy(bold = it) }); Text("خط عريض", Modifier.padding(top = 12.dp)) }
        Row { Checkbox(s.extraBold, { s = s.copy(extraBold = it) }); Text("عريض جداً (تضخيم الحواف)", Modifier.padding(top = 12.dp)) }
        Text("المحاذاة")
        Chips(listOf("تلقائي", "يمين", "وسط", "يسار"), s.align) { s = s.copy(align = it) }
        Text("الأرقام")
        Chips(listOf("كما هي", "1 2 3", "١ ٢ ٣"), s.digits) { s = s.copy(digits = it) }

        // 4) خيارات متقدمة
        Text("متقدم", style = MaterialTheme.typography.titleMedium)
        SliderRow("تأخير الإرسال (ms) - ارفعه إذا ظهرت رموز غريبة", s.delayMs.toString(), s.delayMs.toFloat(), 0f..100f) { s = s.copy(delayMs = it.roundToInt()) }
        SliderRow("أسطر فارغة بعد الطباعة", s.feedLines.toString(), s.feedLines.toFloat(), 0f..12f) { s = s.copy(feedLines = it.roundToInt()) }
        Row { Checkbox(s.compat, { s = s.copy(compat = it) }); Text("وضع توافق للطابعات القديمة (ESC *)", Modifier.padding(top = 12.dp)) }
        Row { Checkbox(s.cut, { s = s.copy(cut = it) }); Text("قصّ الورقة بعد الطباعة", Modifier.padding(top = 12.dp)) }

        // 5) المعاينة والطباعة
        Text("المعاينة (كما ستُطبع)", style = MaterialTheme.typography.titleMedium)
        preview?.let {
            Image(it.asImageBitmap(), null,
                Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White))
        }
        fun print(bmp: Bitmap) {
            scope.launch {
                busy = true
                try {
                    val dev = selected ?: throw Exception("اختر الطابعة أولاً")
                    status = "جارٍ الاتصال..."; printer.connect(dev)
                    status = "جارٍ الطباعة..."
                    printer.send(EscPos.build(bmp, s), s.delayMs)
                    status = "تمت الطباعة ✓"
                } catch (e: Exception) { status = "خطأ: ${e.message}"; printer.close() }
                busy = false
            }
        }
        Button(enabled = !busy && preview != null, modifier = Modifier.fillMaxWidth(),
            onClick = { preview?.let { print(it) } }) { Text("طباعة") }
        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            print(ReceiptRenderer.render("اختبار الطباعة\nTest 12345\n0123456789  ٠١٢٣٤٥٦٧٨٩", s))
        }) { Text("طباعة اختبار") }
        if (status.isNotEmpty()) Text(status)
    }
}
