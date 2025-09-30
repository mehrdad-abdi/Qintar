# Claude Development Instructions

This file contains important instructions for Claude when working on this project.

## Testing Requirements

**ALWAYS add tests for new changes and ensure tests pass:**

1. **Write Tests First**: For any new feature or change, write comprehensive unit tests
2. **Test Coverage**: Ensure all new ViewModels, use cases, and repository changes have corresponding tests
3. **Run Tests**: Always run the test suite after making changes to ensure all tests are green
4. **Test Command**: Use `./gradlew test` to run unit tests

## Testing Guidelines

- **ViewModels**: Test all state changes, user interactions, and error handling
- **Use Cases**: Test business logic, validation, and error scenarios
- **Repositories**: Test data operations, mapping, and error handling
- **UI Components**: Add UI tests for complex user flows when applicable

## Development Workflow

1. Plan the feature/change
2. **Update TodoWrite tool** - Always add new tasks and mark progress
3. Write tests for the expected behavior
4. Implement the feature
5. Run tests to ensure they pass
6. Build the app to verify compilation
7. **Update TodoWrite tool** - Mark tasks as completed
8. Update documentation if needed

Remember: Green tests are mandatory before considering any task complete.

## TODO List Management

**ALWAYS use the TodoWrite tool to track progress:**

- **Start of work**: Add new tasks to todo list
- **During work**: Mark tasks as "in_progress" when starting
- **After completion**: Mark tasks as "completed" immediately
- **Keep granular**: Break large features into smaller, trackable tasks
- **Stay current**: Update the list frequently throughout development

The todo list serves as both progress tracking and documentation of what's been accomplished.

## Technical Environment & Setup

### Java Requirements
- **Required Java Version**: Java 17 (OpenJDK)
- **Location**: `/usr/local/opt/openjdk@17` (via Homebrew)
- **Setup Commands**:
  ```bash
  export JAVA_HOME=/usr/local/opt/openjdk@17
  export PATH=/usr/local/opt/openjdk@17/bin:$PATH
  ```
- **Note**: Android Gradle Plugin 8.13.0 requires Java 17 minimum

### Build Commands
- **Debug Build**: `./gradlew assembleDebug --no-daemon`
- **Run Tests**: `./gradlew test --no-daemon`
- **Clean Build**: `./gradlew clean assembleDebug --no-daemon`

### Audio Settings Implementation
- **Default Reciter**: `ar.alafasy` (Mishary Al-Afasy)
- **Default Bitrate**: `64`
- **Implementation**: Simple checkbox instead of complex dropdown
- **Storage**: `reciterEdition` field in `BookmarkGroup` ("ar.alafasy" when enabled, "none" when disabled)

## Development TODO List

### ✅ Completed Features
1. ✅ Fix navigation after profile creation
2. ✅ Add comprehensive unit tests for ViewModels
3. ✅ Add repository and use case tests
4. ✅ Implement ProfileDetailScreen with bookmarks list
5. ✅ Simplify audio settings to use checkbox with default reciter
6. ✅ Add tests for updated AddProfileViewModel audio settings
7. ✅ Implement bookmark CRUD operations (Add/Edit/Delete bookmarks)
8. ✅ Implement bookmark reading screen with Quran text and audio
9. ✅ Implement EditProfileScreen and EditProfileViewModel with comprehensive tests
10. ✅ **Add daily notification system for profiles**
    - ✅ Profile-level notification settings with enable/disable toggle
    - ✅ Daily reminder scheduling with user-selectable time
    - ✅ WorkManager integration for reliable background notifications
    - ✅ NotificationService with proper Android notification channels
    - ✅ Time picker UI component for notification time selection
    - ✅ Integrated notification settings in Add/Edit Profile screens
    - ✅ Comprehensive unit tests for notification functionality
    - ✅ Hilt dependency injection setup for WorkManager and notifications

11. ✅ **Add UI tests for profile creation flow**
    - ✅ Test complete profile creation workflow
    - ✅ Verify notification settings UI interactions
    - ✅ Test form validation and error handling
    - ✅ Comprehensive AddProfileScreenTest with 18 test cases

12. ✅ **Implement offline caching with WorkManager**
    - ✅ FileDownloadManager for downloading MP3 and image files
    - ✅ Automatic background download when bookmarks created/updated
    - ✅ Cache deduplication (same ayah in multiple bookmarks = single cache)
    - ✅ Local file storage in app cache directory
    - ✅ ReadingViewModel uses cached files first, falls back to streaming
    - ✅ CacheBookmarkContentWorker for background processing
    - ✅ Enhanced VerseMetadata with cached file paths
    - ✅ Full offline reading and audio playback support

### 🔄 Pending Features (Priority Order)
1. ⭐ **Add import/export functionality for profiles**
   - Export profiles and bookmarks to JSON/file
   - Import profiles from backup files
   - Data migration and validation

### 🎯 Future Enhancements
- Audio playback controls and background service
- Bookmark synchronization across devices
- Multiple reciter support (if needed)
- Bookmark categories and tags
- Reading progress tracking
- Dark mode support
- Accessibility improvements

## API Integration

### alquran.cloud API
- **Base URL**: `https://api.alquran.cloud/v1/`
- **Audio Editions**: `/edition/format/audio`
- **Verse Data**: `/ayah/{reference}/{edition}`
- **Image CDN**: Islamic Network CDN for Arabic text images

### Default Settings
- **Reciter ID**: `ar.alafasy`
- **Text Edition**: Arabic text with proper formatting
- **Audio Quality**: 64kbps (configurable)

## Context Management

**ALWAYS redirect long command outputs to files to avoid context bloat:**

- When running tests: `./gradlew test > test_output.txt 2>&1`
- When building: `./gradlew build > build_output.txt 2>&1`
- Then use `grep`, `head`, `tail` to extract needed information
- This prevents LLM context overflow and hallucinations from excessive output
- cleanup txt files you have built to redirect the output, time to time.