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
  static final int INK=Color.rgb(24,32,46), MUTED=Color.rgb(102,113,130), BLUE=Color.rgb(54,91,203), BG=Color.rgb(246,248,251), BORDER=Color.rgb(225,230,239), GREEN=Color.rgb(26,122,82), ORANGE=Color.rgb(174,96,25);
  static final String PREF="sw_v1", SCH="scholars", FEED="feed", SYNC="sync", MIGRATION="match_v2";
  SharedPreferences p; LinearLayout root, scholarBox, feedBox; TextView stat, syncView; ProgressBar bar; Button refresh;
  ExecutorService ex=Executors.newSingleThreadExecutor(); Handler ui=new Handler(Looper.getMainLooper());

  @Override public void onCreate(Bundle b){ super.onCreate(b); p=getSharedPreferences(PREF,MODE_PRIVATE); migrateUnsafeMatches(); build(); schedule(); notifyPermission(); render(); }
  @Override public void onDestroy(){ ex.shutdownNow(); super.onDestroy(); }

  void migrateUnsafeMatches(){
    if(p.getBoolean(MIGRATION,false)) return;
    JSONArray s=arr(SCH), f=arr(FEED), nf=new JSONArray();
    for(int i=0;i<s.length();i++){ JSONObject o=s.optJSONObject(i); if(o==null)continue; try{o.put("aid","");o.put("instId","");o.put("matchName","");o.put("matchInst","");o.put("verified",false);}catch(Exception ignored){} }
    for(int i=0;i<f.length();i++){JSONObject o=f.optJSONObject(i);if(o!=null&&!"论文".equals(o.optString("cat")))nf.put(o);}
    p.edit().putString(SCH,s.toString()).putString(FEED,nf.toString()).putBoolean(MIGRATION,true).apply();
  }

  void build(){
    ScrollView sc=new ScrollView(this); sc.setFillViewport(true); sc.setBackgroundColor(BG);
    root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(28),dp(18),dp(34)); sc.addView(root); setContentView(sc);
    LinearLayout hero=card(); hero.setPadding(dp(20),dp(20),dp(20),dp(20));
    hero.addView(txt("ScholarWatch",28,INK,Typeface.BOLD)); TextView sub=txt("学者动态雷达 · 严格身份匹配",14,MUTED,0); margin(sub,0,4,0,14); hero.addView(sub);
    stat=txt("0 位关注学者",14,INK,Typeface.BOLD); hero.addView(stat); syncView=txt("尚未同步",12,MUTED,0); margin(syncView,0,4,0,0); hero.addView(syncView); root.addView(hero);
    LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); margin(actions,0,14,0,0);
    Button add=btn("＋ 添加学者",true); add.setOnClickListener(v->addDialog()); refresh=btn("↻ 立即刷新",false); refresh.setOnClickListener(v->refreshNow());
    LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(48),1); a.setMarginEnd(dp(7)); actions.addView(add,a); LinearLayout.LayoutParams r=new LinearLayout.LayoutParams(0,dp(48),1); r.setMarginStart(dp(7)); actions.addView(refresh,r); root.addView(actions);
    bar=new ProgressBar(this); bar.setVisibility(View.GONE); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(26),dp(26)); bp.gravity=Gravity.CENTER_HORIZONTAL; bp.setMargins(0,dp(10),0,0); root.addView(bar,bp);
    title("关注列表"); scholarBox=new LinearLayout(this); scholarBox.setOrientation(LinearLayout.VERTICAL); root.addView(scholarBox);
    title("最新动态"); feedBox=new LinearLayout(this); feedBox.setOrientation(LinearLayout.VERTICAL); root.addView(feedBox);
    TextView foot=txt("v0.2：OpenAlex 论文只有在“姓名 + 单位”通过身份校验后才会导入；不确定时保持待确认，不再按同名自动绑定。基金/人才/获奖/参会仍来自公开新闻候选。",11,MUTED,0); foot.setLineSpacing(0,1.25f); margin(foot,2,14,2,0); root.addView(foot);
  }

  void render(){
    JSONArray ss=arr(SCH), ff=arr(FEED); stat.setText(ss.length()+" 位关注学者"); long s=p.getLong(SYNC,0); syncView.setText(s==0?"尚未同步":"最近同步："+new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date(s)));
    scholarBox.removeAllViews(); if(ss.length()==0) scholarBox.addView(empty("还没有关注学者","请填写姓名与单位；英文名能显著提高匹配准确率。"));
    for(int i=0;i<ss.length();i++){JSONObject o=ss.optJSONObject(i);if(o!=null)scholarBox.addView(scholarCard(o));}
    feedBox.removeAllViews(); if(ff.length()==0) feedBox.addView(empty("暂无动态",ss.length()==0?"请先添加学者。":"完成身份匹配后点击“立即刷新”。"));
    for(int i=0;i<Math.min(60,ff.length());i++){JSONObject o=ff.optJSONObject(i);if(o!=null)feedBox.addView(feedCard(o));}
  }

  View scholarCard(JSONObject o){
    LinearLayout c=card(); c.setPadding(dp(16),dp(13),dp(10),dp(13)); margin(c,0,0,0,10); LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout left=new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL); left.addView(txt(o.optString("name"),16,INK,Typeface.BOLD));
    String inst=o.optString("inst","未填写单位"),aid=o.optString("aid"),matchName=o.optString("matchName"),matchInst=o.optString("matchInst"); boolean verified=o.optBoolean("verified",false)&&!aid.isEmpty();
    TextView t=txt(inst+" · "+(verified?"✓ 身份已验证":"⚠ 待确认"),12,verified?GREEN:ORANGE,0); margin(t,0,3,0,0); left.addView(t);
    if(verified){TextView m=txt("OpenAlex: "+(matchName.isEmpty()?aid:matchName)+(matchInst.isEmpty()?"":" · "+matchInst),10,MUTED,0);margin(m,0,3,0,0);left.addView(m);} row.addView(left,new LinearLayout.LayoutParams(0,-2,1));
    LinearLayout buttons=new LinearLayout(this); buttons.setOrientation(LinearLayout.VERTICAL); Button rematch=btn(verified?"重匹配":"匹配",false);rematch.setTextSize(11);rematch.setOnClickListener(v->matchExistingDialog(o.optString("id")));buttons.addView(rematch,new LinearLayout.LayoutParams(dp(70),dp(38)));
    Button d=btn("删除",false);d.setTextSize(11);d.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("删除关注").setMessage("删除 "+o.optString("name")+" 及其本地动态？").setNegativeButton("取消",null).setPositiveButton("删除",(x,w)->{remove(o.optString("id"));render();}).show());LinearLayout.LayoutParams dd=new LinearLayout.LayoutParams(dp(70),dp(38));dd.topMargin=dp(5);buttons.addView(d,dd);row.addView(buttons);c.addView(row);return c;
  }

  View feedCard(JSONObject o){LinearLayout c=card();c.setPadding(dp(16),dp(14),dp(16),dp(14));margin(c,0,0,0,10);String cat=o.optString("cat","动态");TextView b=txt(cat,11,catColor(cat),Typeface.BOLD);b.setPadding(dp(8),dp(3),dp(8),dp(3));b.setBackground(round(Color.rgb(239,243,255),99,BORDER));c.addView(b,new LinearLayout.LayoutParams(-2,-2));TextView who=txt(o.optString("scholar")+" · "+o.optString("date"),11,MUTED,0);margin(who,0,7,0,0);c.addView(who);TextView title=txt(o.optString("title"),15,INK,Typeface.BOLD);title.setLineSpacing(0,1.14f);margin(title,0,5,0,0);c.addView(title);String st=o.optString("sub");if(!st.isEmpty()){TextView s=txt(st,12,MUTED,0);s.setMaxLines(2);margin(s,0,6,0,0);c.addView(s);}String url=o.optString("url");if(!url.isEmpty())c.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){}});return c;}

  void addDialog(){LinearLayout f=form();EditText n=input("中文名 / 常用姓名（必填）"),en=input("英文名（可选，例如 Jinyang Huang）"),i=input("学校 / 研究机构（必填，用于消歧）");f.addView(n);f.addView(en);f.addView(i);AlertDialog d=new AlertDialog.Builder(this).setTitle("添加关注学者").setMessage("新版不会仅凭同名自动绑定。单位必须参与验证；有英文名时建议填写。").setView(f).setNegativeButton("取消",null).setPositiveButton("查找身份",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String name=n.getText().toString().trim(),eng=en.getText().toString().trim(),inst=i.getText().toString().trim();if(name.isEmpty()){n.setError("请输入姓名");return;}if(inst.isEmpty()){i.setError("请填写单位以避免同名误匹配");return;}d.dismiss();searchAndAdd(name,eng,inst);}));d.show();}
  void matchExistingDialog(String sid){JSONObject s=findScholar(sid);if(s==null)return;LinearLayout f=form();EditText en=input("英文名（可选，例如 Jinyang Huang）");f.addView(en);new AlertDialog.Builder(this).setTitle("重新匹配 "+s.optString("name")).setMessage("单位："+s.optString("inst")+"\n如果中文名搜索不到正确作者，请补充英文名。").setView(f).setNegativeButton("取消",null).setPositiveButton("查找",(d,w)->searchCandidatesForExisting(sid,en.getText().toString().trim())).show();}
  void searchAndAdd(String name,String eng,String inst){busy(true);ex.execute(()->{try{JSONArray c=resolveCandidates(name,eng,inst);ui.post(()->{busy(false);if(c.length()==0){add(name,eng,inst,"","","","",false);render();showNoMatch(name,inst);}else showCandidateDialog(name,eng,inst,"",c,true);});}catch(Exception e){ui.post(()->{busy(false);Toast.makeText(this,"身份查询失败，请稍后重试",Toast.LENGTH_LONG).show();});}});}
  void searchCandidatesForExisting(String sid,String eng){JSONObject s=findScholar(sid);if(s==null)return;busy(true);ex.execute(()->{try{JSONArray c=resolveCandidates(s.optString("name"),eng,s.optString("inst"));ui.post(()->{busy(false);if(c.length()==0)showNoMatch(s.optString("name"),s.optString("inst"));else showCandidateDialog(s.optString("name"),eng,s.optString("inst"),sid,c,false);});}catch(Exception e){ui.post(()->{busy(false);Toast.makeText(this,"身份查询失败，请稍后重试",Toast.LENGTH_LONG).show();});}});}
  void showCandidateDialog(String name,String eng,String inst,String sid,JSONArray cs,boolean isNew){String[] labels=new String[cs.length()];for(int i=0;i<cs.length();i++){JSONObject c=cs.optJSONObject(i);labels[i]=(c==null?"":c.optString("display"))+"\n"+(c==null?"":c.optString("inst"))+" · "+(c==null?0:c.optInt("works"))+" 篇";}new AlertDialog.Builder(this).setTitle("确认学者身份").setMessage("以下候选均通过了单位校验。请选择正确的人；不确定可取消。").setItems(labels,(d,which)->{JSONObject c=cs.optJSONObject(which);if(c==null)return;if(isNew)add(name,eng,inst,c.optString("aid"),c.optString("instId"),c.optString("display"),c.optString("inst"),true);else updateMatch(sid,eng,c);render();refreshNow();}).setNegativeButton("暂不匹配",(d,w)->{if(isNew){add(name,eng,inst,"","","","",false);render();}}).show();}
  void showNoMatch(String name,String inst){new AlertDialog.Builder(this).setTitle("未找到可信匹配").setMessage("没有找到同时满足“"+name+" + "+inst+"”的 OpenAlex 作者。\n\n这比错误绑定更安全。可以点击该学者的“匹配”按钮补充英文名后重试。").setPositiveButton("知道了",null).show();}

  void refreshNow(){if(arr(SCH).length()==0){Toast.makeText(this,"请先添加学者",Toast.LENGTH_SHORT).show();return;}busy(true);ex.execute(()->{int[] res=refreshAll(this);ui.post(()->{busy(false);render();Toast.makeText(this,"新增 "+res[0]+" 条动态"+(res[1]>0?"，"+res[1]+" 位刷新异常":""),Toast.LENGTH_SHORT).show();});});}
  void busy(boolean x){bar.setVisibility(x?View.VISIBLE:View.GONE);refresh.setEnabled(!x);}
  synchronized void add(String name,String eng,String inst,String aid,String instId,String matchName,String matchInst,boolean verified){JSONArray a=arr(SCH);JSONObject o=new JSONObject();try{o.put("id",UUID.randomUUID().toString());o.put("name",name);o.put("eng",eng);o.put("inst",inst);o.put("aid",aid);o.put("instId",instId);o.put("matchName",matchName);o.put("matchInst",matchInst);o.put("verified",verified);a.put(o);}catch(Exception ignored){}p.edit().putString(SCH,a.toString()).apply();}
  synchronized void updateMatch(String sid,String eng,JSONObject c){JSONArray a=arr(SCH);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&sid.equals(o.optString("id"))){try{o.put("eng",eng);o.put("aid",c.optString("aid"));o.put("instId",c.optString("instId"));o.put("matchName",c.optString("display"));o.put("matchInst",c.optString("inst"));o.put("verified",true);}catch(Exception ignored){}}}clearPapersForScholar(sid);p.edit().putString(SCH,a.toString()).apply();}
  synchronized void clearPapersForScholar(String sid){JSONArray f=arr(FEED),nf=new JSONArray();for(int i=0;i<f.length();i++){JSONObject o=f.optJSONObject(i);if(o!=null&&!(sid.equals(o.optString("sid"))&&"论文".equals(o.optString("cat"))))nf.put(o);}p.edit().putString(FEED,nf.toString()).apply();}
  synchronized void remove(String id){JSONArray s=arr(SCH),ns=new JSONArray();for(int i=0;i<s.length();i++){JSONObject o=s.optJSONObject(i);if(o!=null&&!id.equals(o.optString("id")))ns.put(o);}JSONArray f=arr(FEED),nf=new JSONArray();for(int i=0;i<f.length();i++){JSONObject o=f.optJSONObject(i);if(o!=null&&!id.equals(o.optString("sid")))nf.put(o);}p.edit().putString(SCH,ns.toString()).putString(FEED,nf.toString()).apply();}
  JSONObject findScholar(String id){JSONArray s=arr(SCH);for(int i=0;i<s.length();i++){JSONObject o=s.optJSONObject(i);if(o!=null&&id.equals(o.optString("id")))return o;}return null;}
  JSONArray arr(String k){try{return new JSONArray(p.getString(k,"[]"));}catch(Exception e){return new JSONArray();}}
  int[] refreshAll(Context ctx){int added=0,err=0;JSONArray scholars=arr(SCH);for(int i=0;i<scholars.length();i++){JSONObject s=scholars.optJSONObject(i);if(s==null)continue;try{List<JSONObject> incoming=new ArrayList<>();if(s.optBoolean("verified",false)&&!s.optString("aid").isEmpty())incoming.addAll(papers(s));incoming.addAll(news(s));added+=merge(incoming);}catch(Exception e){err++;}}p.edit().putLong(SYNC,System.currentTimeMillis()).apply();return new int[]{added,err};}

  JSONArray resolveCandidates(String name,String eng,String inst)throws Exception{String instId=resolveInstitution(inst);if(instId.isEmpty())return new JSONArray();LinkedHashMap<String,JSONObject> authors=new LinkedHashMap<>();collectAuthors(name,authors);if(!eng.isEmpty()&&!norm(eng).equals(norm(name)))collectAuthors(eng,authors);List<JSONObject> good=new ArrayList<>();for(JSONObject a:authors.values()){if(!authorHasInstitution(a,instId))continue;int ns=Math.max(nameScore(a,name),eng.isEmpty()?0:nameScore(a,eng));if(ns<35)continue;JSONObject c=new JSONObject();c.put("aid",shortId(a.optString("id")));c.put("instId",instId);c.put("display",a.optString("display_name"));c.put("inst",bestInstitutionLabel(a,instId,inst));c.put("works",a.optInt("works_count"));c.put("score",ns);good.add(c);}good.sort((x,y)->{int c=Integer.compare(y.optInt("score"),x.optInt("score"));return c!=0?c:Integer.compare(y.optInt("works"),x.optInt("works"));});JSONArray out=new JSONArray();for(int i=0;i<Math.min(5,good.size());i++)out.put(good.get(i));return out;}
  String resolveInstitution(String inst)throws Exception{if(inst.trim().isEmpty())return"";JSONObject root=new JSONObject(get("https://api.openalex.org/institutions?search="+Uri.encode(inst)+"&per-page=8"));JSONArray rs=root.optJSONArray("results");if(rs==null||rs.length()==0)return"";String ni=norm(inst);int best=-1;String id="";for(int i=0;i<rs.length();i++){JSONObject z=rs.optJSONObject(i);if(z==null)continue;int sc=0;String dn=norm(z.optString("display_name"));if(dn.equals(ni))sc=100;else if(dn.contains(ni)||ni.contains(dn))sc=70;JSONArray alt=z.optJSONArray("display_name_alternatives");if(alt!=null)for(int j=0;j<alt.length();j++){String a=norm(alt.optString(j));if(a.equals(ni))sc=Math.max(sc,100);else if(a.contains(ni)||ni.contains(a))sc=Math.max(sc,75);}if(sc>best){best=sc;id=shortId(z.optString("id"));}}if(best<=0){JSONObject top=rs.optJSONObject(0);id=top==null?"":shortId(top.optString("id"));}return id;}
  void collectAuthors(String q,Map<String,JSONObject> out)throws Exception{JSONObject root=new JSONObject(get("https://api.openalex.org/authors?search="+Uri.encode(q)+"&per-page=25"));JSONArray rs=root.optJSONArray("results");if(rs==null)return;for(int i=0;i<rs.length();i++){JSONObject a=rs.optJSONObject(i);if(a!=null)out.put(shortId(a.optString("id")),a);}}
  int nameScore(JSONObject a,String q){String nq=norm(q);if(nq.isEmpty())return 0;int sc=0;String dn=norm(a.optString("display_name"));if(dn.equals(nq))sc=100;else if(dn.contains(nq)||nq.contains(dn))sc=65;JSONArray alt=a.optJSONArray("display_name_alternatives");if(alt!=null)for(int i=0;i<alt.length();i++){String x=norm(alt.optString(i));if(x.equals(nq))sc=Math.max(sc,100);else if(x.contains(nq)||nq.contains(x))sc=Math.max(sc,70);}return sc;}
  boolean authorHasInstitution(JSONObject a,String instId){JSONArray ins=a.optJSONArray("last_known_institutions");if(hasInstitutionId(ins,instId))return true;JSONArray aff=a.optJSONArray("affiliations");if(aff!=null)for(int i=0;i<aff.length();i++){JSONObject z=aff.optJSONObject(i);JSONObject in=z==null?null:z.optJSONObject("institution");if(in!=null&&instId.equals(shortId(in.optString("id"))))return true;}return false;}
  boolean hasInstitutionId(JSONArray arr,String instId){if(arr==null)return false;for(int i=0;i<arr.length();i++){JSONObject z=arr.optJSONObject(i);if(z!=null&&instId.equals(shortId(z.optString("id"))))return true;}return false;}
  String bestInstitutionLabel(JSONObject a,String instId,String fallback){JSONArray ins=a.optJSONArray("last_known_institutions");if(ins!=null)for(int i=0;i<ins.length();i++){JSONObject z=ins.optJSONObject(i);if(z!=null&&instId.equals(shortId(z.optString("id"))))return z.optString("display_name",fallback);}JSONArray aff=a.optJSONArray("affiliations");if(aff!=null)for(int i=0;i<aff.length();i++){JSONObject z=aff.optJSONObject(i);JSONObject in=z==null?null:z.optJSONObject("institution");if(in!=null&&instId.equals(shortId(in.optString("id"))))return in.optString("display_name",fallback);}return fallback;}

  List<JSONObject> papers(JSONObject s)throws Exception{List<JSONObject> out=new ArrayList<>();String aid=s.optString("aid");JSONObject root=new JSONObject(get("https://api.openalex.org/works?filter="+Uri.encode("authorships.author.id:"+aid)+"&sort=publication_date:desc&per-page=15"));JSONArray rs=root.optJSONArray("results");if(rs==null)return out;for(int i=0;i<rs.length();i++){JSONObject w=rs.optJSONObject(i);if(w==null)continue;String id=w.optString("id"),url="",venue="";JSONObject loc=w.optJSONObject("primary_location");if(loc!=null){url=loc.optString("landing_page_url");JSONObject src=loc.optJSONObject("source");if(src!=null)venue=src.optString("display_name");}if(url.isEmpty())url=!w.optString("doi").isEmpty()?w.optString("doi"):id;out.add(item("paper:"+id,s,"论文",w.optString("title"),venue.isEmpty()?"OpenAlex 收录论文":venue,w.optString("publication_date"),url));}return out;}
  List<JSONObject> news(JSONObject s)throws Exception{List<JSONObject> out=new ArrayList<>();Set<String> seen=new HashSet<>();String name=s.optString("name"),inst=s.optString("inst"),eng=s.optString("eng");String who="\""+name+"\" \""+inst+"\"";String[] qs={who+" (优青 OR 杰青 OR 面上 OR 国家自然科学基金 OR 获批 OR 入选 OR 获奖)",who+" (会议 OR 大会 OR 论坛 OR 报告 OR keynote OR conference OR workshop OR 受邀)"};for(String q:qs){String u="https://news.google.com/rss/search?q="+Uri.encode(q)+"&hl=zh-CN&gl=CN&ceid=CN:zh-Hans";for(Map<String,String> e:rss(u)){String hay=e.get("title")+" "+e.get("description"),low=hay.toLowerCase(Locale.ROOT);boolean hasName=low.contains(name.toLowerCase(Locale.ROOT))||(!eng.isEmpty()&&low.contains(eng.toLowerCase(Locale.ROOT)));if(!hasName)continue;String id="news:"+sha(e.get("link")+e.get("title"));if(!seen.add(id))continue;out.add(item(id,s,classify(hay),e.get("title"),e.get("source"),rssDate(e.get("pubDate")),e.get("link")));}}return out;}

  JSONObject item(String id,JSONObject s,String cat,String title,String sub,String date,String url){JSONObject o=new JSONObject();try{o.put("id",id);o.put("sid",s.optString("id"));o.put("scholar",s.optString("name"));o.put("cat",cat);o.put("title",title);o.put("sub",sub==null?"":sub);o.put("date",date==null?"":date);o.put("url",url==null?"":url);o.put("ts",System.currentTimeMillis());}catch(Exception ignored){}return o;}
  synchronized int merge(List<JSONObject> in){JSONArray a=arr(FEED);Set<String> ids=new HashSet<>();List<JSONObject> all=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null){all.add(o);ids.add(o.optString("id"));}}int n=0;for(JSONObject o:in)if(ids.add(o.optString("id"))){all.add(o);n++;}all.sort((x,y)->y.optString("date").compareTo(x.optString("date")));JSONArray out=new JSONArray();for(int i=0;i<Math.min(300,all.size());i++)out.put(all.get(i));p.edit().putString(FEED,out.toString()).apply();return n;}
  static List<Map<String,String>> rss(String url)throws Exception{List<Map<String,String>> out=new ArrayList<>();HttpURLConnection c=open(url);try(InputStream in=c.getInputStream()){XmlPullParser x=Xml.newPullParser();x.setInput(in,"UTF-8");Map<String,String> cur=null;String tag="";int e=x.getEventType();while(e!=XmlPullParser.END_DOCUMENT){if(e==XmlPullParser.START_TAG){tag=x.getName();if("item".equalsIgnoreCase(tag)){cur=new HashMap<>();cur.put("title","");cur.put("link","");cur.put("description","");cur.put("pubDate","");cur.put("source","");}}else if(e==XmlPullParser.TEXT&&cur!=null){String v=x.getText();if(cur.containsKey(tag))cur.put(tag,cur.get(tag)+v);}else if(e==XmlPullParser.END_TAG){if("item".equalsIgnoreCase(x.getName())&&cur!=null){out.add(cur);cur=null;}tag="";}e=x.next();}}finally{c.disconnect();}return out;}
  static HttpURLConnection open(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(16000);c.setRequestProperty("User-Agent","ScholarWatch/0.2 Android");int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code);return c;}
  static String get(String u)throws Exception{HttpURLConnection c=open(u);StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)b.append(l);}finally{c.disconnect();}return b.toString();}
  static String classify(String s){String t=s.toLowerCase(Locale.ROOT);if(any(t,"优青","优秀青年","杰青","杰出青年","面上","国家自然科学基金","获批","基金","grant"))return"基金/人才";if(any(t,"获奖","award"))return"获奖";if(any(t,"会议","大会","论坛","报告","受邀","keynote","conference","workshop"))return"参会/报告";return"新闻";}
  static boolean any(String s,String...w){for(String x:w)if(s.contains(x.toLowerCase(Locale.ROOT)))return true;return false;}
  static String shortId(String s){int i=s.lastIndexOf('/');return i>=0?s.substring(i+1):s;}
  static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]","");}
  static String sha(String s){try{byte[] d=MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format(Locale.US,"%02x",x));return b.toString();}catch(Exception e){return Integer.toHexString(s.hashCode());}}
  static String rssDate(String s){try{Date d=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",Locale.US).parse(s);return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(d);}catch(Exception e){return s!=null&&s.length()>=10?s.substring(0,10):"";}}
  void schedule(){JobScheduler js=(JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);JobInfo j=new JobInfo.Builder(260821,new ComponentName(this,RefreshJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(7L*24*60*60*1000).setPersisted(true).build();js.schedule(j);}
  void notifyPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);}
  LinearLayout form(){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(22),0,dp(22),0);return f;}
  EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine();e.setInputType(InputType.TYPE_CLASS_TEXT);return e;}
  void title(String s){TextView t=txt(s,16,INK,Typeface.BOLD);margin(t,2,22,2,10);root.addView(t);}
  LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setBackground(round(Color.WHITE,15,BORDER));l.setElevation(dp(1));return l;}
  View empty(String a,String b){LinearLayout c=card();c.setPadding(dp(18),dp(17),dp(18),dp(17));c.addView(txt(a,15,INK,Typeface.BOLD));TextView d=txt(b,12,MUTED,0);margin(d,0,5,0,0);c.addView(d);return c;}
  Button btn(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?Color.WHITE:INK);b.setBackground(round(primary?BLUE:Color.WHITE,13,primary?BLUE:BORDER));return b;}
  TextView txt(String s,int z,int c,int st){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setTypeface(Typeface.DEFAULT,st);return t;}
  GradientDrawable round(int fill,float rad,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(rad));g.setStroke(dp(1),stroke);return g;}
  void margin(View v,int l,int t,int r,int b){ViewGroup.MarginLayoutParams p=v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams?(ViewGroup.MarginLayoutParams)v.getLayoutParams():new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));v.setLayoutParams(p);}
  int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);} int catColor(String c){if(c.contains("基金"))return Color.rgb(151,74,18);if(c.contains("获奖"))return Color.rgb(132,76,173);if(c.contains("参会"))return Color.rgb(0,111,105);return BLUE;}
}
