# UI/UX Design Brief & Gap Analysis

## 1. Design Vision
The Neo application must embody a "Cyberpunk / Synthwave" aesthetic characterized by deep blacks, vibrant neon gradients, glassmorphism (translucent frosted glass), and fluid, spring-based animations. The current Android Compose implementation is too flat, utilizes incorrect colors, and lacks the micro-interactions present in the React/Vite prototype.

## 2. Global Design Tokens (To be strictly enforced)

### 2.1. Color Palette (Update `Color.kt`)
*   **Background:** Pure Black (`#000000`)
*   **Surfaces (Cards/Modals):** Glassmorphic White (`rgba(255,255,255, 0.05)`) with borders of `rgba(255,255,255, 0.1)`
*   **Primary Gradients:**
    *   *Brand Gradient:* Purple (`#8B5CF6`) → Orange (`#F97316`) → Teal (`#14B8A6`)
    *   *Alert/Live:* Orange (`#F97316`) to Red (`#EF4444`)
*   **Text:** Primary White (`#FFFFFF`), Secondary (`rgba(255,255,255, 0.6)`)
*   **Accents:** Lime (`#A3E635`), Cyan (`#06B6D4`), Pink (`#EC4899`)

### 2.2. Typography (Update `Type.kt`)
*   **Font Family:** Inter or Android System UI (Sans-serif).
*   **Header Logo:** Tracking wider (letter-spacing), bold, text "NEXUS".
*   **Body:** Crisp, highly legible sans-serif, standard weights (400, 500).

### 2.3. Shadows & Glows
*   **Neon Glows:** Utilize Compose `Modifier.shadow` or custom `graphicsLayer` drawing to simulate colored glowing drop-shadows (e.g., Purple/Orange glows behind cards).

## 3. Component Gap Analysis & Implementation Guide

### 3.1. Header / TopAppBar
*   **React:** Blurry, sticky header (`backdrop-blur-xl bg-black/50`). Contains a gradient logo, search, bell with pulsing notification dot, and profile avatar with a Lime border.
*   **Android Gap:** Basic implementation. Missing the blur effect, the specific gradient logo, the pulsing notification badge, and the border on the avatar.
*   **Fix:** Implement `Modifier.blur` (or Accompanist equivalent for older API levels) on the header background. Add spring animations for button presses.

### 3.2. Post Card (`EnhancedPostCard.kt`)
*   **React:** `bg-gradient-to-b from-white/5 to-white/[0.02]`. Heavy neon shadow (`boxShadow: '0 0 40px rgba(139, 92, 246, 0.15)...'`). "LIVE" badge for active users. Icons (Heart, Message, Share, Bookmark) have specific hover colors (Orange, Teal, Purple).
*   **Android Gap:** Lacks the complex neon shadows and precise gradients. Icons don't have the color-fill transition on click.
*   **Fix:** Build a custom modifier for the multi-colored neon shadow. Implement animated color transitions for the reaction buttons using `animateColorAsState`.

### 3.3. Floating Action Button (FAB)
*   **React:** Large gradient button (`from-purple-500 via-orange-500 to-teal-500`). Continuous pulsing shadow animation.
*   **Android Gap:** Basic solid color FAB without the infinite pulse animation.
*   **Fix:** Create a custom FAB using `rememberInfiniteTransition` to animate the shadow radius and opacity continuously.

### 3.4. BLE Mesh Status Screen
*   **React:** Highly dynamic. Animated background waves (`linear-gradient(45deg...)`), central Sync Icon with orbital dots and expanding pulse rings. Metric cards with subtle background glows.
*   **Android Gap:** The current Android app only has a small card on the feed. It completely lacks the dedicated, animated diagnostic screen.
*   **Fix:** Build `BLEMeshStatusScreen.kt` from scratch, translating the Framer Motion (`motion/react`) animations into Compose `InfiniteTransition` and `Animatable` properties.

### 3.5. Story Row (New)
*   **React:** Horizontal scrolling list of users with gradient borders.
*   **Android Gap:** Missing entirely.
*   **Fix:** Implement a `LazyRow` at the top of the feed containing `StoryItem` components with gradient circular borders.

### 3.6. Post Creation Modal (`CommentsBottomSheet` / Create Screen)
*   **React:** Glassmorphic modal with a noise texture overlay. Animated character counter (circular SVG progress ring). Media selection grid.
*   **Android Gap:** Uses standard Android screens/bottom sheets. Lacks the noise texture, circular progress indicator, and glassmorphic styling.
*   **Fix:** Update `EnhancedCreatePostScreen.kt` to act as a dark, blurred overlay. Implement the custom circular progress indicator using `Canvas`.
