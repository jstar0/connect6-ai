package stud.g06;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

/**
 * G06 AI - 重构版
 * 核心：斩杀检测 + 必堵防御 + Alpha-Beta搜索
 */
public class AI extends core.player.AI {

    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private static final int MAX_DEPTH = 4;
    private static final int INF = 10000000;
    private static final long TIME_LIMIT = 4500;

    private long startTime;
    private Map<Long, int[]> transTable = new HashMap<>();
    private long[][] zobristTable = new long[361][3];
    private long currentHash = 0;

    public AI() {
        Random rand = new Random(12345);
        for (int i = 0; i < 361; i++) {
            for (int j = 0; j < 3; j++) {
                zobristTable[i][j] = rand.nextLong();
            }
        }
    }

    @Override
    public Move findNextMove(Move opponentMove) {
        if (opponentMove != null) {
            board.makeMove(opponentMove);
            updateHash(opponentMove);
        }
        startTime = System.currentTimeMillis();

        PieceColor myColor = board.whoseMove();
        PieceColor oppColor = myColor.opposite();

        // 1. 斩杀检测 - 我能赢吗？
        Move killMove = findKillMove(myColor);
        if (killMove != null) {
            board.makeMove(killMove);
            updateHash(killMove);
            return killMove;
        }

        // 2. 必堵防御 - 对方能赢吗？必须堵住！
        List<Integer> mustBlock = getMustBlockSpots(oppColor);
        if (!mustBlock.isEmpty()) {
            int s1 = mustBlock.get(0);
            int s2 = mustBlock.size() > 1 ? mustBlock.get(1) : findBestSpot(s1);
            Move m = new Move(s1, s2);
            board.makeMove(m);
            updateHash(m);
            return m;
        }

        // 3. Alpha-Beta搜索
        Move bestMove = alphaBetaSearch(myColor);
        board.makeMove(bestMove);
        updateHash(bestMove);
        return bestMove;
    }

    // 找所有"对方下一步能成6"的空位
    private List<Integer> getMustBlockSpots(PieceColor color) {
        Set<Integer> spots = new LinkedHashSet<>();

        for (int r = 0; r < 19; r++) {
            for (int c = 0; c < 19; c++) {
                for (int[] d : DIRECTIONS) {
                    List<Integer> empties = checkWindow(r, c, d[0], d[1], color);
                    if (empties != null && empties.size() <= 2) {
                        spots.addAll(empties);
                    }
                }
            }
        }
        return new ArrayList<>(spots);
    }

    // 检查6格窗口，返回空位列表（如果该窗口有4+连子且没有对方棋子）
    private List<Integer> checkWindow(int r, int c, int dr, int dc, PieceColor color) {
        List<Integer> empties = new ArrayList<>();
        int count = 0;

        for (int i = 0; i < 6; i++) {
            int nr = r + dr * i, nc = c + dc * i;
            if (nr < 0 || nr >= 19 || nc < 0 || nc >= 19) return null;

            int pos = nr * 19 + nc;
            PieceColor p = board.get(pos);

            if (p == color.opposite()) return null; // 被对方截断
            if (p == color) count++;
            else empties.add(pos);
        }

        return (count >= 4) ? empties : null; // 4连或5连才是威胁
    }

    // 找斩杀走法（我方有4+连子的窗口）
    private Move findKillMove(PieceColor color) {
        List<Integer> winSpots = getMustBlockSpots(color);
        if (winSpots.size() >= 2) {
            return new Move(winSpots.get(0), winSpots.get(1));
        }
        if (winSpots.size() == 1) {
            return new Move(winSpots.get(0), findBestSpot(winSpots.get(0)));
        }
        return null;
    }

    // 找最佳空位（排除指定位置）
    private int findBestSpot(int exclude) {
        List<int[]> spots = new ArrayList<>();
        for (int pos = 0; pos < 361; pos++) {
            if (pos != exclude && board.get(pos) == PieceColor.EMPTY) {
                spots.add(new int[]{pos, evaluateSpot(pos)});
            }
        }
        spots.sort((a, b) -> b[1] - a[1]);
        return spots.isEmpty() ? (exclude + 1) % 361 : spots.get(0)[0];
    }

    // Alpha-Beta搜索
    private Move alphaBetaSearch(PieceColor myColor) {
        List<Move> moves = generateMoves();
        if (moves.isEmpty()) return new Move(180, 181);

        Move bestMove = moves.get(0);
        int bestScore = -INF;

        for (Move move : moves) {
            if (System.currentTimeMillis() - startTime > TIME_LIMIT) break;

            makeMove(move);
            int score = -alphaBeta(myColor.opposite(), MAX_DEPTH - 1, -INF, -bestScore);
            undoMove(move);

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }
        return bestMove;
    }

    private int alphaBeta(PieceColor color, int depth, int alpha, int beta) {
        // 检查是否有必胜/必败
        List<Integer> myWin = getMustBlockSpots(color);
        if (myWin.size() >= 2) return INF - (MAX_DEPTH - depth); // 我能赢

        List<Integer> oppWin = getMustBlockSpots(color.opposite());
        if (oppWin.size() > 2) return -INF + (MAX_DEPTH - depth); // 对方必胜

        if (depth == 0) return evaluate(color);

        // 置换表
        int[] cached = transTable.get(currentHash);
        if (cached != null && cached[0] >= depth) return cached[1];

        List<Move> moves = generateMoves();
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

    // 生成走法（按评分排序）
    private List<Move> generateMoves() {
        List<Integer> candidates = getCandidates();
        List<int[]> scoredMoves = new ArrayList<>();

        int n = Math.min(candidates.size(), 15);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int pos1 = candidates.get(i), pos2 = candidates.get(j);
                int score = evaluateSpot(pos1) + evaluateSpot(pos2);
                scoredMoves.add(new int[]{pos1, pos2, score});
            }
        }

        scoredMoves.sort((a, b) -> b[2] - a[2]);

        List<Move> moves = new ArrayList<>();
        int limit = Math.min(scoredMoves.size(), 30);
        for (int i = 0; i < limit; i++) {
            moves.add(new Move(scoredMoves.get(i)[0], scoredMoves.get(i)[1]));
        }
        return moves;
    }

    // 获取候选位置（已有棋子周围2格）
    private List<Integer> getCandidates() {
        Map<Integer, Integer> scores = new HashMap<>();
        for (int pos = 0; pos < 361; pos++) {
            if (board.get(pos) != PieceColor.EMPTY) {
                int r = pos / 19, c = pos % 19;
                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        int nr = r + dr, nc = c + dc;
                        if (nr >= 0 && nr < 19 && nc >= 0 && nc < 19) {
                            int npos = nr * 19 + nc;
                            if (board.get(npos) == PieceColor.EMPTY) {
                                scores.put(npos, evaluateSpot(npos));
                            }
                        }
                    }
                }
            }
        }

        List<Integer> list = new ArrayList<>(scores.keySet());
        list.sort((a, b) -> scores.get(b) - scores.get(a));
        return list;
    }

    // 评估单点
    private int evaluateSpot(int pos) {
        int r = pos / 19, c = pos % 19;
        int score = 0;
        PieceColor myColor = board.whoseMove();

        for (int[] d : DIRECTIONS) {
            for (int offset = -5; offset <= 0; offset++) {
                int sr = r + d[0] * offset, sc = c + d[1] * offset;
                int myC = 0, oppC = 0;
                boolean valid = true;

                for (int i = 0; i < 6; i++) {
                    int nr = sr + d[0] * i, nc = sc + d[1] * i;
                    if (nr < 0 || nr >= 19 || nc < 0 || nc >= 19) {
                        valid = false;
                        break;
                    }
                    PieceColor p = board.get(nr * 19 + nc);
                    if (p == myColor) myC++;
                    else if (p == myColor.opposite()) oppC++;
                }

                if (valid) {
                    // 防御权重略高于进攻
                    if (oppC > 0 && myC == 0) score += (int) Math.pow(10, oppC);
                    if (myC > 0 && oppC == 0) score += (int) Math.pow(9, myC);
                }
            }
        }

        // 中心加分
        score += (9 - Math.abs(r - 9)) + (9 - Math.abs(c - 9));
        return score;
    }

    // 评估局面
    private int evaluate(PieceColor myColor) {
        int myScore = 0, oppScore = 0;
        PieceColor oppColor = myColor.opposite();

        for (int r = 0; r < 19; r++) {
            for (int c = 0; c < 19; c++) {
                for (int[] d : DIRECTIONS) {
                    int endR = r + d[0] * 5, endC = c + d[1] * 5;
                    if (endR < 0 || endR >= 19 || endC < 0 || endC >= 19) continue;

                    int my = 0, opp = 0;
                    for (int i = 0; i < 6; i++) {
                        PieceColor p = board.get((r + d[0] * i) * 19 + (c + d[1] * i));
                        if (p == myColor) my++;
                        else if (p == oppColor) opp++;
                    }

                    if (opp == 0 && my > 0) myScore += (int) Math.pow(10, my);
                    if (my == 0 && opp > 0) oppScore += (int) Math.pow(10, opp);
                }
            }
        }
        return myScore - oppScore;
    }

    private void makeMove(Move move) {
        board.makeMove(move);
        updateHash(move);
    }

    private void undoMove(Move move) {
        PieceColor color = board.whoseMove();
        board.undo();
        int idx = (color == PieceColor.BLACK) ? 1 : 2;
        currentHash ^= zobristTable[move.index1()][idx] ^ zobristTable[move.index2()][idx];
        currentHash ^= zobristTable[move.index1()][0] ^ zobristTable[move.index2()][0];
    }

    private void updateHash(Move move) {
        if (move == null) return;
        PieceColor color = board.get(move.index1());
        int idx = (color == PieceColor.BLACK) ? 1 : 2;
        currentHash ^= zobristTable[move.index1()][0] ^ zobristTable[move.index2()][0];
        currentHash ^= zobristTable[move.index1()][idx] ^ zobristTable[move.index2()][idx];
    }

    @Override
    public String name() { return "G06"; }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
        currentHash = 0;
        transTable.clear();
    }
}
