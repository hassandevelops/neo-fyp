package com.neo.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.ui.components.GradientBackground
import com.neo.ui.theme.*

/**
 * Edit Profile Screen for updating user information
 */
@Composable
fun EditProfileScreen(
    currentName: String = "Neo User",
    currentBio: String = "Decentralized social media enthusiast",
    onSave: (name: String, bio: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(currentName) }
    var bio by remember { mutableStateOf(currentBio) }
    var nameError by remember { mutableStateOf<String?>(null) }
    
    GradientBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NeoBlack.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                                onSave(name.trim(), bio.trim())
                            }
                        }
                    ) {
                        Text(
                            text = "Save",
                            color = NeoLime,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
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
                // Profile Picture Placeholder
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(NeoPurple, NeoPink, NeoOrange)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile Picture",
                        tint = TextWhite,
                        modifier = Modifier.size(60.dp)
                    )
                }
                
                Text(
                    text = "Change Photo",
                    color = NeoCyan,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
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
