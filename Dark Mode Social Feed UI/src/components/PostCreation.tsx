import { X, Image, Video, Smile, AtSign, Hash, MapPin } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { useState, useRef } from 'react';

interface PostCreationProps {
  isOpen: boolean;
  onClose: () => void;
}

export function PostCreation({ isOpen, onClose }: PostCreationProps) {
  const [text, setText] = useState('');
  const [isFocused, setIsFocused] = useState(false);
  const [selectedMedia, setSelectedMedia] = useState<string[]>([]);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const maxChars = 280;
  const charsRemaining = maxChars - text.length;
  const isOverLimit = charsRemaining < 0;

  const handleMediaSelect = (type: 'image' | 'video') => {
    // Mock media selection
    const mockUrl = type === 'image' 
      ? 'https://images.unsplash.com/photo-1544124094-8aea0374da93?w=400&h=400&fit=crop'
      : 'https://images.unsplash.com/photo-1626113666894-ac3923772213?w=400&h=400&fit=crop';
    setSelectedMedia([...selectedMedia, mockUrl]);
  };

  const removeMedia = (index: number) => {
    setSelectedMedia(selectedMedia.filter((_, i) => i !== index));
  };

  const handlePost = () => {
    if (text.trim() && !isOverLimit) {
      // Handle post submission
      console.log('Posting:', { text, media: selectedMedia });
      onClose();
      setText('');
      setSelectedMedia([]);
    }
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
        >
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="absolute inset-0 bg-black/80 backdrop-blur-xl"
          />

          {/* Modal Content */}
          <motion.div
            initial={{ scale: 0.9, opacity: 0, y: 20 }}
            animate={{ scale: 1, opacity: 1, y: 0 }}
            exit={{ scale: 0.9, opacity: 0, y: 20 }}
            transition={{ type: 'spring', damping: 25, stiffness: 300 }}
            className="relative w-full max-w-2xl bg-black border border-white/10 rounded-3xl shadow-2xl overflow-hidden"
            style={{
              background: 'radial-gradient(circle at top right, rgba(139, 92, 246, 0.05), rgba(0, 0, 0, 0.95) 50%, rgba(249, 115, 22, 0.03))',
            }}
          >
            {/* Noise texture overlay */}
            <div 
              className="absolute inset-0 opacity-[0.02] pointer-events-none"
              style={{
                backgroundImage: 'url("data:image/svg+xml,%3Csvg viewBox=\'0 0 400 400\' xmlns=\'http://www.w3.org/2000/svg\'%3E%3Cfilter id=\'noiseFilter\'%3E%3CfeTurbulence type=\'fractalNoise\' baseFrequency=\'0.9\' numOctaves=\'4\' /%3E%3C/filter%3E%3Crect width=\'100%25\' height=\'100%25\' filter=\'url(%23noiseFilter)\' /%3E%3C/svg%3E")',
              }}
            />

            {/* Header */}
            <div className="relative flex items-center justify-between p-6 border-b border-white/5">
              <h2 className="text-white">Create Post</h2>
              <motion.button
                whileHover={{ scale: 1.1, rotate: 90 }}
                whileTap={{ scale: 0.9 }}
                onClick={onClose}
                className="p-2 rounded-full bg-white/5 hover:bg-white/10 transition-colors"
              >
                <X className="w-5 h-5 text-white/70" />
              </motion.button>
            </div>

            {/* Content */}
            <div className="relative p-6 space-y-6">
              {/* Text Input */}
              <div className="relative">
                <motion.div
                  animate={{
                    boxShadow: isFocused
                      ? '0 0 40px rgba(139, 92, 246, 0.3), 0 0 80px rgba(249, 115, 22, 0.15)'
                      : '0 0 0px rgba(139, 92, 246, 0)',
                  }}
                  className="rounded-2xl"
                >
                  <textarea
                    ref={textareaRef}
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    onFocus={() => setIsFocused(true)}
                    onBlur={() => setIsFocused(false)}
                    placeholder="What's on your mind?"
                    className="w-full min-h-[200px] px-6 py-5 bg-white/5 border border-white/10 rounded-2xl text-white placeholder:text-white/30 resize-none focus:outline-none focus:border-purple-500/50 transition-all"
                    style={{
                      background: 'linear-gradient(135deg, rgba(255,255,255,0.03) 0%, rgba(255,255,255,0.01) 100%)',
                    }}
                  />
                </motion.div>

                {/* Animated placeholder when empty */}
                {!text && !isFocused && (
                  <motion.div
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    className="absolute top-5 left-6 pointer-events-none"
                  >
                    <motion.span
                      animate={{
                        opacity: [0.3, 0.5, 0.3],
                      }}
                      transition={{
                        duration: 3,
                        repeat: Infinity,
                        ease: 'easeInOut',
                      }}
                      className="text-white/30"
                    >
                      Share your thoughts...
                    </motion.span>
                  </motion.div>
                )}

                {/* Character Counter */}
                <motion.div
                  className="flex items-center justify-between mt-3 px-2"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                >
                  <div className="flex items-center gap-2">
                    {/* Progress ring */}
                    <svg width="32" height="32" className="-rotate-90">
                      <circle
                        cx="16"
                        cy="16"
                        r="14"
                        fill="none"
                        stroke="rgba(255,255,255,0.1)"
                        strokeWidth="2"
                      />
                      <motion.circle
                        cx="16"
                        cy="16"
                        r="14"
                        fill="none"
                        stroke={isOverLimit ? '#ff4444' : '#4ade80'}
                        strokeWidth="2"
                        strokeDasharray={`${2 * Math.PI * 14}`}
                        initial={{ strokeDashoffset: 2 * Math.PI * 14 }}
                        animate={{
                          strokeDashoffset: 2 * Math.PI * 14 * (1 - text.length / maxChars),
                        }}
                        style={{
                          filter: 'drop-shadow(0 0 8px rgba(74, 222, 128, 0.5))',
                        }}
                      />
                    </svg>
                    <span className={`text-sm ${isOverLimit ? 'text-red-400' : 'text-green-400'}`}>
                      {charsRemaining}
                    </span>
                  </div>

                  <span className="text-xs text-white/40">
                    {text.length} / {maxChars}
                  </span>
                </motion.div>
              </div>

              {/* Media Preview */}
              {selectedMedia.length > 0 && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  className="grid grid-cols-3 gap-3"
                >
                  {selectedMedia.map((media, index) => (
                    <motion.div
                      key={index}
                      initial={{ scale: 0 }}
                      animate={{ scale: 1 }}
                      exit={{ scale: 0 }}
                      className="relative aspect-square rounded-xl overflow-hidden border border-white/10"
                    >
                      <img src={media} alt="" className="w-full h-full object-cover" />
                      <motion.button
                        whileHover={{ scale: 1.1 }}
                        whileTap={{ scale: 0.9 }}
                        onClick={() => removeMedia(index)}
                        className="absolute top-2 right-2 p-1.5 rounded-full bg-black/70 backdrop-blur-sm"
                      >
                        <X className="w-3 h-3 text-white" />
                      </motion.button>
                    </motion.div>
                  ))}
                </motion.div>
              )}

              {/* Media Upload & Actions */}
              <div className="flex items-center gap-3 flex-wrap">
                <motion.button
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                  onClick={() => handleMediaSelect('image')}
                  className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/5 border border-purple-500/30 hover:border-purple-500/60 transition-all group"
                  style={{
                    boxShadow: '0 0 20px rgba(139, 92, 246, 0.1)',
                  }}
                >
                  <Image className="w-4 h-4 text-purple-400 group-hover:text-purple-300 transition-colors" />
                  <span className="text-sm text-white/70 group-hover:text-white transition-colors">
                    Image
                  </span>
                </motion.button>

                <motion.button
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                  onClick={() => handleMediaSelect('video')}
                  className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/5 border border-orange-500/30 hover:border-orange-500/60 transition-all group"
                  style={{
                    boxShadow: '0 0 20px rgba(249, 115, 22, 0.1)',
                  }}
                >
                  <Video className="w-4 h-4 text-orange-400 group-hover:text-orange-300 transition-colors" />
                  <span className="text-sm text-white/70 group-hover:text-white transition-colors">
                    Video
                  </span>
                </motion.button>

                <motion.button
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                  className="p-2.5 rounded-xl bg-white/5 border border-white/10 hover:border-teal-500/60 transition-all group"
                >
                  <Smile className="w-4 h-4 text-white/40 group-hover:text-teal-400 transition-colors" />
                </motion.button>

                <motion.button
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                  className="p-2.5 rounded-xl bg-white/5 border border-white/10 hover:border-teal-500/60 transition-all group"
                >
                  <AtSign className="w-4 h-4 text-white/40 group-hover:text-teal-400 transition-colors" />
                </motion.button>

                <motion.button
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                  className="p-2.5 rounded-xl bg-white/5 border border-white/10 hover:border-teal-500/60 transition-all group"
                >
                  <Hash className="w-4 h-4 text-white/40 group-hover:text-teal-400 transition-colors" />
                </motion.button>

                <motion.button
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                  className="p-2.5 rounded-xl bg-white/5 border border-white/10 hover:border-teal-500/60 transition-all group"
                >
                  <MapPin className="w-4 h-4 text-white/40 group-hover:text-teal-400 transition-colors" />
                </motion.button>
              </div>
            </div>

            {/* Footer with Post Button */}
            <div className="relative p-6 pt-0">
              <motion.button
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.98 }}
                onClick={handlePost}
                disabled={!text.trim() || isOverLimit}
                className="w-full py-4 rounded-2xl bg-gradient-to-r from-purple-500 via-orange-500 to-teal-500 text-white disabled:opacity-40 disabled:cursor-not-allowed transition-all"
                style={{
                  boxShadow: text.trim() && !isOverLimit
                    ? '0 0 40px rgba(139, 92, 246, 0.4), 0 0 80px rgba(249, 115, 22, 0.3)'
                    : 'none',
                }}
              >
                <motion.span
                  animate={text.trim() && !isOverLimit ? {
                    textShadow: [
                      '0 0 10px rgba(255,255,255,0.5)',
                      '0 0 20px rgba(255,255,255,0.8)',
                      '0 0 10px rgba(255,255,255,0.5)',
                    ],
                  } : {}}
                  transition={{ repeat: Infinity, duration: 2 }}
                >
                  Post
                </motion.span>
              </motion.button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
