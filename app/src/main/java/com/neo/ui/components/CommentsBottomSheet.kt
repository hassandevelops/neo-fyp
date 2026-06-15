package com.neo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neo.data.model.Comment
import com.neo.data.model.CommentThread
import com.neo.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    postId: String,
    threads: List<CommentThread>,
    avatarFor: (Comment) -> String?,
    onDismiss: () -> Unit,
    onCommentSubmit: (content: String, parentCommentId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var commentText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Comment?>(null) }
    val maxCommentLength = 500
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(replyingTo) {
        if (replyingTo != null) {
            delay(200)
            focusRequester.requestFocus()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = NeoDarkGray,     // Solid charcoal surface
        scrimColor = Color(0x990C0C0E),
        shape = NeoShapes.sheet,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp)
                .imePadding()
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

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(SurfaceElevated2, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Comments or empty state
            if (threads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
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
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(threads, key = { it.comment.id }) { thread ->
                        CommentThreadItem(
                            thread = thread,
                            avatarFor = avatarFor,
                            onReply = { replyingTo = it }
                        )
                    }
                }
            }

            // Reply indicator
            if (replyingTo != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = SurfaceElevated2,
                    shape = NeoShapes.control,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Replying to ${replyingTo!!.authorName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeoLime,
                            fontWeight = FontWeight.SemiBold
                        )

                        TextButton(onClick = { replyingTo = null }) {
                            Text("Cancel", color = TextWhite60)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = {
                        if (it.length <= maxCommentLength) commentText = it
                    },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            if (replyingTo != null) "Write a reply..."
                            else "Write a comment..."
                        )
                    },
                    maxLines = 3,
                    shape = NeoShapes.control,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = NeoLime,
                        unfocusedBorderColor = NeoHairline,
                        focusedContainerColor = SurfaceElevated2,
                        unfocusedContainerColor = SurfaceElevated1,
                        cursorColor = NeoLime
                    ),
                    supportingText = {
                        Text(
                            text = "${commentText.length}/$maxCommentLength",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            onCommentSubmit(commentText.trim(), replyingTo?.id)
                            commentText = ""
                            replyingTo = null
                        }
                    },
                    enabled = commentText.isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (commentText.isNotBlank()) NeoLime else SurfaceElevated2,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send comment",
                        tint = if (commentText.isNotBlank()) NeoBlack else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
