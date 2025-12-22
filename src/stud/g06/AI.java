package stud.g06;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

/**
 * G06 AI - V3++ 终极版
 * 功能：TBS + PVS + 时间管理 + 精细棋型评分 + 杀手启发 + 历史启发
 */
public class AI extends core.player.AI {

    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private static final int MAX_DEPTH = 6;  // 最大深度
    private static final int TBS_DEPTH = 12;
    private static final int INF = 10000000;
    private static final long TIME_LIMIT = 4500; // 4.5秒时间限制

    private long startTime;
    private boolean timeOut;

    // 棋型分数
    private static final int SCORE_WIN = 1000000;
    private static final int SCORE_LIVE5 = 100000;
    private static final int SCORE_LIVE4 = 50000;
    private static final int SCORE_RUSH4 = 10000;
    private static final int SCORE_LIVE3 = 5000;
    private static final int SCORE_SLEEP3 = 1000;
    private static final int SCORE_LIVE2 = 500;
    private static final int SCORE_SLEEP2 = 100;

    // 置换表
    private Map<Long, int[]> transTable = new HashMap<>();
    private long[][] zobristTable = new long[361][3];
    private long currentHash = 0;

    // 杀手启发：记录每层导致剪枝的走法
    private Move[][] killerMoves = new Move[MAX_DEPTH + 2][2];

    // 历史启发：记录走法的历史得分
    private int[][] historyScore = new int[361][361];

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

        startTime = System.currentTimeMillis();
        timeOut = false;
        PieceColor myColor = board.whoseMove();

        // 1. 威胁空间搜索 - 寻找必胜序列
        Move tbsMove = threatSpaceSearch(myColor, TBS_DEPTH);
        if (tbsMove != null) {
            board.makeMove(tbsMove);
            updateHash(tbsMove);
            return tbsMove;
        }

        // 2. 迭代加深PVS搜索 + 时间管理
        Move bestMove = null;
        int bestScore = -INF;
        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            if (System.currentTimeMillis() - startTime > TIME_LIMIT) break;

            int[] result = pvsRoot(myColor, depth, bestScore);
            if (!timeOut && result[1] != -INF) {
                bestMove = new Move(result[0] / 361, result[0] % 361);
                bestScore = result[1];
            }
        }

        if (bestMove == null) {
            List<Move> moves = generateMoves(myColor, 0);
            bestMove = moves.isEmpty() ? new Move(0, 1) : moves.get(0);
        }

        board.makeMove(bestMove);
        updateHash(bestMove);
        return bestMove;
    }

    // PVS根节点 - 返回[编码走法, 分数]
    private int[] pvsRoot(PieceColor color, int depth, int prevScore) {
        List<Move> moves = generateMoves(color, 0);
        if (moves.isEmpty()) return new int[]{0, -INF};

        // 窄化窗口（Aspiration Window）
        int alpha = (prevScore == -INF) ? -INF : prevScore - 50;
        int beta = (prevScore == -INF) ? INF : prevScore + 50;

        Move bestMove = moves.get(0);
        int bestScore = -INF;
        boolean firstMove = true;

        for (Move move : moves) {
            if (System.currentTimeMillis() - startTime > TIME_LIMIT) {
                timeOut = true;
                break;
            }

            makeMove(move);
            int score;

            if (firstMove) {
                score = -pvs(color.opposite(), depth - 1, -beta, -alpha, 1);
                firstMove = false;
            } else {
                // PVS: 先用窄窗口搜索
                score = -pvs(color.opposite(), depth - 1, -alpha - 1, -alpha, 1);
                if (score > alpha && score < beta) {
                    // 重新搜索
                    score = -pvs(color.opposite(), depth - 1, -beta, -alpha, 1);
                }
            }
            undoMove(move);

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            alpha = Math.max(alpha, score);
            if (alpha >= beta) break;
        }

        // 窗口失败，重新搜索
        if (bestScore <= prevScore - 50 || bestScore >= prevScore + 50) {
            alpha = -INF;
            beta = INF;
            firstMove = true;
            for (Move move : moves) {
                if (timeOut) break;
                makeMove(move);
                int score = firstMove ?
                    -pvs(color.opposite(), depth - 1, -beta, -alpha, 1) :
                    -pvs(color.opposite(), depth - 1, -alpha - 1, -alpha, 1);
                if (!firstMove && score > alpha && score < beta) {
                    score = -pvs(color.opposite(), depth - 1, -beta, -alpha, 1);
                }
                undoMove(move);
                firstMove = false;
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                alpha = Math.max(alpha, score);
                if (alpha >= beta) break;
            }
        }

        int encoded = bestMove.index1() * 361 + bestMove.index2();
        historyScore[bestMove.index1()][bestMove.index2()] += depth * depth;
        return new int[]{encoded, bestScore};
    }

    // PVS搜索
    private int pvs(PieceColor color, int depth, int alpha, int beta, int ply) {
        if (System.currentTimeMillis() - startTime > TIME_LIMIT) {
            timeOut = true;
            return 0;
        }

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

        List<Move> moves = generateMoves(color, ply);
        if (moves.isEmpty()) return evaluate(color);

        int bestScore = -INF;
        boolean firstMove = true;

        for (Move move : moves) {
            if (timeOut) break;

            makeMove(move);
            int score;

            if (firstMove) {
                score = -pvs(color.opposite(), depth - 1, -beta, -alpha, ply + 1);
                firstMove = false;
            } else {
                score = -pvs(color.opposite(), depth - 1, -alpha - 1, -alpha, ply + 1);
                if (score > alpha && score < beta) {
                    score = -pvs(color.opposite(), depth - 1, -beta, -alpha, ply + 1);
                }
            }
            undoMove(move);

            if (score > bestScore) bestScore = score;
            alpha = Math.max(alpha, score);

            if (alpha >= beta) {
                if (ply < killerMoves.length) {
                    killerMoves[ply][1] = killerMoves[ply][0];
                    killerMoves[ply][0] = move;
                }
                break;
            }
        }

        if (!timeOut) transTable.put(currentHash, new int[]{depth, bestScore});
        return bestScore;
    }

    // 生成走法（带排序）
    private List<Move> generateMoves(PieceColor color, int ply) {
        List<Integer> candidates = getCandidates();
        List<int[]> scoredMoves = new ArrayList<>();

        for (int i = 0; i < candidates.size(); i++) {
            int pos1 = candidates.get(i);
            for (int j = i + 1; j < candidates.size(); j++) {
                int pos2 = candidates.get(j);

                // 计算走法分数用于排序
                int score = 0;

                // 胜着检测
                board.makeMove(new Move(pos1, pos2));
                if (checkWin(pos1, color) || checkWin(pos2, color)) {
                    score = SCORE_WIN;
                } else {
                    // 棋型评分
                    score = evaluateMove(pos1, pos2, color);
                }
                board.undo();

                // 杀手启发加分
                if (ply < killerMoves.length) {
                    Move m = new Move(pos1, pos2);
                    if (m.equals(killerMoves[ply][0])) score += 10000;
                    else if (m.equals(killerMoves[ply][1])) score += 5000;
                }

                // 历史启发加分
                score += historyScore[pos1][pos2];

                scoredMoves.add(new int[]{pos1, pos2, score});
            }
        }

        // 按分数排序
        scoredMoves.sort((a, b) -> b[2] - a[2]);

        // 转换为Move列表，限制数量
        List<Move> moves = new ArrayList<>();
        int limit = Math.min(scoredMoves.size(), 60);
        for (int i = 0; i < limit; i++) {
            int[] m = scoredMoves.get(i);
            moves.add(new Move(m[0], m[1]));
        }

        return moves;
    }

    // 评估单步走法
    private int evaluateMove(int pos1, int pos2, PieceColor color) {
        return evaluatePosition(pos1, color) + evaluatePosition(pos2, color);
    }

    // 评估单点（精细棋型）
    private int evaluatePosition(int pos, PieceColor color) {
        int col = pos % 19;
        int row = pos / 19;
        int score = 0;

        // 中心位置加分
        score += (18 - Math.abs(col - 9) - Math.abs(row - 9)) * 5;

        for (int[] dir : DIRECTIONS) {
            int[] pattern = getPattern(col, row, dir, color);
            score += patternScore(pattern, color);
        }

        return score;
    }

    // 获取某方向的棋型
    private int[] getPattern(int col, int row, int[] dir, PieceColor color) {
        int myCount = 0, oppCount = 0, emptyCount = 0;
        int openEnds = 0;

        // 正向
        boolean blocked = false;
        for (int k = 1; k <= 5; k++) {
            int nc = col + dir[0] * k;
            int nr = row + dir[1] * k;
            if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) {
                blocked = true;
                break;
            }
            PieceColor c = board.get(nc * 19 + nr);
            if (c == color) myCount++;
            else if (c == PieceColor.EMPTY) {
                emptyCount++;
                break;
            } else {
                oppCount++;
                blocked = true;
                break;
            }
        }
        if (!blocked) openEnds++;

        // 反向
        blocked = false;
        for (int k = 1; k <= 5; k++) {
            int nc = col - dir[0] * k;
            int nr = row - dir[1] * k;
            if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) {
                blocked = true;
                break;
            }
            PieceColor c = board.get(nc * 19 + nr);
            if (c == color) myCount++;
            else if (c == PieceColor.EMPTY) {
                emptyCount++;
                break;
            } else {
                oppCount++;
                blocked = true;
                break;
            }
        }
        if (!blocked) openEnds++;

        return new int[]{myCount, oppCount, openEnds};
    }

    // 棋型评分
    private int patternScore(int[] pattern, PieceColor color) {
        int my = pattern[0], opp = pattern[1], open = pattern[2];

        // 进攻评分
        if (opp == 0) {
            if (my >= 5) return SCORE_LIVE5;
            if (my == 4 && open == 2) return SCORE_LIVE4;
            if (my == 4 && open == 1) return SCORE_RUSH4;
            if (my == 3 && open == 2) return SCORE_LIVE3;
            if (my == 3 && open == 1) return SCORE_SLEEP3;
            if (my == 2 && open == 2) return SCORE_LIVE2;
            if (my == 2 && open == 1) return SCORE_SLEEP2;
            if (my == 1) return 10;
        }

        // 防守评分（对方棋型）
        if (my == 0) {
            if (opp >= 5) return SCORE_LIVE5 / 2;
            if (opp == 4 && open >= 1) return SCORE_RUSH4 / 2;
            if (opp == 3 && open == 2) return SCORE_LIVE3 / 2;
            if (opp == 3 && open == 1) return SCORE_SLEEP3 / 2;
            if (opp == 2 && open == 2) return SCORE_LIVE2 / 2;
        }

        return 0;
    }

    // 威胁空间搜索
    private Move threatSpaceSearch(PieceColor color, int depth) {
        if (depth <= 0) return null;

        List<Move> threats = findThreatMoves(color);

        for (Move threat : threats) {
            board.makeMove(threat);

            if (checkWin(threat.index1(), color) || checkWin(threat.index2(), color)) {
                board.undo();
                return threat;
            }

            List<Integer> defensePoints = findDefensePoints(color);

            if (defensePoints.isEmpty() || defensePoints.size() > 2) {
                board.undo();
                return threat;
            }

            boolean allWin = true;
            for (int i = 0; i < defensePoints.size() && allWin; i++) {
                int d1 = defensePoints.get(i);
                int d2 = (defensePoints.size() > 1) ? defensePoints.get(1 - i) : findBestDefense(color.opposite(), d1);

                if (d2 == -1) continue;

                Move defense = new Move(d1, d2);
                if (!board.legalMove(defense)) continue;

                board.makeMove(defense);
                Move nextThreat = threatSpaceSearch(color, depth - 1);
                if (nextThreat == null) allWin = false;
                board.undo();
            }

            board.undo();

            if (allWin && !defensePoints.isEmpty()) return threat;
        }

        return null;
    }

    // 找威胁走法
    private List<Move> findThreatMoves(PieceColor color) {
        List<int[]> threats = new ArrayList<>();
        List<Integer> candidates = getCandidates();

        for (int i = 0; i < candidates.size(); i++) {
            int pos1 = candidates.get(i);
            for (int j = i + 1; j < candidates.size(); j++) {
                int pos2 = candidates.get(j);
                Move move = new Move(pos1, pos2);

                if (!board.legalMove(move)) continue;

                board.makeMove(move);
                int threatCount = countThreats(pos1, color) + countThreats(pos2, color);
                board.undo();

                if (threatCount > 0) {
                    threats.add(new int[]{pos1, pos2, threatCount});
                }
            }
        }

        threats.sort((a, b) -> b[2] - a[2]);

        List<Move> result = new ArrayList<>();
        int limit = Math.min(threats.size(), 25);
        for (int i = 0; i < limit; i++) {
            result.add(new Move(threats.get(i)[0], threats.get(i)[1]));
        }
        return result;
    }

    private int countThreats(int pos, PieceColor color) {
        int col = pos % 19, row = pos / 19, threats = 0;

        for (int[] dir : DIRECTIONS) {
            int count = 1, openEnds = 0;

            for (int k = 1; k <= 5; k++) {
                int nc = col + dir[0] * k, nr = row + dir[1] * k;
                if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) break;
                PieceColor c = board.get(nc * 19 + nr);
                if (c == color) count++;
                else if (c == PieceColor.EMPTY) { openEnds++; break; }
                else break;
            }

            for (int k = 1; k <= 5; k++) {
                int nc = col - dir[0] * k, nr = row - dir[1] * k;
                if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) break;
                PieceColor c = board.get(nc * 19 + nr);
                if (c == color) count++;
                else if (c == PieceColor.EMPTY) { openEnds++; break; }
                else break;
            }

            if (count >= 5) threats += 100;
            else if (count == 4 && openEnds == 2) threats += 50;
            else if (count == 4 && openEnds == 1) threats += 10;
            else if (count == 3 && openEnds == 2) threats += 5;
        }
        return threats;
    }

    private List<Integer> findDefensePoints(PieceColor attackColor) {
        Set<Integer> defensePoints = new HashSet<>();

        for (int pos = 0; pos < 361; pos++) {
            if (board.get(pos) != PieceColor.EMPTY) continue;
            int col = pos % 19, row = pos / 19;

            for (int[] dir : DIRECTIONS) {
                int count = 0;
                for (int k = -5; k <= 5; k++) {
                    int nc = col + dir[0] * k, nr = row + dir[1] * k;
                    if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) continue;
                    if (board.get(nc * 19 + nr) == attackColor) count++;
                }
                if (count >= 4) { defensePoints.add(pos); break; }
            }
        }
        return new ArrayList<>(defensePoints);
    }

    private int findBestDefense(PieceColor color, int exclude) {
        int bestPos = -1, bestScore = -1;
        for (int pos = 0; pos < 361; pos++) {
            if (pos == exclude || board.get(pos) != PieceColor.EMPTY) continue;
            int score = evaluatePosition(pos, color);
            if (score > bestScore) { bestScore = score; bestPos = pos; }
        }
        return bestPos;
    }

    // 有效路估值
    private int evaluate(PieceColor myColor) {
        int myScore = 0, oppScore = 0;
        PieceColor oppColor = myColor.opposite();

        for (int pos = 0; pos < 361; pos++) {
            int col = pos % 19, row = pos / 19;

            for (int[] dir : DIRECTIONS) {
                if (!validLine(col, row, dir)) continue;

                int my = 0, opp = 0;
                for (int k = 0; k < 6; k++) {
                    PieceColor c = board.get((col + dir[0] * k) * 19 + (row + dir[1] * k));
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
        int endCol = col + dir[0] * 5, endRow = row + dir[1] * 5;
        return endCol >= 0 && endCol < 19 && endRow >= 0 && endRow < 19;
    }

    private List<Integer> getCandidates() {
        Set<Integer> candidates = new HashSet<>();
        for (int pos = 0; pos < 361; pos++) {
            if (board.get(pos) != PieceColor.EMPTY) {
                int col = pos % 19, row = pos / 19;
                for (int dc = -2; dc <= 2; dc++) {
                    for (int dr = -2; dr <= 2; dr++) {
                        int nc = col + dc, nr = row + dr;
                        if (nc >= 0 && nc < 19 && nr >= 0 && nr < 19) {
                            int npos = nc * 19 + nr;
                            if (board.get(npos) == PieceColor.EMPTY) candidates.add(npos);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(candidates);
    }

    private boolean checkWin(int pos, PieceColor color) {
        int col = pos % 19, row = pos / 19;
        for (int[] dir : DIRECTIONS) {
            int count = 1;
            for (int k = 1; k < 6; k++) {
                int nc = col + dir[0] * k, nr = row + dir[1] * k;
                if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) break;
                if (board.get(nc * 19 + nr) != color) break;
                count++;
            }
            for (int k = 1; k < 6; k++) {
                int nc = col - dir[0] * k, nr = row - dir[1] * k;
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
    public String name() { return "G06"; }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
        currentHash = 0;
        transTable.clear();
        for (int i = 0; i < killerMoves.length; i++) {
            killerMoves[i][0] = null;
            killerMoves[i][1] = null;
        }
        for (int i = 0; i < 361; i++) Arrays.fill(historyScore[i], 0);
        currentHash ^= zobristTable[Move.index('J', 'J')][1];
    }
}
