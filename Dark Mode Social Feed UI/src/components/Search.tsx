import { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Search as SearchIcon, X, User, Hash, Image, TrendingUp, ArrowLeft } from 'lucide-react';

interface SearchProps {
  onBack: () => void;
}

const mockUsers = [
  { id: 1, username: 'luna.dreams', name: 'Luna Dreams', avatar: 'https://images.unsplash.com/photo-1544124094-8aea0374da93?w=100&h=100&fit=crop', followers: '128K', isVerified: true },
  { id: 2, username: 'neon.nights', name: 'Neon Nights', avatar: 'https://images.unsplash.com/photo-1626113666894-ac3923772213?w=100&h=100&fit=crop', followers: '89K', isVerified: true },
  { id: 3, username: 'fashion.forward', name: 'Fashion Forward', avatar: 'https://images.unsplash.com/photo-1571513722275-4b41940f54b8?w=100&h=100&fit=crop', followers: '256K', isVerified: true },
  { id: 4, username: 'urban.vibe', name: 'Urban Vibe', avatar: 'https://images.unsplash.com/photo-1670324382035-f9cfacc3b59b?w=100&h=100&fit=crop', followers: '67K', isVerified: false },
  { id: 5, username: 'cyber.aesthetic', name: 'Cyber Aesthetic', avatar: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=100&h=100&fit=crop', followers: '142K', isVerified: true },
];

const mockHashtags = [
  { id: 1, tag: 'neonvibes', posts: '2.4M' },
  { id: 2, tag: 'cyberaesthetic', posts: '1.8M' },
  { id: 3, tag: 'futuristicstyle', posts: '892K' },
  { id: 4, tag: 'nexuslife', posts: '3.1M' },
  { id: 5, tag: 'urbannight', posts: '1.2M' },
];

const mockPosts = [
  { id: 1, image: 'https://images.unsplash.com/photo-1544124094-8aea0374da93?w=300&h=300&fit=crop', likes: 1234 },
  { id: 2, image: 'https://images.unsplash.com/photo-1626113666894-ac3923772213?w=300&h=300&fit=crop', likes: 2567 },
  { id: 3, image: 'https://images.unsplash.com/photo-1571513722275-4b41940f54b8?w=300&h=300&fit=crop', likes: 3421 },
  { id: 4, image: 'https://images.unsplash.com/photo-1670324382035-f9cfacc3b59b?w=300&h=300&fit=crop', likes: 1876 },
  { id: 5, image: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=300&h=300&fit=crop', likes: 4521 },
  { id: 6, image: 'https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=300&h=300&fit=crop', likes: 2134 },
];

const trendingSearches = [
  { id: 1, text: 'neon photography', icon: TrendingUp },
  { id: 2, text: 'cyberpunk fashion', icon: TrendingUp },
  { id: 3, text: 'urban nightlife', icon: TrendingUp },
  { id: 4, text: 'futuristic art', icon: TrendingUp },
];

type FilterType = 'all' | 'users' | 'posts' | 'tags';

export function Search({ onBack }: SearchProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [activeFilter, setActiveFilter] = useState<FilterType>('all');
  const [isFocused, setIsFocused] = useState(false);

  const filters: { id: FilterType; label: string; icon: any }[] = [
    { id: 'all', label: 'All', icon: SearchIcon },
    { id: 'users', label: 'Users', icon: User },
    { id: 'posts', label: 'Posts', icon: Image },
    { id: 'tags', label: 'Tags', icon: Hash },
  ];

  const filteredUsers = mockUsers.filter(user =>
    user.username.toLowerCase().includes(searchQuery.toLowerCase()) ||
    user.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const filteredHashtags = mockHashtags.filter(tag =>
    tag.tag.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const showResults = searchQuery.length > 0;
  const showTrending = !showResults;

  return (
    <div className="min-h-screen bg-black relative overflow-hidden">
      {/* Gradient background effects */}
      <div className="fixed inset-0 pointer-events-none">
        <div className="absolute top-0 right-1/4 w-96 h-96 bg-purple-600/20 rounded-full blur-[120px]" />
        <div className="absolute top-1/2 left-1/4 w-80 h-80 bg-teal-500/15 rounded-full blur-[100px]" />
        <div className="absolute bottom-1/4 right-1/3 w-72 h-72 bg-orange-500/15 rounded-full blur-[100px]" />
      </div>

      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="sticky top-0 z-50 backdrop-blur-xl bg-black/70 border-b border-white/5"
      >
        <div className="px-4 py-4">
          <div className="flex items-center gap-4">
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={onBack}
              className="p-2 rounded-full bg-white/5 hover:bg-white/10 transition-colors"
            >
              <ArrowLeft className="w-5 h-5 text-white/70" />
            </motion.button>

            {/* Search Input */}
            <div className="flex-1 relative">
              <motion.div
                animate={{
                  boxShadow: isFocused
                    ? '0 0 30px rgba(168, 85, 247, 0.4)'
                    : '0 0 0px rgba(168, 85, 247, 0)',
                }}
                className="relative"
              >
                <SearchIcon className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-purple-400" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  onFocus={() => setIsFocused(true)}
                  onBlur={() => setIsFocused(false)}
                  placeholder="Search NEXUS..."
                  className="w-full pl-12 pr-12 py-3 bg-white/5 border border-white/10 rounded-2xl text-white placeholder-white/40 focus:outline-none focus:border-purple-500/50 transition-all"
                />
                <AnimatePresence>
                  {searchQuery && (
                    <motion.button
                      initial={{ opacity: 0, scale: 0.8 }}
                      animate={{ opacity: 1, scale: 1 }}
                      exit={{ opacity: 0, scale: 0.8 }}
                      onClick={() => setSearchQuery('')}
                      className="absolute right-4 top-1/2 -translate-y-1/2 p-1 rounded-full bg-white/10 hover:bg-white/20 transition-colors"
                    >
                      <X className="w-4 h-4 text-white/70" />
                    </motion.button>
                  )}
                </AnimatePresence>
              </motion.div>
            </div>
          </div>

          {/* Filter Tabs */}
          <div className="flex gap-2 mt-4 overflow-x-auto pb-2 no-scrollbar">
            {filters.map((filter) => {
              const Icon = filter.icon;
              return (
                <motion.button
                  key={filter.id}
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                  onClick={() => setActiveFilter(filter.id)}
                  className={`flex items-center gap-2 px-4 py-2 rounded-xl transition-all whitespace-nowrap ${
                    activeFilter === filter.id
                      ? 'bg-gradient-to-r from-purple-500 to-pink-500 text-white shadow-lg'
                      : 'bg-white/5 text-white/60 hover:bg-white/10'
                  }`}
                  style={
                    activeFilter === filter.id
                      ? { boxShadow: '0 0 20px rgba(168, 85, 247, 0.5)' }
                      : {}
                  }
                >
                  <Icon className="w-4 h-4" />
                  <span className="text-sm">{filter.label}</span>
                </motion.button>
              );
            })}
          </div>
        </div>
      </motion.div>

      {/* Content */}
      <div className="relative z-10 px-4 py-6 max-w-2xl mx-auto">
        <AnimatePresence mode="wait">
          {showTrending && (
            <motion.div
              key="trending"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              className="space-y-6"
            >
              {/* Trending Searches */}
              <div>
                <h2 className="text-white mb-4 flex items-center gap-2">
                  <TrendingUp className="w-5 h-5 text-orange-400" />
                  Trending Searches
                </h2>
                <div className="space-y-3">
                  {trendingSearches.map((search, index) => {
                    const Icon = search.icon;
                    return (
                      <motion.button
                        key={search.id}
                        initial={{ opacity: 0, x: -20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: index * 0.05 }}
                        whileHover={{ x: 4, scale: 1.02 }}
                        onClick={() => setSearchQuery(search.text)}
                        className="w-full flex items-center gap-4 p-4 rounded-2xl bg-white/5 hover:bg-white/10 border border-white/5 hover:border-orange-500/30 transition-all group"
                      >
                        <div className="p-2 rounded-xl bg-orange-500/20 group-hover:bg-orange-500/30 transition-colors">
                          <Icon className="w-5 h-5 text-orange-400" />
                        </div>
                        <span className="text-white/80 group-hover:text-white transition-colors">{search.text}</span>
                      </motion.button>
                    );
                  })}
                </div>
              </div>

              {/* Popular Tags */}
              <div>
                <h2 className="text-white mb-4 flex items-center gap-2">
                  <Hash className="w-5 h-5 text-teal-400" />
                  Popular Tags
                </h2>
                <div className="flex flex-wrap gap-2">
                  {mockHashtags.map((tag) => (
                    <motion.button
                      key={tag.id}
                      whileHover={{ scale: 1.05, y: -2 }}
                      whileTap={{ scale: 0.95 }}
                      onClick={() => setSearchQuery(tag.tag)}
                      className="px-4 py-2 rounded-xl bg-gradient-to-r from-teal-500/20 to-cyan-500/20 border border-teal-400/30 text-teal-400 hover:border-teal-400 transition-all"
                      style={{ boxShadow: '0 0 15px rgba(20, 184, 166, 0.2)' }}
                    >
                      #{tag.tag}
                    </motion.button>
                  ))}
                </div>
              </div>
            </motion.div>
          )}

          {showResults && (
            <motion.div
              key="results"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              className="space-y-6"
            >
              {/* Users Results */}
              {(activeFilter === 'all' || activeFilter === 'users') && filteredUsers.length > 0 && (
                <div>
                  <h2 className="text-white mb-4 flex items-center gap-2">
                    <User className="w-5 h-5 text-purple-400" />
                    Users
                  </h2>
                  <div className="space-y-3">
                    {filteredUsers.map((user, index) => (
                      <motion.div
                        key={user.id}
                        initial={{ opacity: 0, x: -20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: index * 0.05 }}
                        whileHover={{ x: 4 }}
                        className="flex items-center justify-between p-4 rounded-2xl bg-white/5 hover:bg-white/10 border border-white/5 hover:border-purple-500/30 transition-all group cursor-pointer"
                      >
                        <div className="flex items-center gap-3">
                          <div className="relative">
                            <img
                              src={user.avatar}
                              alt={user.name}
                              className="w-12 h-12 rounded-xl object-cover border-2 border-purple-500/30 group-hover:border-purple-500 transition-colors"
                            />
                            {user.isVerified && (
                              <div className="absolute -bottom-1 -right-1 w-5 h-5 bg-gradient-to-br from-lime-400 to-teal-500 rounded-full flex items-center justify-center border-2 border-black">
                                <span className="text-black text-xs">✓</span>
                              </div>
                            )}
                          </div>
                          <div>
                            <div className="text-white flex items-center gap-2">
                              {user.name}
                            </div>
                            <div className="text-white/50 text-sm">@{user.username}</div>
                          </div>
                        </div>
                        <div className="text-right">
                          <div className="text-white/60 text-sm">{user.followers} followers</div>
                        </div>
                      </motion.div>
                    ))}
                  </div>
                </div>
              )}

              {/* Posts Results */}
              {(activeFilter === 'all' || activeFilter === 'posts') && (
                <div>
                  <h2 className="text-white mb-4 flex items-center gap-2">
                    <Image className="w-5 h-5 text-orange-400" />
                    Posts
                  </h2>
                  <div className="grid grid-cols-3 gap-2">
                    {mockPosts.map((post, index) => (
                      <motion.div
                        key={post.id}
                        initial={{ opacity: 0, scale: 0.8 }}
                        animate={{ opacity: 1, scale: 1 }}
                        transition={{ delay: index * 0.05 }}
                        whileHover={{ scale: 1.05, y: -4 }}
                        className="relative aspect-square rounded-xl overflow-hidden cursor-pointer group"
                      >
                        <img
                          src={post.image}
                          alt="Post"
                          className="w-full h-full object-cover"
                        />
                        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity">
                          <div className="absolute bottom-2 left-2 text-white text-sm">
                            ❤️ {post.likes.toLocaleString()}
                          </div>
                        </div>
                      </motion.div>
                    ))}
                  </div>
                </div>
              )}

              {/* Tags Results */}
              {(activeFilter === 'all' || activeFilter === 'tags') && filteredHashtags.length > 0 && (
                <div>
                  <h2 className="text-white mb-4 flex items-center gap-2">
                    <Hash className="w-5 h-5 text-teal-400" />
                    Hashtags
                  </h2>
                  <div className="space-y-3">
                    {filteredHashtags.map((tag, index) => (
                      <motion.div
                        key={tag.id}
                        initial={{ opacity: 0, x: -20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: index * 0.05 }}
                        whileHover={{ x: 4 }}
                        className="flex items-center justify-between p-4 rounded-2xl bg-white/5 hover:bg-white/10 border border-white/5 hover:border-teal-500/30 transition-all cursor-pointer group"
                      >
                        <div className="flex items-center gap-3">
                          <div className="p-3 rounded-xl bg-teal-500/20 group-hover:bg-teal-500/30 transition-colors">
                            <Hash className="w-5 h-5 text-teal-400" />
                          </div>
                          <div>
                            <div className="text-white">#{tag.tag}</div>
                            <div className="text-white/50 text-sm">{tag.posts} posts</div>
                          </div>
                        </div>
                      </motion.div>
                    ))}
                  </div>
                </div>
              )}

              {/* No Results */}
              {filteredUsers.length === 0 && filteredHashtags.length === 0 && (
                <motion.div
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  className="text-center py-16"
                >
                  <div className="w-20 h-20 mx-auto mb-4 rounded-2xl bg-white/5 flex items-center justify-center">
                    <SearchIcon className="w-10 h-10 text-white/30" />
                  </div>
                  <h3 className="text-white mb-2">No results found</h3>
                  <p className="text-white/50">Try searching for something else</p>
                </motion.div>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
