package com.neo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.R

/**
 * Screen for creating a new post.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    onPostClick: (content: String, authorName: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var content by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf("") }
    val maxLength = 280
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_post)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (content.isNotBlank() && authorName.isNotBlank()) {
                                onPostClick(content.trim(), authorName.trim())
                            }
                        },
                        enabled = content.isNotBlank() && 
                                 authorName.isNotBlank() && 
                                 content.length <= maxLength
                    ) {
                        Text(stringResource(R.string.post_button))
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Author name input
            OutlinedTextField(
                value = authorName,
                onValueChange = { authorName = it },
                label = { Text(stringResource(R.string.author_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Post content input
            OutlinedTextField(
                value = content,
                onValueChange = { 
                    if (it.length <= maxLength) {
                        content = it
                    }
                },
                label = { Text(stringResource(R.string.post_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                maxLines = 10
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Character count
            Text(
                text = stringResource(R.string.character_limit, content.length, maxLength),
                style = MaterialTheme.typography.bodySmall,
                color = if (content.length > maxLength) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
