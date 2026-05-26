package com.neo.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.neo.ui.components.GradientBackground
import com.neo.ui.theme.*

/**
 * Edit Profile Screen — name, bio, and profile picture editing.
 */
@Composable
fun EditProfileScreen(
    currentName: String = "Neo User",
    currentBio: String = "Decentralized social media enthusiast",
    currentImageUri: String? = null,
    onSave: (name: String, bio: String, imageUri: String?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(currentName) }
    var bio by remember { mutableStateOf(currentBio) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(currentImageUri?.let { Uri.parse(it) }) }
    var isSaving by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) selectedImageUri = uri
    }

    GradientBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header — solid dark, integrated
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NeoBlack
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = TextWhite
                        )
                    }

                    Text(
                        text = "Edit Profile",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    TextButton(
                        onClick = {
                            if (name.isBlank()) {
                                nameError = "Name cannot be empty"
                            } else {
                                nameError = null
                                isSaving = true
                                onSave(name.trim(), bio.trim(), selectedImageUri?.toString())
                            }
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = NeoLime,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Save",
                                color = NeoLime,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Profile Picture — clickable with camera overlay
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null) {
                        // Show selected image
                        Image(
                            painter = rememberAsyncImagePainter(selectedImageUri),
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Default avatar
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(NeoGray800),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile Picture",
                                tint = TextWhite60,
                                modifier = Modifier.size(60.dp)
                            )
                        }
                    }

                    // Camera overlay badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NeoLime),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change photo",
                            tint = NeoBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // "Change Photo" text — also clickable
                Text(
                    text = "Change Photo",
                    color = NeoLime,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { imagePickerLauncher.launch("image/*") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Name Field
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Name",
                        color = TextWhite60,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter your name", color = TextWhite40) },
                        isError = nameError != null,
                        supportingText = nameError?.let {
                            { Text(it, color = MaterialTheme.colorScheme.error) }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = NeoLime,
                            unfocusedBorderColor = BorderWhite10,
                            errorBorderColor = MaterialTheme.colorScheme.error,
                            cursorColor = NeoLime
                        ),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                }

                // Bio Field
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Bio",
                        color = TextWhite60,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        placeholder = { Text("Tell us about yourself", color = TextWhite40) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = NeoLime,
                            unfocusedBorderColor = BorderWhite10,
                            cursorColor = NeoLime
                        ),
                        shape = RoundedCornerShape(16.dp),
                        maxLines = 5
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Cancel Button
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextWhite60
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(
                            colors = listOf(BorderWhite10, BorderWhite10)
                        )
                    )
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}
