package com.example

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.example.architecture.*
import com.example.ui.theme.*
import com.example.ui.components.CustomUnstyledTextField
import com.example.ui.components.CustomUnstyledButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AuthScreen(onAction: (AppAction) -> Unit) {
    val ipAddressState = rememberTextFieldState()
    
    val context = LocalContext.current
    val nsdHelper = remember { com.example.network.NsdHelper(context) }
    val discoveredServices by nsdHelper.discoveredServices.collectAsStateWithLifecycle()
    
    LaunchedEffect(discoveredServices) {
        if(discoveredServices.isNotEmpty() && ipAddressState.text.isEmpty()){
            ipAddressState.setTextAndPlaceCursorAtEnd(discoveredServices.first().host)
        }
    }
    
    DisposableEffect(Unit) {
        nsdHelper.startDiscovery()
        onDispose {
            nsdHelper.stopDiscovery()
        }
    }
    
    LaunchedEffect(Unit) {
        // Reset connection status if we are on auth screen
        onAction(PairingAction.Disconnect)
    }
    var isScanning by remember { mutableStateOf(false) }
    
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Connect to OpenCode",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Scan the QR code on your laptop terminal to pair this device, or enter IP manually.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            if (isScanning) {
                if (cameraPermissionState.status.isGranted) {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(SurfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        QRScannerPreview(onQrCodeScanned = { qrContent ->
                            isScanning = false
                            try {
                                val extractedIp = qrContent.split(";").find { it.startsWith("ip=") }?.substringAfter("=") ?: qrContent
                                val deviceId = java.util.UUID.randomUUID().toString()
                                
                                val parts = qrContent.split(";")
                                val ip = parts.find { it.startsWith("ip=") }?.substringAfter("=")
                                val key = parts.find { it.startsWith("key=") }?.substringAfter("=")
                                if (ip != null && key != null) {
                                    ipAddressState.setTextAndPlaceCursorAtEnd(ip)
                                    com.example.network.KtorClient.updateApiKey(key)
                                    onAction(PairingAction.ConnectManually(ip))
                                } else {
                                    ipAddressState.setTextAndPlaceCursorAtEnd(qrContent)
                                }
                            } catch (e: Exception) {
                                ipAddressState.setTextAndPlaceCursorAtEnd(qrContent)
                            }
                        })
                        Button(onClick = { isScanning = false }, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                            Text("Cancel")
                        }
                    }
                } else {
                    LaunchedEffect(Unit) {
                        cameraPermissionState.launchPermissionRequest()
                    }
                    Text("Requesting camera permission...")
                    Button(onClick = { isScanning = false }) { Text("Cancel") }
                }
            } else {
                CustomUnstyledTextField(
                    state = ipAddressState,
                    placeholder = "e.g. 192.168.1.100"
                )
            }
            
            val ipStr = ipAddressState.text.toString()
            val ipPart = ipStr.substringBefore(":")
            val isIpValid = isValidIp(ipPart)
            
            if (ipStr.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cleartext Warning",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Connection will use unencrypted cleartext HTTP/WebSockets. Ensure you are on a trusted local network.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        if (!isIpValid) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "⚠️ Invalid IP Address format",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            CustomUnstyledButton(
                onClick = { 
                    if (ipStr.isNotBlank()) {
                        if (isIpValid) {
                            onAction(PairingAction.ConnectManually(ipStr))
                        }
                    } else {
                        isScanning = true 
                    }
                },
                enabled = ipStr.isBlank() || isIpValid,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (ipStr.isNotBlank()) "Connect" else "Scan QR Code", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { onAction(NavigationAction.Navigate("devices")) }) {
                Text("Manage Paired Devices", color = Primary)
            }
        }
    }
}

private fun isValidIp(ip: String): Boolean {
    val ipRegex = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
    return ip.trim().matches(ipRegex.toRegex())
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun QRScannerPreview(onQrCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasScanned = remember { AtomicBoolean(false) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            val scanner = BarcodeScanning.getClient()

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null || hasScanned.get()) {
                        imageProxy.close()
                    } else {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val value = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
                                    ?: barcodes.firstOrNull()?.rawValue
                                if (value != null && hasScanned.compareAndSet(false, true)) {
                                    onQrCodeScanned(value)
                                }
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("QRScanner", "Barcode scan failed: ${e.message}")
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (exc: Exception) {
                    exc.printStackTrace()
                }
            }, executor)
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
