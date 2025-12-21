#!/bin/bash
# Pre-edit hook: 检查文件路径是否在允许编辑的范围内

FILE_PATH="$1"
PROJECT_ROOT="/Users/jstar/ComputerScience/EduProj/ClassIntroToAI/Proj-2"

# 获取相对路径
REL_PATH="${FILE_PATH#$PROJECT_ROOT/}"

# 允许的路径模式
if [[ "$REL_PATH" == src/stud/g06/* ]] || [[ "$REL_PATH" == "src/AITester.java" ]] || [[ "$REL_PATH" == .claude/* ]] || [[ "$REL_PATH" == ".gitignore" ]] || [[ "$REL_PATH" == "CLAUDE_RULES.md" ]]; then
    exit 0  # 允许
fi

# 禁止的路径
echo "BLOCKED: 不允许编辑 $REL_PATH"
echo "允许编辑的路径: src/stud/g06/**, src/AITester.java"
exit 1
