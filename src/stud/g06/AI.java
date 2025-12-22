package stud.g06;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

/**
 * G06 AI - V3 战术优化版
 * 核心：快速威胁检测 + 双威胁搜索 + 迭代加深Alpha-Beta
 */
public class AI extends core.player.AI {

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private static final int INF = 10000000;
    private static final long TIME_LIMIT = 4800;
    private static final int[] POW = {1, 10, 100, 1000, 10000, 100000, 1000000};

    private long startTime;
    private Map<Long, int[]> tt = new HashMap<>();
    private long[][] zobrist = new long[361][3];
    private long hash = 0;

    // 缓存威胁检测结果
    private int[] threatCache = new int[361];
    private long threatCacheHash = -1;

    public AI() {
        Random r = new Random(12345);
        for (int i = 0; i < 361; i++)
            for (int j = 0; j < 3; j++)
                zobrist[i][j] = r.nextLong();
    }

    @Override
    public Move findNextMove(Move opponentMove) {
        if (opponentMove != null) {
            board.makeMove(opponentMove);
            updateHash(opponentMove);
        }
        startTime = System.currentTimeMillis();
        PieceColor me = board.whoseMove();
        PieceColor opp = me.opposite();

        // 1. 即时斩杀
        List<Integer> myWin = getWinSpots(me);
        if (myWin.size() >= 2) return commit(new Move(myWin.get(0), myWin.get(1)));
        if (myWin.size() == 1) return commit(new Move(myWin.get(0), getBest(myWin.get(0), me)));

        // 2. 必须防守
        List<Integer> oppWin = getWinSpots(opp);
        if (!oppWin.isEmpty()) {
            int s1 = oppWin.get(0);
            int s2 = oppWin.size() > 1 ? oppWin.get(1) : getBest(s1, me);
            return commit(new Move(s1, s2));
        }

        // 3. 快速双威胁搜索（深度2）
        Move dt = findDoubleThreat(me, 2);
        if (dt != null) return commit(dt);

        // 4. 检测对方潜在双威胁并阻止
        Move block = blockDoubleThreat(opp);
        if (block != null) return commit(block);

        // 5. 迭代加深搜索
        Move best = iterativeDeepening(me);
        return commit(best);
    }

    private Move commit(Move m) {
        board.makeMove(m);
        updateHash(m);
        return m;
    }

    // 获取4连/5连的空位
    private List<Integer> getWinSpots(PieceColor color) {
        Map<Integer, Integer> spots = new HashMap<>();
        PieceColor opp = color.opposite();
        for (int r = 0; r < 19; r++) {
            for (int c = 0; c < 19; c++) {
                for (int[] d : DIRS) {
                    int cnt = 0, emptyCnt = 0;
                    int[] empties = new int[2];
                    boolean valid = true;
                    for (int i = 0; i < 6 && valid; i++) {
                        int nr = r + d[0] * i, nc = c + d[1] * i;
                        if (nr < 0 || nr >= 19 || nc < 0 || nc >= 19) { valid = false; break; }
                        PieceColor p = board.get(nr * 19 + nc);
                        if (p == opp) valid = false;
                        else if (p == color) cnt++;
                        else if (emptyCnt < 2) empties[emptyCnt++] = nr * 19 + nc;
                        else emptyCnt++;
                    }
                    if (valid && cnt >= 4 && emptyCnt <= 2) {
                        for (int i = 0; i < emptyCnt; i++) spots.merge(empties[i], 1, Integer::sum);
                    }
                }
            }
        }
        List<Integer> result = new ArrayList<>(spots.keySet());
        result.sort((a, b) -> spots.get(b) - spots.get(a));
        return result;
    }

    // 快速双威胁搜索
    private Move findDoubleThreat(PieceColor color, int depth) {
        if (depth <= 0) return null;

        List<int[]> cands = getScoredCandidates(color);
        int n = Math.min(cands.size(), 12);

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Move m = new Move(cands.get(i)[0], cands.get(j)[0]);
                board.makeMove(m);

                List<Integer> threats = getWinSpots(color);
                if (threats.size() >= 3) {
                    board.undo();
                    return m;
                }

                if (threats.size() >= 1 && depth > 1) {
                    // 对方防守后继续搜索
                    PieceColor opp = color.opposite();
                    int b1 = threats.get(0);
                    int b2 = threats.size() > 1 ? threats.get(1) : getBestFast(b1);
                    Move def = new Move(b1, b2);
                    board.makeMove(def);

                    Move next = findDoubleThreat(color, depth - 1);
                    board.undo();
                    board.undo();

                    if (next != null) return m;
                } else {
                    board.undo();
                }
            }
        }
        return null;
    }

    // 阻止对方双威胁
    private Move blockDoubleThreat(PieceColor opp) {
        List<int[]> oppCands = getScoredCandidates(opp);
        int n = Math.min(oppCands.size(), 10);

        Set<Integer> dangerSpots = new HashSet<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Move m = new Move(oppCands.get(i)[0], oppCands.get(j)[0]);
                board.makeMove(m);
                List<Integer> threats = getWinSpots(opp);
                board.undo();

                if (threats.size() >= 3) {
                    dangerSpots.add(oppCands.get(i)[0]);
                    dangerSpots.add(oppCands.get(j)[0]);
                }
            }
        }

        if (dangerSpots.size() >= 2) {
            List<Integer> list = new ArrayList<>(dangerSpots);
            return new Move(list.get(0), list.get(1));
        }
        return null;
    }

    private List<int[]> getScoredCandidates(PieceColor color) {
        List<int[]> result = new ArrayList<>();
        PieceColor opp = color.opposite();

        for (int pos : getCandidates()) {
            int score = evalSpotFor(pos, color);
            result.add(new int[]{pos, score});
        }
        result.sort((a, b) -> b[1] - a[1]);
        return result;
    }

    private int evalSpotFor(int pos, PieceColor color) {
        int r = pos / 19, c = pos % 19;
        int score = 0;
        PieceColor opp = color.opposite();

        for (int[] d : DIRS) {
            for (int off = -5; off <= 0; off++) {
                int myC = 0, oppC = 0, empty = 0;
                boolean valid = true, hasPos = false;
                for (int i = 0; i < 6 && valid; i++) {
                    int nr = r + d[0] * (off + i), nc = c + d[1] * (off + i);
                    if (nr < 0 || nr >= 19 || nc < 0 || nc >= 19) { valid = false; break; }
                    int p = nr * 19 + nc;
                    if (p == pos) hasPos = true;
                    PieceColor pc = board.get(p);
                    if (pc == opp) oppC++;
                    else if (pc == color) myC++;
                    else empty++;
                }
                if (valid && hasPos && oppC == 0) {
                    score += POW[myC + 1]; // 进攻分
                }
            }
        }
        score += 18 - Math.abs(r - 9) - Math.abs(c - 9);
        return score;
    }

    // 迭代加深
    private Move iterativeDeepening(PieceColor me) {
        List<Move> moves = genMoves(me);
        if (moves.isEmpty()) return new Move(180, 181);

        Move best = moves.get(0);

        for (int depth = 2; depth <= 8; depth += 2) {
            if (System.currentTimeMillis() - startTime > TIME_LIMIT - 1000) break;

            int alpha = -INF, beta = INF;
            Move iterBest = moves.get(0);
            int iterBestScore = -INF;

            for (int i = 0; i < moves.size(); i++) {
                if (System.currentTimeMillis() - startTime > TIME_LIMIT - 500) break;

                Move m = moves.get(i);
                makeMove(m);
                int score;
                if (i == 0) {
                    score = -negamax(depth - 1, -beta, -alpha);
                } else {
                    score = -negamax(depth - 1, -alpha - 1, -alpha);
                    if (score > alpha && score < beta) {
                        score = -negamax(depth - 1, -beta, -alpha);
                    }
                }
                undoMove(m);

                if (score > iterBestScore) {
                    iterBestScore = score;
                    iterBest = m;
                }
                alpha = Math.max(alpha, score);
            }

            best = iterBest;

            // 重排序
            final Move fb = best;
            moves.sort((a, b) -> a.equals(fb) ? -1 : b.equals(fb) ? 1 : 0);
        }
        return best;
    }

    private int negamax(int depth, int alpha, int beta) {
        PieceColor me = board.whoseMove();
        PieceColor opp = me.opposite();

        // 终局检测
        List<Integer> myWin = getWinSpots(me);
        if (myWin.size() >= 2) return INF - (20 - depth);

        List<Integer> oppWin = getWinSpots(opp);
        if (oppWin.size() > 2) return -INF + (20 - depth);

        if (depth <= 0) return eval(me);

        // 置换表
        int[] cached = tt.get(hash);
        if (cached != null && cached[0] >= depth) {
            if (cached[2] == 0) return cached[1];
            if (cached[2] == 1 && cached[1] >= beta) return cached[1];
            if (cached[2] == -1 && cached[1] <= alpha) return cached[1];
        }

        // 必须防守
        if (!oppWin.isEmpty()) {
            int s1 = oppWin.get(0);
            int s2 = oppWin.size() > 1 ? oppWin.get(1) : getBestFast(s1);
            Move m = new Move(s1, s2);
            makeMove(m);
            int score = -negamax(depth - 1, -beta, -alpha);
            undoMove(m);
            return score;
        }

        List<Move> moves = genMoves(me);
        if (moves.isEmpty()) return eval(me);

        int origAlpha = alpha;
        int bestScore = -INF;

        for (int i = 0; i < moves.size(); i++) {
            Move m = moves.get(i);
            makeMove(m);
            int score;
            if (i == 0) {
                score = -negamax(depth - 1, -beta, -alpha);
            } else {
                score = -negamax(depth - 1, -alpha - 1, -alpha);
                if (score > alpha && score < beta) {
                    score = -negamax(depth - 1, -beta, -alpha);
                }
            }
            undoMove(m);

            bestScore = Math.max(bestScore, score);
            alpha = Math.max(alpha, score);
            if (alpha >= beta) break;
        }

        int flag = (bestScore <= origAlpha) ? -1 : (bestScore >= beta) ? 1 : 0;
        tt.put(hash, new int[]{depth, bestScore, flag});
        return bestScore;
    }

    private List<Move> genMoves(PieceColor me) {
        PieceColor opp = me.opposite();

        // 优先威胁点
        Set<Integer> priority = new HashSet<>();
        priority.addAll(getWinSpots(me));
        priority.addAll(getWinSpots(opp));

        List<int[]> spots = new ArrayList<>();
        for (int pos : getCandidates()) {
            int score = evalSpot(pos, me);
            if (priority.contains(pos)) score += 100000;
            spots.add(new int[]{pos, score});
        }
        spots.sort((a, b) -> b[1] - a[1]);

        List<Move> moves = new ArrayList<>();
        int n = Math.min(spots.size(), 16);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                moves.add(new Move(spots.get(i)[0], spots.get(j)[0]));
            }
        }
        if (moves.size() > 35) moves = moves.subList(0, 35);
        return moves;
    }

    private Set<Integer> getCandidates() {
        Set<Integer> cands = new HashSet<>();
        for (int pos = 0; pos < 361; pos++) {
            if (board.get(pos) != PieceColor.EMPTY) {
                int r = pos / 19, c = pos % 19;
                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        int nr = r + dr, nc = c + dc;
                        if (nr >= 0 && nr < 19 && nc >= 0 && nc < 19) {
                            int np = nr * 19 + nc;
                            if (board.get(np) == PieceColor.EMPTY) cands.add(np);
                        }
                    }
                }
            }
        }
        if (cands.isEmpty()) cands.add(180);
        return cands;
    }

    private int evalSpot(int pos, PieceColor me) {
        int r = pos / 19, c = pos % 19;
        int score = 0;
        PieceColor opp = me.opposite();

        for (int[] d : DIRS) {
            for (int off = -5; off <= 0; off++) {
                int myC = 0, oppC = 0;
                boolean valid = true;
                for (int i = 0; i < 6 && valid; i++) {
                    int nr = r + d[0] * (off + i), nc = c + d[1] * (off + i);
                    if (nr < 0 || nr >= 19 || nc < 0 || nc >= 19) { valid = false; break; }
                    PieceColor p = board.get(nr * 19 + nc);
                    if (p == me) myC++;
                    else if (p == opp) oppC++;
                }
                if (valid) {
                    if (oppC == 0 && myC > 0) score += POW[myC];
                    if (myC == 0 && oppC > 0) score += POW[oppC] * 2;
                }
            }
        }
        score += 18 - Math.abs(r - 9) - Math.abs(c - 9);
        return score;
    }

    private int eval(PieceColor me) {
        int myScore = 0, oppScore = 0;
        PieceColor opp = me.opposite();

        for (int r = 0; r < 19; r++) {
            for (int c = 0; c < 19; c++) {
                for (int[] d : DIRS) {
                    if (r + d[0] * 5 < 0 || r + d[0] * 5 >= 19 ||
                        c + d[1] * 5 < 0 || c + d[1] * 5 >= 19) continue;

                    int my = 0, op = 0;
                    for (int i = 0; i < 6; i++) {
                        PieceColor p = board.get((r + d[0] * i) * 19 + (c + d[1] * i));
                        if (p == me) my++;
                        else if (p == opp) op++;
                    }
                    if (op == 0 && my > 0) myScore += POW[my];
                    if (my == 0 && op > 0) oppScore += POW[op];
                }
            }
        }
        return myScore - oppScore;
    }

    private int getBest(int exclude, PieceColor me) {
        int best = -1, bestScore = -1;
        for (int pos : getCandidates()) {
            if (pos != exclude) {
                int s = evalSpot(pos, me);
                if (s > bestScore) { bestScore = s; best = pos; }
            }
        }
        return best >= 0 ? best : (exclude + 1) % 361;
    }

    private int getBestFast(int exclude) {
        for (int pos : getCandidates()) {
            if (pos != exclude) return pos;
        }
        return (exclude + 1) % 361;
    }

    private void makeMove(Move m) {
        board.makeMove(m);
        updateHash(m);
    }

    private void undoMove(Move m) {
        PieceColor c = board.whoseMove();
        board.undo();
        int idx = (c == PieceColor.BLACK) ? 1 : 2;
        hash ^= zobrist[m.index1()][idx] ^ zobrist[m.index2()][idx];
        hash ^= zobrist[m.index1()][0] ^ zobrist[m.index2()][0];
    }

    private void updateHash(Move m) {
        if (m == null) return;
        PieceColor c = board.get(m.index1());
        int idx = (c == PieceColor.BLACK) ? 1 : 2;
        hash ^= zobrist[m.index1()][0] ^ zobrist[m.index2()][0];
        hash ^= zobrist[m.index1()][idx] ^ zobrist[m.index2()][idx];
    }

    @Override
    public String name() { return "G06"; }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
        hash = 0;
        tt.clear();
    }
}
