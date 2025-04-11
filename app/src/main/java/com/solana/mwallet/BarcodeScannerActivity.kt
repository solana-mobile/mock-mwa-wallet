package com.solana.mwallet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.solana.mobilewalletadapter.walletlib.association.AssociationUri
import com.solana.mwallet.ui.theme.Lilac400
import com.solana.mwallet.ui.theme.OffWhite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class BarcodeScannerActivity : ComponentActivity() {
    private val _isCameraPermissionGranted = MutableStateFlow(false)
    private lateinit var scope: CoroutineScope
    private lateinit var snackbarHostState: SnackbarHostState

    private val cameraPermissionRequestLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            _isCameraPermissionGranted.value = isGranted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            scope = rememberCoroutineScope()
            snackbarHostState = remember { SnackbarHostState() }
            val permissionGranted = _isCameraPermissionGranted.collectAsState().value
            Box(modifier = Modifier.fillMaxSize()) {
                if (permissionGranted) {
                    BarcodeScanner { uri ->
                        runCatching {
                            val mwaUri = AssociationUri.parse(uri)!!
                            startActivity(
                                Intent(
                                    applicationContext,
                                    MobileWalletAdapterActivity::class.java
                                ).apply {
                                    data = mwaUri.uri
                                })
                            finish()
                        }.onFailure {
                            Toast.makeText(
                                this@BarcodeScannerActivity,
                                R.string.str_invalid_mwa_qr,
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }
                    }
                    BarcodeViewFinderOverlay()
                } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        this@BarcodeScannerActivity, Manifest.permission.CAMERA)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(
                            text = stringResource(R.string.str_camera_permission_required),
                            fontSize = 18.sp,
                            color = OffWhite,
                            modifier = Modifier.padding(32.dp)
                        )
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = Uri.fromParts("package", packageName, null)
                                startActivity(intent)
                            }
                        ) {
                            Text(text = stringResource(R.string.label_open_settings))
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            // Invoke the method from BaseActivity to handle permission request
                            handleCameraPermission()
                        },
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(text = stringResource(R.string.label_start_scanner))
                    }
                }
            }
        }
        handleCameraPermission()
    }

    private fun handleCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is already granted, update the state
                _isCameraPermissionGranted.value = true
            }

            else -> {
                // Permission is not granted: request it
                cameraPermissionRequestLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

@Composable
fun BarcodeScanner(
    onBarcodeDetected: (Uri) -> Unit, // Callback to handle detected QR/barcode
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var barcode by remember { mutableStateOf<Barcode?>(null) }
    var lastDetectedBarcode by remember { mutableStateOf<Barcode?>(null) }
    var boundingBox by remember { mutableStateOf<android.graphics.Rect?>(null) }
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            // Bind the LifecycleCameraController to the lifecycleOwner
            bindToLifecycle(lifecycleOwner)

            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                MlKitAnalyzer(
                    listOf(scanner),
                    ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, // Use view-referenced coordinates
                    ContextCompat.getMainExecutor(context) // Use the main thread for results
                ) { result: MlKitAnalyzer.Result? ->
                    // Process the barcode scanning results
                    val barcodes = result?.getValue(scanner)
                    barcodes?.firstOrNull()?.let {
                        boundingBox = it.boundingBox
                        if (it.rawValue != null && it.rawValue != lastDetectedBarcode?.rawValue) {
                            barcode = it
                            lastDetectedBarcode = barcode
                        }
                    }
                }
            )
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            // Initialize the PreviewView and configure it
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                controller = cameraController // Set the controller to manage the camera lifecycle
            }
        },
        onRelease = {
            // Release the camera controller when the composable is removed from the screen
            cameraController.unbind()
        }
    )

    // If a QR/barcode has been detected, trigger the callback
    LaunchedEffect(barcode) {
        barcode?.let {
            // Delay for a short duration to allow recomposition
            delay(100) // Adjust delay as needed

            it.rawValue?.toUri()?.let { uri ->
                onBarcodeDetected(uri) // Trigger the callback with the new barcode
            }

            // Clear the detected barcode to prevent re-triggering
            barcode = null
        }
    }

    // Draw a box around detected QR codes
    boundingBox?.let {
        DrawRectangle(it.toComposeRect())
    }
}

@Composable
fun DrawRectangle(rect: Rect?) {
    rect?.let {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = Lilac400,
                topLeft = Offset(it.left, it.top),
                size = Size(it.width, it.height),
                style = Stroke(width = 5f)
            )
        }
    }
}

@Preview
@Composable
fun BarcodeViewFinderOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Canvas dimensions
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Define relative positions and dimensions
        val cutoutSize = 0.75f*canvasWidth
        val cutoutXOff = 0.5f
        val cutoutYOff = 0.4f
        val cutoutTop = canvasHeight * cutoutYOff - cutoutSize/2
        val cutoutBottom = canvasHeight * cutoutYOff + cutoutSize/2
        val cutoutLeft = canvasWidth * cutoutXOff - cutoutSize/2
        val cutoutRight = canvasWidth * cutoutXOff + cutoutSize/2
        val cornerRadius = 96f

        // Background Rectangle with Rounded Corners Cutout
        val backgroundPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, canvasWidth, canvasHeight)) // Outer rectangle
            addRoundRect(
                RoundRect(
                    Rect(cutoutLeft, cutoutTop, cutoutRight, cutoutBottom),
                    CornerRadius(cornerRadius, cornerRadius)
                )
            )
        }
        drawPath(
            path = backgroundPath,
            color = Color(0x65000000) // Semi-transparent black fill
        )

        val arcSize = cornerRadius*2
        val strokeWidth = 18f
        drawArc(
            color = Color.White,
            startAngle = 180f,    // Starting angle (0 degrees)
            sweepAngle = 90f,   // Sweep angle for a quarter circle (90 degrees)
            useCenter = false,  // Draw only the stroke, not the filled shape
            topLeft = Offset(cutoutLeft, cutoutTop),
            size = Size(arcSize, arcSize),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round) // Stroke with rounded ends
        )
        drawArc(
            color = Color.White,
            startAngle = 270f,    // Starting angle (0 degrees)
            sweepAngle = 90f,   // Sweep angle for a quarter circle (90 degrees)
            useCenter = false,  // Draw only the stroke, not the filled shape
            topLeft = Offset(cutoutRight - arcSize, cutoutTop),
            size = Size(arcSize, arcSize),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round) // Stroke with rounded ends
        )
        drawArc(
            color = Color.White,
            startAngle = 90f,    // Starting angle (0 degrees)
            sweepAngle = 90f,   // Sweep angle for a quarter circle (90 degrees)
            useCenter = false,  // Draw only the stroke, not the filled shape
            topLeft = Offset(cutoutLeft, cutoutBottom - arcSize),
            size = Size(arcSize, arcSize),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round) // Stroke with rounded ends
        )
        drawArc(
            color = Color.White,
            startAngle = 0f,    // Starting angle (0 degrees)
            sweepAngle = 90f,   // Sweep angle for a quarter circle (90 degrees)
            useCenter = false,  // Draw only the stroke, not the filled shape
            topLeft = Offset(cutoutRight - arcSize, cutoutBottom - arcSize),
            size = Size(arcSize, arcSize),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round) // Stroke with rounded ends
        )
    }
}
