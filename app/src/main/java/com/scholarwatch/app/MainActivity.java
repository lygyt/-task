package com.scholarwatch.app;

import android.Manifest;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.util.Xml;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
  static final int INK=Color.rgb(24,32,46), MUTED=Color.rgb(102,113,130), BLUE=Color.rgb(54,91,203), BG=Color.rgb(246,248,251), BORDER=Color.rgb(225,230,239);
  static final String PREF="sw_v1", SCH="scholars", FEED="feed", SYNC="sync";
  SharedPreferences p; LinearLayout root, scholarBox, feedBox; TextView stat, syncView; ProgressBar bar; Button refresh;
  ExecutorService ex=Executors.newSingleThreadExecutor(); Handler ui=new Handler(Looper.getMainLooper());

  @Override public void onCreate(Bundle b){ super.onCreate(b); p=getSharedPreferences(PREF,MODE_PRIVATE); build(); schedule(); notifyPermission(); render(); }
  @Override public void onDestroy(){ ex.shutdownNow(); super.onDestroy(); }

  void build(){
    ScrollView sc=new ScrollView(this); sc.setFillViewport(true); sc.setBackgroundColor(BG);
    root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(28),dp(18),dp(34)); sc.addView(root); setContentView(sc);
    LinearLayout hero=card(); hero.setPadding(dp(20),dp(20),dp(20),dp(20));
    hero.addView(txt("ScholarWatch",28,INK,Typeface.BOLD)); TextView sub=txt("学者动态雷达 · 每周自动追踪",14,MUTED,0); margin(sub,0,4,0,14); hero.addView(sub);
    stat=txt("0 位关注学者",14,INK,Typeface.BOLD); hero.addView(stat); syncView=txt("尚未同步",12,MUTED,0); margin(syncView,0,4,0,0); hero.addView(syncView); root.addView(hero);
    LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); margin(actions,0,14,0,0);
    Button add=btn("＋ 添加学者",true); add.setOnClickListener(v->addDialog()); refresh=btn("↻ 立即刷新",false); refresh.setOnClickListener(v->refreshNow());
    LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(48),1); a.setMarginEnd(dp(7)); actions.addView(add,a); LinearLayout.LayoutParams r=new LinearLayout.LayoutParams(0,dp(48),1); r.setMarginStart(dp(7)); actions.addView(refresh,r); root.addView(actions);
    bar=new ProgressBar(this); bar.setVisibility(View.GONE); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(26),dp(26)); bp.gravity=Gravity.CENTER_HORIZONTAL; bp.setMargins(0,dp(10),0,0); root.addView(bar,bp);
    title("关注列表"); scholarBox=new LinearLayout(this); scholarBox.setOrientation(LinearLayout.VERTICAL); root.addView(scholarBox);
    title("最新动态"); feedBox=new LinearLayout(this); feedBox.setOrientation(LinearLayout.VERTICAL); root.addView(feedBox);
    TextView foot=txt("论文：OpenAlex。基金/优青/杰青/获奖/参会：公开新闻搜索候选。新闻类结果建议点击原文核验身份。",11,MUTED,0); foot.setLineSpacing(0,1.25f); margin(foot,2,14,2,0); root.addView(foot);
  }

  void render(){
    JSONArray ss=arr(SCH), ff=arr(FEED); stat.setText(ss.length()+" 位关注学者"); long s=p.getLong(SYNC,0); syncView.setText(s==0?"尚未同步":"最近同步："+new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date(s)));
    scholarBox.removeAllViews(); if(ss.length()==0) scholarBox.addView(empty("还没有关注学者","添加姓名和单位后即可开始追踪。"));
    for(int i=0;i<ss.length();i++){ JSONObject o=ss.optJSONObject(i); if(o!=null) scholarBox.addView(scholarCard(o)); }
    feedBox.removeAllViews(); if(ff.length()==0) feedBox.addView(empty("暂无动态",ss.length()==0?"请先添加学者。":"点击“立即刷新”获取最新动态。"));
    for(int i=0;i<Math.min(60,ff.length());i++){ JSONObject o=ff.optJSONObject(i); if(o!=null) feedBox.addView(feedCard(o)); }
  }

  View scholarCard(JSONObject o){ LinearLayout c=card(); c.setPadding(dp(16),dp(13),dp(10),dp(13)); margin(c,0,0,0,10); LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout left=new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL); left.addView(txt(o.optString("name"),16,INK,Typeface.BOLD)); String inst=o.optString("inst","未填写单位"); String aid=o.optString("aid"); TextView t=txt(inst+(aid.isEmpty()?" · 论文源待匹配":" · OpenAlex 已匹配"),12,MUTED,0); margin(t,0,3,0,0); left.addView(t); row.addView(left,new LinearLayout.LayoutParams(0,-2,1));
    Button d=btn("删除",false); d.setTextSize(12); d.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("删除关注").setMessage("删除 "+o.optString("name")+" 及其本地动态？").setNegativeButton("取消",null).setPositiveButton("删除",(x,w)->{remove(o.optString("id"));render();}).show()); row.addView(d,new LinearLayout.LayoutParams(dp(66),dp(40))); c.addView(row); return c; }

  View feedCard(JSONObject o){ LinearLayout c=card(); c.setPadding(dp(16),dp(14),dp(16),dp(14)); margin(c,0,0,0,10); String cat=o.optString("cat","动态"); TextView b=txt(cat,11,catColor(cat),Typeface.BOLD); b.setPadding(dp(8),dp(3),dp(8),dp(3)); b.setBackground(round(Color.rgb(239,243,255),99,BORDER)); c.addView(b,new LinearLayout.LayoutParams(-2,-2));
    TextView who=txt(o.optString("scholar")+" · "+o.optString("date"),11,MUTED,0); margin(who,0,7,0,0); c.addView(who); TextView title=txt(o.optString("title"),15,INK,Typeface.BOLD); title.setLineSpacing(0,1.14f); margin(title,0,5,0,0); c.addView(title); String st=o.optString("sub"); if(!st.isEmpty()){TextView s=txt(st,12,MUTED,0);s.setMaxLines(2);margin(s,0,6,0,0);c.addView(s);} String url=o.optString("url"); if(!url.isEmpty())c.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){}}); return c; }

  void addDialog(){ LinearLayout f=new LinearLayout(this); f.setOrientation(LinearLayout.VERTICAL); f.setPadding(dp(22),0,dp(22),0); EditText n=new EditText(this);n.setHint("学者姓名（必填）");n.setSingleLine();n.setInputType(InputType.TYPE_CLASS_TEXT); f.addView(n); EditText i=new EditText(this);i.setHint("学校 / 研究机构（建议填写）");i.setSingleLine();f.addView(i);
    AlertDialog d=new AlertDialog.Builder(this).setTitle("添加关注学者").setMessage("单位用于同名消歧。添加后自动匹配 OpenAlex。").setView(f).setNegativeButton("取消",null).setPositiveButton("添加",null).create(); d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String name=n.getText().toString().trim(),inst=i.getText().toString().trim(); if(name.isEmpty()){n.setError("请输入姓名");return;} d.dismiss(); busy(true); ex.execute(()->{String aid="";try{aid=resolveAuthor(name,inst);}catch(Exception ignored){} add(name,inst,aid);ui.post(()->{busy(false);render();refreshNow();});});})); d.show(); }

  void refreshNow(){ if(arr(SCH).length()==0){Toast.makeText(this,"请先添加学者",Toast.LENGTH_SHORT).show();return;} busy(true); ex.execute(()->{int[] res=refreshAll(this); ui.post(()->{busy(false);render();Toast.makeText(this,"新增 "+res[0]+" 条动态"+(res[1]>0?"，"+res[1]+" 位刷新异常":""),Toast.LENGTH_SHORT).show();});}); }
  void busy(boolean x){bar.setVisibility(x?View.VISIBLE:View.GONE);refresh.setEnabled(!x);}

  synchronized void add(String name,String inst,String aid){JSONArray a=arr(SCH);JSONObject o=new JSONObject();try{o.put("id",UUID.randomUUID().toString());o.put("name",name);o.put("inst",inst);o.put("aid",aid);a.put(o);}catch(Exception ignored){}p.edit().putString(SCH,a.toString()).apply();}
  synchronized void remove(String id){JSONArray s=arr(SCH),ns=new JSONArray();for(int i=0;i<s.length();i++){JSONObject o=s.optJSONObject(i);if(o!=null&&!id.equals(o.optString("id")))ns.put(o);}JSONArray f=arr(FEED),nf=new JSONArray();for(int i=0;i<f.length();i++){JSONObject o=f.optJSONObject(i);if(o!=null&&!id.equals(o.optString("sid")))nf.put(o);}p.edit().putString(SCH,ns.toString()).putString(FEED,nf.toString()).apply();}
  JSONArray arr(String k){try{return new JSONArray(p.getString(k,"[]"));}catch(Exception e){return new JSONArray();}}

  int[] refreshAll(Context ctx){int added=0,err=0;JSONArray scholars=arr(SCH);for(int i=0;i<scholars.length();i++){JSONObject s=scholars.optJSONObject(i);if(s==null)continue;try{String aid=s.optString("aid");if(aid.isEmpty()){aid=resolveAuthor(s.optString("name"),s.optString("inst"));s.put("aid",aid);}List<JSONObject> incoming=new ArrayList<>();if(!aid.isEmpty())incoming.addAll(papers(s));incoming.addAll(news(s));added+=merge(incoming);}catch(Exception e){err++;}}p.edit().putString(SCH,scholars.toString()).putLong(SYNC,System.currentTimeMillis()).apply();return new int[]{added,err};}

  String resolveAuthor(String name,String inst)throws Exception{JSONObject root=new JSONObject(get("https://api.openalex.org/authors?search="+Uri.encode(name)+"&per-page=10"));JSONArray rs=root.optJSONArray("results");if(rs==null)return"";int best=-1;String id="";String nn=norm(name),ii=norm(inst);for(int k=0;k<rs.length();k++){JSONObject a=rs.optJSONObject(k);if(a==null)continue;int sc=0;String dn=norm(a.optString("display_name"));if(dn.equals(nn))sc+=60;else if(dn.contains(nn)||nn.contains(dn))sc+=30;JSONArray ins=a.optJSONArray("last_known_institutions");if(ins!=null&&!ii.isEmpty())for(int j=0;j<ins.length();j++){JSONObject z=ins.optJSONObject(j);String x=norm(z==null?"":z.optString("display_name"));if(x.contains(ii)||ii.contains(x))sc+=45;}sc+=Math.min(15,a.optInt("works_count")/10);if(sc>best){best=sc;id=shortId(a.optString("id"));}}return best>=35?id:"";}

  List<JSONObject> papers(JSONObject s)throws Exception{List<JSONObject> out=new ArrayList<>();String aid=s.optString("aid");JSONObject root=new JSONObject(get("https://api.openalex.org/works?filter="+Uri.encode("authorships.author.id:"+aid)+"&sort=publication_date:desc&per-page=12"));JSONArray rs=root.optJSONArray("results");if(rs==null)return out;for(int i=0;i<rs.length();i++){JSONObject w=rs.optJSONObject(i);if(w==null)continue;String id=w.optString("id"),url="",venue="";JSONObject loc=w.optJSONObject("primary_location");if(loc!=null){url=loc.optString("landing_page_url");JSONObject src=loc.optJSONObject("source");if(src!=null)venue=src.optString("display_name");}if(url.isEmpty())url=!w.optString("doi").isEmpty()?w.optString("doi"):id;out.add(item("paper:"+id,s,"论文",w.optString("title"),venue.isEmpty()?"OpenAlex 收录论文":venue,w.optString("publication_date"),url));}return out;}

  List<JSONObject> news(JSONObject s)throws Exception{List<JSONObject> out=new ArrayList<>();Set<String> seen=new HashSet<>();String name=s.optString("name"),inst=s.optString("inst");String who="\""+name+"\""+(inst.isEmpty()?"":" \""+inst+"\"");String[] qs={who+" (优青 OR 杰青 OR 面上 OR 国家自然科学基金 OR 获批 OR 入选 OR 获奖)",who+" (会议 OR 大会 OR 论坛 OR 报告 OR keynote OR conference OR workshop OR 受邀)"};for(String q:qs){String u="https://news.google.com/rss/search?q="+Uri.encode(q)+"&hl=zh-CN&gl=CN&ceid=CN:zh-Hans";for(Map<String,String> e:rss(u)){String hay=e.get("title")+" "+e.get("description");if(!hay.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))continue;String id="news:"+sha(e.get("link")+e.get("title"));if(!seen.add(id))continue;out.add(item(id,s,classify(hay),e.get("title"),e.get("source"),rssDate(e.get("pubDate")),e.get("link")));}}return out;}

  JSONObject item(String id,JSONObject s,String cat,String title,String sub,String date,String url){JSONObject o=new JSONObject();try{o.put("id",id);o.put("sid",s.optString("id"));o.put("scholar",s.optString("name"));o.put("cat",cat);o.put("title",title);o.put("sub",sub==null?"":sub);o.put("date",date==null?"":date);o.put("url",url==null?"":url);o.put("ts",System.currentTimeMillis());}catch(Exception ignored){}return o;}
  synchronized int merge(List<JSONObject> in){JSONArray a=arr(FEED);Set<String> ids=new HashSet<>();List<JSONObject> all=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null){all.add(o);ids.add(o.optString("id"));}}int n=0;for(JSONObject o:in)if(ids.add(o.optString("id"))){all.add(o);n++;}all.sort((x,y)->y.optString("date").compareTo(x.optString("date")));JSONArray out=new JSONArray();for(int i=0;i<Math.min(300,all.size());i++)out.put(all.get(i));p.edit().putString(FEED,out.toString()).apply();return n;}

  static List<Map<String,String>> rss(String url)throws Exception{List<Map<String,String>> out=new ArrayList<>();HttpURLConnection c=open(url);try(InputStream in=c.getInputStream()){XmlPullParser x=Xml.newPullParser();x.setInput(in,"UTF-8");Map<String,String> cur=null;String tag="";int e=x.getEventType();while(e!=XmlPullParser.END_DOCUMENT){if(e==XmlPullParser.START_TAG){tag=x.getName();if("item".equalsIgnoreCase(tag)){cur=new HashMap<>();cur.put("title","");cur.put("link","");cur.put("description","");cur.put("pubDate","");cur.put("source","");}}else if(e==XmlPullParser.TEXT&&cur!=null){String v=x.getText();if(cur.containsKey(tag))cur.put(tag,cur.get(tag)+v);}else if(e==XmlPullParser.END_TAG){if("item".equalsIgnoreCase(x.getName())&&cur!=null){out.add(cur);cur=null;}tag="";}e=x.next();}}finally{c.disconnect();}return out;}
  static HttpURLConnection open(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(16000);c.setRequestProperty("User-Agent","ScholarWatch/0.1 Android");int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code);return c;}
  static String get(String u)throws Exception{HttpURLConnection c=open(u);StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)b.append(l);}finally{c.disconnect();}return b.toString();}
  static String classify(String s){String t=s.toLowerCase(Locale.ROOT);if(any(t,"优青","优秀青年","杰青","杰出青年","面上","国家自然科学基金","获批","基金","grant"))return"基金/人才";if(any(t,"获奖","award"))return"获奖";if(any(t,"会议","大会","论坛","报告","受邀","keynote","conference","workshop"))return"参会/报告";return"新闻";}
  static boolean any(String s,String...w){for(String x:w)if(s.contains(x.toLowerCase(Locale.ROOT)))return true;return false;}
  static String shortId(String s){int i=s.lastIndexOf('/');return i>=0?s.substring(i+1):s;}
  static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]","");}
  static String sha(String s){try{byte[] d=MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format(Locale.US,"%02x",x));return b.toString();}catch(Exception e){return Integer.toHexString(s.hashCode());}}
  static String rssDate(String s){try{Date d=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",Locale.US).parse(s);return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(d);}catch(Exception e){return s!=null&&s.length()>=10?s.substring(0,10):"";}}

  void schedule(){JobScheduler js=(JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);JobInfo j=new JobInfo.Builder(260821,new ComponentName(this,RefreshJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(7L*24*60*60*1000).setPersisted(true).build();js.schedule(j);}
  void notifyPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);}
  void title(String s){TextView t=txt(s,16,INK,Typeface.BOLD);margin(t,2,22,2,10);root.addView(t);}
  LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setBackground(round(Color.WHITE,15,BORDER));l.setElevation(dp(1));return l;}
  View empty(String a,String b){LinearLayout c=card();c.setPadding(dp(18),dp(17),dp(18),dp(17));c.addView(txt(a,15,INK,Typeface.BOLD));TextView d=txt(b,12,MUTED,0);margin(d,0,5,0,0);c.addView(d);return c;}
  Button btn(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?Color.WHITE:INK);b.setBackground(round(primary?BLUE:Color.WHITE,13,primary?BLUE:BORDER));return b;}
  TextView txt(String s,int z,int c,int st){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setTypeface(Typeface.DEFAULT,st);return t;}
  GradientDrawable round(int fill,float rad,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(rad));g.setStroke(dp(1),stroke);return g;}
  void margin(View v,int l,int t,int r,int b){ViewGroup.MarginLayoutParams p=v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams?(ViewGroup.MarginLayoutParams)v.getLayoutParams():new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));v.setLayoutParams(p);}
  int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);} int catColor(String c){if(c.contains("基金"))return Color.rgb(151,74,18);if(c.contains("获奖"))return Color.rgb(132,76,173);if(c.contains("参会"))return Color.rgb(0,111,105);return BLUE;}
}
