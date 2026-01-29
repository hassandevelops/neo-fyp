import { motion, AnimatePresence } from 'motion/react';
import { ArrowLeft, Heart, MessageCircle, Share2, Bookmark, Send, MoreVertical, Smile } from 'lucide-react';
import { useState } from 'react';

interface Comment {
  id: number;
  username: string;
  avatar: string;
  text: string;
  timestamp: string;
  likes: number;
  isLiked?: boolean;
}

interface PostDetailProps {
  post: {
    id: number;
    username: string;
    avatar: string;
    timestamp: string;
    image: string;
    likes: number;
    comments: number;
    caption?: string;
  };
  onBack: () => void;
}

const mockComments: Comment[] = [
  {
    id: 1,
    username: 'alex.kim',
    avatar: 'https://images.unsplash.com/photo-1614283233556-f35b0c801ef1?w=100&h=100&fit=crop',
    text: 'This is absolutely stunning! The lighting is perfect 🔥',
    timestamp: '2h ago',
    likes: 124,
    isLiked: false,
  },
  {
    id: 2,
    username: 'maya.rose',
    avatar: 'https://images.unsplash.com/photo-1544124094-8aea0374da93?w=100&h=100&fit=crop',
    text: 'Love the neon aesthetic! How did you capture this?',
    timestamp: '3h ago',
    likes: 89,
    isLiked: true,
  },
  {
    id: 3,
    username: 'josh.wave',
    avatar: 'https://images.unsplash.com/photo-1626113666894-ac3923772213?w=100&h=100&fit=crop',
    text: 'Incredible composition 👏',
    timestamp: '4h ago',
    likes: 56,
    isLiked: false,
  },
  {
    id: 4,
    username: 'stella.nx',
    avatar: 'https://images.unsplash.com/photo-1571513722275-4b41940f54b8?w=100&h=100&fit=crop',
    text: 'The colors are so vibrant! What camera did you use?',
    timestamp: '5h ago',
    likes: 42,
    isLiked: false,
  },
  {
    id: 5,
    username: 'ryan.sky',
    avatar: 'https://images.unsplash.com/photo-1670324382035-f9cfacc3b59b?w=100&h=100&fit=crop',
    text: 'This belongs in a gallery 🎨',
    timestamp: '6h ago',
    likes: 78,
    isLiked: false,
  },
];

function CommentItem({ comment }: { comment: Comment }) {
  const [isLiked, setIsLiked] = useState(comment.isLiked || false);
  const [likes, setLikes] = useState(comment.likes);

  const handleLike = () => {
    setIsLiked(!isLiked);
    setLikes(isLiked ? likes - 1 : likes + 1);
  };

  return (
    <motion.div
      initial={{ opacity: 0, x: -20 }}
      animate={{ opacity: 1, x: 0 }}
      className="flex gap-3 py-4"
    >
      <img
        src={comment.avatar}
        alt={comment.username}
        className="w-8 h-8 rounded-full object-cover border border-white/10 flex-shrink-0"
      />
      
      <div className="flex-1 min-w-0">
        <div className="flex items-start justify-between gap-2">
          <div className="flex-1">
            <div className="flex items-center gap-2 mb-1">
              <span className="text-white text-sm">{comment.username}</span>
              <span className="text-white/40 text-xs">{comment.timestamp}</span>
            </div>
            <p className="text-white/80 text-sm leading-relaxed">{comment.text}</p>
          </div>
        </div>
        
        <div className="flex items-center gap-4 mt-2">
          <motion.button
            whileTap={{ scale: 0.9 }}
            onClick={handleLike}
            className="flex items-center gap-1 group"
          >
            <motion.div
              animate={isLiked ? { scale: [1, 1.3, 1] } : {}}
              transition={{ duration: 0.3 }}
            >
              <Heart
                className={`w-4 h-4 transition-colors ${
                  isLiked ? 'fill-orange-500 text-orange-500' : 'text-white/40 group-hover:text-orange-500'
                }`}
              />
            </motion.div>
            {likes > 0 && (
              <span className="text-white/60 text-xs">{likes}</span>
            )}
          </motion.button>
          
          <button className="text-white/40 hover:text-white/60 text-xs transition-colors">
            Reply
          </button>
        </div>
      </div>
    </motion.div>
  );
}

export function PostDetail({ post, onBack }: PostDetailProps) {
  const [liked, setLiked] = useState(false);
  const [saved, setSaved] = useState(false);
  const [likes, setLikes] = useState(post.likes);
  const [commentText, setCommentText] = useState('');
  const [isFocused, setIsFocused] = useState(false);

  const handleLike = () => {
    setLiked(!liked);
    setLikes(liked ? likes - 1 : likes + 1);
  };

  const handleComment = () => {
    if (commentText.trim()) {
      console.log('Posting comment:', commentText);
      setCommentText('');
    }
  };

  return (
    <div className="min-h-screen bg-black relative overflow-hidden">
      {/* Background gradient effects */}
      <div className="fixed inset-0 pointer-events-none">
        <div className="absolute top-0 left-1/4 w-96 h-96 bg-purple-600/20 rounded-full blur-[120px]" />
        <div className="absolute top-1/2 right-1/4 w-80 h-80 bg-orange-500/15 rounded-full blur-[100px]" />
        <div className="absolute bottom-1/4 left-1/3 w-72 h-72 bg-teal-500/15 rounded-full blur-[100px]" />
      </div>

      {/* Header */}
      <motion.header
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="sticky top-0 z-50 backdrop-blur-xl bg-black/70 border-b border-white/5"
      >
        <div className="flex items-center justify-between px-4 py-4">
          <motion.button
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            onClick={onBack}
            className="p-2 rounded-full bg-white/5 hover:bg-white/10 transition-colors"
          >
            <ArrowLeft className="w-5 h-5 text-white" />
          </motion.button>
          
          <h2 className="text-white">Post</h2>
          
          <motion.button
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            className="p-2 rounded-full bg-white/5 hover:bg-white/10 transition-colors"
          >
            <MoreVertical className="w-5 h-5 text-white" />
          </motion.button>
        </div>
      </motion.header>

      {/* Main Content */}
      <div className="relative z-10 max-w-2xl mx-auto pb-32">
        {/* Post Card */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="relative"
        >
          {/* Post Header */}
          <div className="flex items-center justify-between p-4">
            <div className="flex items-center gap-3">
              <img
                src={post.avatar}
                alt={post.username}
                className="w-10 h-10 rounded-full object-cover border border-white/20"
              />
              <div>
                <p className="text-white text-sm">{post.username}</p>
                <p className="text-white/40 text-xs">{post.timestamp}</p>
              </div>
            </div>
          </div>

          {/* Main Image */}
          <div className="relative">
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ delay: 0.1 }}
              className="relative aspect-[4/5] overflow-hidden rounded-3xl mx-4"
              style={{
                boxShadow: '0 0 60px rgba(139, 92, 246, 0.2), 0 0 100px rgba(249, 115, 22, 0.15)',
              }}
            >
              <img
                src={post.image}
                alt="Post"
                className="w-full h-full object-cover"
              />
              
              {/* Vignette overlay */}
              <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent" />
              <div className="absolute inset-0 bg-gradient-to-b from-black/40 via-transparent to-transparent" />
            </motion.div>
          </div>

          {/* Actions */}
          <div className="px-4 py-4 space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-5">
                <motion.button
                  whileTap={{ scale: 0.9 }}
                  onClick={handleLike}
                  className="flex items-center gap-2 group"
                >
                  <motion.div
                    animate={liked ? { scale: [1, 1.4, 1] } : {}}
                    transition={{ duration: 0.4 }}
                  >
                    <Heart
                      className={`w-7 h-7 transition-colors ${
                        liked ? 'fill-orange-500 text-orange-500' : 'text-white/70 group-hover:text-orange-500'
                      }`}
                    />
                  </motion.div>
                  <span className="text-white/80">{likes.toLocaleString()}</span>
                </motion.button>

                <motion.button
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                  className="flex items-center gap-2 group"
                >
                  <MessageCircle className="w-7 h-7 text-white/70 group-hover:text-teal-400 transition-colors" />
                  <span className="text-white/80">{mockComments.length}</span>
                </motion.button>

                <motion.button
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                  className="group"
                >
                  <Share2 className="w-7 h-7 text-white/70 group-hover:text-purple-400 transition-colors" />
                </motion.button>
              </div>

              <motion.button
                whileTap={{ scale: 0.9 }}
                onClick={() => setSaved(!saved)}
                className="group"
              >
                <Bookmark
                  className={`w-7 h-7 transition-colors ${
                    saved ? 'fill-purple-500 text-purple-500' : 'text-white/70 group-hover:text-purple-500'
                  }`}
                />
              </motion.button>
            </div>

            {/* Caption */}
            {post.caption && (
              <div className="flex gap-2">
                <span className="text-white">{post.username}</span>
                <p className="text-white/80 flex-1">{post.caption}</p>
              </div>
            )}
          </div>
        </motion.div>

        {/* Comments Section */}
        <div className="px-4">
          <div className="border-t border-white/5 pt-4">
            <h3 className="text-white mb-4">
              Comments ({mockComments.length})
            </h3>
            
            <div className="divide-y divide-white/5">
              {mockComments.map((comment, index) => (
                <motion.div
                  key={comment.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.3 + index * 0.05 }}
                >
                  <CommentItem comment={comment} />
                </motion.div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Comment Input Bar */}
      <motion.div
        initial={{ opacity: 0, y: 50 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
        className="fixed bottom-0 left-0 right-0 z-50 backdrop-blur-xl bg-black/70 border-t border-white/10 px-4 py-4"
      >
        <div className="max-w-2xl mx-auto">
          <motion.div
            animate={{
              boxShadow: isFocused
                ? '0 0 40px rgba(139, 92, 246, 0.3), 0 0 80px rgba(249, 115, 22, 0.15)'
                : '0 0 0px rgba(139, 92, 246, 0)',
            }}
            className="flex items-center gap-3 px-4 py-3 rounded-full bg-white/5 border border-white/10"
          >
            <img
              src="https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=100&h=100&fit=crop"
              alt="Your avatar"
              className="w-8 h-8 rounded-full object-cover border border-white/20"
            />
            
            <input
              type="text"
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              onFocus={() => setIsFocused(true)}
              onBlur={() => setIsFocused(false)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleComment();
                }
              }}
              placeholder="Add a comment..."
              className="flex-1 bg-transparent text-white placeholder:text-white/40 outline-none text-sm"
            />

            <motion.button
              whileHover={{ scale: 1.1 }}
              whileTap={{ scale: 0.9 }}
              className="p-1.5 rounded-full bg-white/5 hover:bg-white/10 transition-colors"
            >
              <Smile className="w-5 h-5 text-white/40" />
            </motion.button>

            <motion.button
              whileHover={{ scale: 1.1 }}
              whileTap={{ scale: 0.9 }}
              onClick={handleComment}
              disabled={!commentText.trim()}
              className={`p-2 rounded-full transition-all ${
                commentText.trim()
                  ? 'bg-gradient-to-r from-purple-500 via-orange-500 to-teal-500'
                  : 'bg-white/5'
              }`}
              style={{
                boxShadow: commentText.trim()
                  ? '0 0 20px rgba(139, 92, 246, 0.4), 0 0 40px rgba(249, 115, 22, 0.3)'
                  : 'none',
              }}
            >
              <Send className={`w-4 h-4 ${commentText.trim() ? 'text-white' : 'text-white/40'}`} />
            </motion.button>
          </motion.div>
        </div>
      </motion.div>
    </div>
  );
}
