import { Plus } from 'lucide-react';
import { motion } from 'motion/react';

export function FloatingActionButton({ onClick }: { onClick: () => void }) {
  return (
    <motion.button
      initial={{ scale: 0 }}
      animate={{ scale: 1 }}
      whileHover={{ scale: 1.1 }}
      whileTap={{ scale: 0.95 }}
      onClick={onClick}
      className="fixed bottom-6 right-6 w-16 h-16 rounded-full bg-gradient-to-br from-purple-500 via-orange-500 to-teal-500 flex items-center justify-center shadow-2xl z-50"
      style={{
        boxShadow: '0 0 30px rgba(139, 92, 246, 0.5), 0 0 60px rgba(249, 115, 22, 0.3)',
      }}
    >
      <motion.div
        animate={{
          boxShadow: [
            '0 0 20px rgba(139, 92, 246, 0.5)',
            '0 0 40px rgba(249, 115, 22, 0.5)',
            '0 0 20px rgba(139, 92, 246, 0.5)',
          ],
        }}
        transition={{ repeat: Infinity, duration: 2 }}
        className="absolute inset-0 rounded-full"
      />
      <Plus className="w-8 h-8 text-white relative z-10" />
    </motion.button>
  );
}