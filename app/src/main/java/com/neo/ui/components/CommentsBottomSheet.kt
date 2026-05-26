package com.neo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.neo.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    postId: String,
    topLevelComments: List<Comment>,
    getReplies: @Composable (String) -> List<Comment>,
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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
                        .background(NeoGray800, CircleShape)
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
            if (topLevelComments.isEmpty()) {
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

            // Reply indicator
            if (replyingTo != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = NeoGray800,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 0.dp,
                    border = BorderStroke(0.5.dp, BorderWhite10)
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
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = GlassBorderGlow,
                        unfocusedBorderColor = BorderWhite10,
                        focusedContainerColor = GlassWhite8,
                        unfocusedContainerColor = Color.Transparent,
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
                        .size(40.dp)
                        .background(
                            if (commentText.isNotBlank()) NeoGray700 else NeoGray800,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send comment",
                        tint = if (commentText.isNotBlank()) NeoLime else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentWithReplies(
    comment: Comment,
    getReplies: @Composable (String) -> List<Comment>,
    onReplyClick: (Comment) -> Unit,
    nestingLevel: Int
) {
    CommentItem(
        comment = comment,
        onReplyClick = onReplyClick,
        nestingLevel = nestingLevel
    )

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
