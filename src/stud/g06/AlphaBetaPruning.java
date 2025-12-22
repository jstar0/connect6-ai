package stud.g06;

import java.io.*;
import java.util.*;

/**
 * α-β剪枝算法实现
 * 处理tree.txt文件，输出最佳走步和剪枝信息
 */
public class AlphaBetaPruning {

    private Map<Integer, List<Integer>> children = new HashMap<>();
    private Map<Integer, Integer> values = new HashMap<>();
    private Map<Integer, Integer> parents = new HashMap<>();
    private List<String> pruned = new ArrayList<>();
    private int bestMove = -1;
    private int bestValue = 0;

    public static void main(String[] args) throws Exception {
        AlphaBetaPruning ab = new AlphaBetaPruning();
        ab.loadTree("项目2相关资料/tree.txt");
        ab.search();
        ab.printResult();
    }

    private void loadTree(String filename) throws Exception {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(filename), "UTF-16"));

        String line = reader.readLine(); // 跳过标题行

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\t");
            int nodeId = Integer.parseInt(parts[0]);
            int parentId = Integer.parseInt(parts[1]);
            int value = Integer.parseInt(parts[2]);

            values.put(nodeId, value);
            parents.put(nodeId, parentId);

            if (parentId != -1) {
                children.computeIfAbsent(parentId, k -> new ArrayList<>()).add(nodeId);
            }
        }
        reader.close();
    }

    private void search() {
        // 从根节点1开始，MAX层
        bestValue = alphaBeta(1, Integer.MIN_VALUE, Integer.MAX_VALUE, true, 0);
    }

    private int alphaBeta(int node, int alpha, int beta, boolean isMax, int depth) {
        List<Integer> nodeChildren = children.get(node);

        // 叶子节点，返回估值
        if (nodeChildren == null || nodeChildren.isEmpty()) {
            return values.get(node);
        }

        if (isMax) {
            int maxVal = Integer.MIN_VALUE;
            for (int i = 0; i < nodeChildren.size(); i++) {
                int child = nodeChildren.get(i);
                int val = alphaBeta(child, alpha, beta, false, depth + 1);

                if (val > maxVal) {
                    maxVal = val;
                    if (depth == 0) {
                        bestMove = child;
                    }
                }

                alpha = Math.max(alpha, val);

                // β剪枝
                if (alpha >= beta) {
                    // 记录被剪掉的节点
                    for (int j = i + 1; j < nodeChildren.size(); j++) {
                        pruned.add(node + " " + nodeChildren.get(j) + " beta");
                    }
                    break;
                }
            }
            return maxVal;
        } else {
            int minVal = Integer.MAX_VALUE;
            for (int i = 0; i < nodeChildren.size(); i++) {
                int child = nodeChildren.get(i);
                int val = alphaBeta(child, alpha, beta, true, depth + 1);

                if (val < minVal) {
                    minVal = val;
                }

                beta = Math.min(beta, val);

                // α剪枝
                if (alpha >= beta) {
                    // 记录被剪掉的节点
                    for (int j = i + 1; j < nodeChildren.size(); j++) {
                        pruned.add(node + " " + nodeChildren.get(j) + " alpha");
                    }
                    break;
                }
            }
            return minVal;
        }
    }

    private void printResult() {
        // 第一行：走步及估值
        System.out.println("1   " + bestMove + "   " + bestValue);

        // 后面：剪掉的枝子
        for (String p : pruned) {
            System.out.println(p);
        }
    }
}
