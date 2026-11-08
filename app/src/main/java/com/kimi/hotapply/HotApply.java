package com.kimi.hotapply;

public final class HotApply {
    private HotApply() {}

    /*
     * 分级结果：
     *   SAFE   - 有明确恢复入口，可热操作
     *   RISKY  - 无恢复入口但无危险项，可尝试，需用户确认
     *   REBOOT - 必须重启
     */
    public static final String SAFE = "SAFE", RISKY = "RISKY", REBOOT = "REBOOT";

    private static String q(String id) { return "'" + id + "'"; }

    private static String dangerousDirs(String p) {
        return "find " + p + " -maxdepth 2 -type d \\( -name system -o -name vendor -o -name product -o -name system_ext -o -name zygisk -o -name riru -o -name sepolicy \\) -print -quit | grep -q . ";
    }
    private static String dangerousKeywords(String p) {
        return "grep -Eiq 'nomount|metamodule|meta-module|zygisk|riru|lsposed|sepolicy|bootimage' " + p + "/module.prop 2>/dev/null ";
    }
    private static String dangerousScriptOps(String p) {
        return "find " + p + " -type f -name '*.sh' -exec grep -Elqi 'magic_mount|mount --bind|mount -t|nsenter|magisk --mount|overlay' {} + 2>/dev/null | grep -q . ";
    }
    private static String hasRestore(String p) {
        return "find " + p + " -maxdepth 1 -type f \\( -name uninstall.sh -o -name cleanup.sh -o -name stop.sh -o -name temp_control.sh \\) -print -quit | grep -q . ";
    }
    private static String analyze(String p) {
        return "if [ ! -f " + p + "/module.prop ]; then echo REBOOT; exit 0; fi; " +
               "if " + dangerousDirs(p) + " || " + dangerousKeywords(p) + " || " + dangerousScriptOps(p) + "; then echo REBOOT; exit 0; fi; " +
               "if " + hasRestore(p) + "; then echo SAFE; exit 0; fi; echo RISKY";
    }
    public static String checkInstall(String id) {
        String upd = "/data/adb/modules_update/" + q(id);
        String dir = "/data/adb/modules/" + q(id);
        return normalize(Root.run("p=" + upd + "; if [ -d $p ]; then " + analyze("$p") + "; else p=" + dir + "; [ -d $p ] || { echo REBOOT; }; " + analyze("$p") + "; fi"));
    }
    public static String checkUninstall(String id) {
        String dir = "/data/adb/modules/" + q(id);
        return normalize(Root.run("p=" + dir + "; [ -d $p ] || { echo REBOOT; }; " + analyze("$p")));
    }
    public static String normalize(String raw) {
        if (raw == null) return REBOOT;
        if (raw.contains("RISKY")) return RISKY;
        if (raw.contains("SAFE")) return SAFE;
        return REBOOT;
    }

    public static String apply(String id, boolean keepBackup) {
        String q = q(id), dir = "/data/adb/modules/" + q, upd = "/data/adb/modules_update/" + q, backup = "/data/adb/hot_apply_backup/" + q;
        return Root.run("set -eu; umask 077; " + idGuard(q) +
                "[ -f " + upd + "/module.prop ] || { echo NO_PENDING_UPDATE; exit 3; }; " +
                "if " + dangerousDirs(upd) + " || " + dangerousKeywords(upd) + " || " + dangerousScriptOps(upd) + "; then echo NEED_REBOOT; exit 4; fi; " +
                "if [ " + flag(keepBackup) + " = 1 ]; then mkdir -p /data/adb/hot_apply_backup; rm -rf " + backup + "; fi; " +
                "if [ -d " + dir + " ]; then if [ " + flag(keepBackup) + " = 1 ]; then mv " + dir + " " + backup + "; else rm -rf " + dir + "; fi; fi; " +
                "if ! mv " + upd + " " + dir + "; then [ ! -d " + backup + " ] || mv " + backup + " " + dir + "; exit 5; fi; " +
                "find " + dir + " -type d -exec chmod 755 {} +; find " + dir + " -type f -exec chmod 644 {} +; find " + dir + " -maxdepth 1 -type f -name '*.sh' -exec chmod 755 {} +; " +
                "restorecon -RF " + dir + " 2>/dev/null || true; rm -f " + dir + "/disable " + dir + "/update " + dir + "/remove; " +
                "if [ -f " + dir + "/service.sh ]; then " + stopServices(id) + "nohup /system/bin/sh " + dir + "/service.sh >/data/adb/hot_apply_" + id + ".log 2>&1 & fi; echo APPLIED_NO_REBOOT");
    }

    public static String uninstall(String id, boolean keepBackup) {
        String q = q(id), dir = "/data/adb/modules/" + q, backup = "/data/adb/hot_apply_removed/" + q;
        String pickRestore = "r=\"\"; for f in uninstall.sh cleanup.sh stop.sh temp_control.sh; do [ -x " + dir + "/$f ] && { r=$f; break; }; done; ";
        return Root.run("set -u; umask 077; " + idGuard(q) + "[ -d " + dir + " ] || { echo ALREADY_REMOVED; exit 0; }; " +
                "if " + dangerousDirs(dir) + " || " + dangerousKeywords(dir) + " || " + dangerousScriptOps(dir) + "; then echo NEED_REBOOT; exit 4; fi; " +
                pickRestore +
                "if [ -n \"$r\" ]; then /system/bin/sh " + dir + "/$r || { echo RESTORE_FAILED; exit 6; }; fi; " +
                "[ -d " + dir + " ] || { echo REMOVED_BY_RESTORE; exit 0; }; " +
                stopServices(id) +
                "if [ " + flag(keepBackup) + " = 1 ]; then mkdir -p /data/adb/hot_apply_removed; rm -rf " + backup + "; mv " + dir + " " + backup + "; else rm -rf " + dir + "; fi; " +
                "rm -rf /data/adb/modules_update/" + q + "; echo REMOVED_NO_REBOOT");
    }

    private static String stopServices(String id) {
        String path = "/data/adb/modules/" + id + "/";
        String pick = "ps -A -o PID,ARGS | awk -v p='" + path + "' -v me=$$ '$1 != me && $0 !~ /awk/ && index($0,p)>0 {print $1}'";
        return "pids=$(" + pick + "); for pid in $pids; do kill $pid 2>/dev/null; done; sleep 1; " +
               "pids=$(" + pick + "); for pid in $pids; do kill -9 $pid 2>/dev/null; done; sleep 1; ";
    }
    private static String flag(boolean b) { return b ? "1" : "0"; }
    private static String idGuard(String q) { return "case " + q + " in ''|*[!A-Za-z0-9._-]*) echo BAD_ID; exit 2;; esac; "; }

    public static String listResidual(String id) {
        String qid = q(id);
        return Root.run("for d in /data/adb/*/; do b=${d%/}; n=${b##*/}; [ \"$n\" = " + qid + " ] || case \"$n\" in " + qid + "-*) ;; *) continue;; esac; case \"$b\" in /data/adb/modules|/data/adb/modules_update) continue;; esac; echo \"$b\"; done");
    }
    public static String removeResidual(String path) {
        if (path == null) return "SKIPPED";
        String qp = q(path);
        return Root.run("case " + qp + " in /data/adb|/data/adb/modules|/data/adb/modules_update) echo SKIPPED_PROTECTED;; *) rm -rf " + qp + " && echo REMOVED " + qp + ";; esac");
    }
}
