package com.focuslock.mdm

import android.net.Uri

data class VideoItem(
    val uri        : Uri,
    val name       : String,
    val fileName   : String,
    val isUnlocked : Boolean
)