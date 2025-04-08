package com.august.viiin

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONObject
import kotlin.coroutines.resume

// MainActivity.kt
class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BarcodeScannerScreen()
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun BarcodeScannerScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState = remember { mutableStateOf(ScannerUIState.Start) }
    val scannedCode = remember { mutableStateOf("") }
    val productInfo = remember { mutableStateOf("") }

    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    val toneGen = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }

    when (uiState.value) {
        ScannerUIState.Start -> {
            StartScreen(onStartClick = { uiState.value = ScannerUIState.Scanning })
        }

        ScannerUIState.Scanning -> {
            ScanningScreen(
                context = context,
                lifecycleOwner = lifecycleOwner,
                alreadyScannedCode = scannedCode.value,
                onCodeScanned = { code ->
                    scannedCode.value = code

                    // 진동 및 소리
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            100,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP)

                    // 와인 정보 가져오기
                    CoroutineScope(Dispatchers.IO).launch {
                        val result = fetchWineInfo(code)
                        withContext(Dispatchers.Main) {
                            productInfo.value = result
                            uiState.value = ScannerUIState.Result
                        }
                    }
                },
                onCloseCamera = {
                    uiState.value = ScannerUIState.Start
                    scannedCode.value = ""
                    productInfo.value = ""
                }
            )
        }

        ScannerUIState.Result -> {
            ResultScreen(
                code = scannedCode.value,
                info = productInfo.value,
                onRescan = {
                    scannedCode.value = ""
                    productInfo.value = ""
                    uiState.value = ScannerUIState.Scanning
                },
                onBackToHome = {
                    uiState.value = ScannerUIState.Start
                    scannedCode.value = ""
                    productInfo.value = ""
                }
            )
        }
    }
}

@Composable
fun StartScreen(onStartClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("바코드를 스캔하여 와인 정보를 확인하세요", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onStartClick) {
            Text("스캔 시작하기")
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun ScanningScreen(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    alreadyScannedCode: String,
    onCodeScanned: (String) -> Unit,
    onCloseCamera: () -> Unit
) {
    // 1. PreviewView를 Composable 내에서 생성
    val previewView = remember { PreviewView(context) }
    Log.d("ScanningScreen", "PreviewView surfaceProvider : ${previewView.surfaceProvider}")
    // 3. UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("카메라에 바코드를 비춰주세요", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))

        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onCloseCamera) {
            Text("카메라 닫기")
        }
    }

    // 2. LaunchedEffect로 카메라 바인딩
    LaunchedEffect(Unit) {
        val cameraProvider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13)
            .build()
        val barcodeScanner = BarcodeScanning.getClient(options)

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    val code = barcode.rawValue ?: continue
                                    if (barcode.format == Barcode.FORMAT_EAN_13 &&
                                        code != alreadyScannedCode) {
                                        onCodeScanned(code)
                                        break
                                    }
                                }
                            }
                            .addOnFailureListener {
                                Log.e("BarcodeAnalyzer", "Barcode scanning failed", it)
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            imageAnalysis
        )
    }
}

@Composable
fun ResultScreen(
    code: String,
    info: String,
    onRescan: () -> Unit,
    onBackToHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("스캔된 바코드", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(code, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))
        Text("상품 정보", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(info, fontSize = 16.sp)

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRescan) {
            Text("다시 스캔하기")
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onBackToHome) {
            Text("처음으로")
        }
    }
}

// 네트워크 요청 예시 (UPCItemDB API)
suspend fun fetchWineInfo(code: String): String = suspendCancellableCoroutine { cont ->
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://api.upcitemdb.com/prod/trial/lookup?upc=$code")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resume("API 요청 실패: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val body = response.body?.string() ?: run {
                    cont.resume("No response")
                    return
                }

                val json = JSONObject(body)
                val items = json.getJSONArray("items")

                if (items.length() > 0) {
                    val item = items.getJSONObject(0)
                    val title = item.getString("title")
                    val brand = item.optString("brand", "")
                    cont.resume("$title ($brand)")
                } else {
                    cont.resume("상품 정보를 찾을 수 없습니다.")
                }

            } catch (e: Exception) {
                cont.resume("응답 처리 실패: ${e.message}")
            }
        }
    })

    cont.invokeOnCancellation {
        client.dispatcher.cancelAll()
    }
}

enum class ScannerUIState {
    Start,
    Scanning,
    Result
}