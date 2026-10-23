package fr.nicoos.alerte;

import android.Manifest;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class AlertJobService extends JobService {
 static final String CHANNEL_ID="nico_os_alerts";
 static final int NOTIF_ID=2301;

 @Override public boolean onStartJob(JobParameters params){
  new Thread(()->{
   try{checkAndNotify(getApplicationContext(),false);}catch(Exception ignored){}
   jobFinished(params,false);
  }).start();
  return true;
 }

 @Override public boolean onStopJob(JobParameters params){return true;}

 static String checkAndNotify(Context context,boolean force)throws Exception{
  SharedPreferences prefs=context.getSharedPreferences("nico_local",Context.MODE_PRIVATE);
  String base=prefs.getString("base_url",""),key=prefs.getString("link_key","");
  if(base==null||base.isEmpty()||key==null||key.isEmpty())return "Connexion Nico OS non configurée.";

  JSONObject root=new JSONObject(request(base,key));
  if(root.optBoolean("empty",false))return "Aucune tâche en attente.";
  JSONObject t=root.getJSONObject("task");

  long id=t.optLong("id",-1);
  String due=t.optString("due","").trim();
  String title=t.optString("title","Tâche");
  String project=t.optString("project","Nico OS");
  String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());

  if(!force && (due.isEmpty()||due.compareTo(today)>0))return "Aucune tâche due aujourd'hui.";

  String signature=id+"|"+due+"|"+today;
  if(!force && signature.equals(prefs.getString("last_notified_signature","")))
   return "Alerte déjà envoyée aujourd'hui.";

  if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
   return "Autorise les notifications Nico OS Alerte.";

  NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
  if(Build.VERSION.SDK_INT>=26){
   NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"Nico OS Alertes",NotificationManager.IMPORTANCE_HIGH);
   ch.setDescription("Tâches Nico OS dues ou en retard");
   nm.createNotificationChannel(ch);
  }

  Intent open=new Intent(context,MainActivity.class);
  open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  PendingIntent pi=PendingIntent.getActivity(context,2301,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

  String state;
  if(due.isEmpty())state="PROCHAINE TÂCHE";
  else if(due.compareTo(today)<0)state="EN RETARD";
  else if(due.equals(today))state="À FAIRE AUJOURD'HUI";
  else state="PROCHAINE TÂCHE";

  String detail=(project==null||project.isEmpty()?"Nico OS":project)+" • "+title;
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL_ID):new Notification.Builder(context);
  b.setSmallIcon(R.drawable.nico_os_alerte)
   .setContentTitle("Nico OS • "+state)
   .setContentText(detail)
   .setStyle(new Notification.BigTextStyle().bigText(detail))
   .setContentIntent(pi)
   .setAutoCancel(true)
   .setOnlyAlertOnce(true);

  nm.notify(NOTIF_ID,b.build());
  if(!force)prefs.edit().putString("last_notified_signature",signature).apply();
  return "Notification Nico OS envoyée ✓";
 }

 static String request(String base,String key)throws Exception{
  String u=base;
  while(u.endsWith("/"))u=u.substring(0,u.length()-1);
  HttpURLConnection c=(HttpURLConnection)new URL(u+"/next").openConnection();
  c.setRequestMethod("GET");
  c.setConnectTimeout(8000);
  c.setReadTimeout(12000);
  c.setRequestProperty("X-Nico-Key",key);
  int code=c.getResponseCode();
  InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
  String raw=readAll(in);
  if(code<200||code>=300){
   try{throw new Exception(new JSONObject(raw).optString("error","HTTP "+code));}
   catch(JSONException e){throw new Exception("HTTP "+code);}
  }
  return raw;
 }

 static String readAll(InputStream in)throws Exception{
  if(in==null)return "";
  BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
  StringBuilder s=new StringBuilder();String line;
  while((line=r.readLine())!=null)s.append(line);
  return s.toString();
 }
}
