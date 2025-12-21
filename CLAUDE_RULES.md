# Claude Code 编辑规则 - 六子棋AI项目 (G06)

## 老师要求（来自项目2-博弈搜索.pdf）
- 使用课程提供的博弈树及六子棋对弈的基本框架
- 使用 Board, Move, Player, Game 等超类
- **不允许修改类的公共接口**

## 允许编辑的路径
ALLOWED:
- src/stud/g06/**          # 我们的AI代码（可添加辅助类）
- src/AITester.java        # 测试入口（仅添加g06相关代码）

## 禁止编辑的路径
FORBIDDEN:
- framework_src/**         # 框架源码，不允许修改超类和公共接口
- src/stud/g77/**          # 示例AI（走法1随机棋手）
- src/stud/g88/**          # 示例AI（走法2随机棋手）
- src/stud/g99/**          # 示例AI（走法3随机棋手）
- lib/**                   # 依赖库
- 项目2相关资料/**          # 参考资料

## 规则说明
1. 所有AI代码必须在 src/stud/g06/ 目录下
2. 可以添加辅助类（如G06Board）来封装功能
3. AITester.java 只能添加 g06 的导入和实例化
4. 如果需要编辑禁止路径的文件，必须先询问用户确认
