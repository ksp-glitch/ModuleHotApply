package com.kimi.hotapply;

import android.app.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private final List<ModuleItem> all = new ArrayList<>(), shown = new ArrayList<>();
    private ModuleAdapter adapter;
    private TextView header, log;
    private Button refresh;
    private boolean busy, keepBackup;
    private int mode;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xff111318);
        header = new TextView(this); header.setTextSize(18); header.setTextColor(Color.WHITE); header.setPadding(dp(18),dp(24),dp(18),dp(12));
        root.addView(header,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
        String[] names={"安装 / 更新","卸载 / 移除","必须重启"};
        for(int i=0;i<3;i++){ final int m=i; Button b=new Button(this); b.setText(names[i]); b.setAllCaps(false); b.setTextSize(13);
            b.setOnClickListener(v->{mode=m; show();}); tabs.addView(b,new LinearLayout.LayoutParams(0,dp(50),1)); }
        refresh=new Button(this); refresh.setText("刷新模块"); refresh.setAllCaps(false); refresh.setOnClickListener(v->load());
        keepBackup=getSharedPreferences("settings",MODE_PRIVATE).getBoolean("keep_backup",false);
        Switch backupSwitch=new Switch(this); backupSwitch.setText("保留备份"); backupSwitch.setTextColor(0xffd5dce8); backupSwitch.setChecked(keepBackup);
        backupSwitch.setPadding(dp(16),0,0,0); backupSwitch.setOnCheckedChangeListener((v,on)->{keepBackup=on;getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("keep_backup",on).apply();});
        LinearLayout options=new LinearLayout(this); options.setOrientation(LinearLayout.HORIZONTAL);
        options.addView(backupSwitch,new LinearLayout.LayoutParams(0,dp(48),1));
        options.addView(refresh,new LinearLayout.LayoutParams(0,dp(48),1)); root.addView(options,new LinearLayout.LayoutParams(-1,-2));
        ListView list=new ListView(this); list.setDividerHeight(0); adapter=new ModuleAdapter(shown,this::confirmAction); list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        log=new TextView(this); log.setTextSize(12); log.setTextColor(0xffb6c0cf); log.setPadding(dp(16),dp(8),dp(16),dp(8)); log.setMaxLines(5);
        root.addView(log,new LinearLayout.LayoutParams(-1,-2)); setContentView(root); load();
    }

    private void show() {
        shown.clear();
        for(ModuleItem m:all){
            if(mode==0 && m.update && m.hotInstall()) shown.add(m);
            else if(mode==1 && m.installed && m.hotUninstall() && !m.update) shown.add(m);
            else if(mode==2 && !((m.update&&m.hotInstall())||(m.installed&&m.hotUninstall()))) shown.add(m);
        }
        adapter.setMode(mode); adapter.notifyDataSetChanged();
        String title=mode==0?"安装 / 更新":"卸载 / 移除";
        if(mode==2) title="必须重启";
        header.setText(title+" · "+shown.size()+" 个");
    }

    private void load() {
        if(busy)return; busy=true; refresh.setEnabled(false); header.setText("正在读取 Root 模块…");
        new Thread(()->{
            String raw=Root.run("id -u; for p in /data/adb/modules/* /data/adb/modules_update/*; do [ -f \"$p/module.prop\" ] || continue; printf '\\n@@%s|%s\\n' \"${p##*/}\" \"$p\"; sed -n 's/^name=/name=/p;s/^version=/version=/p;s/^description=/description=/p' \"$p/module.prop\"; [ -f \"$p/disable\" ] && echo disabled=1; [ -f \"$p/service.sh\" ] && echo service=1; [ -f \"$p/remove\" ] && echo remove=1; done");
            Map<String,ModuleItem> map=new LinkedHashMap<>(); ModuleItem cur=null;
            for(String line:raw.split("\\r?\\n")){
                if(line.startsWith("@@")){String[] p=line.substring(2).split("\\|",2); cur=map.computeIfAbsent(p[0],k->{ModuleItem m=new ModuleItem();m.id=k;m.name=k;m.version="未知版本";m.description="";return m;}); if(p[1].contains("modules_update"))cur.update=true;else cur.installed=true;}
                else if(cur!=null&&line.contains("=")){int n=line.indexOf('=');String k=line.substring(0,n),v=line.substring(n+1);switch(k){case"name":cur.name=v;break;case"version":cur.version=v;break;case"description":cur.description=v;break;case"disabled":cur.disabled=true;break;case"service":cur.service=true;break;case"remove":cur.markedRemove=true;break;}}
            }
            List<ModuleItem> result=new ArrayList<>(map.values());
            for(ModuleItem m:result){
                m.installRisk=HotApply.normalize(HotApply.checkInstall(m.id));
                m.uninstallRisk=HotApply.normalize(HotApply.checkUninstall(m.id));
            }
            result.sort((a,b)->a.name.compareToIgnoreCase(b.name));
            runOnUiThread(()->{all.clear();all.addAll(result);show();log.setText(result.isEmpty()?raw:"根据目录与脚本内容自动分级：SAFE可即时处理、RISKY可尝试、REBOOT必须重启。挂载/注入/元模块默认必须重启。");busy=false;refresh.setEnabled(true);});
        }).start();
    }

    private void confirmAction(ModuleItem item) {
        String text=mode==0?"将应用待更新文件并立即启动服务。":"将执行可用恢复脚本（若有），停止模块进程并移除目录。\n\n不会处理挂载/注入/元模块。";
        if(mode==0 && item.riskyInstall()) text+="\n该模块没有恢复脚本，安装后如需卸载可能需重启。";
        if(mode==1 && item.riskyUninstall()) text+="\n该模块没有恢复脚本，卸载只能停止进程并删除目录，运行态副作用无法自动还原。";
        if(mode==1 && !item.riskyUninstall()) text+="\n该模块有恢复脚本（uninstall/cleanup/stop/temp_control），将优先调用它还原系统修改。";
        text+=keepBackup?"\n本次保留旧文件备份。":"\n本次不保留旧文件备份。";
        new AlertDialog.Builder(this).setTitle(item.name).setMessage(text).setNegativeButton("取消",null).setPositiveButton(mode==0?"免重启安装/更新":"免重启卸载",(d,w)->execute(item)).show();
    }
    private void execute(ModuleItem item){busy=true;refresh.setEnabled(false);header.setText("正在处理 "+item.name+"…");
        new Thread(()->{
            if(mode==0){String result=HotApply.apply(item.id,keepBackup);runOnUiThread(()->{log.setText(result);busy=false;refresh.setEnabled(true);load();});}
            else{String result=HotApply.uninstall(item.id,keepBackup);String residual=HotApply.listResidual(item.id);runOnUiThread(()->handleUninstall(item,result,residual));}
        }).start();
    }
    private void handleUninstall(ModuleItem item, String result, String residual){
        log.setText(result);
        String[] opts = residual==null ? new String[0] : residual.trim().split("\\n");
        List<String> valid = new ArrayList<>();
        for(String o:opts) if(!o.trim().isEmpty()) valid.add(o.trim());
        if(valid.isEmpty()){ busy=false; refresh.setEnabled(true); load(); return; }
        String[] arr = valid.toArray(new String[0]);
        boolean[] checked = new boolean[arr.length]; Arrays.fill(checked,true);
        new AlertDialog.Builder(this).setTitle("检测到同 id 数据目录")
            .setMessage("以下目录与模块 id 同名或以 id 为前缀，可能与模块相关，已默认勾选。请确认后再删除：")
            .setMultiChoiceItems(arr, checked, (d,i,b)->checked[i]=b)
            .setPositiveButton("删除选中",(d,w)->{
                new Thread(()->{
                    StringBuilder sb=new StringBuilder();
                    for(int i=0;i<arr.length;i++) if(checked[i]) sb.append(HotApply.removeResidual(arr[i])).append("\n");
                    runOnUiThread(()->{log.setText(result+"\n"+sb);busy=false;refresh.setEnabled(true);load();});
                }).start();
            })
            .setNegativeButton("保留",(d,w)->{busy=false;refresh.setEnabled(true);load();})
            .setCancelable(false).show();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
