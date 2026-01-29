import { Heart, MessageCircle, Share2, Bookmark } from 'lucide-react';
import { motion } from 'motion/react';
import { useState } from 'react';

interface Post {
  id: number;
  username: string;
  avatar: string;
  timestamp: string;
  image: string;
  likes: number;
  comments: number;
  isLive?: boolean;
}

export function PostCard({ post, onClick }: { post: Post; onClick?: () => void }) {
  const [liked, setLiked] = useState(false);
  const [saved, setSaved] = useState(false);
  const [likes, setLikes] = useState(post.likes);

  const handleLike = () => {
    setLiked(!liked);
    setLikes(liked ? likes - 1 : likes + 1);
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      whileHover={{ y: -4 }}
      className="relative rounded-3xl overflow-hidden bg-gradient-to-b from-white/5 to-white/[0.02] border border-white/10 backdrop-blur-sm shadow-2xl"
      style={{
        boxShadow: '0 0 40px rgba(139, 92, 246, 0.15), 0 0 80px rgba(249, 115, 22, 0.1)',
      }}
    >
      {/* Header */}
      <div className="flex items-center justify-between p-4">
        <div className="flex items-center gap-3">
          <div className="relative">
            <img
              src={post.avatar}
              alt={post.username}
              className="w-10 h-10 rounded-full object-cover border border-white/20"
            />
            {post.isLive && (
              <motion.span
                animate={{ scale: [1, 1.2, 1] }}
                transition={{ repeat: Infinity, duration: 2 }}
                className="absolute -bottom-0.5 -right-0.5 px-1.5 py-0.5 bg-gradient-to-r from-orange-500 to-orange-600 rounded text-[9px] text-white shadow-lg"
              >
                LIVE
              </motion.span>
            )}
          </div>
          <div>
            <p className="text-white text-sm">{post.username}</p>
            <p className="text-white/40 text-xs">{post.timestamp}</p>
          </div>
        </div>
        
        <button className="text-white/60 hover:text-white transition-colors">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor">
            <circle cx="10" cy="4" r="1.5" />
            <circle cx="10" cy="10" r="1.5" />
            <circle cx="10" cy="16" r="1.5" />
          </svg>
        </button>
      </div>

      {/* Image */}
      <div className="relative aspect-[3/4] overflow-hidden cursor-pointer" onClick={onClick}>
        <img
          src={post.image}
          alt="Post"
          className="w-full h-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent" />
      </div>

      {/* Actions */}
      <div className="p-4 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <motion.button
              whileTap={{ scale: 0.9 }}
              onClick={handleLike}
              className="flex items-center gap-2 group"
            >
              <motion.div
                animate={liked ? { scale: [1, 1.3, 1] } : {}}
                transition={{ duration: 0.3 }}
              >
                <Heart
                  className={`w-6 h-6 transition-colors ${
                    liked ? 'fill-orange-500 text-orange-500' : 'text-white/70 group-hover:text-orange-500'
                  }`}
                />
              </motion.div>
              <span className="text-white/80 text-sm">{likes.toLocaleString()}</span>
            </motion.button>

            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={onClick}
              className="flex items-center gap-2 group"
            >
              <MessageCircle className="w-6 h-6 text-white/70 group-hover:text-teal-400 transition-colors" />
              <span className="text-white/80 text-sm">{post.comments}</span>
            </motion.button>

            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              className="flex items-center gap-2 group"
            >
              <Share2 className="w-6 h-6 text-white/70 group-hover:text-purple-400 transition-colors" />
            </motion.button>
          </div>

          <motion.button
            whileTap={{ scale: 0.9 }}
            onClick={() => setSaved(!saved)}
            className="group"
          >
            <Bookmark
              className={`w-6 h-6 transition-colors ${
                saved ? 'fill-purple-500 text-purple-500' : 'text-white/70 group-hover:text-purple-500'
              }`}
            />
          </motion.button>
        </div>
      </div>
    </motion.div>
  );
}