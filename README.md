# Qintar

> 🤖 **100% Vibe Coding Project** - Built entirely with [Claude Code](https://claude.com/claude-code)

A modern Android app for organizing and studying your favorite Quran passages through personalized bookmark profiles.

## Overview

Quran-Bookmarks allows users to create custom bookmark profiles (e.g., "Daily Duas", "Comfort Verses", "Memorization List") and organize their favorite Quran passages with visual Arabic text, synchronized audio recitation, and smart notifications.

### Why Quran-Bookmarks?

- **Personal Organization**: Create themed bookmark collections for different spiritual needs
- **Visual Experience**: High-quality Arabic calligraphy images for authentic reading
- **Audio Synchronization**: Seamless audio-visual experience with auto-scroll functionality
- **Smart Reminders**: Custom notification scheduling per bookmark profile
- **Offline Access**: Download and cache content for uninterrupted study

## Core Features

### 🔖 Bookmark Profile Management
- Create unlimited bookmark profiles with custom names and descriptions (up to 500 characters)
- Color-code profiles for easy visual organization
- Individual reciter selection per profile
- Custom notification scheduling (daily, weekly, or specific times)

### 📖 Flexible Content Addition
- **Individual Ayahs**: Bookmark specific verses
- **Ayah Ranges**: Save multiple consecutive verses (e.g., Ayah 255-257)
- **Full Surahs**: Bookmark entire chapters
- **Specific Pages**: Reference by page number
- **Personal Notes**: Add descriptions to each bookmark (up to 500 characters)

### 🎵 Advanced Reading Experience
- **Visual Arabic Text**: High-resolution calligraphy images from trusted sources
- **Audio Controls**: Play, pause, stop with speed and volume adjustment
- **Auto-Scroll**: Adjustable scroll speed synchronized with audio playback
- **Screen Wake Lock**: Prevent screen timeout during reading sessions
- **Audio-Visual Sync**: Perfectly timed highlighting and scrolling

### 🔔 Smart Notifications
- Profile-specific reminder scheduling
- Custom timing options (daily at 7 AM, Fridays at 4 PM, etc.)
- Non-intrusive reminders to revisit your saved passages

## Technical Architecture

### Technology Stack
- **Platform**: Native Android (Kotlin)
- **UI Framework**: Jetpack Compose (Modern Android UI)
- **Database**: Room (SQLite wrapper) for offline storage
- **Image Loading**: Coil with intelligent caching
- **Audio Playback**: ExoPlayer for professional audio experience
- **Background Tasks**: WorkManager for downloads and notifications
- **Networking**: Retrofit for API communication

### API Integration
**Data Source**: [alquran.cloud](https://alquran.cloud) - Free Quran API

- **Image CDN**: `https://cdn.islamic.network/quran/images/{surah}_{ayah}.png`
- **High-Res Images**: `https://cdn.islamic.network/quran/images/high-resolution/{surah}_{ayah}.png`
- **Audio CDN**: `https://cdn.islamic.network/quran/audio/{bitrate}/{reciter}/{verse}.mp3`
- **Metadata API**: `https://api.alquran.cloud/v1/` for verse information and available reciters

### Database Schema
```
BookmarkGroup:
- id, name, description, color
- reciterEdition (selected audio reciter)
- notificationSettings (custom schedule)

Bookmark:
- id, groupId, type (AYAH/RANGE/SURAH/PAGE)
- startSurah, startAyah, endSurah, endAyah
- description (personal notes)

CachedContent:
- id, surah, ayah
- imagePath (local cached image)
- audioPath (local cached audio)
- metadata (verse information)
```

## Development Setup

### Prerequisites
- **Android Studio** (recommended) or compatible IDE
- **Android SDK** API Level 24+ (Android 7.0+)
- **Java 17** (Required for Android Gradle Plugin 8.13.0)
- **Kotlin** support
- **Internet connection** for initial content download

### Environment Setup
```bash
# Java 17 setup (via Homebrew on macOS)
export JAVA_HOME=/usr/local/opt/openjdk@17
export PATH=/usr/local/opt/openjdk@17/bin:$PATH
```

### Getting Started
1. Clone the repository
   ```bash
   git clone https://github.com/mehrdad-abdi/Qintar.git
   cd Qintar
   ```

2. Open in Android Studio
   - File → Open → Select project directory
   - Let Android Studio sync dependencies

3. Build and Run
   ```bash
   # Build the project
   ./gradlew assembleDebug --no-daemon

   # Run tests
   ./gradlew test --no-daemon

   # Install on device
   ./gradlew installDebug
   ```

### Development Status

#### ✅ Completed Features
- **Profile Management**: Create, list, and manage bookmark profiles
- **Audio Settings**: Simplified checkbox with default reciter (Mishary Al-Afasy)
- **Navigation**: Complete app navigation flow with proper back handling
- **UI Components**: Profile creation, profile detail screens with modern Compose UI
- **Database Layer**: Room implementation with entities, DAOs, and repositories
- **Architecture**: Clean architecture with ViewModels, use cases, and dependency injection
- **Testing**: Comprehensive unit tests for ViewModels, use cases, and repositories

#### 🔄 Currently In Development
- UI tests for profile creation flow
- Bookmark CRUD operations (Add/Edit/Delete bookmarks)

#### ⭐ Planned Features
- Bookmark reading screen with Quran text and audio
- Profile editing functionality
- Offline caching with WorkManager
- Notification system for bookmarks
- Search functionality within bookmarks

### Project Structure
```
app/
├── src/main/
│   ├── java/com/quran/bookmarks/
│   │   ├── data/          # Database, API, Repository
│   │   ├── domain/        # Business logic, Use cases
│   │   ├── presentation/  # UI, ViewModels
│   │   └── di/           # Dependency Injection
│   ├── res/
│   │   ├── values/       # Strings, Colors, Dimensions
│   │   └── drawable/     # Icons and graphics
│   └── AndroidManifest.xml
└── build.gradle
```

## Unique Value Proposition

### vs. Existing Quran Apps
- **Profile-Centric Approach**: Unlike folder-based organization, bookmark profiles provide themed collections with individual settings
- **Visual-First Experience**: High-quality Arabic calligraphy images instead of rendered text
- **Advanced Audio Sync**: Sophisticated audio-visual synchronization with customizable auto-scroll
- **Granular Control**: Per-profile reciter selection and notification scheduling
- **Offline Excellence**: Smart caching system for uninterrupted study

### Target Benefits
- **For Daily Readers**: Quick access to daily verses with automated reminders
- **For Students**: Organized study materials with visual and audio learning
- **For Memorizers**: Profile-based organization for different memorization goals
- **For Researchers**: Categorized verse collections for specific topics

## Roadmap

### Phase 1: Core Functionality ✅
- [x] Project setup and architecture
- [x] Basic bookmark CRUD operations
- [x] API integration and caching
- [x] Image display and audio playback

### Phase 2: Enhanced Experience
- [ ] Audio-visual synchronization
- [ ] Auto-scroll implementation
- [ ] Advanced playback controls
- [ ] Notification system

### Phase 3: Polish & Optimization
- [ ] Performance optimization
- [ ] Error handling and offline support
- [ ] UI/UX improvements
- [ ] Comprehensive testing

### Future Considerations
- Multilingual UI support
- Translation text alongside Arabic images
- Community features and sharing
- Advanced search capabilities

## Contributing

We welcome contributions! Please read our contributing guidelines:

1. **Code Style**: Follow Kotlin coding conventions
2. **Architecture**: Maintain clean architecture principles
3. **Testing**: Include unit tests for new features
4. **Documentation**: Update README for significant changes

### Issue Reporting
- Use GitHub Issues for bug reports and feature requests
- Provide detailed reproduction steps
- Include device information and Android version

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- **API Provider**: [alquran.cloud](https://alquran.cloud) for free Quran API access
- **Calligraphy**: Islamic Network for high-quality Arabic text images
- **Development Tool**: [Claude Code](https://claude.com/claude-code) - This entire project was built through AI-assisted vibe coding
- **Inspiration**: The global Muslim community's need for personalized Quran study tools

## About This Project

**Qintar** is a testament to the power of AI-assisted development. This entire application - from architecture to implementation, from UI design to testing - was built through collaborative "vibe coding" sessions with Claude Code. Every line of code, every feature, and every design decision represents a conversation between human vision and AI capability.

**Repository**: [github.com/mehrdad-abdi/Qintar](https://github.com/mehrdad-abdi/Qintar)

---

**Built with ❤️ in Antwerp 🇧🇪 for the Muslim community and all people, powered by 🤖 Claude Code**

*May this app help you strengthen your connection with the Quran and find peace in its verses.*