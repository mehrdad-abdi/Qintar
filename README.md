# Qintar

> **قال رسول الله (ص):**
>
> من قَرَأَ عَشْرَ آیاتٍ فی لَیْلَةٍ لَمْ یُکْتَبْ مِنَ الْغافِلینَ 🔹،
> وَمَنْ‌ قَرَأَ خَمْسینَ آیَةً کُتِبَ مِنَ الذّاکِرینَ 🔺،‌
> وَمَنْ قَرَأَ‌ مِائَة آیةٍ کتب من القانِتینَ 🥉،
> وَمَنْ‌ قَرَأَ مِائتی آیَةٍ کُتِبَ مِنَ الْخاشِعینَ 🥈،
> وَمَنْ قَرَأَ ثَلاثَ مِئَةِ آیةٍ کُتِبَ مِنَ الْفائِزینَ 🥇،
> وَمَنْ قَرَأَ خَمْسَ مِائَةِ آیةٍ کُتِبَ مِنَ المُجْتَهدینَ 🎖️،
> وَمَنْ قَرَأَ ألْفَ آیةٍ کُتِبَ لَهُ قِنْطارٌ مِنْ بِرًْ 👑
>
> *"The Messenger of Allah (peace be upon him and his family and companions) said:*
> *Whoever recites ten verses in a night will not be written among the heedless,*
> *whoever recites fifty verses will be written among those who remember Allah,*
> *whoever recites one hundred verses will be written among the devout,*
> *whoever recites two hundred verses will be written among the humble,*
> *whoever recites three hundred verses will be written among the winners,*
> *whoever recites five hundred verses will be written among the strivers,*
> *and whoever recites one thousand verses will be rewarded with a Qintar of virtue."*

A modern Android app inspired by this Hadith, helping you build consistent Quran reading habits through personalized bookmarks, automatic daily tracking, and spiritual progress badges.

## Overview

Qintar is a comprehensive Quran companion app that helps you build consistent reading habits through bookmarks, daily tracking, and motivating progress badges. Organize your favorite passages, track your daily reading achievements, and work towards spiritual milestones inspired by authentic Hadith.

### Why Qintar?

- **Daily Reading Tracking**: Automatically track your daily Quran reading with beautiful progress badges
- **Spiritual Milestones**: Seven badge levels from "Bismillah" to "Qintar" based on daily ayah counts
- **Flexible Bookmarking**: Save individual ayahs, ranges, complete surahs, or specific pages
- **Visual Experience**: High-quality Arabic calligraphy images for authentic reading
- **Audio Playback**: Professional audio with reciter selection and playback controls
- **Statistics & Calendar**: View reading streaks, 30-day charts, and monthly badge calendar
- **Offline Access**: Automatic content caching for uninterrupted study
- **Multiple Reading Modes**: Bookmarks, Random Ayah, and Khatm (complete Quran) reading

## Core Features

### 📊 Daily Reading Tracking & Badges
- **Automatic Progress Tracking**: Every ayah you read is automatically tracked
- **Seven Badge Levels** (based on the Hadith above):
  - 🔹 **Not Ghafil** (10 ayahs) - Not among the heedless
  - 🔺 **Zakir** (50 ayahs) - Among those who remember Allah
  - 🥉 **Qanit** (100 ayahs) - Among the devout
  - 🥈 **Khashie** (200 ayahs) - Among the humble
  - 🥇 **Faez** (300 ayahs) - Among the winners
  - 🎖️ **Mujtahid** (500 ayahs) - Among the strivers
  - 👑 **Qintar** (1000 ayahs) - Rewarded with a Qintar of virtue
- **Reading Streaks**: Track your consistency with daily streak counters
- **Statistics Dashboard**: 30-day bar charts, total ayahs read, best day, and average per day
- **Badge Calendar**: Monthly view showing your daily achievements with visual badges
- **Last 7 Days View**: Quick glance at recent reading activity

### 🔖 Flexible Bookmark Management
- **Multiple Types**: Save individual ayahs, ayah ranges, complete surahs, or specific pages
- **Personal Notes**: Add descriptions to bookmarks (up to 500 characters)
- **Full CRUD Operations**: Create, read, update, and delete bookmarks easily
- **Smart Organization**: Bookmarks organized by creation date

### 📖 Rich Reading Experience
- **Visual Arabic Text**: High-resolution calligraphy images from Islamic Network CDN
- **Audio Playback**: Professional reciters with play/pause/stop controls
- **Multiple Reading Modes**:
  - **Bookmark Reading**: Read your saved passages
  - **Random Ayah/Page**: Get a random verse or page for variety
  - **Khatm Reading**: Read the complete Quran (604 pages) with page navigation
- **Auto-Scroll**: Synchronized scrolling with audio playback
- **Jump Navigation**: Quickly jump to any surah, juz, or page number

### 🎵 Audio Features
- **Reciter Selection**: Choose from multiple professional reciters
- **Offline Playback**: Automatic caching of audio files for offline listening
- **Playback Controls**: Play, pause, stop with intuitive controls

### 🔔 Daily Notifications
- **Smart Reminders**: Customizable daily notifications
- **Badge Motivation**: Reminders encourage you to earn your daily badge
- **Flexible Scheduling**: Set your preferred notification time

### 💾 Backup & Restore
- **Export Data**: Export all bookmarks and reading history
- **Import Data**: Restore from backup files
- **Data Portability**: Take your progress with you

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

#### ✅ All Core Features Completed!

**Reading & Tracking:**
- ✅ Daily ayah reading tracking with automatic badge progression
- ✅ Seven Hadith-based badge levels (10, 50, 100, 200, 300, 500, 1000 ayahs)
- ✅ Badge icons: 🔹 → 🔺 → 🥉 → 🥈 → 🥇 → 🎖️ → 👑
- ✅ Reading streak calculation and display
- ✅ Statistics dashboard with 30-day charts
- ✅ Badge calendar with monthly view
- ✅ Last 7 days quick view

**Bookmark Management:**
- ✅ Create bookmarks (ayah, range, surah, page)
- ✅ Edit and delete bookmarks
- ✅ Personal notes for each bookmark
- ✅ Full CRUD operations

**Reading Experience:**
- ✅ Bookmark reading screen with Arabic text and audio
- ✅ Random ayah/page selection
- ✅ Khatm reading (complete Quran 604 pages)
- ✅ Jump navigation (surah, juz, page)
- ✅ Auto-scroll synchronized with audio

**Audio & Media:**
- ✅ Multiple reciter selection
- ✅ Audio playback controls
- ✅ Offline caching with WorkManager
- ✅ Background audio playback

**Settings & Personalization:**
- ✅ Theme selection (Light/Dark/System)
- ✅ Primary color customization
- ✅ Multilingual support (English, Arabic, Persian)
- ✅ Daily notification scheduling
- ✅ Backup and restore functionality

**Technical:**
- ✅ Clean architecture (Domain/Data/Presentation layers)
- ✅ Jetpack Compose UI
- ✅ Room database with offline-first approach
- ✅ Hilt dependency injection
- ✅ Comprehensive unit tests
- ✅ Reactive data with Kotlin Flow

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

## What Makes Qintar Special?

### Hadith-Inspired Badge System
Unlike other Quran apps, Qintar's badge system is directly inspired by an authentic Hadith that rewards readers based on the number of ayahs read daily. This gamification approach motivates consistent reading while staying true to Islamic traditions.

### Comprehensive Progress Tracking
- **Daily Tracking**: Automatic tracking of every ayah you read
- **Visual Calendar**: See your entire month's reading activity at a glance
- **Meaningful Metrics**: Streaks, totals, averages, and best days
- **Badge Progression**: Visual representation of spiritual growth

### Flexible Reading Experience
- **Bookmarks**: Save and organize your favorite passages
- **Random Mode**: Discover new verses with random ayah/page selection
- **Khatm Mode**: Read the complete Quran with page-by-page navigation
- **Offline First**: All content cached for uninterrupted access

### Modern Architecture
- **Native Android**: Built with latest Jetpack Compose
- **Clean Code**: Follows clean architecture principles
- **Well-Tested**: Comprehensive unit test coverage
- **Multilingual**: Full support for English, Arabic, and Persian

## Future Roadmap

### Potential Enhancements
- 📊 **Advanced Analytics**: Deeper insights into reading patterns and habits
- 🌙 **Prayer Times Integration**: Combine reading with prayer time reminders
- 📝 **Translation Support**: Display verse translations alongside Arabic text
- 🎯 **Reading Goals**: Set and track custom reading goals
- 🔍 **Search Functionality**: Search within your bookmarks and notes
- 👥 **Community Features**: Share achievements and inspire others (optional)
- 🌐 **Cloud Sync**: Sync data across multiple devices
- 🎨 **Custom Themes**: More color schemes and customization options

**Note**: The app is feature-complete and fully functional. Future enhancements will be based on community feedback and requests.

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

- **Quran API**: [alquran.cloud](https://alquran.cloud) - Free Quran API and Islamic Network for high-quality Arabic calligraphy images
- **Development**: [Claude Code](https://claude.com/claude-code) - AI-assisted development tool used to build this entire project
- **Inspiration**: The Hadith of Prophet Muhammad (ﷺ) about the rewards of Quran recitation

## About This Project

**Qintar** was built entirely through AI-assisted development with Claude Code. From architecture design to implementation, from UI/UX to testing, every aspect of this app represents a collaboration between human vision and AI capability.

**Repository**: [github.com/mehrdad-abdi/Qintar](https://github.com/mehrdad-abdi/Qintar)

---

**Built with ❤️ in Antwerp 🇧🇪**

**For the Muslim community and all people**

*May this app help you strengthen your connection with the Quran and find peace in its verses.*
