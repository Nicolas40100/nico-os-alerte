package fr.nicoos.alerte;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 static final int BG=Color.rgb(8,13,25), PANEL=Color.rgb(17,26,47), TEXT=Color.rgb(247,248,255), MUTED=Color.rgb(154,168,199), CYAN=Color.rgb(56,200,255), GREEN=Color.rgb(53,208,127), ORANGE=Color.rgb(255,159,67), RED=Color.rgb(255,85,114), SECONDARY=Color.rgb(38,55,93);
 final ExecutorService exec=Executors.newSingleThreadExecutor();
 SharedPreferences prefs;
 LinearLayout root,setupBox,appBox,followBox;
 TextView urgency,project,task,date,status,msg,connection;
 EditText serverInput,keyInput,nextTask;
 Button nextDateButton;
 String nextDate="",baseUrl="",linkKey="";
 long currentTaskId=-1,currentProjectId=-1;
 String currentPriority="Moyenne";

 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  prefs=getSharedPreferences("nico_local",MODE_PRIVATE);
  baseUrl=prefs.getString("base_url","");
  linkKey=prefs.getString("link_key","");
  buildUi();
  if(baseUrl.isEmpty()||linkKey.isEmpty()) showSetup(); else {showApp();loadTask();}
 }

 TextView tv(String s,int sp,int c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);v.setPadding(4,10,4,10);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
 Button btn(String s,int c){Button b=new Button(this);b.setText(s);b.setTextColor(c==GREEN?Color.rgb(6,19,12):Color.WHITE);b.setBackgroundColor(c);return b;}
 EditText input(String h){EditText e=new EditText(this);e.setHint(h);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setBackgroundColor(Color.rgb(24,35,61));e.setPadding(18,14,18,14);return e;}

 void buildUi(){
  ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,44,28,44);sc.addView(root);setContentView(sc);

  setupBox=new LinearLayout(this);setupBox.setOrientation(LinearLayout.VERTICAL);setupBox.setPadding(24,24,24,24);setupBox.setBackgroundColor(PANEL);
  setupBox.addView(tv("◉  NICO OS ALERTE",25,CYAN,true));
  setupBox.addView(tv("Connexion directe à Nico OS local",16,MUTED,false));
  setupBox.addView(tv("Sur le PC, lance Nico OS puis DEMARRER_PONT_MOBILE.bat. Recopie ici l'adresse et la clé affichées.",14,TEXT,false));
  serverInput=input("Adresse du PC — ex. http://192.168.1.42:8766");
  keyInput=input("Clé de liaison");
  serverInput.setText(baseUrl);keyInput.setText(linkKey);
  setupBox.addView(serverInput);setupBox.addView(keyInput);
  Button connect=btn("CONNECTER À NICO OS",GREEN);setupBox.addView(connect);
  msg=tv("",13,MUTED,false);setupBox.addView(msg);
  connect.setOnClickListener(v->saveConnection());
  root.addView(setupBox);

  appBox=new LinearLayout(this);appBox.setOrientation(LinearLayout.VERTICAL);appBox.setPadding(24,24,24,24);appBox.setBackgroundColor(PANEL);
  appBox.addView(tv("◉  NICO OS LOCAL",22,CYAN,true));
  connection=tv("Connexion locale",13,GREEN,false);appBox.addView(connection);
  urgency=tv("Chargement…",32,CYAN,true);project=tv("",17,Color.BLACK,true);task=tv("",22,TEXT,true);date=tv("",18,ORANGE,true);status=tv("",13,MUTED,false);
  appBox.addView(urgency);appBox.addView(project);appBox.addView(task);appBox.addView(date);
  LinearLayout row=new LinearLayout(this);Button done=btn("✓ FAIT",GREEN),refresh=btn("↻ ACTUALISER",SECONDARY);row.addView(done,new LinearLayout.LayoutParams(0,-2,1));row.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));appBox.addView(row);
  done.setOnClickListener(v->markDone());refresh.setOnClickListener(v->loadTask());

  followBox=new LinearLayout(this);followBox.setOrientation(LinearLayout.VERTICAL);followBox.addView(tv("Quelle est la prochaine étape de ce projet ?",17,TEXT,true));
  nextTask=input("Prochaine tâche");followBox.addView(nextTask);nextDateButton=btn("CHOISIR LA DATE",SECONDARY);followBox.addView(nextDateButton);
  Button add=btn("＋ AJOUTER",GREEN),skip=btn("PAS MAINTENANT",SECONDARY);followBox.addView(add);followBox.addView(skip);followBox.setVisibility(View.GONE);appBox.addView(followBox);
  nextDateButton.setOnClickListener(v->pickDate());add.setOnClickListener(v->addNext());skip.setOnClickListener(v->{followBox.setVisibility(View.GONE);loadTask();});

  appBox.addView(status);
  Button settings=btn("⚙ CONNEXION",SECONDARY);appBox.addView(settings);settings.setOnClickListener(v->showSetup());
  root.addView(appBox);
 }

 void showSetup(){setupBox.setVisibility(View.VISIBLE);appBox.setVisibility(View.GONE);serverInput.setText(baseUrl);keyInput.setText(linkKey);}
 void showApp(){setupBox.setVisibility(View.GONE);appBox.setVisibility(View.VISIBLE);}

 String normalizeBase(String s){s=s.trim();while(s.endsWith("/"))s=s.substring(0,s.length()-1);if(!s.startsWith("http://")&&!s.startsWith("https://"))s="http://"+s;return s;}

 void saveConnection(){
  String u=normalizeBase(serverInput.getText().toString()),k=keyInput.getText().toString().trim();
  if(u.equals("http://")||k.isEmpty()){msg.setText("Adresse et clé requises.");return;}
  msg.setText("Test de la liaison…");
  exec.execute(()->{try{
   String oldBase=baseUrl,oldKey=linkKey;baseUrl=u;linkKey=k;
   JSONObject h=new JSONObject(request("GET","/health",null));
   if(!h.optBoolean("ok",false))throw new Exception(h.optString("error","Connexion impossible"));
   prefs.edit().putString("base_url",baseUrl).putString("link_key",linkKey).apply();
   runOnUiThread(()->{msg.setText("");showApp();loadTask();});
  }catch(Exception e){runOnUiThread(()->msg.setText(clean(e)));}});
 }

 String request(String method,String path,String body)throws Exception{
  if(baseUrl.isEmpty())throw new Exception("Adresse Nico OS non configurée");
  HttpURLConnection c=(HttpURLConnection)new URL(baseUrl+path).openConnection();
  c.setRequestMethod(method);c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setRequestProperty("X-Nico-Key",linkKey);c.setRequestProperty("Content-Type","application/json");
  if(body!=null){c.setDoOutput(true);try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}}
  int code=c.getResponseCode();InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();String out=readAll(in);
  if(code<200||code>=300)throw new Exception(errorFrom(out,"HTTP "+code));return out;
 }

 String errorFrom(String raw,String fallback){try{JSONObject j=new JSONObject(raw);return j.optString("error",fallback);}catch(Exception e){return raw==null||raw.isEmpty()?fallback:raw;}}
 String readAll(InputStream in)throws Exception{if(in==null)return"";BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();String l;while((l=r.readLine())!=null)s.append(l);return s.toString();}

 void loadTask(){
  followBox.setVisibility(View.GONE);status.setText("Lecture de Nico OS local…");connection.setText("Connexion locale…");connection.setTextColor(CYAN);
  exec.execute(()->{try{JSONObject j=new JSONObject(request("GET","/next",null));runOnUiThread(()->render(j));}catch(Exception e){runOnUiThread(()->{connection.setText("HORS LIGNE");connection.setTextColor(RED);status.setText(clean(e));});}});
 }

 void render(JSONObject root){
  try{
   status.setText("");connection.setText("● NICO OS LOCAL CONNECTÉ");connection.setTextColor(GREEN);
   if(root.optBoolean("empty",false)){currentTaskId=-1;currentProjectId=-1;urgency.setText("TOUT EST À JOUR ✓");urgency.setTextColor(GREEN);project.setText("");task.setText("Aucune tâche en attente.");date.setText("");return;}
   JSONObject j=root.getJSONObject("task");currentTaskId=j.getLong("id");currentProjectId=j.optLong("project_id",-1);currentPriority=j.optString("priority","Moyenne");
   String due=j.optString("due",""),title=j.optString("title","Tâche"),pn=j.optString("project","Sans projet");Due d=dueInfo(due);
   urgency.setText(d.label);urgency.setTextColor(d.color);project.setText("  "+pn+"  ");project.setBackgroundColor(d.color);task.setText(title);date.setText(d.pretty.isEmpty()?"":"ÉCHÉANCE • "+d.pretty);date.setTextColor(d.color);
  }catch(Exception e){status.setText(clean(e));}
 }

 static class Due{String label,pretty;int color;Due(String l,String p,int c){label=l;pretty=p;color=c;}}
 Due dueInfo(String s){if(s==null||s.isEmpty())return new Due("SANS DATE","",CYAN);try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE);Date d=f.parse(s),n=f.parse(f.format(new Date()));long x=(d.getTime()-n.getTime())/86400000L;String p=new SimpleDateFormat("dd MMMM yyyy",Locale.FRANCE).format(d);if(x<0)return new Due("EN RETARD • "+(-x)+" J",p,RED);if(x==0)return new Due("AUJOURD'HUI",p,ORANGE);if(x==1)return new Due("DEMAIN",p,ORANGE);return new Due("DANS "+x+" JOURS",p,CYAN);}catch(Exception e){return new Due("ÉCHÉANCE",s,CYAN);}}

 void markDone(){
  if(currentTaskId<0)return;long tid=currentTaskId,pid=currentProjectId;String pri=currentPriority;status.setText("Validation locale…");
  exec.execute(()->{try{JSONObject r=new JSONObject(request("POST","/done",new JSONObject().put("id",tid).toString()));int remaining=r.optInt("project_remaining",-1);runOnUiThread(()->{if(pid>0&&remaining==0){currentProjectId=pid;currentPriority=pri;task.setText("Tâche terminée ✓");date.setText("");followBox.setVisibility(View.VISIBLE);status.setText("");}else loadTask();});}catch(Exception e){runOnUiThread(()->status.setText(clean(e)));}});
 }

 void pickDate(){Calendar c=Calendar.getInstance();new DatePickerDialog(this,(v,y,m,d)->{nextDate=String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d);nextDateButton.setText("ÉCHÉANCE • "+String.format(Locale.FRANCE,"%02d/%02d/%04d",d,m+1,y));},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}

 void addNext(){
  String title=nextTask.getText().toString().trim();if(title.isEmpty()){Toast.makeText(this,"Écris la prochaine tâche.",Toast.LENGTH_SHORT).show();return;}status.setText("Ajout dans Nico OS local…");
  exec.execute(()->{try{JSONObject b=new JSONObject().put("title",title).put("project_id",currentProjectId).put("priority",currentPriority).put("duration",30);if(!nextDate.isEmpty())b.put("due",nextDate);request("POST","/create",b.toString());runOnUiThread(()->{nextTask.setText("");nextDate="";nextDateButton.setText("CHOISIR LA DATE");loadTask();});}catch(Exception e){runOnUiThread(()->status.setText(clean(e)));}});
 }

 String clean(Exception e){String s=e.getMessage();if(s==null)return"Erreur réseau";return s.length()>240?s.substring(0,240):s;}
 @Override protected void onDestroy(){exec.shutdownNow();super.onDestroy();}
}
