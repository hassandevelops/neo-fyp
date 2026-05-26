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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.neo.ui.theme.*
import com.neo.ui.viewmodel.CreatePostViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Create Post — modal sheet with drag-to-dismiss on the handle only.
 * Text input is fully interactive — drag gesture is isolated to the handle area.
 */
@Composable
fun EnhancedCreatePostScreen(
    onPostClick: (String, String, String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var content by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val maxContentLength = 500
    val maxAuthorLength = 50
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

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
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(NeoDarkGray)
                // Stop scrim click from propagating through the sheet
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { /* consume — don't dismiss */ }
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Drag handle — ONLY this area is draggable ────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
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
                                onDragCancel = {
                                    scope.launch {
                                        sheetOffsetY.animateTo(0f, spring())
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        val newOffset = (sheetOffsetY.value + dragAmount).coerceAtLeast(0f)
                                        sheetOffsetY.snapTo(newOffset)
                                    }
                                }
                            )
                        }
                        .padding(top = 12.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(GlassBorderMid)
                    )
                }

                // ── Scrollable content — fully interactive ───────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .imePadding()
                        .padding(horizontal = 20.dp)
                ) {
                    // ── Top bar: × | New Post ───────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { animateDismiss() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(NeoGray800, CircleShape)
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

                    // ── Author row ──────────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .border(2.dp, NeoLime, CircleShape)
                                .background(NeoGray800),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, null, tint = TextWhite60, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            OutlinedTextField(
                                value = authorName,
                                onValueChange = { if (it.length <= maxAuthorLength) authorName = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        "Neo User",
                                        color = TextWhite60,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = NeoLime
                                ),
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextWhite
                                ),
                                singleLine = true
                            )
                            Text("Public visibility", color = TextWhite60, fontSize = 12.sp)
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderWhite10))
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
                                    .clip(RoundedCornerShape(12.dp))
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
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, BorderWhite20, RoundedCornerShape(12.dp))
                                .background(NeoGray800, RoundedCornerShape(12.dp))
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, "Add image", tint = TextWhite40, modifier = Modifier.size(28.dp))
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderWhite10))

                    // ── Toolbar ──────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Icon(Icons.Default.Image, "Photo", tint = TextWhite60, modifier = Modifier.size(22.dp).clickable { imagePickerLauncher.launch("image/*") })
                            Icon(Icons.Default.Gif, "GIF", tint = TextWhite60, modifier = Modifier.size(22.dp))
                            Icon(Icons.Default.LocationOn, "Location", tint = TextWhite60, modifier = Modifier.size(22.dp))
                        }

                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(BorderWhite20)
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
                    Button(
                        onClick = {
                            if (content.isNotBlank()) {
                                val name = authorName.ifBlank { "Neo User" }
                                onPostClick(content, name, selectedImageUri?.toString())
                            }
                        },
                        enabled = content.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .padding(bottom = 4.dp),
                        shape = RoundedCornerShape(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeoLime,
                            disabledContainerColor = NeoLime.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = "Post to Neo  ▷",
                            color = NeoBlack,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }
        }
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
