/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.gaurav.avnc.model.ServerProfile

/**
 * ViewModel for profile editor
 */
class EditorViewModel(app: Application, state: SavedStateHandle, initialProfile: ServerProfile) : BaseViewModel(app) {

    /**
     * Profile being edited
     */
    val profile = state["profile"] ?: initialProfile.copy()

    init {
        state["profile"] = profile
    }

    /**
     * While most fields of [profile] are straightforward to edit, some require
     * more complex handling, and live feedback in UI.
     * For these, we have to use dedicated LiveData fields.
     */
    val useRawEncoding = state.getLiveData("useRawEncoding", profile.useRawEncoding)
    val enableWol = state.getLiveData("enableWol", profile.enableWol)

    fun prepareProfileForSave(): ServerProfile {
        profile.useRawEncoding = useRawEncoding.value == true
        profile.enableWol = enableWol.value == true
        profile.channelType = ServerProfile.CHANNEL_TCP
        return profile
    }
}