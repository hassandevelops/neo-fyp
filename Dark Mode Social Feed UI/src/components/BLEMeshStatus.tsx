import { motion, AnimatePresence } from 'motion/react';
import { ArrowLeft, Wifi, Radio, Zap, Clock, Users, Activity, RefreshCw } from 'lucide-react';
import { useState, useEffect } from 'react';

interface BLEMeshStatusProps {
  onBack: () => void;
}

interface DeviceNode {
  id: string;
  name: string;
  rssi: number;
  online: boolean;
}

function WaveBackground() {
  return (
    <div className="absolute inset-0 overflow-hidden">
      {/* Animated gradient waves */}
      <motion.div
        animate={{
          backgroundPosition: ['0% 0%', '100% 100%'],
        }}
        transition={{
          duration: 20,
          repeat: Infinity,
          repeatType: 'reverse',
          ease: 'linear',
        }}
        className="absolute inset-0 opacity-30"
        style={{
          background: 'linear-gradient(45deg, #8b5cf6 0%, #ec4899 25%, #06b6d4 50%, #8b5cf6 75%, #ec4899 100%)',
          backgroundSize: '400% 400%',
        }}
      />
      
      {/* Animated wave layers */}
      {[0, 1, 2].map((index) => (
        <motion.div
          key={index}
          animate={{
            y: [0, -20, 0],
            opacity: [0.1, 0.2, 0.1],
          }}
          transition={{
            duration: 8 + index * 2,
            repeat: Infinity,
            delay: index * 0.5,
            ease: 'easeInOut',
          }}
          className="absolute inset-0"
          style={{
            background: `radial-gradient(circle at ${30 + index * 20}% ${40 + index * 10}%, rgba(139, 92, 246, 0.2), transparent 60%)`,
          }}
        />
      ))}
    </div>
  );
}

function PulseRing({ delay = 0 }: { delay?: number }) {
  return (
    <motion.div
      initial={{ scale: 0, opacity: 1 }}
      animate={{ scale: 3, opacity: 0 }}
      transition={{
        duration: 3,
        repeat: Infinity,
        delay,
        ease: 'easeOut',
      }}
      className="absolute inset-0 border-2 border-cyan-400 rounded-full"
      style={{
        boxShadow: '0 0 20px rgba(6, 182, 212, 0.6)',
      }}
    />
  );
}

function SyncIcon({ isSyncing }: { isSyncing: boolean }) {
  return (
    <div className="relative w-32 h-32 flex items-center justify-center">
      {/* Pulse rings when syncing */}
      {isSyncing && (
        <>
          <PulseRing delay={0} />
          <PulseRing delay={0.5} />
          <PulseRing delay={1} />
        </>
      )}
      
      {/* Center icon */}
      <motion.div
        animate={isSyncing ? {
          rotate: 360,
        } : {}}
        transition={{
          duration: 2,
          repeat: isSyncing ? Infinity : 0,
          ease: 'linear',
        }}
        className="relative z-10 w-20 h-20 rounded-full bg-gradient-to-br from-cyan-400 via-purple-500 to-pink-500 flex items-center justify-center"
        style={{
          boxShadow: '0 0 40px rgba(6, 182, 212, 0.6), 0 0 80px rgba(139, 92, 246, 0.4)',
        }}
      >
        <motion.div
          animate={{
            scale: isSyncing ? [1, 1.1, 1] : 1,
          }}
          transition={{
            duration: 1,
            repeat: isSyncing ? Infinity : 0,
          }}
        >
          <RefreshCw className="w-10 h-10 text-white" />
        </motion.div>
      </motion.div>

      {/* Orbital dots */}
      {[0, 1, 2].map((index) => (
        <motion.div
          key={index}
          animate={{
            rotate: 360,
          }}
          transition={{
            duration: 4 + index,
            repeat: Infinity,
            ease: 'linear',
          }}
          className="absolute inset-0"
          style={{
            transformOrigin: 'center',
          }}
        >
          <div
            className="absolute w-3 h-3 rounded-full bg-cyan-400"
            style={{
              top: '0%',
              left: '50%',
              transform: 'translate(-50%, -50%)',
              boxShadow: '0 0 10px rgba(6, 182, 212, 0.8)',
            }}
          />
        </motion.div>
      ))}
    </div>
  );
}

function MetricCard({ 
  icon: Icon, 
  label, 
  value, 
  color, 
  isLive = false 
}: { 
  icon: any; 
  label: string; 
  value: string | number; 
  color: string;
  isLive?: boolean;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.9 }}
      animate={{ opacity: 1, scale: 1 }}
      whileHover={{ scale: 1.05, y: -4 }}
      className="relative rounded-2xl bg-white/5 border border-white/10 backdrop-blur-sm p-5 overflow-hidden"
      style={{
        boxShadow: `0 0 30px ${color}20`,
      }}
    >
      {/* Animated glow */}
      {isLive && (
        <motion.div
          animate={{
            opacity: [0.3, 0.6, 0.3],
            scale: [1, 1.1, 1],
          }}
          transition={{
            duration: 2,
            repeat: Infinity,
          }}
          className="absolute inset-0"
          style={{
            background: `radial-gradient(circle at center, ${color}15, transparent 70%)`,
          }}
        />
      )}

      <div className="relative">
        <div className="flex items-center justify-between mb-3">
          <div
            className="p-2 rounded-lg"
            style={{
              backgroundColor: `${color}20`,
              boxShadow: `0 0 15px ${color}30`,
            }}
          >
            <Icon className="w-5 h-5" style={{ color }} />
          </div>
          {isLive && (
            <motion.div
              animate={{ opacity: [0.5, 1, 0.5] }}
              transition={{ duration: 2, repeat: Infinity }}
              className="flex items-center gap-1 text-xs"
              style={{ color }}
            >
              <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: color }} />
              LIVE
            </motion.div>
          )}
        </div>

        <div className="space-y-1">
          <div className="text-white/50 text-xs uppercase tracking-wider">{label}</div>
          <div className="text-white text-2xl tabular-nums">{value}</div>
        </div>
      </div>
    </motion.div>
  );
}

function DeviceNode({ device, index }: { device: DeviceNode; index: number }) {
  return (
    <motion.div
      initial={{ opacity: 0, x: -20 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay: index * 0.05 }}
      className="flex items-center justify-between p-3 rounded-xl bg-white/5 border border-white/10"
    >
      <div className="flex items-center gap-3">
        <div className="relative">
          <div
            className={`w-2 h-2 rounded-full ${
              device.online ? 'bg-green-400' : 'bg-red-400'
            }`}
            style={{
              boxShadow: device.online
                ? '0 0 10px rgba(74, 222, 128, 0.6)'
                : '0 0 10px rgba(248, 113, 113, 0.6)',
            }}
          />
          {device.online && (
            <motion.div
              animate={{ scale: [1, 1.5, 1], opacity: [0.5, 0, 0.5] }}
              transition={{ duration: 2, repeat: Infinity }}
              className="absolute inset-0 bg-green-400 rounded-full"
            />
          )}
        </div>
        <div>
          <div className="text-white text-sm">{device.name}</div>
          <div className="text-white/40 text-xs">RSSI: {device.rssi} dBm</div>
        </div>
      </div>
      <div className="flex items-center gap-1">
        {Array.from({ length: 4 }).map((_, i) => (
          <div
            key={i}
            className={`w-1 h-3 rounded-sm ${
              Math.abs(device.rssi) < 50 + i * 10 ? 'bg-cyan-400' : 'bg-white/20'
            }`}
            style={{
              height: `${8 + i * 4}px`,
            }}
          />
        ))}
      </div>
    </motion.div>
  );
}

export function BLEMeshStatus({ onBack }: BLEMeshStatusProps) {
  const [isSyncing, setIsSyncing] = useState(true);
  const [devicesCount, setDevicesCount] = useState(12);
  const [lastSync, setLastSync] = useState(0);
  const [dataRate, setDataRate] = useState(0);

  const mockDevices: DeviceNode[] = [
    { id: '1', name: 'Node Alpha', rssi: -42, online: true },
    { id: '2', name: 'Node Beta', rssi: -58, online: true },
    { id: '3', name: 'Node Gamma', rssi: -71, online: true },
    { id: '4', name: 'Node Delta', rssi: -65, online: false },
    { id: '5', name: 'Node Epsilon', rssi: -48, online: true },
  ];

  useEffect(() => {
    // Simulate sync activity
    const syncInterval = setInterval(() => {
      setIsSyncing(Math.random() > 0.3);
      setDevicesCount(Math.floor(10 + Math.random() * 5));
      setDataRate(Math.floor(Math.random() * 100));
    }, 3000);

    // Update last sync timer
    const timerInterval = setInterval(() => {
      setLastSync((prev) => prev + 1);
    }, 1000);

    return () => {
      clearInterval(syncInterval);
      clearInterval(timerInterval);
    };
  }, []);

  return (
    <div className="min-h-screen bg-black relative overflow-hidden">
      {/* Animated wave background */}
      <WaveBackground />

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
          
          <h2 className="text-white">BLE Mesh Network</h2>
          
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-cyan-400/10 border border-cyan-400/30">
            <motion.div
              animate={{ scale: [1, 1.2, 1] }}
              transition={{ duration: 2, repeat: Infinity }}
              className="w-2 h-2 rounded-full bg-cyan-400"
              style={{ boxShadow: '0 0 10px rgba(6, 182, 212, 0.8)' }}
            />
            <span className="text-cyan-400 text-xs uppercase tracking-wider">Active</span>
          </div>
        </div>
      </motion.header>

      {/* Main Content */}
      <div className="relative z-10 max-w-2xl mx-auto px-4 py-6 pb-24">
        {/* Central Sync Icon */}
        <motion.div
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: 0.2 }}
          className="flex justify-center mb-8"
        >
          <SyncIcon isSyncing={isSyncing} />
        </motion.div>

        {/* Status Text */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.3 }}
          className="text-center mb-8"
        >
          <AnimatePresence mode="wait">
            {isSyncing ? (
              <motion.p
                key="syncing"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                className="text-cyan-400 text-lg"
              >
                Syncing mesh network...
              </motion.p>
            ) : (
              <motion.p
                key="idle"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                className="text-white/60"
              >
                Network idle
              </motion.p>
            )}
          </AnimatePresence>
        </motion.div>

        {/* Metrics Grid */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
          className="grid grid-cols-2 gap-4 mb-6"
        >
          <MetricCard
            icon={Users}
            label="Nearby Devices"
            value={devicesCount}
            color="#06b6d4"
            isLive={isSyncing}
          />
          <MetricCard
            icon={Clock}
            label="Last Sync"
            value={`${lastSync}s ago`}
            color="#8b5cf6"
          />
          <MetricCard
            icon={Activity}
            label="Data Rate"
            value={`${dataRate} kb/s`}
            color="#ec4899"
            isLive={isSyncing}
          />
          <MetricCard
            icon={Zap}
            label="Signal Quality"
            value="Excellent"
            color="#22c55e"
          />
        </motion.div>

        {/* Network Status Card */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.5 }}
          className="rounded-2xl bg-white/5 border border-white/10 backdrop-blur-sm p-5 mb-6"
        >
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-3">
              <Radio className="w-5 h-5 text-cyan-400" />
              <h3 className="text-white">Network Topology</h3>
            </div>
            <div className="px-3 py-1 rounded-full bg-green-400/10 border border-green-400/30 text-green-400 text-xs">
              {mockDevices.filter(d => d.online).length}/{mockDevices.length} Online
            </div>
          </div>

          <div className="space-y-2">
            {mockDevices.map((device, index) => (
              <DeviceNode key={device.id} device={device} index={index} />
            ))}
          </div>
        </motion.div>

        {/* Connection Strength Visualization */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.6 }}
          className="rounded-2xl bg-white/5 border border-white/10 backdrop-blur-sm p-5"
        >
          <div className="flex items-center gap-3 mb-4">
            <Wifi className="w-5 h-5 text-purple-400" />
            <h3 className="text-white">Signal Distribution</h3>
          </div>

          <div className="space-y-3">
            {[
              { label: 'Excellent', value: 45, color: '#22c55e' },
              { label: 'Good', value: 30, color: '#06b6d4' },
              { label: 'Fair', value: 20, color: '#f59e0b' },
              { label: 'Poor', value: 5, color: '#ef4444' },
            ].map((signal, index) => (
              <div key={signal.label}>
                <div className="flex items-center justify-between mb-1.5">
                  <span className="text-white/60 text-sm">{signal.label}</span>
                  <span className="text-white/80 text-sm">{signal.value}%</span>
                </div>
                <div className="h-2 rounded-full bg-white/10 overflow-hidden">
                  <motion.div
                    initial={{ width: 0 }}
                    animate={{ width: `${signal.value}%` }}
                    transition={{ delay: 0.7 + index * 0.1, duration: 1, ease: 'easeOut' }}
                    className="h-full rounded-full"
                    style={{
                      backgroundColor: signal.color,
                      boxShadow: `0 0 10px ${signal.color}80`,
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        </motion.div>
      </div>
    </div>
  );
}
