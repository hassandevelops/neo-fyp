package com.neo.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.neo.ui.components.*
import com.neo.ui.theme.*
import com.neo.ui.viewmodel.CreatePostViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Create Post — modal sheet. The header area (handle + title bar) is draggable to
 * dismiss; the content scrolls independently. Identity is read-only; location can
 * be attached from the device GPS.
 */
@Composable
fun EnhancedCreatePostScreen(
    onPostClick: (String, String, String?, String?) -> Unit,
    onDismiss: () -> Unit,
    currentUserName: String = "",
    currentUserImageUri: String? = null,
    modifier: Modifier = Modifier
) {
    var content by remember { mutableStateOf("") }
    val authorName = currentUserName.ifBlank { "Neo User" }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var locationName by remember { mutableStateOf<String?>(null) }
    var locationLoading by remember { mutableStateOf(false) }
    val maxContentLength = 500
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Location capture ──────────────────────────────────────────────────────
    fun captureLocation() {
        locationLoading = true
        scope.launch {
            when (val r = com.neo.media.LocationProvider.currentPlace(context)) {
                is com.neo.media.LocationProvider.Result.Success -> locationName = r.name
                is com.neo.media.LocationProvider.Result.Unavailable ->
                    snackbarHostState.showSnackbar("Couldn't get your location. Try again outdoors.")
                is com.neo.media.LocationProvider.Result.PermissionDenied ->
                    snackbarHostState.showSnackbar("Location permission is required to attach a place.")
            }
            locationLoading = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) captureLocation()
        else scope.launch { snackbarHostState.showSnackbar("Location permission denied.") }
    }

    fun onLocationClick() {
        if (locationName != null) { locationName = null; return }  // toggle off
        if (com.neo.media.LocationProvider.hasPermission(context)) captureLocation()
        else locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // ── Drag-to-dismiss state ────────────────────────────────────────────────
    val sheetOffsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 200.dp.toPx() }

    // Animated dismiss function
    fun animateDismiss() {
        scope.launch {
            sheetOffsetY.animateTo(
                targetValue = with(density) { 900.dp.toPx() },
                animationSpec = tween(300)
            )
            onDismiss()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedImageUri = uri }

    // ── Full-screen scrim ────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xB30C0C0E))
            .clickable { animateDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        // ── Sheet ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .offset { IntOffset(0, sheetOffsetY.value.roundToInt()) }
                .clip(NeoShapes.sheet)
                .background(NeoDarkGray)
                // Stop scrim click from propagating through the sheet
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { /* consume — don't dismiss */ }
        ) {
            // Drag gesture shared by the whole header region (handle + title bar),
            // so users can dismiss by dragging anywhere in the header, not just the
            // tiny handle. Content below scrolls independently.
            val headerDrag = Modifier.pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (sheetOffsetY.value > dismissThreshold) {
                                animateDismiss()
                            } else {
                                sheetOffsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    },
                    onDragCancel = { scope.launch { sheetOffsetY.animateTo(0f, spring()) } },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val newOffset = (sheetOffsetY.value + dragAmount).coerceAtLeast(0f)
                            sheetOffsetY.snapTo(newOffset)
                        }
                    }
                )
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Draggable header: handle + title bar ─────────────────────
                Column(modifier = Modifier.fillMaxWidth().then(headerDrag)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TextWhite20)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 4.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { animateDismiss() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(SurfaceElevated2, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "New Post",
                            color = TextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.size(36.dp))
                    }
                }

                // ── Scrollable content — fully interactive ───────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .imePadding()
                        .padding(horizontal = 20.dp)
                ) {

                    // ── Author row — read-only identity (current user) ──────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        UserAvatar(
                            imageUri = currentUserImageUri,
                            size = 42.dp,
                            ringColor = NeoLime,
                            contentDescription = "Your avatar"
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = authorName,
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Public,
                                    null,
                                    tint = TextWhite60,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text("Public visibility", color = TextWhite60, fontSize = 12.sp)
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NeoHairline))
                    Spacer(Modifier.height(12.dp))

                    // ── Content text area ───────────────────────────────────
                    OutlinedTextField(
                        value = content,
                        onValueChange = { if (it.length <= maxContentLength) content = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp),
                        placeholder = {
                            Text(
                                "What's pulsating?",
                                color = TextWhite40,
                                fontSize = 16.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = NeoLime.copy(alpha = 0.3f),
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = NeoLime
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 16.sp,
                            color = TextWhite
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    // ── Image picker ─────────────────────────────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        if (selectedImageUri != null) {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(NeoShapes.control)
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(selectedImageUri),
                                    contentDescription = "Selected",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { selectedImageUri = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .background(NeoBlack.copy(0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, null, tint = TextWhite, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(NeoShapes.control)
                                .background(SurfaceElevated2, NeoShapes.control)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, "Add image", tint = TextWhite40, modifier = Modifier.size(28.dp))
                        }
                    }

                    // ── Attached location chip ───────────────────────────────
                    if (locationLoading || locationName != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .clip(NeoShapes.pill)
                                .background(NeoLimeGlow20)
                                .clickable(enabled = !locationLoading) { locationName = null }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            if (locationLoading) {
                                CircularProgressIndicator(
                                    color = NeoLime,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("Getting location…", color = NeoLime, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            } else {
                                Icon(Icons.Default.LocationOn, null, tint = NeoLime, modifier = Modifier.size(14.dp))
                                Text(locationName!!, color = NeoLime, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Icon(Icons.Default.Close, "Remove location", tint = NeoLime, modifier = Modifier.size(13.dp))
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NeoHairline))

                    // ── Toolbar ──────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Icon(
                                Icons.Default.Image, "Photo", tint = TextWhite60,
                                modifier = Modifier.size(22.dp).clickable { imagePickerLauncher.launch("image/*") }
                            )
                            Icon(
                                Icons.Default.LocationOn, "Location",
                                tint = if (locationName != null || locationLoading) NeoLime else TextWhite60,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable(enabled = !locationLoading) { onLocationClick() }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(SurfaceElevated3)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(content.length.toFloat() / maxContentLength)
                                    .background(
                                        when {
                                            content.length > maxContentLength * 0.9f -> NeoRed
                                            else -> NeoLime
                                        },
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }

                    // ── Post button ──────────────────────────────────────────
                    NeoPrimaryButton(
                        text = "Post to Neo",
                        icon = Icons.Default.Send,
                        onClick = {
                            if (content.isNotBlank()) {
                                onPostClick(content, authorName, selectedImageUri?.toString(), locationName)
                            }
                        },
                        enabled = content.isNotBlank(),
                        fillMaxWidth = true
                    )

                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        // Snackbar for location permission / availability feedback.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
        )
    }
}

@Composable
private fun CircularCharCounter(
    current: Int,
    max: Int,
    modifier: Modifier = Modifier
) {
    val progress = current.toFloat() / max
    val color = when {
        current > max * 0.9 -> NeoRed
        current > max * 0.7 -> NeoOrange
        else -> NeoLime
    }
    Box(modifier = modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = 3.dp.toPx()
            val r = (size.minDimension - sw) / 2
            drawCircle(color = SurfaceWhite10, radius = r, style = Stroke(sw))
            drawArc(color = color, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text("${max - current}", color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}
