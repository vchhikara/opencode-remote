package com.opencode.remote.ui.screens.pairing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.opencode.remote.data.network.ConnectionState
import com.opencode.remote.data.network.RemoteSessionManager
import com.opencode.remote.data.pairing.parseQrResult
import com.opencode.remote.data.storage.TokenStorage
import com.opencode.remote.ui.components.Eyebrow
import com.opencode.remote.ui.components.OcButton
import com.opencode.remote.ui.components.OcButtonKind
import com.opencode.remote.ui.components.OcTextField
import com.opencode.remote.ui.components.StatusDot
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcType
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun PairingScreen(
    sessionManager: RemoteSessionManager,
    tokenStorage: TokenStorage,
    onNavigateToWorkspaces: () -> Unit
) {
    val context = LocalContext.current
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf(Build.MODEL) }
    var isScanning by remember { mutableStateOf(false) }

    val connectionState by sessionManager.connectionState.collectAsState()

    LaunchedEffect(Unit) {
        tokenStorage.getLastCredentials()?.let {
            host = it.host
            port = it.port.toString()
            token = it.token
            deviceName = it.deviceName
        }
    }

    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            val p = port.toIntOrNull() ?: 8080
            tokenStorage.saveCredentials(host, p, token, deviceName)
            onNavigateToWorkspaces()
        }
    }

    if (isScanning) {
        QRScannerScreen(
            onQrCodeScanned = { result ->
                parseQrResult(result)?.let {
                    host = it.host
                    port = it.port.toString()
                    token = it.token
                }
                isScanning = false
            },
            onClose = { isScanning = false }
        )
    } else {
        val c = Oc.colors
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Eyebrow("OpenCode Remote", color = c.goldInk)
            Spacer(modifier = Modifier.height(10.dp))
            Text("Pair with your bridge", style = OcType.pageTitle, color = c.ink)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Scan the QR code the bridge prints, or enter its address and token.",
                style = OcType.body, color = c.ink2
            )
            Spacer(modifier = Modifier.height(24.dp))

            PairingField("Host", host, { host = it }, "192.168.x.x", mono = true)
            PairingField("Port", port, { port = it }, "8080", mono = true, keyboardType = KeyboardType.Number)
            PairingField("Token", token, { token = it }, "pairing token", mono = true)
            PairingField("Device name", deviceName, { deviceName = it }, "This phone")

            if (connectionState is ConnectionState.Error) {
                Text(
                    text = (connectionState as ConnectionState.Error).message,
                    style = OcType.body,
                    color = c.negInk,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            } else if (connectionState is ConnectionState.Connecting) {
                Row(
                    Modifier.padding(top = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    StatusDot(c.posInk, pulsing = true)
                    Text("Connecting…", style = OcType.body, color = c.ink2)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OcButton(
                    "Scan QR",
                    onClick = { isScanning = true },
                    height = 46.dp,
                    textStyle = OcType.buttonLarge,
                    modifier = Modifier.weight(1f)
                )
                OcButton(
                    "Connect",
                    onClick = {
                        val p = port.toIntOrNull() ?: 8080
                        sessionManager.connect(host, p, token, deviceName)
                    },
                    kind = OcButtonKind.Gold,
                    enabled = host.isNotBlank() && token.isNotBlank(),
                    height = 46.dp,
                    textStyle = OcType.buttonLargeStrong,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PairingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    mono: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Eyebrow(label)
    Spacer(modifier = Modifier.height(8.dp))
    OcTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = Modifier.fillMaxWidth(),
        textStyle = if (mono) OcType.mono.copy(fontSize = OcType.input.fontSize, lineHeight = OcType.input.lineHeight) else OcType.input,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = ImeAction.Next
        )
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun QRScannerScreen(onQrCodeScanned: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    ) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val scanner = BarcodeScanning.getClient()
                        val executor = Executors.newSingleThreadExecutor()

                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        barcodes.firstOrNull()?.rawValue?.let { value ->
                                            onQrCodeScanned(value)
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
            
            OcButton(
                "Close",
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Oc.colors.panel)
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
            Eyebrow("Camera needed", color = Oc.colors.goldInk)
            Spacer(modifier = Modifier.height(10.dp))
            Text("Allow camera access to scan the bridge's QR code.", style = OcType.bodyLarge, color = Oc.colors.ink2)
            Spacer(modifier = Modifier.height(18.dp))
            OcButton("Back to manual entry", onClick = onClose, height = 46.dp)
        }
    }
}
