package com.salati.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.salati.app.MainActivity
import com.salati.app.R
import com.salati.app.SalatiApp
import com.salati.app.data.SalatiRepository
import com.salati.app.domain.Prayer
import com.salati.app.domain.QadaaMath
import com.salati.app.domain.of
import com.salati.app.domain.total
import com.salati.app.ui.prayerNameRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Notifications {
    const val CH_PRAYER = "prayer_times"
    const val CH_PRAYER_SILENT = "prayer_times_silent"
    const val CH_QADAA = "qadaa_reminders"
    const val CH_QADAA_SILENT = "qadaa_reminders_silent"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        fun ch(id: String, name: Int, importance: Int) = NotificationChannel(id, ctx.getString(name), importance).apply {
            if (importance < NotificationManager.IMPORTANCE_DEFAULT) setSound(null, null)
        }
        nm.createNotificationChannels(
            listOf(
                ch(CH_PRAYER, R.string.channel_prayer, NotificationManager.IMPORTANCE_DEFAULT),
                ch(CH_PRAYER_SILENT, R.string.channel_prayer_silent, NotificationManager.IMPORTANCE_LOW),
                ch(CH_QADAA, R.string.channel_qadaa, NotificationManager.IMPORTANCE_DEFAULT),
                ch(CH_QADAA_SILENT, R.string.channel_qadaa_silent, NotificationManager.IMPORTANCE_LOW),
            )
        )
    }

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, id: Int, channel: String, title: String, text: String) {
        if (!hasPermission(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    /** Context whose resources follow the user's chosen app language (needed below Android 13). */
    fun localized(ctx: Context, language: String): Context {
        val conf = Configuration(ctx.resources.configuration)
        conf.setLocale(Locale.forLanguageTag(language))
        return ctx.createConfigurationContext(conf)
    }
}

object AlarmScheduler {
    const val EXTRA_KIND = "kind"
    const val EXTRA_PRAYER = "prayer"
    const val KIND_PRAYER = "prayer"
    const val KIND_QADAA = "qadaa"
    private const val PRAYER_BASE = 100
    private const val QADAA_BASE = 200
    const val MAX_REMINDERS = 10

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    private fun pending(ctx: Context, code: Int, intent: Intent?): PendingIntent? {
        val i = intent ?: Intent(ctx, AlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (intent == null) PendingIntent.FLAG_NO_CREATE else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(ctx, code, i, flags)
    }

    private fun schedule(ctx: Context, code: Int, at: ZonedDateTime, intent: Intent) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(ctx, code, intent) ?: return
        val ms = at.toInstant().toEpochMilli()
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, ms, 10 * 60 * 1000L, pi)
        } catch (_: SecurityException) {
            am.setWindow(AlarmManager.RTC_WAKEUP, ms, 10 * 60 * 1000L, pi)
        }
    }

    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val codes = (0 until 5).map { PRAYER_BASE + it } + (0 until MAX_REMINDERS).map { QADAA_BASE + it }
        for (c in codes) pending(ctx, c, null)?.let { am.cancel(it); it.cancel() }
    }

    /** Schedules only the next occurrence of each alarm; each alarm reschedules on firing. */
    suspend fun rescheduleAll(ctx: Context, repo: SalatiRepository) {
        cancelAll(ctx)
        val s = repo.getSettings()
        if (!s.setupComplete) return
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        if (s.prayerNotifEnabled) {
            val today = repo.times(s, now.toLocalDate(), zone)
            val tomorrow = repo.times(s, now.toLocalDate().plusDays(1), zone)
            for (p in Prayer.entries) {
                val t = today.of(p).takeIf { it.isAfter(now) } ?: tomorrow.of(p)
                val i = Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_KIND, KIND_PRAYER).putExtra(EXTRA_PRAYER, p.name)
                schedule(ctx, PRAYER_BASE + p.ordinal, t, i)
            }
        }
        if (s.qadaaNotifEnabled) {
            s.reminderTimes().take(MAX_REMINDERS).forEachIndexed { idx, lt ->
                var at = now.toLocalDate().atTime(lt).atZone(zone)
                if (!at.isAfter(now)) at = now.toLocalDate().plusDays(1).atTime(lt).atZone(zone)
                val i = Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_KIND, KIND_QADAA)
                schedule(ctx, QADAA_BASE + idx, at, i)
            }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as SalatiApp
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val repo = app.repository
                val s = repo.getSettings()
                val lc = Notifications.localized(context, s.language)
                when (intent.getStringExtra(AlarmScheduler.EXTRA_KIND)) {
                    AlarmScheduler.KIND_PRAYER -> {
                        val p = Prayer.parse(intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER) ?: "")
                        if (p != null && s.prayerNotifEnabled) {
                            val t = repo.times(s, LocalDate.now()).of(p)
                            val fmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.forLanguageTag(s.language))
                            val name = lc.getString(prayerNameRes(p))
                            Notifications.show(
                                context, 1000 + p.ordinal,
                                if (s.prayerNotifMuted) Notifications.CH_PRAYER_SILENT else Notifications.CH_PRAYER,
                                lc.getString(R.string.notif_prayer_title, name),
                                lc.getString(R.string.notif_prayer_text, name, t.format(fmt)),
                            )
                        }
                    }
                    AlarmScheduler.KIND_QADAA -> if (s.qadaaNotifEnabled) {
                        val entries = repo.entries.first()
                        val done = QadaaMath.madeUpOn(entries, LocalDate.now()).total()
                        val goal = s.dailyTarget().total()
                        Notifications.show(
                            context, 2000,
                            if (s.qadaaNotifMuted) Notifications.CH_QADAA_SILENT else Notifications.CH_QADAA,
                            lc.getString(R.string.notif_qadaa_title),
                            lc.getString(R.string.notif_qadaa_text, done, goal),
                        )
                    }
                }
                repo.runAutoMiss()
                AlarmScheduler.rescheduleAll(context, repo)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Reschedules after reboot, app update, clock/time-zone changes (covers DST) and exact-alarm permission changes. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as SalatiApp
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                AlarmScheduler.rescheduleAll(context, app.repository)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
