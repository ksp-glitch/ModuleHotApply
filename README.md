# 模块热拔插 (ModuleHotApply)

免重启安装、更新、卸载 APatch / Magisk / KernelSU 模块的 Android 工具。

## 功能

- 三个入口分开：安装 / 更新、卸载 / 移除、必须重启
- 自动扫描模块目录与脚本内容分级：
  - SAFE：有恢复脚本，可即时处理
  - RISKY：无恢复脚本，仅停进程 + 删目录
  - REBOOT：挂载 / 注入 / 元模块，必须重启
- 卸载优先调用模块自身恢复脚本：uninstall.sh → cleanup.sh → stop.sh → temp_control.sh
- 停止该模块路径下的残留进程
- 清理 `/data/adb` 下与模块 id 同名或以 id 为前缀的数据目录（用户确认后）
- 保留备份开关（默认关闭）

## 编译

- Android SDK（compileSdk 35）
- JDK 17
- Gradle 8.x + Android Gradle Plugin 8.6.1
- 依赖 [libsu](https://github.com/topjohnwu/libsu) 6.0.0

```bash
./gradlew assembleRelease
```

## 说明与风险

- 删除模块目录不等于卸载干净，注入类 / sepolicy / 分区挂载类只能重启。
- 没有恢复脚本的模块，卸载只能停进程 + 删目录，其运行态副作用可能残留。
- 本工具是 Root 应用，操作的是系统模块目录，请先了解自己的模块，风险自负。

## License

MIT
