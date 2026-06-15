package com.neo.ui.screens

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.ui.theme.*
import androidx.compose.material3.MaterialTheme

data class OnboardingPage(
    val icon: @Composable () -> Unit,
    val title: String,
    val description: String
)

@Composable
fun OnboardingScreen(
    onComplete: (username: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableIntStateOf(0) }
    var username by remember { mutableStateOf("") }

    val pages = remember {
        listOf(
            OnboardingPage(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                },
                title = "Decentralized Network",
                description = "Connect with nearby devices using Bluetooth Low Energy. Your data stays on your device, shared directly with peers."
            ),
            OnboardingPage(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(64.dp)
                    )
                },
                title = "End-to-End Encrypted",
                description = "Every post is cryptographically signed with your private key. Only you can prove you created it."
            ),
            OnboardingPage(
                icon = {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                },
                title = "Offline First",
                description = "No servers, no accounts. Your social network works anywhere, even without internet."
            ),
            OnboardingPage(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(64.dp)
                    )
                },
                title = "Build Your Community",
                description = "Discover and interact with people around you. The more peers, the stronger the network."
            )
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "onboarding")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button (skips the intro, but the profile step is still required)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                if (currentPage < pages.size) {
                    TextButton(onClick = { currentPage = pages.size }) {
                        Text(
                            text = "Skip",
                            color = TextWhite60,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Page content
            if (currentPage < pages.size) {
                val page = pages[currentPage]

                // Icon with glow
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(pulseScale),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated2, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        page.icon()
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Title
                Text(
                    text = page.title,
                    color = TextWhite,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Description
                Text(
                    text = page.description,
                    color = TextWhite60,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                // Final step: choose a username (required)
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = NeoLime,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Choose a username",
                    color = TextWhite,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "This is how you'll appear to other people on the network. You can add a profile photo later in Settings.",
                    color = TextWhite60,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { if (it.length <= 30) username = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Username", color = TextWhite40) },
                    shape = NeoShapes.control,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = NeoLime,
                        unfocusedBorderColor = NeoHairline,
                        focusedContainerColor = SurfaceElevated1,
                        unfocusedContainerColor = SurfaceElevated1,
                        cursorColor = NeoLime
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Page indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalSteps = pages.size + 1
                repeat(totalSteps) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentPage) 24.dp else 8.dp, 8.dp)
                            .background(
                                if (index == currentPage) NeoLime else SurfaceElevated3,
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (currentPage > 0) {
                    OutlinedButton(
                        onClick = { currentPage-- },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = NeoShapes.pill,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextWhite
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeoHairline)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Back",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                val isProfileStep = currentPage == pages.size
                val canAdvance = !isProfileStep || username.isNotBlank()

                Button(
                    onClick = {
                        if (isProfileStep) {
                            if (username.isNotBlank()) onComplete(username.trim())
                        } else {
                            currentPage++
                        }
                    },
                    enabled = canAdvance,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = NeoShapes.pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeoLime,
                        contentColor = NeoBlack,
                        disabledContainerColor = SurfaceElevated2,
                        disabledContentColor = TextWhite40
                    )
                ) {
                    Text(
                        text = if (isProfileStep) "Get Started" else "Next",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isProfileStep) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Next",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}