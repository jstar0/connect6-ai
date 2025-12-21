# Claude Code 编辑规则 - 六子棋AI项目 (G06)

## 允许编辑的路径
ALLOWED:
- src/stud/g06/**          # 我们的AI代码
- src/AITester.java        # 测试入口（仅添加g06相关代码）

## 禁止编辑的路径
FORBIDDEN:
- framework_src/**         # 框架源码，绝对不能动
- src/stud/g77/**          # 示例AI，不能修改
- src/stud/g88/**          # 示例AI，不能修改
- src/stud/g99/**          # 示例AI，不能修改
- lib/**                   # 依赖库
- 项目2相关资料/**          # 参考资料

## 规则说明
1. 所有AI代码必须在 src/stud/g06/ 目录下
2. AITester.java 只能添加 g06 的导入和实例化，不能删除或修改其他内容
3. 如果需要编辑禁止路径的文件，必须先询问用户确认
