package com.scholarwatch.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

public class HomeActivity extends Activity {
  static final int INK=Color.rgb(24,32,46),MUTED=Color.rgb(103,113,129),BLUE=Color.rgb(57,91,206),BG=Color.rgb(246,248,251),BORDER=Color.rgb(225,230,239);
  LinearLayout root;
  @Override public void onCreate(Bundle b){super.onCreate(b);build();}
  void build(){ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(30),dp(18),dp(36));sc.addView(root);setContentView(sc);
    LinearLayout hero=card();hero.setPadding(dp(20),dp(20),dp(20),dp(20));hero.addView(txt("ScholarWatch",29,INK,Typeface.BOLD));TextView sub=txt("学术情报 · 人才 · 基金 · 特刊",14,MUTED,0);margin(sub,0,5,0,0);hero.addView(sub);root.addView(hero);
    section("研究情报");root.addView(navCard("国自然 · 学会 · 人才雷达","追踪国自然、主要学会、优青/杰青/院士/海优动态","人才雷达",TalentRadarActivity.class,BLUE));
    root.addView(navCard("CCF-A 英文特刊","搜索普适计算相关 CCF-A 期刊的 Special Issue / Special Section / CFP","特刊搜索",SpecialIssueActivity.class,Color.rgb(119,76,171)));
    section("个人关注");root.addView(navCard("重点关注 · 论文","管理学者身份，严格匹配 OpenAlex 并追踪论文","学者关注",MainActivity.class,Color.rgb(31,125,91)));
    TextView foot=txt("v0.4 · CCF-A 特刊页优先显示特刊名与投稿截止时间；无法可靠识别日期时标记为待核验。",11,MUTED,0);foot.setLineSpacing(0,1.25f);margin(foot,2,18,2,0);root.addView(foot);
  }
  View navCard(String title,String desc,String action,Class<?> cls,int accent){LinearLayout c=card();c.setPadding(dp(17),dp(16),dp(17),dp(16));margin(c,0,0,0,11);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);TextView t=txt(title,17,INK,Typeface.BOLD);l.addView(t);TextView d=txt(desc,12,MUTED,0);d.setLineSpacing(0,1.2f);margin(d,0,6,0,0);l.addView(d);row.addView(l,new LinearLayout.LayoutParams(0,-2,1));TextView chip=txt(action,11,accent,Typeface.BOLD);chip.setPadding(dp(10),dp(7),dp(10),dp(7));chip.setBackground(round(Color.WHITE,99,accent));row.addView(chip);c.addView(row);c.setOnClickListener(v->startActivity(new Intent(this,cls)));return c;}
  void section(String s){TextView t=txt(s,16,INK,Typeface.BOLD);margin(t,2,23,2,10);root.addView(t);}
  LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setBackground(round(Color.WHITE,15,BORDER));l.setElevation(dp(1));return l;}
  TextView txt(String s,int z,int c,int st){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setTypeface(Typeface.DEFAULT,st);return t;}
  GradientDrawable round(int fill,float rad,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(rad));g.setStroke(dp(1),stroke);return g;}
  void margin(View v,int l,int t,int r,int b){ViewGroup.MarginLayoutParams p=v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams?(ViewGroup.MarginLayoutParams)v.getLayoutParams():new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));v.setLayoutParams(p);}
  int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
