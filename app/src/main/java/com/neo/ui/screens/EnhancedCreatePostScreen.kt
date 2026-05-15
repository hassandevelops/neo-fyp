package com.neo.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.neo.ui.theme.*
import com.neo.ui.viewmodel.CreatePostViewModel
import kotlinx.coroutines.delay

/**
 * Create Post — glassmorphic modal sheet.
 * Dark background with color-blob decoration, NeoLime "Post to Neo" pill at bottom.
 */
@Composable
fun EnhancedCreatePostScreen(
    viewModel: CreatePostViewModel,
    snackbarHostState: SnackbarHostState,
    onPostClick: (String, String, String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var content by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val uiState by viewModel.uiState.collectAsState()
    val maxContentLength = 500
    val maxAuthorLength = 50

    LaunchedEffect(Unit) { viewModel.resetUiState() }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is CreatePostViewModel.UiState.Success -> {
                snackbarHostState.showSnackbar(message = state.message)
                delay(1500)
                onDismiss()
            }
            is CreatePostViewModel.UiState.Error -> {
                snackbarHostState.showSnackbar(message = state.message)
            }
            else -> {}
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedImageUri = uri }

    // ── Full-screen dark overlay ──────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBlack.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        // ── Sheet ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        ) {
            // Color blob background (glassmorphic)
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Brown/orange blob – top left
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x88A0522D), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.15f),
                        radius = size.width * 0.55f
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.2f, size.height * 0.15f)
                )
                // Teal blob – right
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x6614B8A6), Color.Transparent),
                        center = Offset(size.width * 0.8f, size.height * 0.3f),
                        radius = size.width * 0.5f
                    ),
                    radius = size.width * 0.5f,
                    center = Offset(size.width * 0.8f, size.height * 0.3f)
                )
            }

            // Dark glass layer on top of blobs
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC0A0A0A))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                // ── Top bar: × | New Post ───────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
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
                    Spacer(Modifier.size(36.dp)) // balance
                }

                // ── Author row ──────────────────────────────────────────────
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
                        // Author name field
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

                // ── Content text area ───────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { if (it.length <= maxContentLength) content = it },
                        modifier = Modifier.fillMaxSize(),
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
                            focusedBorderColor = NeoLime.copy(alpha = 0.5f),
                            unfocusedBorderColor = BorderWhite20,
                            cursorColor = NeoLime
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 16.sp,
                            color = TextWhite
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ── Image grid picker ────────────────────────────────────────
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

                    // Add image slot
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, BorderWhite20, RoundedCornerShape(12.dp))
                            .background(SurfaceWhite5)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, "Add image", tint = TextWhite40, modifier = Modifier.size(28.dp))
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderWhite10))

                // ── Toolbar: photo / gif / location ─────────────────────────
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

                    // Character progress bar
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

                // ── Post to Neo button ───────────────────────────────────────
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
