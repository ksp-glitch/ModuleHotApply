package com.kimi.hotapply;

public final class ModuleItem {
    public String id, name, version, description;
    public boolean update, installed, disabled, service, markedRemove;
    public String installRisk = HotApply.REBOOT, uninstallRisk = HotApply.REBOOT;
    public boolean hotInstall() { return !HotApply.REBOOT.equals(installRisk); }
    public boolean hotUninstall() { return !HotApply.REBOOT.equals(uninstallRisk); }
    public boolean riskyInstall() { return HotApply.RISKY.equals(installRisk); }
    public boolean riskyUninstall() { return HotApply.RISKY.equals(uninstallRisk); }

    public String summary(int mode) {
        String risk = mode == 0 ? installRisk : uninstallRisk;
        String flags;
        if (mode == 0) flags = update ? "待安装/更新" : "没有待更新";
        else if (mode == 1) flags = markedRemove ? "已标记卸载" : "已安装";
        else flags = "必须重启 / 仅查看";
        String desc;
        if (HotApply.SAFE.equals(risk)) desc = "安全：可即时处理";
        else if (HotApply.RISKY.equals(risk)) desc = "可尝试：无恢复脚本，仅停进程+删目录";
        else desc = "必须重启：挂载/注入/元模块/未知";
        return version + " · " + flags + "\n" + id + "\n" + desc;
    }
}
