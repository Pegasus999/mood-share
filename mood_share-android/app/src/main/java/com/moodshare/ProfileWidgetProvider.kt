package com.moodshare

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.RemoteViews
import com.moodshare.core.MoodRepository
import com.moodshare.core.PartnerProfile
import com.moodshare.core.Profile
import com.moodshare.core.SettingsStore
import com.moodshare.R
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class ProfileWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            runBlocking(Dispatchers.IO) {
                MoodRepository(context).refreshPartner()
            }
            refreshWidgets(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    companion object {
        private const val DEFAULT_AVATAR =
            "https://placehold.co/300?text=Mood+Share"
        private const val ACTION_REFRESH = "com.moodshare.widget.REFRESH"

        fun updateAppWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_profile)
            // Load partner profile for widget instead of user profile
            val partnerState = loadPartnerState(context)
            val partnerId = partnerState.first
            val partner = partnerState.second
            val hasPartner = !partnerId.isNullOrBlank()

            remoteViews.setViewVisibility(
                R.id.widget_partner_container,
                if (hasPartner) android.view.View.VISIBLE else android.view.View.GONE
            )
            remoteViews.setViewVisibility(
                R.id.widget_empty_state,
                if (hasPartner) android.view.View.GONE else android.view.View.VISIBLE
            )
            remoteViews.setTextViewText(
                R.id.widget_name,
                partner?.name ?: context.getString(R.string.widget_placeholder_name)
            )
            remoteViews.setTextViewText(
                R.id.widget_mood,
                partner?.mood ?: context.getString(R.string.widget_placeholder_mood)
            )

            val avatarBitmap = fetchAvatar(partner?.avatar ?: DEFAULT_AVATAR)
            if (avatarBitmap != null) {
                remoteViews.setImageViewBitmap(R.id.widget_avatar, avatarBitmap)
            } else {
                remoteViews.setImageViewResource(
                    R.id.widget_avatar,
                    R.drawable.ic_avatar_placeholder
                )
            }

            val intent = Intent(context, MainActivity::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)
            remoteViews.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            val refreshIntent = Intent(context, ProfileWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                flags
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent)

            manager.updateAppWidget(widgetId, remoteViews)
        }

        fun refreshWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ProfileWidgetProvider::class.java)
            )
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }
        }

        private fun loadProfile(context: Context): Profile? = runBlocking(Dispatchers.IO) {
            val store = SettingsStore(context.applicationContext)
            val name = store.profileName.firstOrNull()
            val mood = store.profileMood.firstOrNull()
            val avatar = store.profileAvatar.firstOrNull()
            val isFocused = store.profileIsFocused.firstOrNull() ?: false
            if (name.isNullOrBlank() || mood.isNullOrBlank()) {
                null
            } else {
                Profile(name, avatar ?: DEFAULT_AVATAR, mood, isFocused)
            }
        }

        private fun loadPartnerState(
            context: Context
        ): Pair<String?, PartnerProfile?> = runBlocking(Dispatchers.IO) {
            val store = SettingsStore(context.applicationContext)
            val partnerId = store.profilePartnerId.firstOrNull()
            val name = store.partnerName.firstOrNull()
            val mood = store.partnerMood.firstOrNull()
            val avatar = store.partnerAvatar.firstOrNull()
            val isFocused = store.partnerIsFocused.firstOrNull() ?: false
            val partner = if (name.isNullOrBlank() || mood.isNullOrBlank()) {
                null
            } else {
                PartnerProfile(name, avatar ?: DEFAULT_AVATAR, mood, isFocused)
            }
            partnerId to partner
        }

        private fun fetchAvatar(url: String): Bitmap? {
            return try {
                Picasso.get().load(url).resize(128, 128).centerCrop().get()
            } catch (_: Exception) {
                null
            }
        }
    }
}
