import { Search, Bell, Settings as SettingsIcon } from 'lucide-react';
import { motion } from 'motion/react';

export function Header({ onProfileClick, onSettingsClick, onSearchClick, onNotificationsClick, notificationCount = 0 }: { 
  onProfileClick?: () => void; 
  onSettingsClick?: () => void;
  onSearchClick?: () => void;
  onNotificationsClick?: () => void;
  notificationCount?: number;
}) {
  return (
    <motion.header
      initial={{ opacity: 0, y: -20 }}
      animate={{ opacity: 1, y: 0 }}
      className="sticky top-0 z-50 backdrop-blur-xl bg-black/50 border-b border-white/5"
    >
      <div className="flex items-center justify-between px-4 py-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-purple-500 via-orange-500 to-teal-500 flex items-center justify-center">
            <span className="text-white text-xl">✦</span>
          </div>
          <h1 className="text-white tracking-wider">NEXUS</h1>
        </div>

        <div className="flex items-center gap-4">
          {onSearchClick && (
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={onSearchClick}
              className="p-2 rounded-full bg-white/5 hover:bg-white/10 transition-colors"
            >
              <Search className="w-5 h-5 text-white/70" />
            </motion.button>
          )}
          
          {onNotificationsClick && (
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={onNotificationsClick}
              className="relative p-2 rounded-full bg-white/5 hover:bg-white/10 transition-colors"
            >
              <Bell className="w-5 h-5 text-white/70" />
              {notificationCount > 0 && (
                <>
                  <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-orange-500 rounded-full animate-pulse" />
                  <motion.span
                    initial={{ scale: 0 }}
                    animate={{ scale: 1 }}
                    className="absolute -top-1 -right-1 w-5 h-5 bg-gradient-to-br from-orange-500 to-red-500 rounded-full flex items-center justify-center text-white text-[10px]"
                    style={{ boxShadow: '0 0 10px rgba(251, 146, 60, 0.6)' }}
                  >
                    {notificationCount > 9 ? '9+' : notificationCount}
                  </motion.span>
                </>
              )}
            </motion.button>
          )}

          {onSettingsClick && (
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={onSettingsClick}
              className="p-2 rounded-full bg-white/5 hover:bg-white/10 transition-colors"
            >
              <SettingsIcon className="w-5 h-5 text-white/70" />
            </motion.button>
          )}

          {onProfileClick && (
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={onProfileClick}
              className="w-8 h-8 rounded-full overflow-hidden border-2 border-lime-400/50 hover:border-lime-400 transition-colors"
            >
              <img
                src="https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=100&h=100&fit=crop"
                alt="Profile"
                className="w-full h-full object-cover"
              />
            </motion.button>
          )}
        </div>
      </div>
    </motion.header>
  );
}