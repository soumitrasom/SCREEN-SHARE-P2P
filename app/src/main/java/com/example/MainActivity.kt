package com.example

import android.content.Context
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DeviceConnection
import com.example.data.StreamSession
import com.example.network.TouchCommand
import com.example.services.MediaProjectionService
import com.example.services.RemoteInputAccessibilityService
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.*
import com.example.ui.viewmodel.*
import androidx.compose.ui.graphics.nativeCanvas
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: CastViewModel

    // Media projection permissions contract
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = android.content.Intent(this, MediaProjectionService::class.java).apply {
                putExtra(MediaProjectionService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(MediaProjectionService.EXTRA_RESULT_DATA, result.data)
            }
            MediaProjectionService.activeServerInstance = null // Bind when server connects
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Real Media Projection Service Ready!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission declined.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[CastViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    MainContent(
                        viewModel = viewModel,
                        onRequestSystemProjection = { launchSystemProjection() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun launchSystemProjection() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }
}

@Composable
fun MainContent(
    viewModel: CastViewModel,
    onRequestSystemProjection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeRole by viewModel.activeRole.collectAsStateWithLifecycle()
    val receivedFrame by viewModel.receivedFrame.collectAsStateWithLifecycle()

    // 1. Full Screen Receiver Port (If streaming frame is received, overlay in full depth display)
    if (activeRole is CastViewModel.Role.Receiver && receivedFrame != null) {
        ReceiverDisplayPort(
            viewModel = viewModel,
            bitmapFrame = receivedFrame,
            onCloseStream = { viewModel.stopAll() },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // 2. Standard Tabbed Technical Hub Interface
        val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()

        Column(modifier = modifier.fillMaxSize()) {
            HeaderBar(viewModel)

            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    0 -> SyncHubTab(viewModel, onRequestSystemProjection)
                    1 -> PairingHistoryTab(viewModel)
                    2 -> DiagnosticsTab(viewModel)
                }
            }

            // High custom system navigation dock
            BottomNavigationBar(currentTab) { viewModel.setTab(it) }
        }
    }
}

@Composable
fun HeaderBar(viewModel: CastViewModel) {
    val activeRole by viewModel.activeRole.collectAsStateWithLifecycle()
    val statusText by viewModel.connectionStateText.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepMidnight)
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon container with matching M3 styling from HTML (bg-[#D0BCFF], text-[#381E72])
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VoltGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cast,
                    contentDescription = "CastFlow Logo",
                    tint = Color(0xFF381E72),
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = "CastFlow Pro",
                    color = TextLight,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.5).sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (activeRole is CastViewModel.Role.None) StatusGreen
                                else if (statusText.contains("Live") || statusText.contains("Connected")) StatusGreen
                                else ElectricCyan
                            )
                    )
                    Text(
                        text = if (activeRole is CastViewModel.Role.None) "Ready to Cast" else statusText,
                        color = TextSlate,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        // Accessibility status chip styled elegantly in status colors
        val accessState = RemoteInputAccessibilityService.isRunning
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (accessState) StatusGreen.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.12f))
                .border(1.dp, if (accessState) StatusGreen else Color.Red, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (accessState) "ACCS ACTIVE" else "NO ACCS",
                color = if (accessState) StatusGreen else Color.Red,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun SyncHubTab(
    viewModel: CastViewModel,
    onRequestSystemProjection: () -> Unit
) {
    val activeRole by viewModel.activeRole.collectAsStateWithLifecycle()
    val isLoopbackEnabled by viewModel.isLoopbackEnabled.collectAsStateWithLifecycle()
    val streamFps by viewModel.streamFps.collectAsStateWithLifecycle()
    val streamKbps by viewModel.streamKbps.collectAsStateWithLifecycle()
    val streamLatency by viewModel.streamLatency.collectAsStateWithLifecycle()

    var showMyQrDialog by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // A. SYSTEM CONTROLS & LOOPBACK BINDING
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = BorderStroke(1.dp, TechOutline),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageIconsSync(),
                            contentDescription = "Loopback",
                            tint = VoltGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Local Loopback Synchronizer",
                                fontWeight = FontWeight.Bold,
                                color = TextLight,
                                fontSize = 15.sp
                            )
                            Text(
                                "Run cast sender and receiver together on localhost. Perfect for in-browser reviews!",
                                color = TextSlate,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isLoopbackEnabled,
                            onCheckedChange = { viewModel.toggleLoopback(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VoltGreen,
                                checkedTrackColor = VoltGreen.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }

        // B. NETWORK SETTINGS SCREEN
        if (!isLoopbackEnabled) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CarbonCard),
                    border = BorderStroke(1.dp, TechOutline),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "CONNECTION CHANNEL PARAMETERS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoltGreen,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        val connectionType by viewModel.connectionType.collectAsStateWithLifecycle()
                        val ipInput by viewModel.senderIpInput.collectAsStateWithLifecycle()
                        val nameInput by viewModel.deviceNameInput.collectAsStateWithLifecycle()

                        // Channel Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGreySpace)
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (connectionType == "WIFI") TechOutline else Color.Transparent)
                                    .clickable { viewModel.setConnectionType("WIFI") }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Wi-Fi (TCP)", color = if (connectionType == "WIFI") VoltGreen else TextSlate, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (connectionType == "BLUETOOTH") TechOutline else Color.Transparent)
                                    .clickable { viewModel.setConnectionType("BLUETOOTH") }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Bluetooth (SPP)", color = if (connectionType == "BLUETOOTH") ElectricCyan else TextSlate, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // IP Target Input
                        OutlinedTextField(
                            value = ipInput,
                            onValueChange = { viewModel.updateSenderIp(it) },
                            label = { Text("Sender IP Address (Receiver to Connect To)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VoltGreen,
                                focusedLabelColor = VoltGreen,
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight
                            ),
                            leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = "IP", tint = VoltGreen) },
                            trailingIcon = {
                                IconButton(onClick = { showScannerDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Scan Sender QR",
                                        tint = VoltGreen
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Device Name Input
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { viewModel.updateDeviceName(it) },
                            label = { Text("Device Peer Identifier") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                focusedLabelColor = ElectricCyan,
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight
                            ),
                            leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = "Name", tint = ElectricCyan) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Dynamic QR Pairing Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showMyQrDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = VoltGreen.copy(alpha = 0.15f), contentColor = VoltGreen),
                                border = BorderStroke(1.dp, VoltGreen),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = "My QR", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Show My QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { showScannerDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepMidnight),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan QR Code", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Connectivity Subnet & Bluetooth Verification Panel
        if (!isLoopbackEnabled) {
            item {
                ConnectivityVerificationCard(viewModel)
            }
        }

        // C. REFRESH RATE CONTROLS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = BorderStroke(1.dp, TechOutline),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "SCREEN REFRESH-RATE CAP LIMIT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        "Sets frame rendering dispatcher timing intervals. Higher cycles request faster, smoother mirroring.",
                        color = TextSlate,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val activeLimit by viewModel.refreshRateLimit.collectAsStateWithLifecycle()
                    val fpsOptions = listOf(30, 60, 90, 120)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fpsOptions.forEach { rate ->
                            val selected = activeLimit == rate
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) ElectricCyan.copy(alpha = 0.15f) else DarkGreySpace)
                                    .border(1.dp, if (selected) ElectricCyan else TechOutline, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setRefreshRate(rate) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${rate}Hz",
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) ElectricCyan else TextLight,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        "${(1000f/rate).toInt()}ms",
                                        color = TextSlate,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // D. PERFORMANCE ANALYTICS PANEL (IF STREAMING ACTIVE)
        if (streamFps > 0 || activeRole !is CastViewModel.Role.None) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CarbonCard),
                    border = BorderStroke(1.dp, TechOutline),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "LIVE STREAM PERFORMANCE HUB",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoltGreen,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("FPS Renderer", color = TextSlate, fontSize = 11.sp)
                                Text("$streamFps Hz", color = VoltGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Throughput Speed", color = TextSlate, fontSize = 11.sp)
                                Text(String.format("%.1f Kb/s", streamKbps), color = ElectricCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Local Lag Latency", color = TextSlate, fontSize = 11.sp)
                                Text("$streamLatency ms", color = ElectricPink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        LatencyRollingGraph(streamLatency)
                    }
                }
            }
        }

        // E. SENDER CARD VIEWPORT & COMMAND RIPPLE RENDERER
        if (activeRole is CastViewModel.Role.Sender) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CarbonCard),
                    border = BorderStroke(1.dp, VoltGreen),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "ACTIVE SENDER MONITOR FEED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoltGreen,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                            "Hosting server active. Receiver devices casting into your screen loop will appear here.",
                            color = TextSlate,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Draw live visual simulator monitor inside sender view
                        val touches by viewModel.remoteTouches.collectAsStateWithLifecycle()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGreySpace)
                                .border(1.dp, TechOutline, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "CAST FEED OUTGOING\n(Listening Port 18080)",
                                style = LocalTextStyle.current.copy(
                                    fontSize = 12.sp,
                                    color = VoltGreen.copy(alpha = 0.4f),
                                    lineHeight = 18.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                            )

                            // Render touch location ripples
                            touches.forEach { touch ->
                                ExpandingRippleMarker(touch)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onRequestSystemProjection,
                                colors = ButtonDefaults.buttonColors(containerColor = VoltGreen, contentColor = DeepMidnight),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CastConnected, "Proj", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cast System Screen")
                            }
                            Button(
                                onClick = { viewModel.stopAll() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = TextLight),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Stop Server")
                            }
                        }
                    }
                }
            }
        }

        // F. ROLE SELECTION BUTTONS (IF OFFLINE)
        if (activeRole is CastViewModel.Role.None) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Sender Switcher
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.startSender() },
                        colors = CardDefaults.cardColors(containerColor = CarbonCard),
                        border = BorderStroke(1.dp, VoltGreen)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.QrCodeScanner, "Host", tint = VoltGreen, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Sender Mode", fontWeight = FontWeight.Bold, color = TextLight, fontSize = 14.sp)
                            Text("Broadcasting screen", color = TextSlate, fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                    }

                    // Receiver Switcher
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.startReceiver() },
                        colors = CardDefaults.cardColors(containerColor = CarbonCard),
                        border = BorderStroke(1.dp, ElectricCyan)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Monitor, "Viewer", tint = ElectricCyan, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Receiver Mode", fontWeight = FontWeight.Bold, color = TextLight, fontSize = 14.sp)
                            Text("Render mirror stream", color = TextSlate, fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    val qrUriString by viewModel.myConnectionQrUri.collectAsStateWithLifecycle()

    if (showMyQrDialog) {
        com.example.ui.components.MyQrCodeDialog(
            qrUriString = qrUriString,
            onDismiss = { showMyQrDialog = false }
        )
    }

    if (showScannerDialog) {
        com.example.ui.components.QrCodeScannerDialog(
            onScanSuccess = { text ->
                viewModel.parseQrCodeAndConnect(text)
                showScannerDialog = false
            },
            onDismiss = { showScannerDialog = false }
        )
    }
}

@Composable
fun ExpandingRippleMarker(touch: TouchIndicator) {
    var scaleProgVal by remember { mutableStateOf(0f) }
    var alphaProgVal by remember { mutableStateOf(100f) }

    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(600, easing = LinearEasing),
        finishedListener = { scaleProgVal = 1f }
    )
    val alpha by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(600, easing = LinearEasing),
        finishedListener = { alphaProgVal = 0f }
    )

    // Run trigger
    LaunchedEffect(Unit) {
        scaleProgVal = 0.5f
    }

    if (alphaProgVal > 0) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (touch.xRatio * 410).dp, y = (touch.yRatio * 200).dp) // Adjust bounds of simulator box
                    .size((40 * scale).dp)
                    .clip(CircleShape)
                    .background(Color.Red.copy(alpha = 0.4f * alpha))
                    .border(2.dp, Color.Red.copy(alpha = alpha), CircleShape)
            )
        }
    }
}

@Composable
fun ReceiverDisplayPort(
    viewModel: CastViewModel,
    bitmapFrame: Bitmap?,
    onCloseStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color.Black)) {
        if (bitmapFrame != null) {
            Image(
                bitmap = bitmapFrame.asImageBitmap(),
                contentDescription = "Screen Mirror Feed",
                modifier = Modifier
                    .fillMaxSize()
                    // Detect taps and drags to transmit coordinates down TCP socket
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { offset ->
                                val xRatio = offset.x / size.width.toFloat()
                                val yRatio = offset.y / size.height.toFloat()
                                viewModel.dispatchClientTouch(xRatio, yRatio, 0) // DOWN
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val xRatio = offset.x / size.width.toFloat()
                                val yRatio = offset.y / size.height.toFloat()
                                viewModel.dispatchClientTouch(xRatio, yRatio, 0)
                            },
                            onDrag = { change, _ ->
                                val pos = change.position
                                val xRatio = pos.x / size.width.toFloat()
                                val yRatio = pos.y / size.height.toFloat()
                                viewModel.dispatchClientTouch(xRatio, yRatio, 2) // MOVE
                            },
                            onDragEnd = {
                                viewModel.dispatchClientTouch(0.5f, 0.5f, 1) // UP (Nominal center lift)
                            }
                        )
                    }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VoltGreen)
            }
        }

        // Overlay status heads display on receiver
        val streamFps by viewModel.streamFps.collectAsStateWithLifecycle()
        val streamKbps by viewModel.streamKbps.collectAsStateWithLifecycle()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .border(1.dp, TechOutline, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column {
                    Text("DECODED RENDERING: $streamFps FPS", color = VoltGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("SPEED BANDWIDTH: ${String.format("%.1f", streamKbps)} Kb/s", color = ElectricCyan, fontSize = 10.sp)
                }
            }

            FloatingActionButton(
                onClick = onCloseStream,
                containerColor = Color.Red,
                contentColor = Color.White,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Close, "Close Mirroring")
            }
        }
    }
}

@Composable
fun LatencyRollingGraph(latency: Int) {
    val samplePoints = remember { mutableStateListOf<Int>() }
    val maxSamples = 20

    LaunchedEffect(latency) {
        if (latency > 0) {
            samplePoints.add(latency)
            if (samplePoints.size > maxSamples) {
                samplePoints.removeAt(0)
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(DarkGreySpace, RoundedCornerShape(8.dp))
            .border(1.dp, TechOutline, RoundedCornerShape(8.dp))
    ) {
        if (samplePoints.isNotEmpty()) {
            val widthStep = size.width / (maxSamples - 1)
            val maxVal = (samplePoints.maxOrNull() ?: 10).coerceAtLeast(10).toFloat()

            val path = androidx.compose.ui.graphics.Path()
            samplePoints.forEachIndexed { i, value ->
                // Draw graph values inverted (coordinates y starts at top-left)
                val x = i * widthStep
                val y = size.height - (value / maxVal) * (size.height * 0.7f) - 5f
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = ElectricPink,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
            )

            // Render latency text logs
            val average = samplePoints.average()
            drawContext.canvas.nativeCanvas.drawText(
                "Roundtrip Wave Average: ${String.format("%.1f", average)}ms",
                20f,
                40f,
                android.graphics.Paint().apply {
                    color = Color.White.value.toInt()
                    textSize = 22f
                }
            )
        }
    }
}

@Composable
fun PairingHistoryTab(viewModel: CastViewModel) {
    val connections by viewModel.connectionHistory.collectAsStateWithLifecycle()
    val sessions by viewModel.sessionLogs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MIRROR PAIRINGS RECORDED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = VoltGreen,
                fontFamily = FontFamily.Monospace
            )
            TextButton(onClick = { viewModel.clearDatabaseHistory() }) {
                Text("Clear History", color = Color.Red, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Previous Connected Partners", color = TextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            if (connections.isEmpty()) {
                item {
                    EmptyStateCard(message = "No paired casting devices in history yet.")
                }
            } else {
                items(connections) { pair ->
                    PairDeviceRow(pair)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Casting Session Performance Logs", color = TextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            if (sessions.isEmpty()) {
                item {
                    EmptyStateCard(message = "No metrics gathered. Complete streams to record statistics logs.")
                }
            } else {
                items(sessions) { log ->
                    SessionMetricLogCard(log)
                }
            }
        }
    }
}

@Composable
fun PairDeviceRow(log: DeviceConnection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonCard)
            .border(1.dp, TechOutline, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(log.deviceName, color = TextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("IPv4 address: ${log.ipAddress}", color = TextSlate, fontSize = 11.sp)
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (log.connectionType == "WIFI") VoltGreen.copy(alpha = 0.15f) else ElectricCyan.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(log.connectionType, color = if (log.connectionType == "WIFI") VoltGreen else ElectricCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SessionMetricLogCard(session: StreamSession) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonCard)
            .border(1.dp, TechOutline, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(session.timestamp))
            Text(dateStr, color = TextSlate, fontSize = 11.sp)
            Text("${session.maxRefreshRate} Hz Stream limit", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("FPS (Avg)", color = TextSlate, fontSize = 10.sp)
                Text("${session.averageFps.toInt()} Frame rate", color = VoltGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Column {
                Text("Network Lag", color = TextSlate, fontSize = 10.sp)
                Text("${session.averageLatencyMs} ms", color = ElectricPink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Column {
                Text("Size Transferred", color = TextSlate, fontSize = 10.sp)
                Text("${session.bytesTransferred / 1024} KB", color = TextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun EmptyStateCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(DarkGreySpace, RoundedCornerShape(10.dp))
            .border(1.dp, TechOutline, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = TextSlate, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun DiagnosticsTab(viewModel: CastViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "SYSTEM SETUP MANUALS & TELEMETRY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = VoltGreen,
                fontFamily = FontFamily.Monospace
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = BorderStroke(1.dp, TechOutline),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("How to cast your full mobile device screen:", fontWeight = FontWeight.Bold, color = TextLight, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "1. Toggle the Local Loopback switch off.\n" +
                        "2. Click the 'Cast System Screen' button inside the active settings card.\n" +
                        "3. Grant the MediaProjection request on the system overlay prompt.\n" +
                        "4. The background foreground streamer launches instantly on background notification channels.\n" +
                        "5. Receiver devices connected to your IPv4 address can stream and Remote Control your device, even when the CastFlow app minimized in backgrounds!",
                        color = TextSlate,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonCard),
                border = BorderStroke(1.dp, TechOutline),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("How to enable Remote Input Controls tactile mappings:", fontWeight = FontWeight.Bold, color = TextLight, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "1. Open Android Settings -> Accessibility Features.\n" +
                        "2. Find and choose 'Cast Flow Remote Control service' inside installed services.\n" +
                        "3. Click Enable / Active.\n" +
                        "4. This grants gesture injection authorization so remote taps received across TCP socket stream map exactly and perform real actions on the Android environment!",
                        color = TextSlate,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    NavigationBar(
        containerColor = DarkGreySpace,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onSelect(0) },
            icon = { Icon(Icons.Default.ScreenShare, contentDescription = "Mirror") },
            label = { Text("Mirror") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF1D192B),
                unselectedIconColor = TextSlate,
                selectedTextColor = TextLight,
                unselectedTextColor = TextSlate,
                indicatorColor = Color(0xFFE8DEF8)
            )
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onSelect(1) },
            icon = { Icon(Icons.Default.History, contentDescription = "History") },
            label = { Text("History") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF1D192B),
                unselectedIconColor = TextSlate,
                selectedTextColor = TextLight,
                unselectedTextColor = TextSlate,
                indicatorColor = Color(0xFFE8DEF8)
            )
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onSelect(2) },
            icon = { Icon(Icons.Default.Info, contentDescription = "Guides") },
            label = { Text("Guides") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF1D192B),
                unselectedIconColor = TextSlate,
                selectedTextColor = TextLight,
                unselectedTextColor = TextSlate,
                indicatorColor = Color(0xFFE8DEF8)
            )
        )
    }
}

// Inline fallback vectors mapping support safely
fun imageIconsSync() = Icons.Default.Transform

@Composable
fun ConnectivityVerificationCard(viewModel: CastViewModel) {
    val isVerifying by viewModel.isVerifying.collectAsStateWithLifecycle()
    val wifiVerified by viewModel.wifiVerified.collectAsStateWithLifecycle()
    val wifiVerificationMsg by viewModel.wifiVerificationMsg.collectAsStateWithLifecycle()
    val bluetoothVerified by viewModel.bluetoothVerified.collectAsStateWithLifecycle()
    val bluetoothVerificationMsg by viewModel.bluetoothVerificationMsg.collectAsStateWithLifecycle()
    val overallPassed by viewModel.overallVerificationPassed.collectAsStateWithLifecycle()
    val ipInput by viewModel.senderIpInput.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = CarbonCard),
        border = BorderStroke(1.dp, if (overallPassed) VoltGreen else TechOutline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "CO-LOCATION CONNECTIVITY VERIFIER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (overallPassed) VoltGreen else ElectricCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "Verifies mutual local subnet & bluetooth co-location sync",
                        color = TextSlate,
                        fontSize = 11.sp
                    )
                }
                
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = VoltGreen
                    )
                } else {
                    IconButton(
                        onClick = { viewModel.triggerBackgroundDiscoveryAndVerification() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-verify wireless alignment",
                            tint = TextSlate,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. WiFi Subnet Segment
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkGreySpace)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (wifiVerified) VoltGreen.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (wifiVerified) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Wi-Fi Subnet Status",
                        tint = if (wifiVerified) VoltGreen else Color.Red,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wi-Fi LAN Peer Match Check",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextLight
                    )
                    Text(
                        text = wifiVerificationMsg,
                        fontSize = 11.sp,
                        color = if (wifiVerified) StatusGreen else TextSlate
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Bluetooth Segment
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkGreySpace)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (bluetoothVerified) ElectricCyan.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (bluetoothVerified) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                        contentDescription = "Bluetooth Align Status",
                        tint = if (bluetoothVerified) ElectricCyan else TextSlate,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Background Bluetooth Ad Check",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextLight
                    )
                    Text(
                        text = bluetoothVerificationMsg,
                        fontSize = 11.sp,
                        color = if (bluetoothVerified) ElectricCyan else TextSlate
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Overall result indicator box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (overallPassed) VoltGreen.copy(alpha = 0.08f)
                        else if (ipInput == "127.0.0.1") VoltGreen.copy(alpha = 0.05f)
                        else Color.Red.copy(alpha = 0.05f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (overallPassed) VoltGreen.copy(alpha = 0.3f) else TechOutline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                (if (overallPassed) VoltGreen else if (ipInput == "127.0.0.1") StatusGreen else Color.Red)
                                    .copy(alpha = pulseAlpha)
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (overallPassed) {
                            "Verification Successful! Both Wi-Fi alignment and Bluetooth are verified to match."
                        } else if (ipInput == "127.0.0.1") {
                            "Loopback Mode bypass active: Optimal simulated local casting."
                        } else {
                            "Alert: Wi-Fi subnet or local connection parameters mismatch. Check SSID or peer IP address."
                        },
                        fontSize = 11.sp,
                        color = if (overallPassed) VoltGreen else if (ipInput == "127.0.0.1") StatusGreen else TextSlate,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
