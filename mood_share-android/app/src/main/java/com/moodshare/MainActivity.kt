package com.moodshare

import android.app.DownloadManager
import android.content.DialogInterface
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.content.res.Configuration
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.moodshare.core.MoodRepository
import com.moodshare.core.Profile
import com.squareup.picasso.Picasso
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var repository: MoodRepository
    private lateinit var rootContainer: View
    private lateinit var loadingContainer: View
    private lateinit var uploadContainer: View
    private lateinit var registerSection: View
    private lateinit var nameInput: TextInputEditText
    private lateinit var registerButton: MaterialButton
    private lateinit var profileCard: View
    private lateinit var avatarImage: ImageView
    private lateinit var profileName: TextView
    private lateinit var profileMood: TextView
    private lateinit var statusSwitch: SwitchMaterial
    private lateinit var focusMoonIcon: ImageView
    private lateinit var focusSunIcon: ImageView
    private lateinit var taglineText: TextView
    private lateinit var moodBubble: TextView
    private lateinit var partnerHeading: TextView
    private lateinit var selectPartnerButton: MaterialButton
    private lateinit var partnerCard: View
    private lateinit var partnerAvatar: ImageView
    private lateinit var partnerName: TextView
    private lateinit var partnerMood: TextView
    private lateinit var partnerStatus: TextView
    private lateinit var partnerMoodBubble: TextView
    private lateinit var removePartnerButton: MaterialButton
    private lateinit var pickPhotoLauncher: ActivityResultLauncher<String>
    private lateinit var takePhotoLauncher: ActivityResultLauncher<Void?>
    private var suppressSwitchEvents = false
    private var currentProfile: Profile? = null
    private var hasProfile = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore state from savedInstanceState to prevent flash during theme change
        if (savedInstanceState != null) {
            hasProfile = savedInstanceState.getBoolean("hasProfile", false)
        }

        setContentView(R.layout.activity_main)
        repository = MoodRepository(this)

        rootContainer = findViewById(R.id.root_container)
        loadingContainer = findViewById(R.id.loading_container)
        uploadContainer = findViewById(R.id.profile_avatar_progress)
        taglineText = findViewById(R.id.text_tagline)
        registerSection = findViewById(R.id.register_section)
        nameInput = findViewById(R.id.edit_name)
        registerButton = findViewById(R.id.button_register)
        profileCard = findViewById(R.id.profile_card)
        avatarImage = findViewById(R.id.image_avatar)
        profileName = findViewById(R.id.text_name)
        profileMood = findViewById(R.id.text_mood)
        statusSwitch = findViewById(R.id.switch_focus)
        focusMoonIcon = findViewById(R.id.icon_focus_moon)
        focusSunIcon = findViewById(R.id.icon_focus_sun)
        moodBubble = findViewById(R.id.text_mood_bubble)
        partnerHeading = findViewById(R.id.text_partner_heading)
        selectPartnerButton = findViewById(R.id.button_select_partner)
        partnerCard = findViewById(R.id.partner_card)
        partnerAvatar = findViewById(R.id.image_partner_avatar)
        partnerName = findViewById(R.id.text_partner_name)
        partnerMood = findViewById(R.id.text_partner_mood)
        partnerStatus = findViewById(R.id.text_partner_status)
        partnerMoodBubble = findViewById(R.id.text_partner_mood_bubble)
        removePartnerButton = findViewById(R.id.button_remove_partner)

        pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                lifecycleScope.launch { uploadAvatarFromUri(it) }
            }
        }

        takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            bitmap?.let {
                lifecycleScope.launch { uploadAvatarFromBitmap(it) }
            }
        }

        registerButton.setOnClickListener {
            val input = nameInput.text.toString().trim()
            if (input.isEmpty()) {
                Toast.makeText(this, R.string.register_error_empty, Toast.LENGTH_SHORT).show()
            } else {
                lifecycleScope.launch { performRegistration(input) }
            }
        }

        statusSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSwitchEvents) return@setOnCheckedChangeListener
            lifecycleScope.launch { applyFocusUpdate(isChecked) }
        }

        moodBubble.setOnClickListener {
            showMoodDialog()
        }

        avatarImage.setOnClickListener {
            showPhotoPicker()
        }

        partnerAvatar.setOnClickListener {
            showPartnerDownloadDialog()
        }

        selectPartnerButton.setOnClickListener {
            showPartnerSearchDialog()
        }

        removePartnerButton.setOnClickListener {
            lifecycleScope.launch { removePartner() }
        }

        setLoadingState(true)
        updateFocusIcons(false)

        lifecycleScope.launch { loadSavedProfile() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("hasProfile", hasProfile)
    }

    private suspend fun performRegistration(name: String) {
        try {
            val profile = repository.register(name)
            showRegisteredUi(profile)
            Toast.makeText(this, getString(R.string.register_success, profile.name), Toast.LENGTH_SHORT).show()
            ProfileWidgetProvider.refreshWidgets(this)
        } catch (err: Exception) {
            Toast.makeText(this, err.localizedMessage ?: err.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun loadSavedProfile() {
        setLoadingState(true)
        try {
            val profile = repository.refreshProfile()
            if (profile != null) {
                showRegisteredUi(profile)
            } else {
                hasProfile = false
            }
        } catch (err: Exception) {
            hasProfile = false
            Toast.makeText(this, err.localizedMessage ?: err.toString(), Toast.LENGTH_SHORT).show()
        } finally {
            setLoadingState(false)
            if (!hasProfile) {
                registerSection.visibility = View.VISIBLE
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) {
            registerSection.visibility = View.GONE
            profileCard.visibility = View.GONE
            partnerHeading.visibility = View.GONE
            selectPartnerButton.visibility = View.GONE
            partnerCard.visibility = View.GONE
        }
    }

    private fun setUploadingState(isUploading: Boolean) {
        uploadContainer.visibility = if (isUploading) View.VISIBLE else View.GONE
        avatarImage.isEnabled = !isUploading
        if (isUploading) {
            avatarImage.alpha = 0.6f
        } else {
            avatarImage.alpha = 1f
        }
    }

    private fun showRegisteredUi(profile: Profile) {
        hasProfile = true
        registerSection.visibility = View.GONE
        nameInput.isEnabled = false
        taglineText.visibility = View.GONE
        displayProfile(profile)
    }

    private fun displayProfile(profile: Profile) {
        profileCard.visibility = View.VISIBLE
        profileName.text = profile.name
        profileMood.text = if (profile.isFocused) {
            getString(R.string.switch_focused_label)
        } else {
            getString(R.string.switch_chilling_label)
        }
        moodBubble.text = profile.mood
        updateMoodBubble(moodBubble, profile.isFocused)
        currentProfile = profile
        if (profile.avatar.isBlank()) {
            avatarImage.setImageResource(R.drawable.ic_avatar_placeholder)
        } else {
            Picasso.get()
                .load(profile.avatar)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .error(R.drawable.ic_avatar_placeholder)
                .into(avatarImage)
        }
        setSwitchState(profile.isFocused)
        applyThemeForFocus(profile.isFocused)
        updateFocusIcons(profile.isFocused)
        displayPartner(profile)
    }

    private fun displayPartner(profile: Profile) {
        partnerHeading.visibility = View.VISIBLE
        if (profile.partner != null) {
            selectPartnerButton.visibility = View.GONE
            partnerCard.visibility = View.VISIBLE
            partnerName.text = profile.partner.name
            partnerMood.text = profile.partner.mood
            partnerMoodBubble.text = profile.partner.mood
            updateMoodBubble(partnerMoodBubble, profile.partner.isFocused)
            partnerStatus.text = if (profile.partner.isFocused) {
                getString(R.string.switch_focused_label)
            } else {
                getString(R.string.switch_chilling_label)
            }
            if (profile.partner.avatar.isBlank()) {
                partnerAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
            } else {
                Picasso.get()
                    .load(profile.partner.avatar)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .into(partnerAvatar)
            }
        } else {
            selectPartnerButton.visibility = View.VISIBLE
            partnerCard.visibility = View.GONE
        }
    }

    private fun showMoodDialog() {
        val prefill = currentProfile?.mood ?: ""
        val input = EditText(this).apply {
            hint = getString(R.string.mood_dialog_hint)
            setText(prefill)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.mood_dialog_title)
            .setView(input as View)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                val mood = input.text.toString().trim()
                if (mood.isNotEmpty()) {
                    lifecycleScope.launch { applyMoodUpdate(mood) }
                } else {
                    Toast.makeText(this, R.string.mood_dialog_empty, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private suspend fun applyMoodUpdate(mood: String) {
        try {
            val profile = repository.updateMood(mood)
            if (profile != null) {
                displayProfile(profile)
                Toast.makeText(this, R.string.update_success, Toast.LENGTH_SHORT).show()
                ProfileWidgetProvider.refreshWidgets(this)
            } else {
                Toast.makeText(this, R.string.update_failure, Toast.LENGTH_SHORT).show()
            }
        } catch (err: Exception) {
            Toast.makeText(this, err.localizedMessage ?: err.toString(), Toast.LENGTH_SHORT).show()
        }
    }


    private fun setSwitchState(isFocused: Boolean) {
        suppressSwitchEvents = true
        statusSwitch.isChecked = isFocused
        suppressSwitchEvents = false
    }

    private fun applyThemeForFocus(isFocused: Boolean) {
        // Theme is controlled by focus status:
        // FOCUSED (true) = Dark theme
        // CHILLING (false) = Light theme
        if (isFocused) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun updateFocusIcons(isFocused: Boolean) {
        val activeColor = if (isFocused) {
            getColor(R.color.moodshare_dark_accent)
        } else {
            getColor(R.color.moodshare_light_accent)
        }
        val inactiveColor = if (isFocused) {
            getColor(R.color.moodshare_icon_muted_dark)
        } else {
            getColor(R.color.moodshare_icon_muted_light)
        }

        focusMoonIcon.setColorFilter(if (isFocused) activeColor else inactiveColor)
        focusSunIcon.setColorFilter(if (isFocused) inactiveColor else activeColor)
    }

    private fun updateMoodBubble(bubble: TextView, isFocused: Boolean) {
        if (isFocused) {
            bubble.setBackgroundResource(R.drawable.bg_mood_bubble_active)
            bubble.setTextColor(getColor(R.color.glass_bubble_active_text))
        } else {
            bubble.setBackgroundResource(R.drawable.bg_mood_bubble_inactive)
            bubble.setTextColor(getColor(R.color.glass_bubble_inactive_text))
        }
    }

    private suspend fun applyFocusUpdate(isFocused: Boolean) {
        try {
            val profile = repository.updateFocus(isFocused)
            if (profile != null) {
                // Update only focus-related UI, don't override the mood
                currentProfile = profile
                setSwitchState(profile.isFocused)
                applyThemeForFocus(profile.isFocused)
                updateFocusIcons(profile.isFocused)
                profileMood.text = if (profile.isFocused) {
                    getString(R.string.switch_focused_label)
                } else {
                    getString(R.string.switch_chilling_label)
                }
                updateMoodBubble(moodBubble, profile.isFocused)
                Toast.makeText(this, R.string.update_success, Toast.LENGTH_SHORT).show()
                ProfileWidgetProvider.refreshWidgets(this)
            } else {
                Toast.makeText(this, R.string.update_failure, Toast.LENGTH_SHORT).show()
            }
        } catch (err: Exception) {
            Toast.makeText(this, err.localizedMessage ?: err.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPartnerSearchDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.search_partner_hint)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.search_partner_title)
            .setView(input as View)
            .setPositiveButton(R.string.register_button_label) { _: DialogInterface, _: Int ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    lifecycleScope.launch { searchAndSelectPartner(query) }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
    }

    private suspend fun searchAndSelectPartner(query: String) {
        try {
            val results = repository.searchUsers(query)
            if (results.isEmpty()) {
                Toast.makeText(this, R.string.no_results, Toast.LENGTH_SHORT).show()
                return
            }

            // Show selection dialog
            val names = results.map { it.name }.toTypedArray()
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.select_partner)
                    .setItems(names) { _: DialogInterface, which: Int ->
                        lifecycleScope.launch {
                            setPartner(results[which].name)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        } catch (err: Exception) {
            Toast.makeText(this, err.localizedMessage ?: err.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun setPartner(partnerId: String) {
        try {
            val profile = repository.setPartner(partnerId)
            if (profile != null) {
                displayProfile(profile)
                Toast.makeText(this, R.string.partner_set_success, Toast.LENGTH_SHORT).show()
                ProfileWidgetProvider.refreshWidgets(this)
            } else {
                Toast.makeText(this, R.string.update_failure, Toast.LENGTH_SHORT).show()
            }
        } catch (err: Exception) {
            Toast.makeText(this, err.localizedMessage ?: err.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun removePartner() {
        try {
            val profile = repository.setPartner(null)
            if (profile != null) {
                displayProfile(profile)
                Toast.makeText(this, R.string.partner_removed, Toast.LENGTH_SHORT).show()
                ProfileWidgetProvider.refreshWidgets(this)
            } else {
                Toast.makeText(this, R.string.update_failure, Toast.LENGTH_SHORT).show()
            }
        } catch (err: Exception) {
            Toast.makeText(this, err.localizedMessage ?: err.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPhotoPicker() {
        if (uploadContainer.visibility == View.VISIBLE) {
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.change_photo_title)
            .setMessage(R.string.change_photo_message)
            .setPositiveButton(R.string.photo_source_camera) { _: DialogInterface, _: Int ->
                takePhotoLauncher.launch(null)
            }
            .setNegativeButton(R.string.photo_source_gallery) { _: DialogInterface, _: Int ->
                pickPhotoLauncher.launch("image/*")
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPartnerDownloadDialog() {
        val avatarUrl = currentProfile?.partner?.avatar
        if (avatarUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.update_failure, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.partner_photo_download_title)
            .setMessage(R.string.partner_photo_download_message)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                enqueuePartnerDownload(avatarUrl)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun enqueuePartnerDownload(url: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setTitle(getString(R.string.partner_photo_download_title))
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "moodshare_partner.jpg")

        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
    }

    private suspend fun uploadAvatarFromUri(uri: Uri) {
        val resolver = applicationContext.contentResolver
        val mimeType = resolver.getType(uri) ?: "image/jpeg"
        val displayName = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else null
        }
        val fileName = displayName ?: "avatar.${mimeType.substringAfterLast('/')}"

        val bytes = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }

        if (bytes == null || bytes.isEmpty()) {
            Toast.makeText(this, R.string.update_failure, Toast.LENGTH_SHORT).show()
            return
        }

        uploadAvatarBytes(fileName, bytes, mimeType)
    }

    private suspend fun uploadAvatarFromBitmap(bitmap: Bitmap) {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        val bytes = output.toByteArray()
        if (bytes.isEmpty()) {
            Toast.makeText(this, R.string.update_failure, Toast.LENGTH_SHORT).show()
            return
        }
        uploadAvatarBytes("avatar-camera.jpg", bytes, "image/jpeg")
    }

    private suspend fun uploadAvatarBytes(fileName: String, bytes: ByteArray, mimeType: String) {
        setUploadingState(true)
        try {
            val profile = repository.uploadAvatar(fileName, bytes, mimeType)
            if (profile != null) {
                displayProfile(profile)
                Toast.makeText(this, R.string.update_success, Toast.LENGTH_SHORT).show()
                ProfileWidgetProvider.refreshWidgets(this)
            } else {
                Toast.makeText(this, R.string.update_failure, Toast.LENGTH_SHORT).show()
            }
        } catch (err: Exception) {
            Toast.makeText(this, err.localizedMessage ?: err.toString(), Toast.LENGTH_SHORT).show()
        } finally {
            setUploadingState(false)
        }
    }
}
