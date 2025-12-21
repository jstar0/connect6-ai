package stud.g06;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

/**
 * G06 AI - V2 搜索AI
 * 功能：Alpha-Beta搜索 + 有效路估值 + 置换表 + 迭代加深
 */
public class AI extends core.player.AI {

    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private static final int MAX_DEPTH = 2;
    private static final int INF = 10000000;

    // 置换表
    private Map<Long, int[]> transTable = new HashMap<>();
    private long[][] zobristTable = new long[361][3]; // 0=empty, 1=black, 2=white
    private long currentHash = 0;

    public AI() {
        initZobrist();
    }

    private void initZobrist() {
        Random rand = new Random(12345);
        for (int i = 0; i < 361; i++) {
            for (int j = 0; j < 3; j++) {
                zobristTable[i][j] = rand.nextLong();
            }
        }
    }

    @Override
    public Move findNextMove(Move opponentMove) {
        board.makeMove(opponentMove);
        updateHash(opponentMove);

        PieceColor myColor = board.whoseMove();

        // 迭代加深搜索
        Move bestMove = null;
        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            bestMove = alphaBetaRoot(myColor, depth);
        }

        board.makeMove(bestMove);
        updateHash(bestMove);
        return bestMove;
    }

    // Alpha-Beta根节点
    private Move alphaBetaRoot(PieceColor color, int depth) {
        List<Move> moves = generateMoves(color);
        if (moves.isEmpty()) return null;

        Move bestMove = moves.get(0);
        int bestScore = -INF;

        for (Move move : moves) {
            makeMove(move);
            int score = -alphaBeta(color.opposite(), depth - 1, -INF, -bestScore);
            undoMove(move);

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }

    // Alpha-Beta搜索
    private int alphaBeta(PieceColor color, int depth, int alpha, int beta) {
        // 置换表查询
        int[] cached = transTable.get(currentHash);
        if (cached != null && cached[0] >= depth) {
            return cached[1];
        }

        if (depth == 0) {
            int score = evaluate(color);
            transTable.put(currentHash, new int[]{depth, score});
            return score;
        }

        List<Move> moves = generateMoves(color);
        if (moves.isEmpty()) {
            return evaluate(color);
        }

        int bestScore = -INF;
        for (Move move : moves) {
            makeMove(move);
            int score = -alphaBeta(color.opposite(), depth - 1, -beta, -alpha);
            undoMove(move);

            bestScore = Math.max(bestScore, score);
            alpha = Math.max(alpha, score);
            if (alpha >= beta) break; // 剪枝
        }

        transTable.put(currentHash, new int[]{depth, bestScore});
        return bestScore;
    }

    // 生成走法（智能排序）
    private List<Move> generateMoves(PieceColor color) {
        List<Integer> candidates = getCandidates();
        List<Move> moves = new ArrayList<>();

        // 先检查胜着
        for (int i = 0; i < candidates.size(); i++) {
            int pos1 = candidates.get(i);
            for (int j = i + 1; j < candidates.size(); j++) {
                int pos2 = candidates.get(j);
                Move move = new Move(pos1, pos2);

                board.makeMove(move);
                boolean wins = checkWin(pos1, color) || checkWin(pos2, color);
                board.undo();

                if (wins) {
                    moves.add(0, move); // 胜着放最前
                } else {
                    moves.add(move);
                }
            }
        }

        // 限制走法数量
        if (moves.size() > 50) {
            moves = moves.subList(0, 50);
        }

        return moves;
    }

    // 有效路估值
    private int evaluate(PieceColor myColor) {
        int myScore = 0, oppScore = 0;
        PieceColor oppColor = myColor.opposite();

        // 遍历所有可能的6连线（有效路）
        for (int pos = 0; pos < 361; pos++) {
            int col = pos % 19;
            int row = pos / 19;

            for (int[] dir : DIRECTIONS) {
                // 检查从pos开始的6格路
                if (!validLine(col, row, dir)) continue;

                int my = 0, opp = 0;
                for (int k = 0; k < 6; k++) {
                    int nc = col + dir[0] * k;
                    int nr = row + dir[1] * k;
                    PieceColor c = board.get(nc * 19 + nr);
                    if (c == myColor) my++;
                    else if (c == oppColor) opp++;
                }

                // 有效路评分
                if (opp == 0 && my > 0) {
                    myScore += getPathScore(my);
                }
                if (my == 0 && opp > 0) {
                    oppScore += getPathScore(opp);
                }
            }
        }

        return myScore - oppScore;
    }

    // 有效路分数
    private int getPathScore(int count) {
        switch (count) {
            case 6: return 1000000;
            case 5: return 50000;
            case 4: return 5000;
            case 3: return 500;
            case 2: return 50;
            case 1: return 5;
            default: return 0;
        }
    }

    // 检查6格路是否在棋盘内
    private boolean validLine(int col, int row, int[] dir) {
        int endCol = col + dir[0] * 5;
        int endRow = row + dir[1] * 5;
        return endCol >= 0 && endCol < 19 && endRow >= 0 && endRow < 19;
    }

    // 获取候选点
    private List<Integer> getCandidates() {
        Set<Integer> candidates = new HashSet<>();

        for (int pos = 0; pos < 361; pos++) {
            if (board.get(pos) != PieceColor.EMPTY) {
                int col = pos % 19;
                int row = pos / 19;

                for (int dc = -2; dc <= 2; dc++) {
                    for (int dr = -2; dr <= 2; dr++) {
                        int nc = col + dc;
                        int nr = row + dr;
                        if (nc >= 0 && nc < 19 && nr >= 0 && nr < 19) {
                            int npos = nc * 19 + nr;
                            if (board.get(npos) == PieceColor.EMPTY) {
                                candidates.add(npos);
                            }
                        }
                    }
                }
            }
        }

        return new ArrayList<>(candidates);
    }

    // 检查胜利
    private boolean checkWin(int pos, PieceColor color) {
        int col = pos % 19;
        int row = pos / 19;

        for (int[] dir : DIRECTIONS) {
            int count = 1;
            for (int k = 1; k < 6; k++) {
                int nc = col + dir[0] * k;
                int nr = row + dir[1] * k;
                if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) break;
                if (board.get(nc * 19 + nr) != color) break;
                count++;
            }
            for (int k = 1; k < 6; k++) {
                int nc = col - dir[0] * k;
                int nr = row - dir[1] * k;
                if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) break;
                if (board.get(nc * 19 + nr) != color) break;
                count++;
            }
            if (count >= 6) return true;
        }
        return false;
    }

    // 下棋并更新哈希
    private void makeMove(Move move) {
        board.makeMove(move);
        updateHash(move);
    }

    // 撤销并更新哈希
    private void undoMove(Move move) {
        PieceColor color = board.whoseMove(); // undo前的颜色
        board.undo();
        // 撤销哈希
        int colorIdx = (color == PieceColor.BLACK) ? 1 : 2;
        currentHash ^= zobristTable[move.index1()][colorIdx];
        currentHash ^= zobristTable[move.index2()][colorIdx];
        currentHash ^= zobristTable[move.index1()][0];
        currentHash ^= zobristTable[move.index2()][0];
    }

    // 更新哈希
    private void updateHash(Move move) {
        if (move == null) return;
        PieceColor color = board.get(move.index1());
        int colorIdx = (color == PieceColor.BLACK) ? 1 : 2;
        currentHash ^= zobristTable[move.index1()][0]; // 移除空
        currentHash ^= zobristTable[move.index2()][0];
        currentHash ^= zobristTable[move.index1()][colorIdx]; // 添加棋子
        currentHash ^= zobristTable[move.index2()][colorIdx];
    }

    @Override
    public String name() {
        return "G06";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
        currentHash = 0;
        transTable.clear();
        // 初始化中心黑子的哈希
        currentHash ^= zobristTable[Move.index('J', 'J')][1];
    }
}
