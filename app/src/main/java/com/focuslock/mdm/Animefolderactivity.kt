package com.focuslock.mdm

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * One-time folder picker.
 *
 * Shown only on the very first launch of VideoLibraryActivity.
 * After the user confirms a folder it is saved permanently by VideoManager
 * and this activity is never shown again.
 *
 * The name "AnimeFolderActivity" is kept to match the existing manifest entry.
 */
class AnimeFolderActivity : AppCompatActivity() {

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null) {
                // User pressed back without choosing
                Toast.makeText(this, "You must pick a folder to continue.", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            // Show the chosen path before locking it in
            val path = uri.lastPathSegment ?: uri.toString()
            pendingUri = uri
            findViewById<TextView>(R.id.tvChosenPath).text = "Chosen: $path"
            findViewById<Button>(R.id.btnConfirm).isEnabled = true
        }

    private var pendingUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anime_folder)

        // If already selected somehow, just finish
        if (VideoManager.isFolderSelected(this)) {
            finish()
            return
        }

        findViewById<Button>(R.id.btnPickFolder).setOnClickListener {
            folderPicker.launch(null)
        }

        findViewById<Button>(R.id.btnConfirm).apply {
            isEnabled = false
            setOnClickListener {
                val uri = pendingUri ?: return@setOnClickListener
                VideoManager.setFolder(this@AnimeFolderActivity, uri)
                Toast.makeText(this@AnimeFolderActivity,
                    "Folder locked in. This cannot be changed.", Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.syncLockTaskState(this)
    }

    // Prevent back from escaping the picker on first launch
    @Deprecated("Deprecated in API 33")
    override fun onBackPressed() {
        Toast.makeText(this, "You must select a folder first.", Toast.LENGTH_SHORT).show()
    }
}