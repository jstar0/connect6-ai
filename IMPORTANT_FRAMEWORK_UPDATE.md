# ⚠️ 重要：2025 框架更新（覆盖旧要求）

老师最新要求：框架 `core.player.AI` 的 `firstMove()` **不再是 `final`**，各组可以通过 **override `firstMove()`** 来自己设计第一步（也可以继续复用默认随机开局库）。

## 本仓库已完成的对应处理
- 已使用你提供的新 `aiFramework.jar` 覆盖 `lib/aiFramework.jar`（编译与对战默认都引用 `lib/aiFramework.jar`）。
- `framework_src/src/core.player/AI.java` 已同步去掉 `firstMove()` 的 `final`，避免源码参考与实际 JAR 不一致。

## 如何验证你当前用的是“新框架 JAR”
运行：

`javap -classpath lib/aiFramework.jar core.player.AI | rg "firstMove|findMove"`

期望看到类似：
- `public core.game.Move firstMove();`  （注意：没有 `final`）
- `public final core.game.Move findMove(core.game.Move) ...;`

如果你看到 `public final core.game.Move firstMove();`，说明仍在使用旧版 JAR，需要替换。

## 对 aiDeveloper / oucLeague 的要求
老师要求：请用新的 `aiFramework.jar` 替换掉 `AiDeveloper.zip` 与 `oucLeague.zip` 中 `lib/` 目录下的旧 `aiFramework.jar`。

本仓库对应文件位置：
- `项目2相关资料/AiDeveloper.zip`（已更新内部的 `lib/aiFramework.jar`，并同步更新 `out/artifacts/AiDeveloper_jar/aiFramework.jar`）
- `项目2相关资料/oucLeague.zip`（已更新内部的 `lib/aiFramework.jar`）

## 对我们（G06）的直接影响
现在 **不必再被“随机开局库”限制**：`src/stud/g06/AI.java` 可以直接 `@Override public Move firstMove()` 来控制第一手走法（关键术语：`firstMove` / `final` / `override`）。
