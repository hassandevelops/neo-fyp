package com.neo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenMediaViewer(
    images: List<File>,
    initialIndex: Int = 0,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible || images.isEmpty()) return

    var uiVisible by remember { mutableStateOf(true) }
    val clampedIndex = initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = clampedIndex,
        pageCount = { images.size }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomableImage(
                imageFile = images[page],
                onDismiss = onDismiss
            )
        }

        AnimatedVisibility(
            visible = uiVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(Modifier.fillMaxSize()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                if (images.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(images.size) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == pagerState.currentPage) Color.White
                                        else Color.White.copy(alpha = 0.4f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    imageFile: File,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var swipeOffsetY by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = 250f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    if (newScale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        swipeOffsetY += pan.y
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2f
                        }
                    }
                )
            }
            .graphicsLayer {
                val swipeFraction = abs(swipeOffsetY) / dismissThreshold
                if (scale <= 1f) {
                    alpha = (1f - swipeFraction).coerceIn(0f, 1f)
                }
            }
    ) {
        val painter = rememberAsyncImagePainter(model = imageFile)

        Image(
            painter = painter,
            contentDescription = "Full screen image",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY + swipeOffsetY / 3f
                ),
            contentScale = ContentScale.Fit
        )

        LaunchedEffect(swipeOffsetY) {
            if (abs(swipeOffsetY) > dismissThreshold) {
                onDismiss()
            }
        }
    }
}
