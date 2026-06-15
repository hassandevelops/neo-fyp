package com.neo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.data.model.Comment
import com.neo.data.model.CommentThread
import com.neo.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * A single comment rendered as a clean row: avatar, author + timestamp, content,
 * and a "Reply" action. Used for both top-level comments and replies (replies use
 * a smaller avatar and sit indented within their thread).
 */
@Composable
fun CommentRow(
    comment: Comment,
    avatarFor: (Comment) -> String?,
    onReply: (Comment) -> Unit,
    modifier: Modifier = Modifier,
    isReply: Boolean = false
) {
    val avatarSize = if (isReply) 28.dp else 36.dp
    val imageUri = avatarFor(comment)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        UserAvatar(
            imageUri = imageUri,
            size = avatarSize,
            contentDescription = comment.authorName
        )

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextWhite
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatTimestamp(comment.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextWhite40
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = TextWhite80
            )

            Text(
                text = "Reply",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite60,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { onReply(comment) }
            )
        }
    }
}

/**
 * A top-level comment and its replies. Replies are collapsed behind a
 * "View replies (N)" toggle and indented once when expanded, keeping the list
 * readable regardless of how deep the underlying reply chain goes.
 */
@Composable
fun CommentThreadItem(
    thread: CommentThread,
    avatarFor: (Comment) -> String?,
    onReply: (Comment) -> Unit,
    modifier: Modifier = Modifier
) {
    // Indent so replies + the toggle line up under the parent's text, not its avatar.
    val replyIndent = 46.dp

    Column(modifier = modifier.fillMaxWidth()) {
        CommentRow(
            comment = thread.comment,
            avatarFor = avatarFor,
            onReply = onReply
        )

        if (thread.replies.isNotEmpty()) {
            var expanded by remember(thread.comment.id) { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .padding(start = replyIndent, top = 2.dp, bottom = 2.dp)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Short connector line, Instagram-style.
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(1.dp)
                        .background(NeoHairline)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (expanded) "Hide replies"
                    else "View replies (${thread.replies.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextWhite60,
                    fontSize = 12.sp
                )
            }

            if (expanded) {
                thread.replies.forEach { reply ->
                    CommentRow(
                        comment = reply,
                        avatarFor = avatarFor,
                        onReply = onReply,
                        isReply = true,
                        modifier = Modifier.padding(start = replyIndent)
                    )
                }
            }
        }
    }
}

/**
 * Format timestamp to relative time string.
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
