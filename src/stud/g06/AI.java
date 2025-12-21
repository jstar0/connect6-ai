package stud.g06;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

/**
 * G06 AI - V3 高级搜索AI
 * 功能：威胁空间搜索(TBS) + Alpha-Beta + 有效路估值 + 置换表
 */
public class AI extends core.player.AI {

    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private static final int MAX_DEPTH = 2;
    private static final int TBS_DEPTH = 10; // 威胁搜索深度
    private static final int INF = 10000000;

    // 置换表
    private Map<Long, int[]> transTable = new HashMap<>();
    private long[][] zobristTable = new long[361][3];
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

        // 1. 威胁空间搜索 - 寻找必胜序列
        Move tbsMove = threatSpaceSearch(myColor, TBS_DEPTH);
        if (tbsMove != null) {
            board.makeMove(tbsMove);
            updateHash(tbsMove);
            return tbsMove;
        }

        // 2. Alpha-Beta搜索
        Move bestMove = null;
        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            bestMove = alphaBetaRoot(myColor, depth);
        }

        board.makeMove(bestMove);
        updateHash(bestMove);
        return bestMove;
    }

    // 威胁空间搜索 - 只搜索威胁和防守序列
    private Move threatSpaceSearch(PieceColor color, int depth) {
        if (depth <= 0) return null;

        // 找己方威胁走法（能形成活4或冲4的走法）
        List<Move> threats = findThreatMoves(color);

        for (Move threat : threats) {
            board.makeMove(threat);

            // 检查是否直接获胜
            if (checkWin(threat.index1(), color) || checkWin(threat.index2(), color)) {
                board.undo();
                return threat;
            }

            // 对方必须防守
            List<Integer> defensePoints = findDefensePoints(color);

            if (defensePoints.isEmpty()) {
                // 对方无法防守，我方必胜
                board.undo();
                return threat;
            }

            if (defensePoints.size() > 2) {
                // 对方无法完全防守（每回合只能下2子）
                board.undo();
                return threat;
            }

            // 模拟对方防守
            boolean allWin = true;
            for (int i = 0; i < defensePoints.size(); i++) {
                int d1 = defensePoints.get(i);
                int d2 = (defensePoints.size() > 1) ? defensePoints.get(1 - i) : findBestDefense(color.opposite(), d1);

                if (d2 == -1) continue;

                Move defense = new Move(d1, d2);
                if (!board.legalMove(defense)) continue;

                board.makeMove(defense);

                // 递归搜索
                Move nextThreat = threatSpaceSearch(color, depth - 1);
                if (nextThreat == null) {
                    allWin = false;
                }

                board.undo();

                if (!allWin) break;
            }

            board.undo();

            if (allWin && !defensePoints.isEmpty()) {
                return threat;
            }
        }

        return null;
    }

    // 找威胁走法（能形成活4、冲4、双活3的走法）
    private List<Move> findThreatMoves(PieceColor color) {
        List<Move> threats = new ArrayList<>();
        List<Integer> candidates = getCandidates();

        for (int i = 0; i < candidates.size(); i++) {
            int pos1 = candidates.get(i);
            for (int j = i + 1; j < candidates.size(); j++) {
                int pos2 = candidates.get(j);
                Move move = new Move(pos1, pos2);

                if (!board.legalMove(move)) continue;

                board.makeMove(move);

                // 检查是否形成威胁
                int threatCount = countThreats(pos1, color) + countThreats(pos2, color);
                if (threatCount > 0) {
                    threats.add(move);
                }

                board.undo();
            }
        }

        // 按威胁程度排序
        threats.sort((a, b) -> {
            board.makeMove(b);
            int bScore = countThreats(b.index1(), color) + countThreats(b.index2(), color);
            board.undo();

            board.makeMove(a);
            int aScore = countThreats(a.index1(), color) + countThreats(a.index2(), color);
            board.undo();

            return bScore - aScore;
        });

        // 限制数量
        if (threats.size() > 20) {
            threats = threats.subList(0, 20);
        }

        return threats;
    }

    // 计算某位置的威胁数（活4、冲4数量）
    private int countThreats(int pos, PieceColor color) {
        int col = pos % 19;
        int row = pos / 19;
        int threats = 0;

        for (int[] dir : DIRECTIONS) {
            int count = 1;
            int openEnds = 0;

            // 正向
            int emptyCount = 0;
            for (int k = 1; k <= 5; k++) {
                int nc = col + dir[0] * k;
                int nr = row + dir[1] * k;
                if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) break;
                PieceColor c = board.get(nc * 19 + nr);
                if (c == color) count++;
                else if (c == PieceColor.EMPTY) {
                    emptyCount++;
                    if (emptyCount == 1) openEnds++;
                    break;
                } else break;
            }

            // 反向
            emptyCount = 0;
            for (int k = 1; k <= 5; k++) {
                int nc = col - dir[0] * k;
                int nr = row - dir[1] * k;
                if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) break;
                PieceColor c = board.get(nc * 19 + nr);
                if (c == color) count++;
                else if (c == PieceColor.EMPTY) {
                    emptyCount++;
                    if (emptyCount == 1) openEnds++;
                    break;
                } else break;
            }

            // 5子 = 必胜威胁
            if (count >= 5) threats += 100;
            // 活4 = 强威胁
            else if (count == 4 && openEnds == 2) threats += 50;
            // 冲4 = 威胁
            else if (count == 4 && openEnds == 1) threats += 10;
            // 活3 = 潜在威胁
            else if (count == 3 && openEnds == 2) threats += 5;
        }

        return threats;
    }

    // 找防守点
    private List<Integer> findDefensePoints(PieceColor attackColor) {
        Set<Integer> defensePoints = new HashSet<>();

        for (int pos = 0; pos < 361; pos++) {
            if (board.get(pos) != PieceColor.EMPTY) continue;

            int col = pos % 19;
            int row = pos / 19;

            for (int[] dir : DIRECTIONS) {
                int count = 0;
                // 检查该位置是否能阻断攻击方的连线
                for (int k = -5; k <= 5; k++) {
                    int nc = col + dir[0] * k;
                    int nr = row + dir[1] * k;
                    if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) continue;
                    if (board.get(nc * 19 + nr) == attackColor) count++;
                }

                if (count >= 4) {
                    defensePoints.add(pos);
                    break;
                }
            }
        }

        return new ArrayList<>(defensePoints);
    }

    // 找最佳防守点
    private int findBestDefense(PieceColor color, int exclude) {
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

    // 位置评估
    private int evaluatePosition(int pos, PieceColor color) {
        int col = pos % 19;
        int row = pos / 19;
        int score = (18 - Math.abs(col - 9) - Math.abs(row - 9)) * 2;

        for (int[] dir : DIRECTIONS) {
            int myCount = 0, oppCount = 0;
            for (int k = -5; k <= 5; k++) {
                if (k == 0) continue;
                int nc = col + dir[0] * k;
                int nr = row + dir[1] * k;
                if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) continue;
                PieceColor c = board.get(nc * 19 + nr);
                if (c == color) myCount++;
                else if (c == color.opposite()) oppCount++;
            }
            if (oppCount == 0) score += myCount * myCount * 10;
            if (myCount == 0) score += oppCount * oppCount * 5;
        }
        return score;
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
        if (moves.isEmpty()) return evaluate(color);

        int bestScore = -INF;
        for (Move move : moves) {
            makeMove(move);
            int score = -alphaBeta(color.opposite(), depth - 1, -beta, -alpha);
            undoMove(move);

            bestScore = Math.max(bestScore, score);
            alpha = Math.max(alpha, score);
            if (alpha >= beta) break;
        }

        transTable.put(currentHash, new int[]{depth, bestScore});
        return bestScore;
    }

    // 生成走法
    private List<Move> generateMoves(PieceColor color) {
        List<Integer> candidates = getCandidates();
        List<Move> moves = new ArrayList<>();

        for (int i = 0; i < candidates.size(); i++) {
            int pos1 = candidates.get(i);
            for (int j = i + 1; j < candidates.size(); j++) {
                int pos2 = candidates.get(j);
                Move move = new Move(pos1, pos2);

                board.makeMove(move);
                boolean wins = checkWin(pos1, color) || checkWin(pos2, color);
                board.undo();

                if (wins) {
                    moves.add(0, move);
                } else {
                    moves.add(move);
                }
            }
        }

        if (moves.size() > 50) {
            moves = moves.subList(0, 50);
        }
        return moves;
    }

    // 有效路估值
    private int evaluate(PieceColor myColor) {
        int myScore = 0, oppScore = 0;
        PieceColor oppColor = myColor.opposite();

        for (int pos = 0; pos < 361; pos++) {
            int col = pos % 19;
            int row = pos / 19;

            for (int[] dir : DIRECTIONS) {
                if (!validLine(col, row, dir)) continue;

                int my = 0, opp = 0;
                for (int k = 0; k < 6; k++) {
                    int nc = col + dir[0] * k;
                    int nr = row + dir[1] * k;
                    PieceColor c = board.get(nc * 19 + nr);
                    if (c == myColor) my++;
                    else if (c == oppColor) opp++;
                }

                if (opp == 0 && my > 0) myScore += getPathScore(my);
                if (my == 0 && opp > 0) oppScore += getPathScore(opp);
            }
        }
        return myScore - oppScore;
    }

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

    private boolean validLine(int col, int row, int[] dir) {
        int endCol = col + dir[0] * 5;
        int endRow = row + dir[1] * 5;
        return endCol >= 0 && endCol < 19 && endRow >= 0 && endRow < 19;
    }

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

    private void makeMove(Move move) {
        board.makeMove(move);
        updateHash(move);
    }

    private void undoMove(Move move) {
        PieceColor color = board.whoseMove();
        board.undo();
        int colorIdx = (color == PieceColor.BLACK) ? 1 : 2;
        currentHash ^= zobristTable[move.index1()][colorIdx];
        currentHash ^= zobristTable[move.index2()][colorIdx];
        currentHash ^= zobristTable[move.index1()][0];
        currentHash ^= zobristTable[move.index2()][0];
    }

    private void updateHash(Move move) {
        if (move == null) return;
        PieceColor color = board.get(move.index1());
        int colorIdx = (color == PieceColor.BLACK) ? 1 : 2;
        currentHash ^= zobristTable[move.index1()][0];
        currentHash ^= zobristTable[move.index2()][0];
        currentHash ^= zobristTable[move.index1()][colorIdx];
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
        currentHash ^= zobristTable[Move.index('J', 'J')][1];
    }
}
