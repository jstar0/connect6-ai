package stud.g06;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

/**
 * 带随机性的AI - 用于自我对弈测试
 * 在同分走法中随机选择，测试真实胜率
 */
public class AIRandom extends core.player.AI {

    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private static final int MAX_DEPTH = 4;
    private static final int TBS_DEPTH = 10;
    private static final int INF = 10000000;

    private static final int SCORE_WIN = 1000000;
    private static final int SCORE_LIVE4 = 50000;
    private static final int SCORE_RUSH4 = 10000;
    private static final int SCORE_LIVE3 = 5000;
    private static final int SCORE_SLEEP3 = 1000;
    private static final int SCORE_LIVE2 = 500;

    private Map<Long, int[]> transTable = new HashMap<>();
    private long[][] zobristTable = new long[361][3];
    private long currentHash = 0;
    private Random rand = new Random(); // 不固定种子

    public AIRandom() {
        initZobrist();
    }

    private void initZobrist() {
        Random r = new Random(); // 每次不同
        for (int i = 0; i < 361; i++) {
            for (int j = 0; j < 3; j++) {
                zobristTable[i][j] = r.nextLong();
            }
        }
    }

    @Override
    public Move findNextMove(Move opponentMove) {
        board.makeMove(opponentMove);
        updateHash(opponentMove);

        PieceColor myColor = board.whoseMove();

        // TBS
        Move tbsMove = threatSpaceSearch(myColor, TBS_DEPTH);
        if (tbsMove != null) {
            board.makeMove(tbsMove);
            updateHash(tbsMove);
            return tbsMove;
        }

        // Alpha-Beta with randomness
        Move bestMove = alphaBetaRoot(myColor, MAX_DEPTH);
        board.makeMove(bestMove);
        updateHash(bestMove);
        return bestMove;
    }

    private Move alphaBetaRoot(PieceColor color, int depth) {
        List<Move> moves = generateMoves(color);
        if (moves.isEmpty()) return new Move(0, 1);

        List<Move> bestMoves = new ArrayList<>();
        int bestScore = -INF;

        for (Move move : moves) {
            makeMove(move);
            int score = -alphaBeta(color.opposite(), depth - 1, -INF, -bestScore);
            undoMove(move);

            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (score == bestScore) {
                bestMoves.add(move); // 同分走法都保留
            }
        }

        // 从同分走法中随机选择
        return bestMoves.get(rand.nextInt(bestMoves.size()));
    }

    private int alphaBeta(PieceColor color, int depth, int alpha, int beta) {
        int[] cached = transTable.get(currentHash);
        if (cached != null && cached[0] >= depth) return cached[1];

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

                if (threatCount > 0) threats.add(new int[]{pos1, pos2, threatCount});
            }
        }

        threats.sort((a, b) -> b[2] - a[2]);
        List<Move> result = new ArrayList<>();
        int limit = Math.min(threats.size(), 20);
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
            else if (count == 4 && openEnds >= 1) threats += 10;
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

    private List<Move> generateMoves(PieceColor color) {
        List<Integer> candidates = getCandidates();
        List<int[]> scoredMoves = new ArrayList<>();

        for (int i = 0; i < candidates.size(); i++) {
            int pos1 = candidates.get(i);
            for (int j = i + 1; j < candidates.size(); j++) {
                int pos2 = candidates.get(j);
                board.makeMove(new Move(pos1, pos2));
                int score = (checkWin(pos1, color) || checkWin(pos2, color)) ? SCORE_WIN :
                    evaluatePosition(pos1, color) + evaluatePosition(pos2, color);
                board.undo();
                scoredMoves.add(new int[]{pos1, pos2, score});
            }
        }

        scoredMoves.sort((a, b) -> b[2] - a[2]);
        List<Move> moves = new ArrayList<>();
        int limit = Math.min(scoredMoves.size(), 50);
        for (int i = 0; i < limit; i++) {
            moves.add(new Move(scoredMoves.get(i)[0], scoredMoves.get(i)[1]));
        }
        return moves;
    }

    private int evaluatePosition(int pos, PieceColor color) {
        int col = pos % 19, row = pos / 19;
        int score = (18 - Math.abs(col - 9) - Math.abs(row - 9)) * 5;

        for (int[] dir : DIRECTIONS) {
            int my = 0, opp = 0, open = 0;
            for (int k = 1; k <= 5; k++) {
                int nc = col + dir[0] * k, nr = row + dir[1] * k;
                if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) break;
                PieceColor c = board.get(nc * 19 + nr);
                if (c == color) my++;
                else if (c == PieceColor.EMPTY) { open++; break; }
                else { opp++; break; }
            }
            for (int k = 1; k <= 5; k++) {
                int nc = col - dir[0] * k, nr = row - dir[1] * k;
                if (nc < 0 || nc >= 19 || nr < 0 || nr >= 19) break;
                PieceColor c = board.get(nc * 19 + nr);
                if (c == color) my++;
                else if (c == PieceColor.EMPTY) { open++; break; }
                else { opp++; break; }
            }

            if (opp == 0) {
                if (my >= 4 && open == 2) score += SCORE_LIVE4;
                else if (my >= 4) score += SCORE_RUSH4;
                else if (my == 3 && open == 2) score += SCORE_LIVE3;
                else if (my == 3) score += SCORE_SLEEP3;
                else if (my == 2 && open == 2) score += SCORE_LIVE2;
            }
        }
        return score;
    }

    private int evaluate(PieceColor myColor) {
        int myScore = 0, oppScore = 0;
        PieceColor oppColor = myColor.opposite();

        for (int pos = 0; pos < 361; pos++) {
            int col = pos % 19, row = pos / 19;
            for (int[] dir : DIRECTIONS) {
                int endCol = col + dir[0] * 5, endRow = row + dir[1] * 5;
                if (endCol < 0 || endCol >= 19 || endRow < 0 || endRow >= 19) continue;

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
            default: return 5;
        }
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
    public String name() { return "G06-R"; }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
        currentHash = 0;
        transTable.clear();
        currentHash ^= zobristTable[Move.index('J', 'J')][1];
    }
}
