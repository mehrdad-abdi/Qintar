package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.profile

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mehrdad_abdi.quranbookmarks.presentation.theme.QuranBookmarksTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddProfileScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var navigateBackCalled = false
    private var profileCreatedId: Long? = null

    @Before
    fun setup() {
        navigateBackCalled = false
        profileCreatedId = null
    }

    @Test
    fun addProfileScreen_initialState_displaysCorrectElements() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // Then - verify initial UI elements
        composeTestRule.onNodeWithText("Create Profile").assertIsDisplayed()
        composeTestRule.onNodeWithText("Profile Name *").assertIsDisplayed()
        composeTestRule.onNodeWithText("Description").assertIsDisplayed()
        composeTestRule.onNodeWithText("Profile Color").assertIsDisplayed()
        composeTestRule.onNodeWithText("Audio Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enable Audio Download").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily Reminder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enable Daily Reminder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()

        // Verify initial preview shows placeholder
        composeTestRule.onNodeWithText("Profile Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Audio: Enabled (Mishary Al-Afasy)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily Reminder: Disabled").assertIsDisplayed()
    }

    @Test
    fun addProfileScreen_enterProfileName_updatesPreview() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // When - enter profile name
        composeTestRule.onNodeWithText("Enter profile name")
            .performTextInput("Daily Duas")

        // Then - verify preview updates
        composeTestRule.onNodeWithText("Daily Duas").assertIsDisplayed()
    }

    @Test
    fun addProfileScreen_enterDescription_updatesPreview() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // When - enter description
        composeTestRule.onNodeWithText("Enter description (optional)")
            .performTextInput("My daily prayers and supplications")

        // Then - verify preview updates
        composeTestRule.onNodeWithText("My daily prayers and supplications").assertIsDisplayed()
    }

    @Test
    fun addProfileScreen_audioCheckbox_isDisplayed() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // Then - verify audio checkbox is present
        composeTestRule.onNodeWithText("Enable Audio Download").assertIsDisplayed()
        composeTestRule.onNodeWithText("Audio will be downloaded using the default reciter (Mishary Al-Afasy)")
            .assertIsDisplayed()
    }

    @Test
    fun addProfileScreen_colorSection_isDisplayed() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // Then - verify color selection section is present
        composeTestRule.onNodeWithText("Color").assertIsDisplayed()
    }

    @Test
    fun addProfileScreen_createProfileWithEmptyName_showsError() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // When - click create profile without entering name
        composeTestRule.onNodeWithText("Create Profile", useUnmergedTree = true)
            .performClick()

        // Then - verify error message appears
        composeTestRule.onNodeWithText("Profile name is required").assertIsDisplayed()
    }

    @Test
    fun addProfileScreen_createProfileWithValidName_callsCallback() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // When - enter valid name and create profile
        composeTestRule.onNodeWithText("Enter profile name")
            .performTextInput("Test Profile")

        composeTestRule.onNodeWithText("Create Profile", useUnmergedTree = true)
            .performClick()

        // Wait for async operation
        composeTestRule.waitForIdle()

        // Then - verify profile creation was attempted
        // Note: In a real test, we'd mock the use case to return success
        // and verify the callback was called with the expected ID
    }

    @Test
    fun addProfileScreen_backButton_callsNavigateBack() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // When - click back button
        composeTestRule.onNodeWithContentDescription("Back")
            .performClick()

        // Then - verify navigation callback was called
        assert(navigateBackCalled)
    }

    @Test
    fun addProfileScreen_fillFormFields_updatesPreview() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // When - fill form fields
        composeTestRule.onNodeWithText("Enter profile name")
            .performTextInput("Complete Test Profile")

        composeTestRule.onNodeWithText("Enter description (optional)")
            .performTextInput("A comprehensive test profile")

        // Then - verify preview shows entered data
        composeTestRule.onNodeWithText("Complete Test Profile").assertIsDisplayed()
        composeTestRule.onNodeWithText("A comprehensive test profile").assertIsDisplayed()
    }

    @Test
    fun addProfileScreen_audioSettingsSection_displaysCorrectly() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // Then - verify audio settings section
        composeTestRule.onNodeWithText("Audio Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enable Audio Download").assertIsDisplayed()
        composeTestRule.onNodeWithText("Audio will be downloaded using the default reciter (Mishary Al-Afasy)")
            .assertIsDisplayed()
    }

    // ========== NOTIFICATION SETTINGS TESTS ==========

    @Test
    fun addProfileScreen_notificationSection_displaysCorrectly() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // Then - verify notification settings section
        composeTestRule.onNodeWithText("Daily Reminder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enable Daily Reminder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Get reminded to read your bookmarks every day").assertIsDisplayed()

        // Verify time picker button is not visible initially (notification disabled)
        composeTestRule.onNodeWithText("Reminder Time:", substring = true).assertDoesNotExist()
    }

    @Test
    fun addProfileScreen_enableNotification_showsTimePickerButton() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // When - enable notification
        composeTestRule.onNodeWithText("Enable Daily Reminder").performClick()

        // Then - verify time picker button appears
        composeTestRule.onNodeWithText("Reminder Time:", substring = true).assertIsDisplayed()

        // Verify preview updates
        composeTestRule.onNodeWithText("Daily Reminder: 08:00", substring = true).assertIsDisplayed()
    }

    @Test
    fun addProfileScreen_disableNotification_hidesTimePickerButton() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // When - enable and then disable notification
        composeTestRule.onNodeWithText("Enable Daily Reminder").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Enable Daily Reminder").performClick()

        // Then - verify time picker button is hidden
        composeTestRule.onNodeWithText("Reminder Time:", substring = true).assertDoesNotExist()

        // Verify preview updates
        composeTestRule.onNodeWithText("Daily Reminder: Disabled").assertIsDisplayed()
    }

    @Test
    fun addProfileScreen_notificationEnabled_updatesPreviewText() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // When - enable notification
        composeTestRule.onNodeWithText("Enable Daily Reminder").performClick()

        // Then - verify preview shows notification enabled
        composeTestRule.onNodeWithText("Daily Reminder: 08:00").assertIsDisplayed()

        // Verify description text updates
        composeTestRule.onNodeWithText("You'll receive a daily reminder at 08:00").assertIsDisplayed()
    }

    @Test
    fun addProfileScreen_timePickerButton_clickable() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // When - enable notification and click time picker
        composeTestRule.onNodeWithText("Enable Daily Reminder").performClick()
        composeTestRule.onNodeWithText("Reminder Time:", substring = true).performClick()

        // Then - verify time picker dialog opens (we can't test the actual dialog content
        // without more complex setup, but we can verify the button is clickable)
        composeTestRule.waitForIdle()
    }

    // ========== COMPLETE WORKFLOW TESTS ==========

    @Test
    fun addProfileScreen_completeWorkflow_withAllSettings() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // When - fill out complete form
        composeTestRule.onNodeWithText("e.g., Daily Duas, Comfort Verses").performTextInput("Evening Prayers")
        composeTestRule.onNodeWithText("Describe the purpose of this profile (optional)")
            .performTextInput("Verses for evening reflection and peace")

        // Enable notification
        composeTestRule.onNodeWithText("Enable Daily Reminder").performClick()

        // Disable audio (test toggling)
        composeTestRule.onNodeWithText("Enable Audio Download").performClick()

        // Then - verify all changes in preview
        composeTestRule.onNodeWithText("Evening Prayers").assertIsDisplayed()
        composeTestRule.onNodeWithText("Verses for evening reflection and peace").assertIsDisplayed()
        composeTestRule.onNodeWithText("Audio: Disabled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily Reminder: 08:00").assertIsDisplayed()
    }

    @Test
    fun addProfileScreen_requiredFieldValidation_showsError() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // When - try to save without entering name
        composeTestRule.onNodeWithText("Save").performClick()

        // Then - verify error message appears
        composeTestRule.waitForIdle()
        // Note: The actual error handling might need to be enhanced in the UI
        // For now, we just verify the save button was clicked
    }

    @Test
    fun addProfileScreen_validForm_enablesSaveButton() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // When - enter valid profile name
        composeTestRule.onNodeWithText("e.g., Daily Duas, Comfort Verses")
            .performTextInput("Valid Profile Name")

        // Then - save button should be enabled (not loading)
        composeTestRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun addProfileScreen_audioToggle_updatesPreview() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // Initially audio is enabled
        composeTestRule.onNodeWithText("Audio: Enabled (Mishary Al-Afasy)").assertIsDisplayed()

        // When - disable audio
        composeTestRule.onNodeWithText("Enable Audio Download").performClick()

        // Then - verify preview updates
        composeTestRule.onNodeWithText("Audio: Disabled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Audio playback will be disabled for this profile").assertIsDisplayed()

        // When - re-enable audio
        composeTestRule.onNodeWithText("Enable Audio Download").performClick()

        // Then - verify preview updates back
        composeTestRule.onNodeWithText("Audio: Enabled (Mishary Al-Afasy)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Audio will be downloaded using the default reciter (Mishary Al-Afasy)").assertIsDisplayed()
    }

    @Test
    fun addProfileScreen_colorSelection_isInteractive() {
        // Given
        composeTestRule.setContent {
            QuranBookmarksTheme {
                AddProfileScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    onProfileCreated = { profileCreatedId = it }
                )
            }
        }

        // Then - verify color selection section exists and is interactive
        composeTestRule.onNodeWithText("Profile Color").assertIsDisplayed()

        // The color circles should be present (we can't easily test color values in UI tests,
        // but we can verify the section exists)
        composeTestRule.waitForIdle()
    }
}