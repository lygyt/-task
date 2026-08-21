package com.scholarwatch.app;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.os.Build;

public class TalentRefreshJobService extends JobService {
  @Override public boolean onStartJob(JobParameters params){
    new Thread(()->{
      int[] r=TalentRadarActivity.runOfficialScan(this);
      if(r[0]>0||r[1]>0)notifyIt(r[0],r[1]);
      jobFinished(params,false);
    },"TalentRadarRefresh").start();
    return true;
  }
  @Override public boolean onStopJob(JobParameters params){return true;}
  void notifyIt(int events,int contacts){
    NotificationManager m=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);String ch="talent_updates";
    if(Build.VERSION.SDK_INT>=26)m.createNotificationChannel(new NotificationChannel(ch,"人才与基金动态",NotificationManager.IMPORTANCE_DEFAULT));
    Intent i=new Intent(this,TalentRadarActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,ch):new Notification.Builder(this);
    b.setSmallIcon(android.R.drawable.ic_popup_sync).setContentTitle("ScholarWatch 人才雷达更新").setContentText("新增官方动态 "+events+" 条，人才联系人 "+contacts+" 位").setContentIntent(pi).setAutoCancel(true);
    m.notify(1002,b.build());
  }
}
