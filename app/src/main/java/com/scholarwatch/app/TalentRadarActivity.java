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
import java.util.regex.*;

public class TalentRadarActivity extends Activity {
  static final String PREF="sw_v1", SCH="scholars", CONTACTS="talent_contacts_v1", OFEED="official_feed_v1", OSYNC="official_sync_v1";
  static final int INK=Color.rgb(24,32,46), MUTED=Color.rgb(103,113,129), BLUE=Color.rgb(57,91,206), BG=Color.rgb(246,248,251), BORDER=Color.rgb(225,230,239);
  static final String[][] SOURCES={
    {"国自然","nsfc.gov.cn"}, {"中国计算机学会","ccf.org.cn"}, {"中国电子学会","cie.org.cn"},
    {"中国通信学会","china-cic.cn"}, {"中国自动化学会","caa.org.cn"}, {"中国人工智能学会","caai.cn"},
    {"中国科学院","cas.cn"}, {"中国工程院","cae.cn"}
  };
  SharedPreferences p; LinearLayout root,contactBox,watchBox,feedBox; TextView stats,sync; ProgressBar bar; Button scan;
  ExecutorService ex=Executors.newSingleThreadExecutor(); Handler ui=new Handler(Looper.getMainLooper());

  @Override public void onCreate(Bundle b){super.onCreate(b);p=getSharedPreferences(PREF,MODE_PRIVATE);build();schedule();permission();render();}
  @Override public void onDestroy(){ex.shutdownNow();super.onDestroy();}

  void build(){
    ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(28),dp(18),dp(36));sc.addView(root);setContentView(sc);
    LinearLayout hero=card();hero.setPadding(dp(20),dp(20),dp(20),dp(20));hero.addView(txt("ScholarWatch",28,INK,Typeface.BOLD));TextView sub=txt("国自然 · 学会 · 人才雷达",14,MUTED,0);margin(sub,0,4,0,13);hero.addView(sub);stats=txt("0 位人才联系人",14,INK,Typeface.BOLD);hero.addView(stats);sync=txt("尚未扫描",12,MUTED,0);margin(sync,0,4,0,0);hero.addView(sync);root.addView(hero);
    LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);margin(actions,0,14,0,0);scan=btn("⌁ 扫描官方源",true);scan.setOnClickListener(v->scanNow());Button manage=btn("重点关注 / 论文",false);manage.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(48),1);a.setMarginEnd(dp(7));actions.addView(scan,a);LinearLayout.LayoutParams m=new LinearLayout.LayoutParams(0,dp(48),1);m.setMarginStart(dp(7));actions.addView(manage,m);root.addView(actions);
    bar=new ProgressBar(this);bar.setVisibility(View.GONE);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(26),dp(26));bp.gravity=Gravity.CENTER_HORIZONTAL;bp.setMargins(0,dp(10),0,0);root.addView(bar,bp);
    title("人才联系人");TextView explain=txt("单人消息中识别到优青、杰青、两院院士或海外优青时自动保存。",11,MUTED,0);margin(explain,2,-4,2,10);root.addView(explain);contactBox=new LinearLayout(this);contactBox.setOrientation(LinearLayout.VERTICAL);root.addView(contactBox);
    title("重点关注");watchBox=new LinearLayout(this);watchBox.setOrientation(LinearLayout.VERTICAL);root.addView(watchBox);
    title("官方动态");feedBox=new LinearLayout(this);feedBox.setOrientation(LinearLayout.VERTICAL);root.addView(feedBox);
    TextView foot=txt("优 = 青年科学基金项目（B类）/原优青；杰 = 青年科学基金项目（A类）/原杰青；海优 = 优秀青年科学基金项目（海外）；院 = 中国科学院/中国工程院院士。批量名单与候选人公告只进入动态，不自动写联系人。",11,MUTED,0);foot.setLineSpacing(0,1.25f);margin(foot,2,14,2,0);root.addView(foot);
  }

  void render(){JSONArray c=arr(CONTACTS),s=arr(SCH),f=arr(OFEED);stats.setText(c.length()+" 位人才联系人 · "+s.length()+" 位重点关注");long t=p.getLong(OSYNC,0);sync.setText(t==0?"尚未扫描":"最近扫描："+new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date(t)));
    contactBox.removeAllViews();if(c.length()==0)contactBox.addView(empty("尚未发现人才联系人","点击“扫描官方源”，系统只在单人身份足够明确时自动保存。"));for(int i=0;i<Math.min(c.length(),80);i++){JSONObject o=c.optJSONObject(i);if(o!=null)contactBox.addView(contactCard(o));}
    watchBox.removeAllViews();if(s.length()==0)watchBox.addView(empty("暂无重点关注","进入“重点关注 / 论文”添加学者。"));for(int i=0;i<s.length();i++){JSONObject o=s.optJSONObject(i);if(o!=null)watchBox.addView(watchCard(o));}
    feedBox.removeAllViews();if(f.length()==0)feedBox.addView(empty("暂无官方动态","扫描国自然与主要全国性学会后会显示在这里。"));for(int i=0;i<Math.min(f.length(),100);i++){JSONObject o=f.optJSONObject(i);if(o!=null)feedBox.addView(feedCard(o));}
  }

  View contactCard(JSONObject o){LinearLayout c=card();c.setPadding(dp(16),dp(13),dp(12),dp(13));margin(c,0,0,0,10);LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.addView(txt(o.optString("name"),17,INK,Typeface.BOLD));String inst=o.optString("inst");if(!inst.isEmpty()){TextView it=txt(inst,11,MUTED,0);margin(it,0,3,0,0);left.addView(it);}top.addView(left,new LinearLayout.LayoutParams(0,-2,1));Button follow=btn(isWatched(o.optString("name"))?"已关注":"＋关注",false);follow.setTextSize(11);follow.setEnabled(!isWatched(o.optString("name")));follow.setOnClickListener(v->{addWatch(o);render();});top.addView(follow,new LinearLayout.LayoutParams(dp(72),dp(38)));c.addView(top);
    LinearLayout tags=new LinearLayout(this);tags.setOrientation(LinearLayout.HORIZONTAL);tags.setGravity(Gravity.LEFT);JSONArray ta=o.optJSONArray("tags");if(ta!=null)for(int i=0;i<ta.length();i++){String x=ta.optString(i);TextView chip=tag(x);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(0,dp(8),dp(6),0);tags.addView(chip,lp);}c.addView(tags);
    TextView src=txt(o.optString("source")+" · "+o.optString("date"),11,MUTED,0);margin(src,0,7,0,0);c.addView(src);TextView st=txt(o.optString("sourceTitle"),12,INK,0);st.setMaxLines(2);c.addView(st);String url=o.optString("url");if(!url.isEmpty())c.setOnClickListener(v->open(url));return c;}
  View watchCard(JSONObject o){LinearLayout c=card();c.setPadding(dp(16),dp(13),dp(16),dp(13));margin(c,0,0,0,10);c.addView(txt(o.optString("name"),15,INK,Typeface.BOLD));TextView s=txt(o.optString("inst","未填写单位"),11,MUTED,0);margin(s,0,3,0,0);c.addView(s);return c;}
  View feedCard(JSONObject o){LinearLayout c=card();c.setPadding(dp(16),dp(14),dp(16),dp(14));margin(c,0,0,0,10);LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);TextView src=smallChip(o.optString("source"),BLUE);chips.addView(src);JSONArray tags=o.optJSONArray("tags");if(tags!=null)for(int i=0;i<tags.length();i++){String z=tags.optString(i);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMarginStart(dp(6));chips.addView(tag(z),lp);}String event=o.optString("eventTag");if(!event.isEmpty()&&(tags==null||tags.length()==0)){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMarginStart(dp(6));chips.addView(smallChip(event,Color.rgb(99,78,152)),lp);}c.addView(chips);TextView date=txt(o.optString("date"),11,MUTED,0);margin(date,0,7,0,0);c.addView(date);TextView title=txt(o.optString("title"),15,INK,Typeface.BOLD);title.setLineSpacing(0,1.15f);c.addView(title);String url=o.optString("url");if(!url.isEmpty())c.setOnClickListener(v->open(url));return c;}

  void scanNow(){busy(true);ex.execute(()->{int[] r=runOfficialScan(this);ui.post(()->{busy(false);render();Toast.makeText(this,"新增动态 "+r[0]+" 条，人才联系人 "+r[1]+" 位",Toast.LENGTH_LONG).show();});});}
  void busy(boolean x){bar.setVisibility(x?View.VISIBLE:View.GONE);scan.setEnabled(!x);}

  static int[] runOfficialScan(Context ctx){SharedPreferences p=ctx.getSharedPreferences(PREF,MODE_PRIVATE);JSONArray oldFeed=arr(p,OFEED),oldContacts=arr(p,CONTACTS);Set<String> feedIds=new HashSet<>();for(int i=0;i<oldFeed.length();i++){JSONObject o=oldFeed.optJSONObject(i);if(o!=null)feedIds.add(o.optString("id"));}int newFeed=0,newContacts=0;List<JSONObject> incoming=new ArrayList<>();
    for(String[] source:SOURCES){String label=source[0],domain=source[1];String q=buildQuery(label,domain);try{String u="https://news.google.com/rss/search?q="+Uri.encode(q)+"&hl=zh-CN&gl=CN&ceid=CN:zh-Hans";for(Map<String,String> e:rss(u)){String title=e.get("title").trim(),desc=e.get("description"),url=e.get("link"),date=rssDate(e.get("pubDate"));if(title.isEmpty())continue;String hay=strip(title+" "+desc);JSONArray tags=detectTags(hay);String ev=detectEventTag(hay);if(tags.length()==0&&ev.isEmpty())continue;String id="official:"+sha(label+url+title);JSONObject item=new JSONObject();try{item.put("id",id);item.put("source",label);item.put("title",title);item.put("date",date);item.put("url",url);item.put("tags",tags);item.put("eventTag",ev);}catch(Exception ignored){}incoming.add(item);if(!feedIds.contains(id)){newFeed++;feedIds.add(id);}String name=extractSingleName(hay,tags);if(!name.isEmpty()&&tags.length()>0){String inst=extractInstitution(hay);if(mergeContact(oldContacts,name,inst,tags,label,title,date,url))newContacts++;}}
      }catch(Exception ignored){}
    }
    List<JSONObject> all=new ArrayList<>();for(int i=0;i<oldFeed.length();i++){JSONObject o=oldFeed.optJSONObject(i);if(o!=null)all.add(o);}Set<String> ids=new HashSet<>();for(JSONObject o:all)ids.add(o.optString("id"));for(JSONObject o:incoming)if(ids.add(o.optString("id")))all.add(o);all.sort((a,b)->b.optString("date").compareTo(a.optString("date")));JSONArray nf=new JSONArray();for(int i=0;i<Math.min(400,all.size());i++)nf.put(all.get(i));p.edit().putString(OFEED,nf.toString()).putString(CONTACTS,oldContacts.toString()).putLong(OSYNC,System.currentTimeMillis()).apply();return new int[]{newFeed,newContacts};}

  static String buildQuery(String label,String domain){if("国自然".equals(label))return "site:"+domain+" (面上项目 OR 青年科学基金项目 A类 OR 青年科学基金项目 B类 OR 优秀青年科学基金项目 海外 OR 杰出青年科学基金 OR 优秀青年科学基金) when:90d";if("中国科学院".equals(label)||"中国工程院".equals(label))return "site:"+domain+" (院士 OR 院士增选) when:365d";return "site:"+domain+" (优青 OR 杰青 OR 国家杰青 OR 国家优青 OR 优秀青年科学基金 OR 杰出青年科学基金 OR 海外优青 OR 院士 OR 面上项目) when:90d";}
  static JSONArray detectTags(String s){JSONArray a=new JSONArray();String t=s.replace(" ","");boolean overseas=any(t,"海外优青","优秀青年科学基金项目（海外）","优秀青年科学基金项目(海外)");if(overseas)a.put("海优");if(any(t,"青年科学基金项目（A类）","青年科学基金项目(A类)","国家杰出青年科学基金","国家杰青","杰青获得者","杰青项目"))a.put("杰");if(!overseas&&any(t,"青年科学基金项目（B类）","青年科学基金项目(B类)","国家优秀青年科学基金","国家优青","优青获得者","优青项目"))a.put("优");if(!any(t,"候选院士","院士候选","有效候选人")&&any(t,"中国科学院院士","中国工程院院士","当选院士","两院院士"))a.put("院");return unique(a);}
  static String detectEventTag(String s){String t=s.replace(" ","");if(any(t,"面上项目","面上基金"))return"面上";if(any(t,"基金项目评审","资助项目","获批基金","自然科学基金"))return"国自然";if(any(t,"学术大会","年会","论坛","会议"))return"学会";return"";}
  static String extractSingleName(String s,JSONArray tags){if(tags.length()==0)return"";String t=strip(s);if(any(t,"名单","候选人","有效候选","等人","多人","多名","若干","人获","人入选","人当选","批量"))return"";LinkedHashSet<String> names=new LinkedHashSet<>();String person="[\\u4e00-\\u9fa5]{2,4}";String[] ps={"("+person+")(?:教授|研究员|副教授|博士|老师)?(?:荣获|获批|入选|当选|获得|获评)","(?:中国科学院院士|中国工程院院士|国家杰青|国家优青|杰青|优青)[：:、,，\\s]*("+person+")","("+person+")(?:教授|研究员|副教授)[，,:：\\s]*(?:中国科学院院士|中国工程院院士|国家杰青|国家优青|杰青|优青)"};for(String p:ps){Matcher m=Pattern.compile(p).matcher(t);while(m.find()){String n=m.group(1);if(validName(n))names.add(n);}}return names.size()==1?names.iterator().next():"";}
  static boolean validName(String n){if(n==null||n.length()<2||n.length()>4)return false;String[] bad={"中国","国家","科学","基金","学会","青年","大学","学院","项目","专家","教授","院士","喜报","我会","大会","研究","优秀","杰出","海外","自然"};for(String b:bad)if(n.contains(b))return false;return true;}
  static String extractInstitution(String s){Matcher m=Pattern.compile("([\\u4e00-\\u9fa5]{2,18}(?:大学|研究所|科学院|学院|实验室))").matcher(s);while(m.find()){String x=m.group(1);if(!x.contains("中国科学院院士")&&!x.contains("中国工程院院士"))return trimInst(x);}return"";}
  static String trimInst(String x){String[] cuts={"来自","任职于","就职于","现任","单位"};for(String c:cuts){int i=x.lastIndexOf(c);if(i>=0&&i+c.length()<x.length())x=x.substring(i+c.length());}return x.length()>20?x.substring(x.length()-20):x;}
  static boolean mergeContact(JSONArray contacts,String name,String inst,JSONArray tags,String source,String title,String date,String url){JSONObject hit=null;for(int i=0;i<contacts.length();i++){JSONObject o=contacts.optJSONObject(i);if(o!=null&&name.equals(o.optString("name"))){String oi=o.optString("inst");if(inst.isEmpty()||oi.isEmpty()||oi.equals(inst)){hit=o;break;}}}boolean created=false;if(hit==null){hit=new JSONObject();try{hit.put("id",UUID.randomUUID().toString());hit.put("name",name);hit.put("inst",inst);hit.put("tags",new JSONArray());hit.put("firstSeen",date);contacts.put(hit);created=true;}catch(Exception ignored){}}try{JSONArray old=hit.optJSONArray("tags");if(old==null)old=new JSONArray();Set<String> set=new LinkedHashSet<>();for(int i=0;i<old.length();i++)set.add(old.optString(i));for(int i=0;i<tags.length();i++)set.add(tags.optString(i));JSONArray nt=new JSONArray();for(String x:set)nt.put(x);hit.put("tags",nt);if(hit.optString("inst").isEmpty()&&!inst.isEmpty())hit.put("inst",inst);hit.put("source",source);hit.put("sourceTitle",title);hit.put("date",date);hit.put("url",url);hit.put("lastSeen",date);}catch(Exception ignored){}return created;}

  void addWatch(JSONObject c){JSONArray s=arr(SCH);if(isWatched(c.optString("name")))return;JSONObject o=new JSONObject();try{o.put("id",UUID.randomUUID().toString());o.put("name",c.optString("name"));o.put("inst",c.optString("inst"));o.put("aid","");o.put("instId","");o.put("verified",false);s.put(o);}catch(Exception ignored){}p.edit().putString(SCH,s.toString()).apply();Toast.makeText(this,"已加入重点关注，请进入论文页确认 OpenAlex 身份",Toast.LENGTH_LONG).show();}
  boolean isWatched(String name){JSONArray s=arr(SCH);for(int i=0;i<s.length();i++){JSONObject o=s.optJSONObject(i);if(o!=null&&name.equals(o.optString("name")))return true;}return false;}
  JSONArray arr(String k){return arr(p,k);}static JSONArray arr(SharedPreferences p,String k){try{return new JSONArray(p.getString(k,"[]"));}catch(Exception e){return new JSONArray();}}

  static List<Map<String,String>> rss(String url)throws Exception{List<Map<String,String>> out=new ArrayList<>();HttpURLConnection c=open(url);try(InputStream in=c.getInputStream()){XmlPullParser x=Xml.newPullParser();x.setInput(in,"UTF-8");Map<String,String> cur=null;String tag="";int e=x.getEventType();while(e!=XmlPullParser.END_DOCUMENT){if(e==XmlPullParser.START_TAG){tag=x.getName();if("item".equalsIgnoreCase(tag)){cur=new HashMap<>();cur.put("title","");cur.put("link","");cur.put("description","");cur.put("pubDate","");}}else if(e==XmlPullParser.TEXT&&cur!=null){String v=x.getText();if(cur.containsKey(tag))cur.put(tag,cur.get(tag)+v);}else if(e==XmlPullParser.END_TAG){if("item".equalsIgnoreCase(x.getName())&&cur!=null){out.add(cur);cur=null;}tag="";}e=x.next();}}finally{c.disconnect();}return out;}
  static HttpURLConnection open(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(16000);c.setRequestProperty("User-Agent","ScholarWatch/0.3 Android");int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code);return c;}
  static JSONArray unique(JSONArray a){JSONArray o=new JSONArray();Set<String>s=new LinkedHashSet<>();for(int i=0;i<a.length();i++)s.add(a.optString(i));for(String x:s)o.put(x);return o;}
  static boolean any(String s,String...w){for(String x:w)if(s.contains(x))return true;return false;}
  static String strip(String s){return s==null?"":s.replaceAll("<[^>]+>"," ").replace("&nbsp;"," ").replace("&amp;","&").replaceAll("\\s+"," ").trim();}
  static String sha(String s){try{byte[]d=MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format(Locale.US,"%02x",x));return b.toString();}catch(Exception e){return Integer.toHexString(s.hashCode());}}
  static String rssDate(String s){try{Date d=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",Locale.US).parse(s);return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(d);}catch(Exception e){return s!=null&&s.length()>=10?s.substring(0,10):"";}}

  void schedule(){JobScheduler js=(JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);JobInfo j=new JobInfo.Builder(260823,new ComponentName(this,TalentRefreshJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(7L*24*60*60*1000).setPersisted(true).build();js.schedule(j);}
  void permission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},20);}
  void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ignored){}}
  TextView tag(String s){int c=tagColor(s);TextView t=txt(s,11,c,Typeface.BOLD);t.setPadding(dp(8),dp(3),dp(8),dp(3));t.setBackground(round(blend(c),99,c));return t;}
  TextView smallChip(String s,int c){TextView t=txt(s,11,c,Typeface.BOLD);t.setPadding(dp(8),dp(3),dp(8),dp(3));t.setBackground(round(Color.rgb(241,244,249),99,BORDER));return t;}
  int tagColor(String s){if("杰".equals(s))return Color.rgb(190,55,59);if("院".equals(s))return Color.rgb(171,108,18);if("海优".equals(s))return Color.rgb(122,70,171);return Color.rgb(28,116,88);}
  int blend(int c){return Color.rgb((Color.red(c)+255*5)/6,(Color.green(c)+255*5)/6,(Color.blue(c)+255*5)/6);}
  void title(String s){TextView t=txt(s,17,INK,Typeface.BOLD);margin(t,2,23,2,10);root.addView(t);}
  LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setBackground(round(Color.WHITE,15,BORDER));l.setElevation(dp(1));return l;}
  View empty(String a,String b){LinearLayout c=card();c.setPadding(dp(18),dp(17),dp(18),dp(17));c.addView(txt(a,15,INK,Typeface.BOLD));TextView d=txt(b,12,MUTED,0);margin(d,0,5,0,0);c.addView(d);return c;}
  Button btn(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?Color.WHITE:INK);b.setBackground(round(primary?BLUE:Color.WHITE,13,primary?BLUE:BORDER));return b;}
  TextView txt(String s,int z,int c,int st){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setTypeface(Typeface.DEFAULT,st);return t;}
  GradientDrawable round(int fill,float rad,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(rad));g.setStroke(dp(1),stroke);return g;}
  void margin(View v,int l,int t,int r,int b){ViewGroup.MarginLayoutParams p=v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams?(ViewGroup.MarginLayoutParams)v.getLayoutParams():new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));v.setLayoutParams(p);}
  int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
