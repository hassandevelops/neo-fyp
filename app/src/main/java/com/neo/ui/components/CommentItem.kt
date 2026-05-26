package com.neo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neo.data.model.Comment
import com.neo.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Composable for displaying a single comment.
 * Supports nested replies with indentation.
 */
@Composable
fun CommentItem(
    comment: Comment,
    onReplyClick: (Comment) -> Unit,
    modifier: Modifier = Modifier,
    nestingLevel: Int = 0
) {
    val maxNestingLevel = 3 // Limit nesting depth
    val indentDp = (nestingLevel * 16).dp
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = indentDp, top = 8.dp, end = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = NeoGray900
        ),
        border = BorderStroke(0.5.dp, GlassBorderSubtle),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Author and timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = formatTimestamp(comment.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Comment content
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Reply button (only show if not at max nesting level)
            if (nestingLevel < maxNestingLevel) {
                Spacer(modifier = Modifier.height(8.dp))
                
                TextButton(
                    onClick = { onReplyClick(comment) },
                    modifier = Modifier
                        .height(32.dp)
                        .background(NeoGray800, RoundedCornerShape(16.dp)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Reply,
                        contentDescription = "Reply",
                        modifier = Modifier.size(14.dp),
                        tint = NeoLime
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reply",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeoLime
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
