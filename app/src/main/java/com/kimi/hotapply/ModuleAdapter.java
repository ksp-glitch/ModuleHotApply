package com.kimi.hotapply;

import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class ModuleAdapter extends BaseAdapter {
    private final List<ModuleItem> items;
    private final Listener listener;
    private int mode;
    public interface Listener { void onAction(ModuleItem item); }
    public ModuleAdapter(List<ModuleItem> items, Listener listener) { this.items=items; this.listener=listener; }
    public void setMode(int mode) { this.mode=mode; notifyDataSetChanged(); }
    public int getCount(){return items.size();}
    public ModuleItem getItem(int p){return items.get(p);}
    public long getItemId(int p){return p;}
    public View getView(int position,View convert,ViewGroup parent){
        LinearLayout root=new LinearLayout(parent.getContext());root.setOrientation(LinearLayout.VERTICAL);int pad=dp(parent,16);root.setPadding(pad,pad,pad,pad);
        TextView title=new TextView(parent.getContext());title.setTextSize(17);title.setTextColor(Color.WHITE);title.setTypeface(null,1);root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView sub=new TextView(parent.getContext());sub.setTextSize(13);sub.setTextColor(0xffa8b0bd);sub.setPadding(0,dp(parent,7),0,0);root.addView(sub,new LinearLayout.LayoutParams(-1,-2));
        Button action=new Button(parent.getContext());action.setAllCaps(false);action.setTextSize(14);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(parent,44));bp.topMargin=dp(parent,10);root.addView(action,bp);
        View divider=new View(parent.getContext());divider.setBackgroundColor(0xff2b3038);root.addView(divider,new LinearLayout.LayoutParams(-1,dp(parent,1)));
        ModuleItem item=getItem(position);title.setText(item.name);sub.setText(item.summary(mode));
        if(mode==0){action.setText("免重启安装 / 更新");action.setEnabled(item.update&&item.hotInstall());}
        else if(mode==1){action.setText("免重启卸载");action.setEnabled(item.hotUninstall()&&!item.markedRemove);}
        else{action.setText("必须重启 / 仅查看");action.setEnabled(false);}
        action.setOnClickListener(v->listener.onAction(item));return root;
    }
    private static int dp(View v,int n){return Math.round(n*v.getResources().getDisplayMetrics().density);}
}
