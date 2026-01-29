import { motion } from 'motion/react';
import { Plus } from 'lucide-react';

const stories = [
  {
    id: 'add',
    username: 'Your Story',
    avatar: null,
    hasStory: false,
  },
  {
    id: 1,
    username: 'alex.kim',
    avatar: 'https://images.unsplash.com/photo-1614283233556-f35b0c801ef1?w=100&h=100&fit=crop',
    hasStory: true,
  },
  {
    id: 2,
    username: 'maya.rose',
    avatar: 'https://images.unsplash.com/photo-1544124094-8aea0374da93?w=100&h=100&fit=crop',
    hasStory: true,
  },
  {
    id: 3,
    username: 'josh.wave',
    avatar: 'https://images.unsplash.com/photo-1626113666894-ac3923772213?w=100&h=100&fit=crop',
    hasStory: true,
  },
  {
    id: 4,
    username: 'stella.nx',
    avatar: 'https://images.unsplash.com/photo-1571513722275-4b41940f54b8?w=100&h=100&fit=crop',
    hasStory: true,
  },
  {
    id: 5,
    username: 'ryan.sky',
    avatar: 'https://images.unsplash.com/photo-1670324382035-f9cfacc3b59b?w=100&h=100&fit=crop',
    hasStory: true,
  },
];

export function StoryRow() {
  return (
    <div className="flex gap-4 overflow-x-auto pb-2 scrollbar-hide">
      {stories.map((story, index) => (
        <motion.div
          key={story.id}
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: index * 0.05 }}
          className="flex flex-col items-center gap-2 flex-shrink-0"
        >
          <motion.button
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            className="relative"
          >
            {story.hasStory ? (
              <>
                <div className="absolute inset-0 rounded-full bg-gradient-to-br from-purple-500 via-orange-500 to-teal-500 p-[2px] animate-pulse">
                  <div className="w-full h-full rounded-full bg-black" />
                </div>
                <div className="relative w-16 h-16 rounded-full overflow-hidden border-2 border-black">
                  <img
                    src={story.avatar || ''}
                    alt={story.username}
                    className="w-full h-full object-cover"
                  />
                </div>
              </>
            ) : (
              <div className="w-16 h-16 rounded-full bg-gradient-to-br from-white/10 to-white/5 border border-white/20 flex items-center justify-center">
                <Plus className="w-6 h-6 text-white" />
              </div>
            )}
          </motion.button>
          <span className="text-xs text-white/60 text-center w-16 truncate">
            {story.username}
          </span>
        </motion.div>
      ))}
    </div>
  );
}
