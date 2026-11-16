package io.github.mehrdad_abdi.qintar.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.service.ReminderDecision
import io.github.mehrdad_abdi.qintar.domain.service.ReminderKind
import io.github.mehrdad_abdi.qintar.ui.designsystem.Numerals
import java.util.Locale

/** Channels and notifications of the reminders (§5.11): one channel per kind so each can be silenced. */
object ReminderNotifier {
    const val CHANNEL_DAILY = "reminder_daily"
    const val CHANNEL_STREAK = "reminder_streak"

    fun channelOf(kind: ReminderKind) = if (kind == ReminderKind.DAILY) CHANNEL_DAILY else CHANNEL_STREAK
    private fun notificationId(kind: ReminderKind) = 1000 + kind.ordinal

    /** Idempotent; channel names follow the language of the moment. */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DAILY, context.getString(R.string.reminder_channel_daily), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STREAK, context.getString(R.string.reminder_channel_streak), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** The same permission check as the runtime request: below Android 13 there is nothing to ask. */
    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** The text of a decision; null for [ReminderDecision.Skip]. [context] should speak the app's language. */
    fun text(context: Context, decision: ReminderDecision, prefs: UserPrefs): String? {
        val language = if (prefs.latinNumerals) "en" else context.resources.configuration.locales[0].language
        fun n(value: Int) = Numerals.format(value, language)
        fun rank(rank: Rank) = context.getString(RANK_NAMES[rank.ordinal - 1])
        return when (decision) {
            ReminderDecision.Skip -> null
            is ReminderDecision.NothingRead -> context.getString(R.string.reminder_text_nothing_read)
            is ReminderDecision.InProgress -> context.getString(R.string.reminder_text_in_progress, n(decision.total), n(decision.remaining), rank(decision.target))
            is ReminderDecision.StreakAtRisk -> context.getString(R.string.reminder_text_streak, n(decision.remaining))
        }
    }

    /** Posts the reminder; does nothing without the permission. Tapping it opens the app on Today. */
    fun post(context: Context, kind: ReminderKind, text: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val tap = PendingIntent.getActivity(context, kind.ordinal, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelOf(kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(kind), notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check and the call.
        }
    }

    /** [base] with the app's chosen language, since a receiver's context does not follow per-app locales on every version. */
    fun localized(base: Context, language: AppLanguage): Context {
        val tag = when (language) {
            AppLanguage.SYSTEM -> return base
            AppLanguage.ENGLISH -> "en"
            AppLanguage.ARABIC -> "ar"
            AppLanguage.PERSIAN -> "fa"
        }
        val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(configuration)
    }

    private val RANK_NAMES = intArrayOf(
        R.string.rank_short_not_heedless,
        R.string.rank_short_dhakir,
        R.string.rank_short_qanit,
        R.string.rank_short_khashi,
        R.string.rank_short_faiz,
        R.string.rank_short_mujtahid,
        R.string.rank_short_qintar,
    )
}
