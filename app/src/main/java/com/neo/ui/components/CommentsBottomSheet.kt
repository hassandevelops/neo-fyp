package com.neo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neo.data.model.Comment

/**
 * Bottom sheet for viewing and adding comments on a post.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    postId: String,
    topLevelComments: List<Comment>,
    getReplies: (String) -> List<Comment>,
    onDismiss: () -> Unit,
    onCommentSubmit: (content: String, parentCommentId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var commentText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Comment?>(null) }
    val maxCommentLength = 500
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Comments",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Comments list
            if (topLevelComments.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No comments yet. Be the first to comment!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(topLevelComments) { comment ->
                        CommentWithReplies(
                            comment = comment,
                            getReplies = getReplies,
                            onReplyClick = { replyingTo = it },
                            nestingLevel = 0
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Reply indicator
            if (replyingTo != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Replying to ${replyingTo!!.authorName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        TextButton(onClick = { replyingTo = null }) {
                            Text("Cancel")
                        }
                    }
                }
            }
            
            // Comment input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { 
                        if (it.length <= maxCommentLength) {
                            commentText = it
                        }
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { 
                        Text(
                            if (replyingTo != null) "Write a reply..." 
                            else "Write a comment..."
                        ) 
                    },
                    maxLines = 3,
                    supportingText = {
                        Text(
                            text = "${commentText.length}/$maxCommentLength",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            onCommentSubmit(commentText.trim(), replyingTo?.id)
                            commentText = ""
                            replyingTo = null
                        }
                    },
                    enabled = commentText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send comment",
                        tint = if (commentText.isNotBlank()) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Recursively display a comment and its replies.
 */
@Composable
private fun CommentWithReplies(
    comment: Comment,
    getReplies: (String) -> List<Comment>,
    onReplyClick: (Comment) -> Unit,
    nestingLevel: Int
) {
    CommentItem(
        comment = comment,
        onReplyClick = onReplyClick,
        nestingLevel = nestingLevel
    )
    
    // Display replies
    val replies = getReplies(comment.id)
    replies.forEach { reply ->
        CommentWithReplies(
            comment = reply,
            getReplies = getReplies,
            onReplyClick = onReplyClick,
            nestingLevel = nestingLevel + 1
        )
    }
}
