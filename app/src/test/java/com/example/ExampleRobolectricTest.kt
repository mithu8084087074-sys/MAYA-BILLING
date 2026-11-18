package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Maya Bill App", appName)
    }

    @Test
    fun `main activity launches cleanly and displays login options and google sign in`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("login_mode_gmail").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login_mode_phone").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login_mode_gst").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("login_mode_phone").performClick()
        composeRule.onNodeWithTag("login_phone_input").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("login_mode_gst").performClick()
        composeRule.onNodeWithTag("login_gstin_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login_gstin_input").performTextInput("29ABCDE1234F1Z5")
        composeRule.waitForIdle()
    }

    @Test
    fun `pdf generator enforces Maya Bill App side branding`() {
        assertEquals("MAYA BILL APP", com.example.util.PdfInvoiceGenerator.PDF_SIDE_BRANDING)
    }
}
