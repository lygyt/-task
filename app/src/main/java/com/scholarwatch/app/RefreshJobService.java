package com.scholarwatch.app;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.os.Build;

public class RefreshJobService extends JobService {
  @Override public boolean onStartJob(JobParameters params) {
    new Thread(() -> {
      int added = refresh();
      if (added > 0) notifyIt(added);
      jobFinished(params, false);
    }, "ScholarWatchRefresh").start();
    return true;
  }

  int refresh() {
    try {
      MainActivity helper = new MainActivity();
      helper.p = getSharedPreferences(MainActivity.PREF, MODE_PRIVATE);
      return helper.refreshAll(this)[0];
    } catch (Exception e) {
      return 0;
    }
  }

  @Override public boolean onStopJob(JobParameters params) { return true; }

  void notifyIt(int n) {
    NotificationManager m = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    String ch = "updates";
    if (Build.VERSION.SDK_INT >= 26) {
      m.createNotificationChannel(new NotificationChannel(ch, "学术动态更新", NotificationManager.IMPORTANCE_DEFAULT));
    }
    Intent i = new Intent(this, MainActivity.class);
    PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, ch) : new Notification.Builder(this);
    b.setSmallIcon(android.R.drawable.ic_popup_sync)
      .setContentTitle("ScholarWatch 有新动态")
      .setContentText("发现 " + n + " 条新的学术动态")
      .setContentIntent(pi)
      .setAutoCancel(true);
    m.notify(1001, b.build());
  }
}
