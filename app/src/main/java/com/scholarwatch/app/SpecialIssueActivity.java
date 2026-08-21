package com.scholarwatch.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class SpecialIssueActivity extends Activity {
  static final String PREF="sw_v1",CACHE="special_issues_v1",SYNC="special_issues_sync_v1";
  static final int INK=Color.rgb(24,32,46),MUTED=Color.rgb(103,113,129),PURPLE=Color.rgb(119,76,171),BG=Color.rgb(246,248,251),BORDER=Color.rgb(225,230,239),GREEN=Color.rgb(27,126,87);
  static final String[][] JOURNALS={
    {"TOCHI","ACM Transactions on Computer-Human Interaction","acm.org"},
    {"IJHCS","International Journal of Human-Computer Studies","elsevier.com"},
    {"TMC","IEEE Transactions on Mobile Computing","computer.org"},
    {"JSAC","IEEE Journal on Selected Areas in Communications","comsoc.org"},
    {"TON","IEEE/ACM Transactions on Networking","acm.org"}
  };
  static final String[] TOPICS={"ubiquitous","pervasive","mobile","wearable","sensing","sensor","context-aware","context aware","ambient intelligence","smart environment","human-centered","human centred","human-computer interaction","human computer interaction","digital health","healthcare","internet of things","iot","edge computing","multimodal","mixed reality","augmented reality","cyber-physical","social computing","embodied","human-ai","human ai","smart home","smart city","mobile systems"};
  SharedPreferences p;LinearLayout root,list;TextView stat,sync;ProgressBar bar;Button search;ExecutorService ex=Executors.newSingleThreadExecutor();Handler ui=new Handler(Looper.getMainLooper());
  @Override public void onCreate(Bundle b){super.onCreate(b);p=getSharedPreferences(PREF,MODE_PRIVATE);build();render();}
  @Override public void onDestroy(){ex.shutdownNow();super.onDestroy();}

  void build(){ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(28),dp(18),dp(36));sc.addView(root);setContentView(sc);
    LinearLayout hero=card();hero.setPadding(dp(20),dp(19),dp(20),dp(19));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button back=btn("‹ 首页",false);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(78),dp(40)));TextView title=txt("CCF-A 英文特刊",25,INK,Typeface.BOLD);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMarginStart(dp(10));top.addView(title,tp);hero.addView(top);TextView sub=txt("普适计算 · Mobile · HCI · Sensing",13,MUTED,0);margin(sub,2,9,0,12);hero.addView(sub);stat=txt("尚未搜索",14,INK,Typeface.BOLD);hero.addView(stat);sync=txt("范围：TOCHI · IJHCS · TMC · JSAC · TON",11,MUTED,0);margin(sync,0,4,0,0);hero.addView(sync);root.addView(hero);
    search=btn("⌕ 搜索当前英文特刊",true);search.setOnClickListener(v->searchNow());margin(search,0,14,0,0);root.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));bar=new ProgressBar(this);bar.setVisibility(View.GONE);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(28),dp(28));bp.gravity=Gravity.CENTER_HORIZONTAL;bp.setMargins(0,dp(11),0,0);root.addView(bar,bp);
    TextView note=txt("只保留 Special Issue / Special Section / Call for Papers 中与普适计算、移动计算、HCI、无线/移动感知、可穿戴、数字健康、IoT 等主题相关的结果。时间优先识别 Submission/Manuscript Deadline。",11,MUTED,0);note.setLineSpacing(0,1.25f);margin(note,2,16,2,10);root.addView(note);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
  }

  void render(){JSONArray a=arr(CACHE);long t=p.getLong(SYNC,0);stat.setText(a.length()==0?"暂无缓存结果":a.length()+" 个相关英文特刊 / CFP");sync.setText(t==0?"范围：TOCHI · IJHCS · TMC · JSAC · TON":"最近搜索："+new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date(t))+" · TOCHI/IJHCS/TMC/JSAC/TON");list.removeAllViews();if(a.length()==0){list.addView(empty("暂未发现特刊","点击上方按钮联网搜索；没有可信时间的结果会标记“待核验”。"));return;}for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)list.addView(issueCard(o));}}
  View issueCard(JSONObject o){LinearLayout c=card();c.setPadding(dp(16),dp(14),dp(16),dp(14));margin(c,0,0,0,10);LinearLayout chips=new LinearLayout(this);chips.setGravity(Gravity.CENTER_VERTICAL);chips.addView(chip("CCF A",PURPLE));LinearLayout.LayoutParams jl=new LinearLayout.LayoutParams(-2,-2);jl.setMarginStart(dp(6));chips.addView(chip(o.optString("journal"),Color.rgb(51,92,190)),jl);c.addView(chips);TextView title=txt(o.optString("title"),16,INK,Typeface.BOLD);title.setLineSpacing(0,1.16f);margin(title,0,9,0,0);c.addView(title);String deadline=o.optString("deadline");boolean verified=!deadline.isEmpty();TextView time=txt(verified?"投稿截止："+deadline:"投稿截止：待核验",13,verified?GREEN:Color.rgb(177,99,28),Typeface.BOLD);margin(time,0,8,0,0);c.addView(time);String source=o.optString("source");if(!source.isEmpty()){TextView s=txt("来源："+source,11,MUTED,0);c.addView(s);}String url=o.optString("url");if(!url.isEmpty())c.setOnClickListener(v->openLink(url));return c;}

  void searchNow(){busy(true);ex.execute(()->{try{JSONArray r=scan();p.edit().putString(CACHE,r.toString()).putLong(SYNC,System.currentTimeMillis()).apply();ui.post(()->{busy(false);render();Toast.makeText(this,"找到 "+r.length()+" 个相关英文特刊 / CFP",Toast.LENGTH_LONG).show();});}catch(Exception e){ui.post(()->{busy(false);Toast.makeText(this,"搜索失败，请检查网络后重试",Toast.LENGTH_LONG).show();});}});}
  void busy(boolean x){bar.setVisibility(x?View.VISIBLE:View.GONE);search.setEnabled(!x);}

  JSONArray scan(){List<JSONObject> all=new ArrayList<>();Set<String> seen=new HashSet<>();for(String[] j:JOURNALS){try{List<Candidate> cs=new ArrayList<>();cs.addAll(ddg(j,"\"special issue\""));cs.addAll(ddg(j,"\"special section\" OR \"call for papers\""));int checked=0;for(Candidate c:cs){if(checked++>14)break;String key=norm(c.url+"|"+c.title);if(!seen.add(key))continue;String page="";try{page=get(c.url);}catch(Exception ignored){}String plain=cleanHtml(page);String hay=(c.title+" "+c.snippet+" "+plain).toLowerCase(Locale.ROOT);if(!isSpecial(hay)||!topicRelevant(hay))continue;String title=cleanTitle(c.title,j[0]);if(title.length()<8&&page.length()>0)title=cleanTitle(extractHtmlTitle(page),j[0]);if(title.isEmpty())continue;String deadline=extractDeadline(plain+" "+c.snippet);if(isClearlyExpired(deadline))continue;JSONObject o=new JSONObject();o.put("id",Integer.toHexString((j[0]+title+c.url).hashCode()));o.put("journal",j[0]);o.put("title",title);o.put("deadline",deadline);o.put("url",c.url);o.put("source",host(c.url));all.add(o);}}catch(Exception ignored){}}
    all.sort((a,b)->{String ad=a.optString("deadline"),bd=b.optString("deadline");if(ad.isEmpty()!=bd.isEmpty())return ad.isEmpty()?1:-1;return ad.compareTo(bd);});JSONArray out=new JSONArray();for(int i=0;i<Math.min(all.size(),60);i++)out.put(all.get(i));return out;}

  List<Candidate> ddg(String[] j,String mode)throws Exception{String query="\""+j[1]+"\" "+mode+" (ubiquitous OR pervasive OR mobile OR wearable OR sensing OR \"human-computer interaction\" OR healthcare OR IoT OR multimodal)";String html=get("https://html.duckduckgo.com/html/?q="+Uri.encode(query));List<Candidate> out=new ArrayList<>();Pattern block=Pattern.compile("(?is)<div[^>]*class=\"[^\"]*result[^\"]*\"[^>]*>(.*?)</div>\\s*</div>");Matcher bm=block.matcher(html);while(bm.find()&&out.size()<12){String b=bm.group(1);Matcher am=Pattern.compile("(?is)<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>").matcher(b);if(!am.find()){am=Pattern.compile("(?is)<a[^>]*href=\"([^\"]+)\"[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*>(.*?)</a>").matcher(b);if(!am.find())continue;}String url=unwrap(am.group(1));String title=cleanHtml(am.group(2));Matcher sm=Pattern.compile("(?is)class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</").matcher(b);String sn=sm.find()?cleanHtml(sm.group(1)):"";if(url.startsWith("http"))out.add(new Candidate(title,url,sn));}
    if(out.isEmpty()){Matcher am=Pattern.compile("(?is)<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>").matcher(html);while(am.find()&&out.size()<12){String u=unwrap(am.group(1));if(u.startsWith("http"))out.add(new Candidate(cleanHtml(am.group(2)),u,""));}}
    return out;}

  static boolean isSpecial(String s){return s.contains("special issue")||s.contains("special section")||s.contains("call for papers")||s.contains("call for paper");}
  static boolean topicRelevant(String s){for(String k:TOPICS)if(s.contains(k))return true;return false;}
  static String extractDeadline(String s){if(s==null)return"";String x=s.replace('\u00a0',' ').replaceAll("\\s+"," ");String months="January|February|March|April|May|June|July|August|September|October|November|December|Jan\\.?|Feb\\.?|Mar\\.?|Apr\\.?|Jun\\.?|Jul\\.?|Aug\\.?|Sep\\.?|Sept\\.?|Oct\\.?|Nov\\.?|Dec\\.?";String[] ps={
    "(?i)(?:submission|manuscript|paper|full paper)[^.;]{0,45}?(?:deadline|due|date|submission)?\\s*[:\\-–]?\\s*((?:"+months+")\\s+\\d{1,2}(?:st|nd|rd|th)?[,]?\\s+20\\d{2})",
    "(?i)(?:submission|manuscript|paper|full paper)[^.;]{0,45}?(?:deadline|due|date|submission)?\\s*[:\\-–]?\\s*(\\d{1,2}\\s+(?:"+months+")[,]?\\s+20\\d{2})",
    "(?i)(?:deadline|manuscript due|submission due|submission date)\\s*[:\\-–]?\\s*(20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2})"
  };for(String p:ps){Matcher m=Pattern.compile(p).matcher(x);if(m.find())return tidyDate(m.group(1));}return"";}
  static boolean isClearlyExpired(String raw){if(raw==null||raw.isEmpty())return false;Date d=parseDate(raw);if(d==null)return false;return d.getTime()<System.currentTimeMillis()-60L*24*60*60*1000;}
  static Date parseDate(String s){String x=s.replaceAll("(?i)(st|nd|rd|th)","").replace(",","").replaceAll("\\s+"," ").trim();String[] fs={"MMMM d yyyy","MMM d yyyy","d MMMM yyyy","d MMM yyyy","yyyy-M-d","yyyy/M/d"};for(String f:fs)try{return new SimpleDateFormat(f,Locale.US).parse(x);}catch(Exception ignored){}return null;}
  static String tidyDate(String s){return s==null?"":s.replaceAll("\\s+"," ").trim();}
  static String cleanTitle(String s,String journal){String x=cleanHtml(s);x=x.replaceAll("(?i)^call for papers\\s*[:\\-–]?\\s*","").replaceAll("(?i)^special (issue|section)\\s*[:\\-–]?\\s*","Special $1: ").replaceAll("(?i)\\s*[|\\-–]\\s*(IEEE|ACM|Elsevier).*$","").trim();if(x.equalsIgnoreCase(journal))return"";return x;}
  static String extractHtmlTitle(String h){Matcher m=Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(h);return m.find()?cleanHtml(m.group(1)):"";}
  static String cleanHtml(String s){if(s==null)return"";return s.replaceAll("(?is)<script.*?</script>"," ").replaceAll("(?is)<style.*?</style>"," ").replaceAll("(?is)<[^>]+>"," ").replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replace("&nbsp;"," ").replaceAll("\\s+"," ").trim();}
  static String unwrap(String u){String x=u.replace("&amp;","&");try{if(x.startsWith("//"))x="https:"+x;Uri q=Uri.parse(x);String v=q.getQueryParameter("uddg");if(v!=null&&!v.isEmpty())return URLDecoder.decode(v,"UTF-8");}catch(Exception ignored){}return x;}
  static String host(String u){try{return new URL(u).getHost().replaceFirst("^www\\.","");}catch(Exception e){return"网页";}}
  static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
  static String get(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(13000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");c.setRequestProperty("Accept-Language","en-US,en;q=0.9");int code=c.getResponseCode();if(code<200||code>=400)throw new IOException("HTTP "+code);StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null&&b.length()<900000)b.append(line).append('\n');}finally{c.disconnect();}return b.toString();}
  JSONArray arr(String k){try{return new JSONArray(p.getString(k,"[]"));}catch(Exception e){return new JSONArray();}}
  void openLink(String u){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception ignored){}}

  static class Candidate{String title,url,snippet;Candidate(String t,String u,String s){title=t;url=u;snippet=s;}}
  View empty(String a,String b){LinearLayout c=card();c.setPadding(dp(18),dp(17),dp(18),dp(17));c.addView(txt(a,15,INK,Typeface.BOLD));TextView d=txt(b,12,MUTED,0);margin(d,0,5,0,0);c.addView(d);return c;}
  TextView chip(String s,int color){TextView t=txt(s,11,color,Typeface.BOLD);t.setPadding(dp(8),dp(3),dp(8),dp(3));t.setBackground(round(Color.WHITE,99,color));return t;}
  Button btn(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?Color.WHITE:INK);b.setBackground(round(primary?PURPLE:Color.WHITE,13,primary?PURPLE:BORDER));return b;}
  LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setBackground(round(Color.WHITE,15,BORDER));l.setElevation(dp(1));return l;}
  TextView txt(String s,int z,int c,int st){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setTypeface(Typeface.DEFAULT,st);return t;}
  GradientDrawable round(int fill,float rad,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(rad));g.setStroke(dp(1),stroke);return g;}
  void margin(View v,int l,int t,int r,int b){ViewGroup.MarginLayoutParams p=v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams?(ViewGroup.MarginLayoutParams)v.getLayoutParams():new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));v.setLayoutParams(p);}
  int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
