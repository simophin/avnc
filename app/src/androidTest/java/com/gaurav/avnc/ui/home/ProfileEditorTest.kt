/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.CleanPrefsRule
import com.gaurav.avnc.EmptyDatabaseRule
import com.gaurav.avnc.R
import com.gaurav.avnc.TestServer
import com.gaurav.avnc.checkIsDisplayed
import com.gaurav.avnc.checkWillBeDisplayed
import com.gaurav.avnc.checkWillBeHidden
import com.gaurav.avnc.doClick
import com.gaurav.avnc.doTypeText
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.targetPrefs
import org.hamcrest.core.AllOf.allOf
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasicEditorTest {

    private val testProfile = ServerProfile(name = "foo", host = "bar")

    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)

    @Rule
    @JvmField
    val dbRule = EmptyDatabaseRule()

    @Rule
    @JvmField
    val prefRule = CleanPrefsRule()

    @Test
    fun createSimpleProfile() {
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        onView(withText(R.string.title_add_server_profile)).inRoot(isDialog()).checkIsDisplayed()
        onView(withHint(R.string.hint_server_name)).doTypeText(testProfile.name)
        onView(withHint(R.string.hint_host)).doTypeText(testProfile.host)
        closeSoftKeyboard()
        onView(withText(R.string.title_save)).doClick()

        onView(withText(R.string.msg_server_profile_added)).checkWillBeDisplayed()
        onView(allOf(
                withParent(withId(R.id.servers)),
                hasDescendant(withText(testProfile.name)),
                hasDescendant(withText(testProfile.host)))
        ).checkWillBeDisplayed()
    }

    private fun checkAdvancedModeIsOpen() {
        //This checkbox is only shown in advanced mode
    }

    @Test
    fun createAdvancedProfile() {
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        onView(withText(R.string.title_advanced)).doClick()
        checkAdvancedModeIsOpen()
        onView(withHint(R.string.hint_server_name)).doTypeText(testProfile.name)
        onView(withHint(R.string.hint_host)).doTypeText(testProfile.host)
        closeSoftKeyboard()
        onView(withText(R.string.title_save)).doClick()

        onView(withText(R.string.msg_server_profile_added)).checkWillBeDisplayed()
        onView(allOf(
                withParent(withId(R.id.servers)),
                hasDescendant(withText(testProfile.name)),
                hasDescendant(withText(testProfile.host)))
        ).checkWillBeDisplayed()
    }

    @Test
    fun dataSharingBetweenSimpleAndAdvanceMode() {
        // If user switches to advanced mode after making some changes in simple mode,
        // those changes should not be lost.
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        onView(withHint(R.string.hint_server_name)).doTypeText(testProfile.name)
        onView(withHint(R.string.hint_host)).doTypeText(testProfile.host)

        closeSoftKeyboard()
        onView(withText(R.string.title_advanced)).doClick()

        checkAdvancedModeIsOpen()
        onView(allOf(withHint(R.string.hint_host), withText(testProfile.host))).checkIsDisplayed()
        onView(allOf(withHint(R.string.hint_server_name), withText(testProfile.name))).checkIsDisplayed()
    }

    @Test
    fun directlyOpenAdvancedMode() {
        // open advanced mode
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        closeSoftKeyboard()
        onView(withText(R.string.title_advanced)).doClick()
        checkAdvancedModeIsOpen()

        // set as default
        Espresso.openActionBarOverflowOrOptionsMenu(targetContext)
        onView(withText(R.string.title_always_show_advanced_editor)).doClick()
        Espresso.pressBack()

        // it should now open by default
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        checkAdvancedModeIsOpen()
        Assert.assertTrue(targetPrefs.getBoolean("prefer_advanced_editor", false))
    }

    @Test
    fun tryProfileBeforeSaving() {
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        closeSoftKeyboard()
        onView(withText(R.string.title_advanced)).doClick()
        checkAdvancedModeIsOpen()

        val server = TestServer()
        server.start()
        onView(withHint(R.string.hint_host)).doTypeText(server.host)
        onView(withId(R.id.port)).perform(ViewActions.clearText()).doTypeText(server.port.toString())
        onView(withText(R.string.title_try)).doClick()

        onView(withId(R.id.frame_view)).checkWillBeDisplayed()
        onView(withId(R.id.status_container)).checkWillBeHidden()

        Espresso.pressBack()
        onView(withText(R.string.title_try)).checkWillBeDisplayed()
        checkAdvancedModeIsOpen()
    }
}
