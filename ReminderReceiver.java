package fr.mesoeufs;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/** Rappel quotidien « As-tu noté tes œufs ? ». */
public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "rappel";
    static final String ACTION_COUVEE = "fr.mesoeufs.COUVEE";

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || "android.intent.action.MY_PACKAGE_REPLACED".equals(action)) {
            schedule(c);
            scheduleCouvees(c);
            return;
        }
        if (ACTION_COUVEE.equals(action)) {
            show(c, intent.getStringExtra("title"), intent.getStringExtra("text"), 100 + intent.getIntExtra("n", 0));
            return;
        }
        show(c, "Mes œufs 🥚", "As-tu noté ta récolte d'aujourd'hui ?", 1);
    }

    static void show(Context c, String title, String text, int id) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Rappel quotidien", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(id, n);
    }

    static void schedule(Context c) {
        SharedPreferences p = c.getSharedPreferences("rappel", Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction("fr.mesoeufs.RAPPEL");
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        if (!p.getBoolean("on", false)) return;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, p.getInt("h", 19));
        cal.set(Calendar.MINUTE, p.getInt("m", 0));
        cal.set(Calendar.SECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_MONTH, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    /** Alertes de couvée : une notification ponctuelle par étape (liste JSON envoyée par l'appli). */
    static void scheduleCouvees(Context c) {
        SharedPreferences p = c.getSharedPreferences("rappel", Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        int old = p.getInt("cv_count", 0);
        for (int i = 0; i < old; i++) {
            Intent it = new Intent(c, ReminderReceiver.class);
            it.setAction(ACTION_COUVEE);
            am.cancel(PendingIntent.getBroadcast(c, 1000 + i, it, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        int count = 0;
        try {
            JSONArray arr = new JSONArray(p.getString("cv", "[]"));
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length() && count < 40; i++) {
                JSONObject o = arr.getJSONObject(i);
                long t = o.getLong("t");
                if (t <= now) continue;
                Intent it = new Intent(c, ReminderReceiver.class);
                it.setAction(ACTION_COUVEE);
                it.putExtra("title", o.optString("title", "Couvée"));
                it.putExtra("text", o.optString("text", ""));
                it.putExtra("n", count);
                PendingIntent pi = PendingIntent.getBroadcast(c, 1000 + count, it, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                count++;
            }
        } catch (Exception ignored) { }
        p.edit().putInt("cv_count", count).apply();
    }
}
