#!/usr/bin/env python3
import json
import sys

try:
    input_data = json.load(sys.stdin)
except json.JSONDecodeError:
    sys.exit(0)

file_path = input_data.get("tool_input", {}).get("file_path", "")

# 允许的路径模式
allowed = [
    "/Users/jstar/ComputerScience/EduProj/ClassIntroToAI/Proj-2/src/stud/g06/",
    "/Users/jstar/ComputerScience/EduProj/ClassIntroToAI/Proj-2/src/AITester.java",
    "/Users/jstar/ComputerScience/EduProj/ClassIntroToAI/Proj-2/.claude/",
    "/Users/jstar/ComputerScience/EduProj/ClassIntroToAI/Proj-2/.gitignore",
    "/Users/jstar/ComputerScience/EduProj/ClassIntroToAI/Proj-2/CLAUDE_RULES.md",
]

for pattern in allowed:
    if file_path.startswith(pattern) or file_path == pattern.rstrip('/'):
        sys.exit(0)

print(f"BLOCKED: 不允许编辑 {file_path}", file=sys.stderr)
print("允许: src/stud/g06/**, src/AITester.java", file=sys.stderr)
sys.exit(2)
