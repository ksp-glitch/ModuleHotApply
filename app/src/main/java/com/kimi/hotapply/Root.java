package com.kimi.hotapply;

import com.topjohnwu.superuser.Shell;
import java.util.ArrayList;
import java.util.List;

public final class Root {
    static {
        Shell.enableVerboseLogging = false;
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).setTimeout(20));
    }
    public static String run(String command) {
        List<String> out = new ArrayList<>();
        List<String> err = new ArrayList<>();
        Shell.Result result = Shell.cmd(command).to(out, err).exec();
        String text = String.join("\n", out);
        if (!result.isSuccess()) text += "\n[错误] " + String.join("\n", err);
        return text.trim();
    }
    public static boolean ok(String command) {
        return Shell.cmd(command).exec().isSuccess();
    }
}
