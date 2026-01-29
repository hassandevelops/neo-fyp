import { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { ArrowLeft, Heart, MessageCircle, UserPlus, Radio, Zap, Clock } from 'lucide-react';

interface NotificationsProps {
  onBack: () => void;
}

type NotificationType = 'like' | 'comment' | 'follow' | 'mention' | 'mesh' | 'system';

interface Notification {
  id: number;
  type: NotificationType;
  user?: {
    username: string;
    avatar: string;
    isVerified?: boolean;
  };
  message: string;
  timestamp: string;
  isRead: boolean;
  postImage?: string;
  actionText?: string;
}

const mockNotifications: Notification[] = [
  {
    id: 1,
    type: 'like',
    user: {
      username: 'neon.nights',
      avatar: 'https://images.unsplash.com/photo-1626113666894-ac3923772213?w=100&h=100&fit=crop',
      isVerified: true,
    },
    message: 'liked your post',
    timestamp: '5m ago',
    isRead: false,
    postImage: 'https://images.unsplash.com/photo-1544124094-8aea0374da93?w=100&h=100&fit=crop',
  },
  {
    id: 2,
    type: 'comment',
    user: {
      username: 'fashion.forward',
      avatar: 'https://images.unsplash.com/photo-1571513722275-4b41940f54b8?w=100&h=100&fit=crop',
      isVerified: true,
    },
    message: 'commented: "Amazing aesthetic! 🔥"',
    timestamp: '12m ago',
    isRead: false,
    postImage: 'https://images.unsplash.com/photo-1544124094-8aea0374da93?w=100&h=100&fit=crop',
  },
  {
    id: 3,
    type: 'follow',
    user: {
      username: 'cyber.aesthetic',
      avatar: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=100&h=100&fit=crop',
      isVerified: true,
    },
    message: 'started following you',
    timestamp: '28m ago',
    isRead: false,
  },
  {
    id: 4,
    type: 'mesh',
    message: 'Your device successfully synced with 3 nearby nodes',
    timestamp: '1h ago',
    isRead: true,
  },
  {
    id: 5,
    type: 'like',
    user: {
      username: 'urban.vibe',
      avatar: 'https://images.unsplash.com/photo-1670324382035-f9cfacc3b59b?w=100&h=100&fit=crop',
    },
    message: 'and 24 others liked your post',
    timestamp: '2h ago',
    isRead: true,
    postImage: 'https://images.unsplash.com/photo-1626113666894-ac3923772213?w=100&h=100&fit=crop',
  },
  {
    id: 6,
    type: 'mention',
    user: {
      username: 'luna.dreams',
      avatar: 'https://images.unsplash.com/photo-1544124094-8aea0374da93?w=100&h=100&fit=crop',
      isVerified: true,
    },
    message: 'mentioned you in a comment',
    timestamp: '3h ago',
    isRead: true,
  },
  {
    id: 7,
    type: 'system',
    message: 'Your post reached 10K views! 🎉',
    timestamp: '5h ago',
    isRead: true,
  },
  {
    id: 8,
    type: 'follow',
    user: {
      username: 'digital.dreams',
      avatar: 'https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=100&h=100&fit=crop',
    },
    message: 'started following you',
    timestamp: '8h ago',
    isRead: true,
  },
];

const getNotificationIcon = (type: NotificationType) => {
  switch (type) {
    case 'like':
      return { icon: Heart, color: 'from-pink-500 to-red-500', glow: 'rgba(236, 72, 153, 0.3)' };
    case 'comment':
      return { icon: MessageCircle, color: 'from-purple-500 to-blue-500', glow: 'rgba(168, 85, 247, 0.3)' };
    case 'follow':
      return { icon: UserPlus, color: 'from-lime-400 to-teal-500', glow: 'rgba(163, 230, 53, 0.3)' };
    case 'mention':
      return { icon: MessageCircle, color: 'from-orange-500 to-yellow-500', glow: 'rgba(251, 146, 60, 0.3)' };
    case 'mesh':
      return { icon: Radio, color: 'from-cyan-400 to-teal-500', glow: 'rgba(34, 211, 238, 0.3)' };
    case 'system':
      return { icon: Zap, color: 'from-yellow-400 to-orange-500', glow: 'rgba(250, 204, 21, 0.3)' };
  }
};

export function Notifications({ onBack }: NotificationsProps) {
  const [notifications, setNotifications] = useState(mockNotifications);
  const [filter, setFilter] = useState<'all' | 'unread'>('all');

  const filteredNotifications = filter === 'unread' 
    ? notifications.filter(n => !n.isRead)
    : notifications;

  const unreadCount = notifications.filter(n => !n.isRead).length;

  const markAsRead = (id: number) => {
    setNotifications(prev =>
      prev.map(notification =>
        notification.id === id ? { ...notification, isRead: true } : notification
      )
    );
  };

  const markAllAsRead = () => {
    setNotifications(prev =>
      prev.map(notification => ({ ...notification, isRead: true }))
    );
  };

  return (
    <div className="min-h-screen bg-black relative overflow-hidden">
      {/* Gradient background effects */}
      <div className="fixed inset-0 pointer-events-none">
        <div className="absolute top-0 left-1/4 w-96 h-96 bg-purple-600/20 rounded-full blur-[120px]" />
        <div className="absolute top-1/3 right-1/4 w-80 h-80 bg-pink-500/15 rounded-full blur-[100px]" />
        <div className="absolute bottom-1/4 left-1/3 w-72 h-72 bg-orange-500/15 rounded-full blur-[100px]" />
      </div>

      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="sticky top-0 z-50 backdrop-blur-xl bg-black/70 border-b border-white/5"
      >
        <div className="px-4 py-4">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-4">
              <motion.button
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                onClick={onBack}
                className="p-2 rounded-full bg-white/5 hover:bg-white/10 transition-colors"
              >
                <ArrowLeft className="w-5 h-5 text-white/70" />
              </motion.button>
              <div>
                <h1 className="text-white text-xl">Notifications</h1>
                {unreadCount > 0 && (
                  <p className="text-white/50 text-sm">{unreadCount} new</p>
                )}
              </div>
            </div>

            {unreadCount > 0 && (
              <motion.button
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                onClick={markAllAsRead}
                className="px-4 py-2 rounded-xl bg-gradient-to-r from-purple-500 to-pink-500 text-white text-sm"
                style={{ boxShadow: '0 0 20px rgba(168, 85, 247, 0.4)' }}
              >
                Mark all read
              </motion.button>
            )}
          </div>

          {/* Filter Tabs */}
          <div className="flex gap-2">
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={() => setFilter('all')}
              className={`flex-1 px-4 py-2 rounded-xl transition-all ${
                filter === 'all'
                  ? 'bg-gradient-to-r from-purple-500 to-pink-500 text-white shadow-lg'
                  : 'bg-white/5 text-white/60 hover:bg-white/10'
              }`}
              style={
                filter === 'all'
                  ? { boxShadow: '0 0 20px rgba(168, 85, 247, 0.5)' }
                  : {}
              }
            >
              All
            </motion.button>
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={() => setFilter('unread')}
              className={`flex-1 px-4 py-2 rounded-xl transition-all relative ${
                filter === 'unread'
                  ? 'bg-gradient-to-r from-purple-500 to-pink-500 text-white shadow-lg'
                  : 'bg-white/5 text-white/60 hover:bg-white/10'
              }`}
              style={
                filter === 'unread'
                  ? { boxShadow: '0 0 20px rgba(168, 85, 247, 0.5)' }
                  : {}
              }
            >
              Unread
              {unreadCount > 0 && (
                <span className="ml-2 px-2 py-0.5 rounded-full bg-orange-500 text-white text-xs">
                  {unreadCount}
                </span>
              )}
            </motion.button>
          </div>
        </div>
      </motion.div>

      {/* Notifications List */}
      <div className="relative z-10 max-w-2xl mx-auto px-4 py-6">
        <AnimatePresence mode="popLayout">
          {filteredNotifications.length === 0 ? (
            <motion.div
              key="empty"
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.9 }}
              className="text-center py-16"
            >
              <div className="w-20 h-20 mx-auto mb-4 rounded-2xl bg-white/5 flex items-center justify-center">
                <Clock className="w-10 h-10 text-white/30" />
              </div>
              <h3 className="text-white mb-2">No notifications</h3>
              <p className="text-white/50">You're all caught up!</p>
            </motion.div>
          ) : (
            <div className="space-y-3">
              {filteredNotifications.map((notification, index) => {
                const iconConfig = getNotificationIcon(notification.type);
                const Icon = iconConfig.icon;

                return (
                  <motion.div
                    key={notification.id}
                    layout
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: 20 }}
                    transition={{ delay: index * 0.03 }}
                    whileHover={{ x: 4, scale: 1.01 }}
                    onClick={() => markAsRead(notification.id)}
                    className={`relative p-4 rounded-2xl border transition-all cursor-pointer group ${
                      notification.isRead
                        ? 'bg-white/5 border-white/5 hover:bg-white/10'
                        : 'bg-gradient-to-r from-white/10 to-white/5 border-purple-500/30 hover:border-purple-500/50'
                    }`}
                    style={
                      !notification.isRead
                        ? { boxShadow: `0 0 20px ${iconConfig.glow}` }
                        : {}
                    }
                  >
                    <div className="flex items-start gap-3">
                      {/* Icon */}
                      <motion.div
                        whileHover={{ scale: 1.1, rotate: 5 }}
                        className={`flex-shrink-0 p-2 rounded-xl bg-gradient-to-br ${iconConfig.color}`}
                        style={{ boxShadow: `0 0 15px ${iconConfig.glow}` }}
                      >
                        <Icon className="w-5 h-5 text-white" />
                      </motion.div>

                      {/* Content */}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-start gap-2">
                          {notification.user && (
                            <div className="relative flex-shrink-0">
                              <img
                                src={notification.user.avatar}
                                alt={notification.user.username}
                                className="w-10 h-10 rounded-xl object-cover border-2 border-white/20"
                              />
                              {notification.user.isVerified && (
                                <div className="absolute -bottom-1 -right-1 w-4 h-4 bg-gradient-to-br from-lime-400 to-teal-500 rounded-full flex items-center justify-center border-2 border-black">
                                  <span className="text-black text-[8px]">✓</span>
                                </div>
                              )}
                            </div>
                          )}

                          <div className="flex-1 min-w-0">
                            <p className="text-white/90 text-sm">
                              {notification.user && (
                                <span className="text-white">
                                  {notification.user.username}{' '}
                                </span>
                              )}
                              {notification.message}
                            </p>
                            <div className="flex items-center gap-2 mt-1">
                              <span className="text-white/40 text-xs">
                                {notification.timestamp}
                              </span>
                              {!notification.isRead && (
                                <span className="w-1.5 h-1.5 bg-purple-500 rounded-full animate-pulse" />
                              )}
                            </div>
                          </div>

                          {/* Post Thumbnail */}
                          {notification.postImage && (
                            <motion.div
                              whileHover={{ scale: 1.1 }}
                              className="flex-shrink-0 w-12 h-12 rounded-lg overflow-hidden"
                            >
                              <img
                                src={notification.postImage}
                                alt="Post"
                                className="w-full h-full object-cover"
                              />
                            </motion.div>
                          )}
                        </div>

                        {/* Action Button */}
                        {notification.type === 'follow' && (
                          <motion.button
                            whileHover={{ scale: 1.05 }}
                            whileTap={{ scale: 0.95 }}
                            className="mt-3 px-4 py-2 rounded-xl bg-gradient-to-r from-lime-400 to-teal-500 text-black text-sm"
                            style={{ boxShadow: '0 0 15px rgba(163, 230, 53, 0.3)' }}
                          >
                            Follow Back
                          </motion.button>
                        )}
                      </div>
                    </div>
                  </motion.div>
                );
              })}
            </div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
