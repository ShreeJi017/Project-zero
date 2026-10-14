package com.zerogravity.assistant.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zerogravity.assistant.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZeroGravityNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationOpensAllDestinations() {
        composeRule.onNodeWithText("Your space,")
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Settings navigation").performClick()
        composeRule.onNodeWithText("A considered setup for your assistant.").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Personalisation navigation").performClick()
        composeRule.onNodeWithText("Shape the way Zero feels to you.").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("History navigation").performClick()
        composeRule.onNodeWithText("Conversation history is planned").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Home navigation").performClick()
        composeRule.onNodeWithText("Your space,")
            .assertIsDisplayed()
    }

    @Test
    fun homeShowsManualVoiceControlAndMicrophonePrivacyNotice() {
        composeRule.onNodeWithContentDescription("Start offline voice input").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Microphone is used only after you tap. Speech is processed by an installed on-device recognizer; no online fallback is used.",
        ).assertIsDisplayed()
    }

    @Test
    fun selectedDestinationSurvivesActivityRecreationAndBackReturnsHome() {
        composeRule.onNodeWithContentDescription("Settings navigation").performClick()
        composeRule.onNodeWithText("A considered setup for your assistant.").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("A considered setup for your assistant.").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your space,")
            .assertIsDisplayed()
    }

    @Test
    fun appearancePreferencePersistsAcrossActivityRecreation() {
        composeRule.onNodeWithContentDescription("Settings navigation").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Light").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Light").performClick()
        composeRule.onNodeWithText("Light").assertIsSelected()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Light").assertIsSelected()
        composeRule.onNodeWithText("System").performClick()
        composeRule.onNodeWithText("System").assertIsSelected()
    }
}
