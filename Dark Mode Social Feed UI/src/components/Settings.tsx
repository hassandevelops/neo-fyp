import { motion } from 'motion/react';
import { ArrowLeft, Shield, AlertTriangle, Bell, Lock, Eye, Fingerprint, User, LogOut, ChevronRight } from 'lucide-react';
import { useState } from 'react';

interface SettingsProps {
  onBack: () => void;
}

interface ToggleProps {
  enabled: boolean;
  onChange: (enabled: boolean) => void;
  variant?: 'default' | 'danger';
}

function Toggle({ enabled, onChange, variant = 'default' }: ToggleProps) {
  const isDanger = variant === 'danger';
  
  return (
    <motion.button
      onClick={() => onChange(!enabled)}
      className={`relative w-12 h-6 rounded-full transition-all ${
        enabled
          ? isDanger
            ? 'bg-red-500/20'
            : 'bg-gradient-to-r from-purple-500/20 via-orange-500/20 to-teal-500/20'
          : 'bg-white/10'
      }`}
      style={{
        boxShadow: enabled
          ? isDanger
            ? '0 0 20px rgba(239, 68, 68, 0.4), 0 0 40px rgba(239, 68, 68, 0.2)'
            : '0 0 20px rgba(139, 92, 246, 0.3)'
          : 'none',
      }}
    >
      <motion.div
        animate={{
          x: enabled ? 24 : 2,
        }}
        transition={{ type: 'spring', stiffness: 500, damping: 30 }}
        className={`absolute top-1 w-4 h-4 rounded-full ${
          enabled
            ? isDanger
              ? 'bg-red-500'
              : 'bg-gradient-to-r from-purple-500 via-orange-500 to-teal-500'
            : 'bg-white/40'
        }`}
        style={{
          boxShadow: enabled
            ? isDanger
              ? '0 0 10px rgba(239, 68, 68, 0.6)'
              : '0 0 10px rgba(139, 92, 246, 0.6)'
            : 'none',
        }}
      />
    </motion.button>
  );
}

export function Settings({ onBack }: SettingsProps) {
  const [panicMode, setPanicMode] = useState(false);
  const [privateAccount, setPrivateAccount] = useState(false);
  const [hideActivity, setHideActivity] = useState(true);
  const [twoFactorAuth, setTwoFactorAuth] = useState(true);
  const [pushNotifications, setPushNotifications] = useState(true);
  const [emailNotifications, setEmailNotifications] = useState(false);

  return (
    <div className="min-h-screen bg-black relative overflow-hidden">
      {/* Background gradient effects */}
      <div className="fixed inset-0 pointer-events-none">
        <div className="absolute top-0 right-1/4 w-96 h-96 bg-purple-600/10 rounded-full blur-[120px]" />
        <div className="absolute bottom-1/3 left-1/4 w-80 h-80 bg-orange-500/10 rounded-full blur-[100px]" />
        <div className="absolute top-1/2 right-1/3 w-72 h-72 bg-teal-500/10 rounded-full blur-[100px]" />
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
          
          <h2 className="text-white">Settings & Privacy</h2>
          
          <div className="w-9" /> {/* Spacer for centering */}
        </div>
      </motion.header>

      {/* Main Content */}
      <div className="relative z-10 max-w-2xl mx-auto px-4 py-6 pb-24 space-y-6">
        {/* Panic Mode Section */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="relative rounded-2xl overflow-hidden bg-gradient-to-br from-red-500/10 to-red-900/5 border border-red-500/30 p-5"
          style={{
            boxShadow: panicMode
              ? '0 0 40px rgba(239, 68, 68, 0.3), 0 0 80px rgba(239, 68, 68, 0.15)'
              : '0 0 20px rgba(239, 68, 68, 0.1)',
          }}
        >
          {panicMode && (
            <motion.div
              animate={{ opacity: [0.3, 0.6, 0.3] }}
              transition={{ repeat: Infinity, duration: 2 }}
              className="absolute inset-0 bg-red-500/5"
            />
          )}
          
          <div className="relative flex items-start justify-between gap-4">
            <div className="flex items-start gap-4 flex-1">
              <div className="p-3 rounded-xl bg-red-500/20 border border-red-500/30">
                <AlertTriangle className="w-6 h-6 text-red-500" />
              </div>
              <div className="flex-1">
                <h3 className="text-white mb-1">Panic Mode</h3>
                <p className="text-white/60 text-sm leading-relaxed">
                  Instantly hide all sensitive content and lock your account with emergency protection
                </p>
              </div>
            </div>
            <Toggle enabled={panicMode} onChange={setPanicMode} variant="danger" />
          </div>
        </motion.div>

        {/* Privacy Section */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="rounded-2xl bg-white/5 border border-white/10 overflow-hidden"
        >
          <div className="px-5 py-4 border-b border-white/5">
            <div className="flex items-center gap-3">
              <Shield className="w-5 h-5 text-purple-400" />
              <h3 className="text-white">Privacy</h3>
            </div>
          </div>

          <div className="divide-y divide-white/5">
            <motion.div
              whileHover={{ backgroundColor: 'rgba(255, 255, 255, 0.02)' }}
              className="px-5 py-4 flex items-center justify-between"
            >
              <div className="flex items-center gap-3 flex-1">
                <Lock className="w-5 h-5 text-white/40" />
                <div>
                  <p className="text-white text-sm">Private Account</p>
                  <p className="text-white/40 text-xs">Only approved followers can see your posts</p>
                </div>
              </div>
              <Toggle enabled={privateAccount} onChange={setPrivateAccount} />
            </motion.div>

            <motion.div
              whileHover={{ backgroundColor: 'rgba(255, 255, 255, 0.02)' }}
              className="px-5 py-4 flex items-center justify-between"
            >
              <div className="flex items-center gap-3 flex-1">
                <Eye className="w-5 h-5 text-white/40" />
                <div>
                  <p className="text-white text-sm">Hide Activity Status</p>
                  <p className="text-white/40 text-xs">Don't show when you're online</p>
                </div>
              </div>
              <Toggle enabled={hideActivity} onChange={setHideActivity} />
            </motion.div>

            <motion.div
              whileHover={{ backgroundColor: 'rgba(255, 255, 255, 0.02)' }}
              className="px-5 py-4 flex items-center justify-between"
            >
              <div className="flex items-center gap-3 flex-1">
                <Fingerprint className="w-5 h-5 text-white/40" />
                <div>
                  <p className="text-white text-sm">Two-Factor Authentication</p>
                  <p className="text-white/40 text-xs">Extra security for your account</p>
                </div>
              </div>
              <Toggle enabled={twoFactorAuth} onChange={setTwoFactorAuth} />
            </motion.div>
          </div>
        </motion.div>

        {/* Notifications Section */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
          className="rounded-2xl bg-white/5 border border-white/10 overflow-hidden"
        >
          <div className="px-5 py-4 border-b border-white/5">
            <div className="flex items-center gap-3">
              <Bell className="w-5 h-5 text-orange-400" />
              <h3 className="text-white">Notifications</h3>
            </div>
          </div>

          <div className="divide-y divide-white/5">
            <motion.div
              whileHover={{ backgroundColor: 'rgba(255, 255, 255, 0.02)' }}
              className="px-5 py-4 flex items-center justify-between"
            >
              <div>
                <p className="text-white text-sm">Push Notifications</p>
                <p className="text-white/40 text-xs">Receive notifications on this device</p>
              </div>
              <Toggle enabled={pushNotifications} onChange={setPushNotifications} />
            </motion.div>

            <motion.div
              whileHover={{ backgroundColor: 'rgba(255, 255, 255, 0.02)' }}
              className="px-5 py-4 flex items-center justify-between"
            >
              <div>
                <p className="text-white text-sm">Email Notifications</p>
                <p className="text-white/40 text-xs">Get updates via email</p>
              </div>
              <Toggle enabled={emailNotifications} onChange={setEmailNotifications} />
            </motion.div>
          </div>
        </motion.div>

        {/* Account Section */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
          className="rounded-2xl bg-white/5 border border-white/10 overflow-hidden"
        >
          <div className="px-5 py-4 border-b border-white/5">
            <div className="flex items-center gap-3">
              <User className="w-5 h-5 text-teal-400" />
              <h3 className="text-white">Account</h3>
            </div>
          </div>

          <div className="divide-y divide-white/5">
            <motion.button
              whileHover={{ backgroundColor: 'rgba(255, 255, 255, 0.02)' }}
              whileTap={{ scale: 0.98 }}
              className="w-full px-5 py-4 flex items-center justify-between text-left"
            >
              <div>
                <p className="text-white text-sm">Change Password</p>
                <p className="text-white/40 text-xs">Update your password</p>
              </div>
              <ChevronRight className="w-5 h-5 text-white/40" />
            </motion.button>

            <motion.button
              whileHover={{ backgroundColor: 'rgba(255, 255, 255, 0.02)' }}
              whileTap={{ scale: 0.98 }}
              className="w-full px-5 py-4 flex items-center justify-between text-left"
            >
              <div>
                <p className="text-white text-sm">Blocked Accounts</p>
                <p className="text-white/40 text-xs">Manage blocked users</p>
              </div>
              <ChevronRight className="w-5 h-5 text-white/40" />
            </motion.button>

            <motion.button
              whileHover={{ backgroundColor: 'rgba(255, 255, 255, 0.02)' }}
              whileTap={{ scale: 0.98 }}
              className="w-full px-5 py-4 flex items-center justify-between text-left"
            >
              <div>
                <p className="text-white text-sm">Download Your Data</p>
                <p className="text-white/40 text-xs">Request a copy of your data</p>
              </div>
              <ChevronRight className="w-5 h-5 text-white/40" />
            </motion.button>

            <motion.button
              whileHover={{ backgroundColor: 'rgba(255, 255, 255, 0.02)' }}
              whileTap={{ scale: 0.98 }}
              className="w-full px-5 py-4 flex items-center justify-between text-left"
            >
              <div>
                <p className="text-white text-sm">Delete Account</p>
                <p className="text-white/40 text-xs">Permanently delete your account</p>
              </div>
              <ChevronRight className="w-5 h-5 text-white/40" />
            </motion.button>
          </div>
        </motion.div>

        {/* Logout Button */}
        <motion.button
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.5 }}
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          className="w-full px-6 py-4 rounded-2xl bg-white/5 border border-red-500/30 hover:border-red-500/50 text-red-500 transition-all flex items-center justify-center gap-3"
        >
          <LogOut className="w-5 h-5" />
          <span>Log Out</span>
        </motion.button>

        {/* Version Info */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.6 }}
          className="text-center pt-4"
        >
          <p className="text-white/30 text-xs">NEXUS v2.0.1</p>
          <p className="text-white/20 text-xs mt-1">© 2025 All rights reserved</p>
        </motion.div>
      </div>
    </div>
  );
}
