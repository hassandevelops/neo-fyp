package com.neo.data.model

/**
 * A top-level comment together with all of its replies, flattened to a single
 * level for display (Instagram-style). Replies that are nested deeper in the
 * database (a reply to a reply) are grouped under their thread root so the UI
 * never shows unbounded nesting.
 */
data class CommentThread(
    val comment: Comment,
    val replies: List<Comment> = emptyList()
)
