package com.moodshare

import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Outline
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.moodshare.core.MoodRepository
import com.moodshare.core.Profile
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var repository: MoodRepository
    private lateinit var rootContainer: View
    private lateinit var registerSection: View
    private lateinit var nameInput: TextInputEditText
    private lateinit var registerButton: MaterialButton
    private lateinit var profileCard: View
    private lateinit var avatarImage: ImageView
    private lateinit var profileName: TextView
    private lateinit var profileMood: TextView
    private lateinit var statusSwitch: SwitchMaterial
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
        taglineText = findViewById(R.id.text_tagline)
        registerSection = findViewById(R.id.register_section)
        nameInput = findViewById(R.id.edit_name)
        registerButton = findViewById(R.id.button_register)
        profileCard = findViewById(R.id.profile_card)
        avatarImage = findViewById(R.id.image_avatar)
        profileName = findViewById(R.id.text_name)
        profileMood = findViewById(R.id.text_mood)
        statusSwitch = findViewById(R.id.switch_focus)
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

        // Make avatars circular
        avatarImage.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        avatarImage.clipToOutline = true
        partnerAvatar.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        partnerAvatar.clipToOutline = true

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

        selectPartnerButton.setOnClickListener {
            showPartnerSearchDialog()
        }

        removePartnerButton.setOnClickListener {
            lifecycleScope.launch { removePartner() }
        }

        // If we have a profile from saved state, hide register section immediately
        if (hasProfile) {
            registerSection.visibility = View.GONE
            profileCard.visibility = View.VISIBLE
        }

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
        val profile = repository.refreshProfile()
        if (profile != null) {
            showRegisteredUi(profile)
        }
    }

    private fun showRegisteredUi(profile: Profile) {
        hasProfile = true
        registerSection.visibility = View.GONE
        nameInput.isEnabled = false
        taglineText.text = getString(R.string.register_saved_hint, profile.name)
        displayProfile(profile)
    }

    private fun displayProfile(profile: Profile) {
        profileCard.visibility = View.VISIBLE
        profileName.text = profile.name
        profileMood.text = profile.mood
        moodBubble.text = profile.mood
        currentProfile = profile
        Picasso.get()
            .load(profile.avatar)
            .placeholder(R.drawable.ic_avatar_placeholder)
            .into(avatarImage)
        setSwitchState(profile.isFocused)
        applyThemeForFocus(profile.isFocused)
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
            partnerStatus.text = if (profile.partner.isFocused) {
                getString(R.string.switch_focused_label)
            } else {
                getString(R.string.switch_chilling_label)
            }
            Picasso.get()
                .load(profile.partner.avatar)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .into(partnerAvatar)
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

    private suspend fun applyFocusUpdate(isFocused: Boolean) {
        try {
            val profile = repository.updateFocus(isFocused)
            if (profile != null) {
                // Update only focus-related UI, don't override the mood
                currentProfile = profile
                setSwitchState(profile.isFocused)
                applyThemeForFocus(profile.isFocused)
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
}
