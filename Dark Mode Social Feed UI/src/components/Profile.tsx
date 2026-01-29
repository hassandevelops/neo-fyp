import { motion } from 'motion/react';
import { ArrowLeft, Edit2, MoreVertical, Grid, Bookmark, Tag } from 'lucide-react';
import { useState, useEffect } from 'react';

interface ProfileProps {
  onBack: () => void;
}

const userPosts = [
  { id: 1, image: 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxwb3J0cmFpdCUyMG1vZGVsfGVufDF8fHx8MTc2NjY0OTYxNXww&ixlib=rb-4.1.0&q=80&w=1080', likes: 2456 },
  { id: 2, image: 'https://images.unsplash.com/photo-1557053910-d9eadeed1c58?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxzdHVkaW8lMjBwb3J0cmFpdHxlbnwxfHx8fDE3NjY1NzYzODB8MA&ixlib=rb-4.1.0&q=80&w=1080', likes: 1823 },
  { id: 3, image: 'https://images.unsplash.com/photo-1617409122337-594499222247?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxsaWZlc3R5bGUlMjBwb3J0cmFpdHxlbnwxfHx8fDE3NjY2MDUxMzV8MA&ixlib=rb-4.1.0&q=80&w=1080', likes: 3102 },
  { id: 4, image: 'https://images.unsplash.com/photo-1716569355086-6caed45f6855?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxjcmVhdGl2ZSUyMHBvcnRyYWl0fGVufDF8fHx8MTc2NjY0OTA4NHww&ixlib=rb-4.1.0&q=80&w=1080', likes: 2789 },
  { id: 5, image: 'https://images.unsplash.com/photo-1536924430914-91f9e2041b83?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxhcnRpc3RpYyUyMHBvcnRyYWl0fGVufDF8fHx8MTc2NjU3NTA1MXww&ixlib=rb-4.1.0&q=80&w=1080', likes: 1956 },
  { id: 6, image: 'https://images.unsplash.com/photo-1560250097-0b93528c311a?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxwcm9mZXNzaW9uYWwlMjBwb3J0cmFpdHxlbnwxfHx8fDE3NjY2MjgyNDh8MA&ixlib=rb-4.1.0&q=80&w=1080', likes: 2234 },
];

function AnimatedCounter({ value, delay = 0 }: { value: number; delay?: number }) {
  const [count, setCount] = useState(0);

  useEffect(() => {
    const duration = 1500;
    const steps = 60;
    const increment = value / steps;
    let current = 0;

    const timer = setTimeout(() => {
      const counter = setInterval(() => {
        current += increment;
        if (current >= value) {
          setCount(value);
          clearInterval(counter);
        } else {
          setCount(Math.floor(current));
        }
      }, duration / steps);

      return () => clearInterval(counter);
    }, delay);

    return () => clearTimeout(timer);
  }, [value, delay]);

  return <span>{count.toLocaleString()}</span>;
}

export function Profile({ onBack }: ProfileProps) {
  const [activeTab, setActiveTab] = useState<'posts' | 'saved' | 'tagged'>('posts');

  return (
    <div className="min-h-screen bg-black relative overflow-hidden">
      {/* Background gradient effects */}
      <div className="fixed inset-0 pointer-events-none">
        <div className="absolute top-0 right-1/4 w-96 h-96 bg-lime-400/10 rounded-full blur-[120px]" />
        <div className="absolute top-1/4 left-1/4 w-80 h-80 bg-yellow-400/10 rounded-full blur-[100px]" />
        <div className="absolute bottom-1/3 right-1/3 w-72 h-72 bg-purple-500/10 rounded-full blur-[100px]" />
      </div>

      {/* Header */}
      <motion.header
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="sticky top-0 z-50 backdrop-blur-xl bg-black/50 border-b border-white/5"
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
          
          <h2 className="text-white">Profile</h2>
          
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
      <div className="relative z-10 max-w-2xl mx-auto px-4 pb-24">
        {/* Profile Card */}
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="mt-6 relative"
        >
          <div
            className="relative rounded-3xl overflow-hidden p-6 border border-lime-400/20"
            style={{
              background: 'linear-gradient(135deg, rgba(163, 230, 53, 0.15) 0%, rgba(250, 204, 21, 0.1) 100%)',
              boxShadow: '0 0 60px rgba(163, 230, 53, 0.2), 0 0 100px rgba(250, 204, 21, 0.15)',
            }}
          >
            {/* Glow effect */}
            <motion.div
              animate={{
                opacity: [0.5, 0.8, 0.5],
              }}
              transition={{ repeat: Infinity, duration: 3 }}
              className="absolute inset-0 bg-gradient-to-br from-lime-400/10 via-transparent to-yellow-400/10"
            />

            <div className="relative flex flex-col items-center">
              {/* Avatar with glow */}
              <motion.div
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ type: 'spring', delay: 0.2 }}
                className="relative mb-4"
              >
                <div
                  className="absolute inset-0 rounded-full blur-2xl"
                  style={{
                    background: 'linear-gradient(135deg, rgba(163, 230, 53, 0.4), rgba(250, 204, 21, 0.4))',
                  }}
                />
                <div className="relative w-24 h-24 rounded-full border-4 border-lime-400/30 overflow-hidden">
                  <img
                    src="https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=200&h=200&fit=crop"
                    alt="Profile"
                    className="w-full h-full object-cover"
                  />
                </div>
              </motion.div>

              {/* User Info */}
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.3 }}
                className="text-center mb-4"
              >
                <h1 className="text-white text-2xl mb-1">Luna Dreams</h1>
                <p className="text-lime-400 text-sm">@luna.dreams</p>
                <p className="text-white/60 text-sm mt-3 max-w-sm">
                  Creative soul ✦ Digital artist ✦ Capturing moments in neon lights
                </p>
              </motion.div>
            </div>
          </div>
        </motion.div>

        {/* Stats */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
          className="flex gap-3 mt-6 justify-center"
        >
          {[
            { label: 'Posts', value: 142 },
            { label: 'Followers', value: 12400 },
            { label: 'Likes', value: 48200 },
          ].map((stat, index) => (
            <motion.div
              key={stat.label}
              whileHover={{ scale: 1.05, y: -2 }}
              className="flex-1 px-4 py-3 rounded-full bg-white/5 border border-white/10 backdrop-blur-sm"
              style={{
                boxShadow: '0 0 20px rgba(163, 230, 53, 0.1)',
              }}
            >
              <div className="text-center">
                <div className="text-white text-xl">
                  <AnimatedCounter value={stat.value} delay={index * 100} />
                </div>
                <div className="text-white/50 text-xs mt-0.5">{stat.label}</div>
              </div>
            </motion.div>
          ))}
        </motion.div>

        {/* Edit Profile Button */}
        <motion.button
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.5 }}
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          className="w-full mt-6 px-6 py-3 rounded-full bg-white/5 border-2 border-lime-400/50 hover:border-lime-400 hover:bg-lime-400/10 text-white transition-all flex items-center justify-center gap-2"
          style={{
            boxShadow: '0 0 20px rgba(163, 230, 53, 0.2)',
          }}
        >
          <Edit2 className="w-4 h-4" />
          <span>Edit Profile</span>
        </motion.button>

        {/* Tabs */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.6 }}
          className="flex gap-1 mt-8 border-b border-white/10"
        >
          {[
            { id: 'posts' as const, icon: Grid, label: 'Posts' },
            { id: 'saved' as const, icon: Bookmark, label: 'Saved' },
            { id: 'tagged' as const, icon: Tag, label: 'Tagged' },
          ].map((tab) => (
            <motion.button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              whileHover={{ y: -2 }}
              className={`flex-1 flex items-center justify-center gap-2 py-3 border-b-2 transition-all ${
                activeTab === tab.id
                  ? 'border-lime-400 text-lime-400'
                  : 'border-transparent text-white/40 hover:text-white/60'
              }`}
            >
              <tab.icon className="w-4 h-4" />
              <span className="text-sm">{tab.label}</span>
            </motion.button>
          ))}
        </motion.div>

        {/* Content Grid */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.7 }}
          className="grid grid-cols-3 gap-2 mt-4"
        >
          {userPosts.map((post, index) => (
            <motion.div
              key={post.id}
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: 0.7 + index * 0.05 }}
              whileHover={{ scale: 1.05, y: -4 }}
              className="relative aspect-square rounded-2xl overflow-hidden cursor-pointer group"
              style={{
                boxShadow: '0 0 20px rgba(163, 230, 53, 0.1)',
              }}
            >
              <img
                src={post.image}
                alt={`Post ${post.id}`}
                className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-110"
              />
              
              {/* Overlay on hover */}
              <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/40 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex items-end justify-start p-3">
                <div className="flex items-center gap-1 text-white text-sm">
                  <svg
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    fill="currentColor"
                    className="text-lime-400"
                  >
                    <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
                  </svg>
                  <span>{post.likes.toLocaleString()}</span>
                </div>
              </div>

              {/* Border glow */}
              <div className="absolute inset-0 border border-lime-400/0 group-hover:border-lime-400/50 rounded-2xl transition-all duration-300" />
            </motion.div>
          ))}
        </motion.div>
      </div>
    </div>
  );
}
