import { StoryRow } from './components/StoryRow';
import { PostCard } from './components/PostCard';
import { FloatingActionButton } from './components/FloatingActionButton';
import { Header } from './components/Header';
import { PostCreation } from './components/PostCreation';
import { Profile } from './components/Profile';
import { PostDetail } from './components/PostDetail';
import { Settings } from './components/Settings';
import { BLEMeshStatus } from './components/BLEMeshStatus';
import { Search } from './components/Search';
import { Notifications } from './components/Notifications';
import { useState } from 'react';
import { motion } from 'motion/react';
import { Radio, ChevronRight } from 'lucide-react';

const posts = [
  {
    id: 1,
    username: 'luna.dreams',
    avatar: 'https://images.unsplash.com/photo-1544124094-8aea0374da93?w=100&h=100&fit=crop',
    timestamp: '2h ago',
    image: 'https://images.unsplash.com/photo-1544124094-8aea0374da93?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxwb3J0cmFpdCUyMHBob3RvZ3JhcGh5fGVufDF8fHx8MTc2NjQ5NjU0NHww&ixlib=rb-4.1.0&q=80&w=1080&utm_source=figma&utm_medium=referral',
    likes: 1234,
    comments: 89,
    isLive: true,
    caption: 'Living my best life in neon lights ✨ The city never sleeps and neither do I',
  },
  {
    id: 2,
    username: 'neon.nights',
    avatar: 'https://images.unsplash.com/photo-1626113666894-ac3923772213?w=100&h=100&fit=crop',
    timestamp: '4h ago',
    image: 'https://images.unsplash.com/photo-1626113666894-ac3923772213?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHx1cmJhbiUyMG5pZ2h0JTIwcG9ydHJhaXR8ZW58MXx8fHwxNzY2NTY2MDc0fDA&ixlib=rb-4.1.0&q=80&w=1080&utm_source=figma&utm_medium=referral',
    likes: 2567,
    comments: 134,
    isLive: false,
    caption: 'Urban dreams and midnight scenes 🌃',
  },
  {
    id: 3,
    username: 'fashion.forward',
    avatar: 'https://images.unsplash.com/photo-1571513722275-4b41940f54b8?w=100&h=100&fit=crop',
    timestamp: '6h ago',
    image: 'https://images.unsplash.com/photo-1571513722275-4b41940f54b8?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxmYXNoaW9uJTIwcG9ydHJhaXR8ZW58MXx8fHwxNzY2NDgxMTk1fDA&ixlib=rb-4.1.0&q=80&w=1080&utm_source=figma&utm_medium=referral',
    likes: 3421,
    comments: 201,
    isLive: false,
    caption: 'Fashion is art and I am the canvas 🎨',
  },
  {
    id: 4,
    username: 'urban.vibe',
    avatar: 'https://images.unsplash.com/photo-1670324382035-f9cfacc3b59b?w=100&h=100&fit=crop',
    timestamp: '8h ago',
    image: 'https://images.unsplash.com/photo-1670324382035-f9cfacc3b59b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxjaW5lbWF0aWMlMjBwb3J0cmFpdHxlbnwxfHx8fDE3NjY1NjYwNzR8MA&ixlib=rb-4.1.0&q=80&w=1080&utm_source=figma&utm_medium=referral',
    likes: 1876,
    comments: 92,
    isLive: false,
    caption: 'Cinematic moments in everyday life 🎬',
  },
];

export default function App() {
  const [isPostCreationOpen, setIsPostCreationOpen] = useState(false);
  const [showProfile, setShowProfile] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [showBLEMesh, setShowBLEMesh] = useState(false);
  const [showSearch, setShowSearch] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  const [selectedPost, setSelectedPost] = useState<typeof posts[0] | null>(null);
  const [notificationCount] = useState(3); // Unread notifications count

  if (selectedPost) {
    return <PostDetail post={selectedPost} onBack={() => setSelectedPost(null)} />;
  }

  if (showProfile) {
    return <Profile onBack={() => setShowProfile(false)} />;
  }

  if (showSettings) {
    return <Settings onBack={() => setShowSettings(false)} />;
  }

  if (showBLEMesh) {
    return <BLEMeshStatus onBack={() => setShowBLEMesh(false)} />;
  }

  if (showSearch) {
    return <Search onBack={() => setShowSearch(false)} />;
  }

  if (showNotifications) {
    return <Notifications onBack={() => setShowNotifications(false)} />;
  }

  return (
    <div className="min-h-screen bg-black relative overflow-hidden">
      {/* Gradient background effects */}
      <div className="fixed inset-0 pointer-events-none">
        <div className="absolute top-0 left-1/4 w-96 h-96 bg-purple-600/20 rounded-full blur-[120px]" />
        <div className="absolute top-1/3 right-1/4 w-80 h-80 bg-orange-500/15 rounded-full blur-[100px]" />
        <div className="absolute bottom-1/4 left-1/3 w-72 h-72 bg-teal-500/15 rounded-full blur-[100px]" />
      </div>

      {/* Main content */}
      <div className="relative z-10 max-w-2xl mx-auto">
        <Header 
          onProfileClick={() => setShowProfile(true)}
          onSettingsClick={() => setShowSettings(true)}
          onSearchClick={() => setShowSearch(true)}
          onNotificationsClick={() => setShowNotifications(true)}
          notificationCount={notificationCount}
        />
        
        <div className="px-4 pb-24">
          <StoryRow />
          
          {/* BLE Mesh Demo Card */}
          <motion.button
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            whileHover={{ scale: 1.02, y: -4 }}
            whileTap={{ scale: 0.98 }}
            onClick={() => setShowBLEMesh(true)}
            className="w-full mt-6 p-6 rounded-3xl bg-gradient-to-br from-cyan-500/10 via-purple-500/10 to-pink-500/10 border border-cyan-400/30 backdrop-blur-sm"
            style={{
              boxShadow: '0 0 40px rgba(6, 182, 212, 0.2), 0 0 80px rgba(139, 92, 246, 0.15)',
            }}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-4">
                <div className="p-3 rounded-xl bg-cyan-400/20 border border-cyan-400/30">
                  <Radio className="w-6 h-6 text-cyan-400" />
                </div>
                <div className="text-left">
                  <h3 className="text-white mb-1">BLE Mesh Network</h3>
                  <p className="text-white/60 text-sm">View sync status & topology</p>
                </div>
              </div>
              <ChevronRight className="w-5 h-5 text-cyan-400" />
            </div>
          </motion.button>
          
          <div className="space-y-6 mt-6">
            {posts.map((post) => (
              <PostCard key={post.id} post={post} onClick={() => setSelectedPost(post)} />
            ))}
          </div>
        </div>
      </div>

      <FloatingActionButton onClick={() => setIsPostCreationOpen(true)} />
      <PostCreation isOpen={isPostCreationOpen} onClose={() => setIsPostCreationOpen(false)} />
    </div>
  );
}