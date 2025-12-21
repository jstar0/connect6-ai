package stud.g06;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

/**
 * G06 AI - V1 基础AI
 * 功能：胜着检测、威胁防守、智能选点
 */
public class AI extends core.player.AI {

    // 四个方向：横、竖、主对角、副对角
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    // 棋型分数
    private static final int WIN = 1000000;      // 连6
    private static final int LIVE_FIVE = 100000; // 活5
    private static final int FOUR = 10000;       // 冲4/活4
    private static final int LIVE_THREE = 1000;  // 活3
    private static final int THREE = 100;        // 眠3
    private static final int LIVE_TWO = 10;      // 活2

    @Override
    public Move findNextMove(Move opponentMove) {
        board.makeMove(opponentMove);
        PieceColor myColor = board.whoseMove();
        PieceColor oppColor = myColor.opposite();

        // 1. 检查己方胜着
        Move winMove = findWinningMove(myColor);
        if (winMove != null) {
            board.makeMove(winMove);
            return winMove;
        }

        // 2. 检查对方威胁并防守
        List<Integer> oppThreats = findThreats(oppColor);
        if (oppThreats.size() > 0) {
            Move defenseMove = defendThreats(oppThreats, myColor);
            if (defenseMove != null) {
                board.makeMove(defenseMove);
                return defenseMove;
            }
        }

        // 3. 智能选点 - 基于位置评分
        Move bestMove = findBestMove(myColor);
        board.makeMove(bestMove);
        return bestMove;
    }

    // 寻找胜着（能连6的走法）
    private Move findWinningMove(PieceColor color) {
        List<Integer> candidates = getCandidates();

        for (int i = 0; i < candidates.size(); i++) {
            int pos1 = candidates.get(i);
            for (int j = i + 1; j < candidates.size(); j++) {
                int pos2 = candidates.get(j);

                // 模拟下棋
                board.makeMove(new Move(pos1, pos2));
                boolean wins = checkWin(pos1, color) || checkWin(pos2, color);
                board.undo();

                if (wins) {
                    return new Move(pos1, pos2);
                }
            }
        }
        return null;
    }

    // 检查某位置是否形成连6
    private boolean checkWin(int pos, PieceColor color) {
        int col = pos % 19;
        int row = pos / 19;

        for (int[] dir : DIRECTIONS) {
            int count = 1;
            // 正向
            for (int k = 1; k < 6; k++) {
                int nc = col + dir[0] * k;
                int nr = row + dir[1] * k;
                if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) break;
                if (board.get(nc * 19 + nr) != color) break;
                count++;
            }
            // 反向
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

    // 寻找威胁点（对方的活4、冲4位置）
    private List<Integer> findThreats(PieceColor color) {
        List<Integer> threats = new ArrayList<>();

        for (int pos = 0; pos < 361; pos++) {
            if (board.get(pos) != PieceColor.EMPTY) continue;

            int col = pos % 19;
            int row = pos / 19;

            for (int[] dir : DIRECTIONS) {
                int count = 0;
                int empty = 0;

                // 检查该方向上的连子数
                for (int k = -5; k <= 5; k++) {
                    int nc = col + dir[0] * k;
                    int nr = row + dir[1] * k;
                    if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) continue;

                    PieceColor c = board.get(nc * 19 + nr);
                    if (c == color) count++;
                    else if (c == PieceColor.EMPTY) empty++;
                }

                // 如果有5个同色子且有空位，是威胁
                if (count >= 5) {
                    threats.add(pos);
                    break;
                }
            }
        }
        return threats;
    }

    // 防守威胁
    private Move defendThreats(List<Integer> threats, PieceColor myColor) {
        if (threats.size() == 0) return null;

        // 如果威胁点<=2，直接堵住
        if (threats.size() <= 2) {
            int pos1 = threats.get(0);
            int pos2 = threats.size() > 1 ? threats.get(1) : findBestSingleMove(myColor, pos1);
            return new Move(pos1, pos2);
        }

        // 威胁太多，尝试反击
        return null;
    }

    // 找最佳单点
    private int findBestSingleMove(PieceColor color, int exclude) {
        int bestPos = -1;
        int bestScore = -1;

        for (int pos = 0; pos < 361; pos++) {
            if (pos == exclude || board.get(pos) != PieceColor.EMPTY) continue;
            int score = evaluatePosition(pos, color);
            if (score > bestScore) {
                bestScore = score;
                bestPos = pos;
            }
        }
        return bestPos;
    }

    // 智能选点
    private Move findBestMove(PieceColor color) {
        List<Integer> candidates = getCandidates();

        int bestScore = Integer.MIN_VALUE;
        Move bestMove = null;

        for (int i = 0; i < candidates.size(); i++) {
            int pos1 = candidates.get(i);
            for (int j = i + 1; j < candidates.size(); j++) {
                int pos2 = candidates.get(j);

                int score = evaluatePosition(pos1, color) + evaluatePosition(pos2, color);
                // 两子相邻加分
                if (isAdjacent(pos1, pos2)) score += 50;

                if (score > bestScore) {
                    bestScore = score;
                    bestMove = new Move(pos1, pos2);
                }
            }
        }

        return bestMove;
    }

    // 获取候选点（已有棋子周围的空位）
    private List<Integer> getCandidates() {
        Set<Integer> candidates = new HashSet<>();

        for (int pos = 0; pos < 361; pos++) {
            if (board.get(pos) != PieceColor.EMPTY) {
                int col = pos % 19;
                int row = pos / 19;

                // 周围2格范围
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

    // 评估单点分数
    private int evaluatePosition(int pos, PieceColor color) {
        int col = pos % 19;
        int row = pos / 19;
        int score = 0;

        // 中心位置加分
        int centerDist = Math.abs(col - 9) + Math.abs(row - 9);
        score += (18 - centerDist) * 2;

        // 各方向棋型评估
        for (int[] dir : DIRECTIONS) {
            int myCount = 0, oppCount = 0, empty = 0;

            for (int k = -5; k <= 5; k++) {
                if (k == 0) continue;
                int nc = col + dir[0] * k;
                int nr = row + dir[1] * k;
                if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) continue;

                PieceColor c = board.get(nc * 19 + nr);
                if (c == color) myCount++;
                else if (c == color.opposite()) oppCount++;
                else empty++;
            }

            // 进攻分
            if (oppCount == 0) {
                score += myCount * myCount * 10;
            }
            // 防守分
            if (myCount == 0) {
                score += oppCount * oppCount * 5;
            }
        }

        return score;
    }

    // 判断两点是否相邻
    private boolean isAdjacent(int pos1, int pos2) {
        int c1 = pos1 % 19, r1 = pos1 / 19;
        int c2 = pos2 % 19, r2 = pos2 / 19;
        return Math.abs(c1 - c2) <= 1 && Math.abs(r1 - r2) <= 1;
    }

    @Override
    public String name() {
        return "G06";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
    }
}
