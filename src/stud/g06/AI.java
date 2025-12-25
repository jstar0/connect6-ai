package stud.g06;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

/**
 * G06 AI (tactical optimized V3)
 *
 * <p>Core: fast threat detection + DTSS (double-threat search) + iterative deepening alpha-beta
 */
public class AI extends core.player.AI {

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private static final int INF = 10000000;
    private static final long TIME_LIMIT = 8000;
    private static final int[] POW = {1, 10, 100, 1000, 10000, 100000, 1000000};
    private static final int[] ROAD_SCORE = {0, 9, 520, 2070, 7890, 10020, 1000000};
    private static final int[] ROAD_SCORE_DEF = {0, 3, 480, 2670, 3887, 4900, 1000000};

    private long startTime;
    private Map<Long, int[]> tt = new HashMap<>();
    private long[][] zobrist = new long[361][3];
    private long hash = 0;
    private boolean hashSynced = false;

    // Threat detection cache
    private int[] threatCache = new int[361];
    private long threatCacheHash = -1;

    private static final long DTSS_BUDGET_MS = 5500;
    private static final int DTSS_MAX_DEPTH = 27;
    private static final int DTSS_MAX_POINTS = 32;
    private static final int DTSS_MAX_MOVES = 80;

    private PieceColor dtssAttacker;
    private ArrayList<Move> dtssLine;
    private Move dtssBestMove;
    private long dtssDeadlineMs;
    private boolean dtssTimedOut;

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
        syncHashIfNeeded();
        startTime = System.currentTimeMillis();
        PieceColor me = board.whoseMove();
        PieceColor opp = me.opposite();

        // 1. Immediate win: complete a 4/5-road in one move.
        Move win = findImmediateWinMove(me);
        if (win != null) return commit(win);

        // 2) Must defend
        BoardPro bp = boardPro();
        if (bp != null && bp.countAllThreats(me) > 0) {
            Move block = findBestImmediateBlock(me, startTime + 800);
            if (block != null) return commit(block);
        } else {
            List<Integer> oppWin = getWinSpots(opp);
            if (!oppWin.isEmpty()) {
                int s1 = oppWin.get(0);
                int s2 = oppWin.size() > 1 ? oppWin.get(1) : getBest(s1, me);
                return commit(new Move(s1, s2));
            }
        }

        // 3) Fast DTSS search (iterative deepening within a fixed budget)
        long dtssDeadline = startTime + DTSS_BUDGET_MS;
        for (int depth = 3; depth <= DTSS_MAX_DEPTH; depth += 2) {
            if (System.currentTimeMillis() > dtssDeadline) break;
            Move dt = findDtssWinningMove(me, depth, dtssDeadline);
            if (dt != null) return commit(dt);
        }

        // 4) Iterative deepening search
        Move best = iterativeDeepening(me);

        // 5. Root-level safety: avoid moves that allow an opponent DTSS win sequence.
        long safetyDeadline = startTime + TIME_LIMIT - 200;
        Move safe = findSafeMoveAgainstDtss(best, me, opp, safetyDeadline);
        return commit(safe != null ? safe : best);
    }

    private Move commit(Move m) {
        board.makeMove(m);
        updateHash(m);
        return m;
    }

    private boolean hasWinInOne(PieceColor color) {
        BoardPro bp = boardPro();
        if (bp != null) {
            RoadSet[][] byCount = bp.getRoadTable().getRoadsByCount();
            RoadSet four = (color == PieceColor.BLACK) ? byCount[4][0] : byCount[0][4];
            RoadSet five = (color == PieceColor.BLACK) ? byCount[5][0] : byCount[0][5];
            return !four.isEmpty() || !five.isEmpty();
        }
        return !getWinSpotsByScan(color).isEmpty();
    }

    private Move findImmediateWinMove(PieceColor color) {
        BoardPro bp = boardPro();
        if (bp != null) {
            return findImmediateWinMoveFromRoadTable(color, bp.getRoadTable());
        }
        return findImmediateWinMoveByScan(color);
    }

    private Move findImmediateWinMoveFromRoadTable(PieceColor color, RoadTable roadTable) {
        RoadSet[][] byCount = roadTable.getRoadsByCount();
        RoadSet four = (color == PieceColor.BLACK) ? byCount[4][0] : byCount[0][4];
        RoadSet five = (color == PieceColor.BLACK) ? byCount[5][0] : byCount[0][5];

        for (Road road : four) {
            Move m = buildWinMoveFromRoad(road, color);
            if (m != null) return m;
        }
        for (Road road : five) {
            Move m = buildWinMoveFromRoad(road, color);
            if (m != null) return m;
        }
        return null;
    }

    private Move buildWinMoveFromRoad(Road road, PieceColor color) {
        int p1 = -1;
        int p2 = -1;
        for (int i = 0; i < 6; i++) {
            int pos = road.cellAt(i);
            if (board.get(pos) != PieceColor.EMPTY) continue;
            if (p1 < 0) p1 = pos;
            else {
                p2 = pos;
                break;
            }
        }
        if (p1 < 0) return null;
        if (p2 >= 0) return new Move(p1, p2);
        int filler = pickFillerStone(p1, color);
        return filler >= 0 ? new Move(p1, filler) : null;
    }

    private Move findImmediateWinMoveByScan(PieceColor color) {
        PieceColor opp = color.opposite();
        for (int r = 0; r < 19; r++) {
            for (int c = 0; c < 19; c++) {
                for (int[] d : DIRS) {
                    int myC = 0;
                    int oppC = 0;
                    int emptyCnt = 0;
                    int[] empties = new int[2];
                    boolean valid = true;
                    for (int i = 0; i < 6 && valid; i++) {
                        int nr = r + d[0] * i;
                        int nc = c + d[1] * i;
                        if (nr < 0 || nr >= 19 || nc < 0 || nc >= 19) {
                            valid = false;
                            break;
                        }
                        int pos = nr * 19 + nc;
                        PieceColor p = board.get(pos);
                        if (p == opp) {
                            oppC++;
                        } else if (p == color) {
                            myC++;
                        } else {
                            if (emptyCnt < 2) empties[emptyCnt] = pos;
                            emptyCnt++;
                        }
                    }
                    if (!valid || oppC != 0) continue;
                    if (myC == 4 && emptyCnt == 2) return new Move(empties[0], empties[1]);
                    if (myC == 5 && emptyCnt == 1) {
                        int filler = pickFillerStone(empties[0], color);
                        return filler >= 0 ? new Move(empties[0], filler) : null;
                    }
                }
            }
        }
        return null;
    }

    private int pickFillerStone(int exclude, PieceColor me) {
        int best = getBest(exclude, me);
        if (Move.validSquare(best) && best != exclude && board.get(best) == PieceColor.EMPTY) return best;
        return pickAnyEmptyExcept(exclude);
    }

    private int pickAnyEmptyExcept(int exclude) {
        for (int pos = 0; pos < 361; pos++) {
            if (pos == exclude) continue;
            if (board.get(pos) == PieceColor.EMPTY) return pos;
        }
        return -1;
    }

    // Collect empty cells from 4/5-roads.
    private List<Integer> getWinSpots(PieceColor color) {
        BoardPro bp = boardPro();
        if (bp != null) {
            return getWinSpotsFromRoadTable(color, bp.getRoadTable());
        }
        return getWinSpotsByScan(color);
    }

    private List<Integer> getWinSpotsFromRoadTable(PieceColor color, RoadTable roadTable) {
        Map<Integer, Integer> spots = new HashMap<>();
        RoadSet[][] byCount = roadTable.getRoadsByCount();
        RoadSet four = (color == PieceColor.BLACK) ? byCount[4][0] : byCount[0][4];
        RoadSet five = (color == PieceColor.BLACK) ? byCount[5][0] : byCount[0][5];
        addEmptySpotsFromRoadSet(spots, four);
        addEmptySpotsFromRoadSet(spots, five);
        List<Integer> result = new ArrayList<>(spots.keySet());
        result.sort((a, b) -> spots.get(b) - spots.get(a));
        return result;
    }

    private void addEmptySpotsFromRoadSet(Map<Integer, Integer> spots, RoadSet roads) {
        for (Road road : roads) {
            for (int i = 0; i < 6; i++) {
                int pos = road.cellAt(i);
                if (board.get(pos) == PieceColor.EMPTY) {
                    spots.merge(pos, 1, Integer::sum);
                }
            }
        }
    }

    private List<Integer> getWinSpotsByScan(PieceColor color) {
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

    // Double-threat search (optimized): candidate points only from 2/3-roads.
    private Move findDoubleThreat(PieceColor color, int depth) {
        if (depth <= 0) return null;

        // Collect potential points: empty cells from 2/3-roads.
        List<Integer> potentialSpots = findPotentialSpots(color);
        int n = Math.min(potentialSpots.size(), 15);

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Move m = new Move(potentialSpots.get(i), potentialSpots.get(j));
                board.makeMove(m);

                List<Integer> threats = getWinSpots(color);
                if (threats.size() >= 3) {
                    board.undo();
                    return m;
                }

                if (threats.size() >= 2 && depth > 1) {
                    // Continue search after defender responds.
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

    private Move findDtssWinningMove(PieceColor attacker, int depth, long deadlineMs) {
        BoardPro bp = boardPro();
        if (bp == null) return null;
        if (board.whoseMove() != attacker) return null;
        if (System.currentTimeMillis() > deadlineMs) return null;

        dtssAttacker = attacker;
        dtssLine = new ArrayList<>();
        dtssBestMove = null;
        dtssDeadlineMs = deadlineMs;
        dtssTimedOut = false;

        boolean ok = dtssSearch(depth);
        return ok ? dtssBestMove : null;
    }

    private boolean dtssSearch(int depth) {
        if (System.currentTimeMillis() > dtssDeadlineMs) {
            dtssTimedOut = true;
            return false;
        }

        BoardPro bp = boardPro();
        if (bp == null) return false;

        // If the game has already ended, the previous mover (whoseMove().opposite()) is the winner.
        if (board.gameOver()) {
            PieceColor winner = board.whoseMove().opposite();
            boolean attackerWon = winner == dtssAttacker;
            if (attackerWon && !dtssLine.isEmpty()) dtssBestMove = dtssLine.get(0);
            return attackerWon;
        }

        // If the side to move can win immediately, DTSS outcome is decided right here.
        PieceColor toMove = board.whoseMove();
        if (hasWinInOne(toMove)) {
            if (toMove != dtssAttacker) return false;
            if (dtssLine.isEmpty()) {
                dtssBestMove = findImmediateWinMove(dtssAttacker);
            } else {
                dtssBestMove = dtssLine.get(0);
            }
            return dtssBestMove != null;
        }

        if (board.whoseMove() == dtssAttacker) {
            if (depth <= 0) return false;

            // G02-style pruning: if the attacker is under immediate threat but has no threat on the defender,
            // the attacker must defend and can't continue the DTSS attack line.
            RoadSet[][] byCount = bp.getRoadTable().getRoadsByCount();
            RoadSet oppFour = (dtssAttacker == PieceColor.WHITE) ? byCount[4][0] : byCount[0][4];
            RoadSet oppFive = (dtssAttacker == PieceColor.WHITE) ? byCount[5][0] : byCount[0][5];
            boolean attackerThreatened = !oppFour.isEmpty() || !oppFive.isEmpty();

            RoadSet myFour = (dtssAttacker == PieceColor.BLACK) ? byCount[4][0] : byCount[0][4];
            RoadSet myFive = (dtssAttacker == PieceColor.BLACK) ? byCount[5][0] : byCount[0][5];
            boolean attackerHasThreats = !myFour.isEmpty() || !myFive.isEmpty();

            if (attackerThreatened && !attackerHasThreats) return false;

            // Attacker turn: try any move that creates at least a double threat.
            List<Move> threats = generateDoubleThreatMoves(dtssAttacker);
            for (Move m : threats) {
                board.makeMove(m);
                dtssLine.add(m);
                boolean ok = dtssSearch(depth - 1);
                dtssLine.remove(dtssLine.size() - 1);
                board.undo();
                if (ok) return true;
                if (System.currentTimeMillis() > dtssDeadlineMs) {
                    dtssTimedOut = true;
                    return false;
                }
            }
            return false;
        }

        // Defender turn: if threats are un-blockable, attacker wins.
        PieceColor defender = board.whoseMove();
        if (depth <= 0) return false;
        int threats = bp.countAllThreats(defender);
        if (threats >= 3) {
            if (!dtssLine.isEmpty()) dtssBestMove = dtssLine.get(0);
            return true;
        }

        // Enumerate all valid blocks; all must fail for defender for the attacker to have a forced win.
        List<Move> blocks = generateDoubleBlockMoves(defender);
        for (Move m : blocks) {
            board.makeMove(m);
            dtssLine.add(m);
            boolean ok = dtssSearch(depth - 1);
            dtssLine.remove(dtssLine.size() - 1);
            board.undo();
            if (!ok) return false;
            if (System.currentTimeMillis() > dtssDeadlineMs) {
                dtssTimedOut = true;
                return false;
            }
        }
        // No blocks -> attacker wins.
        if (!dtssLine.isEmpty()) dtssBestMove = dtssLine.get(0);
        return true;
    }

    private static final class ScoredMove {
        private final Move move;
        private final int score;

        private ScoredMove(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }

    private List<Move> generateDoubleThreatMoves(PieceColor attacker) {
        BoardPro bp = boardPro();
        if (bp == null) return List.of();

        RoadTable roadTable = bp.getRoadTable();
        List<Integer> points = collectDtssPotentialPoints(attacker, roadTable);
        int n = Math.min(points.size(), DTSS_MAX_POINTS);
        if (n < 2) return List.of();

        ArrayList<ScoredMove> scored = new ArrayList<>();
        PieceColor defender = attacker.opposite();

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (System.currentTimeMillis() > dtssDeadlineMs) {
                    dtssTimedOut = true;
                    break;
                }
                Move m = new Move(points.get(i), points.get(j));
                board.makeMove(m);
                // If the defender can win immediately, this is not a viable DTSS threat move.
                if (hasWinInOne(defender)) {
                    board.undo();
                    continue;
                }
                int threatLevel = bp.countAllThreats(defender);
                if (threatLevel >= 2) {
                    int score = threatLevel * 1_000_000 + evalFromRoadTable(attacker, roadTable);
                    scored.add(new ScoredMove(m, score));
                }
                board.undo();
            }
            if (System.currentTimeMillis() > dtssDeadlineMs) {
                dtssTimedOut = true;
                break;
            }
        }

        scored.sort((a, b) -> b.score - a.score);
        ArrayList<Move> result = new ArrayList<>();
        int limit = Math.min(scored.size(), DTSS_MAX_MOVES);
        for (int i = 0; i < limit; i++) {
            result.add(scored.get(i).move);
        }
        return result;
    }

    private List<Integer> collectDtssPotentialPoints(PieceColor attacker, RoadTable roadTable) {
        RoadSet[][] byCount = roadTable.getRoadsByCount();
        RoadSet two = (attacker == PieceColor.BLACK) ? byCount[2][0] : byCount[0][2];
        RoadSet three = (attacker == PieceColor.BLACK) ? byCount[3][0] : byCount[0][3];

        HashMap<Integer, Integer> weights = new HashMap<>();

        // Score by how often a point appears on 3-roads/2-roads (order-independent, unlike HashSet iteration).
        for (Road road : three) {
            for (int i = 0; i < 6; i++) {
                int pos = road.cellAt(i);
                if (board.get(pos) != PieceColor.EMPTY) continue;
                weights.merge(pos, 10, Integer::sum);
            }
        }
        for (Road road : two) {
            for (int i = 0; i < 6; i++) {
                int pos = road.cellAt(i);
                if (board.get(pos) != PieceColor.EMPTY) continue;
                weights.merge(pos, 1, Integer::sum);
            }
        }

        ArrayList<int[]> scored = new ArrayList<>(weights.size());
        for (Map.Entry<Integer, Integer> e : weights.entrySet()) {
            int pos = e.getKey();
            int w = e.getValue();
            int r = pos / 19;
            int c = pos % 19;
            int center = 18 - Math.abs(r - 9) - Math.abs(c - 9);
            scored.add(new int[]{pos, w, center});
        }

        if (scored.isEmpty()) {
            ArrayList<Integer> fallback = new ArrayList<>(getCandidates());
            fallback.sort((a, b) -> {
                int ar = a / 19, ac = a % 19;
                int br = b / 19, bc = b % 19;
                int ca = 18 - Math.abs(ar - 9) - Math.abs(ac - 9);
                int cb = 18 - Math.abs(br - 9) - Math.abs(bc - 9);
                if (cb != ca) return cb - ca;
                return a - b;
            });
            return fallback;
        }

        scored.sort((a, b) -> {
            if (b[1] != a[1]) return b[1] - a[1];
            if (b[2] != a[2]) return b[2] - a[2];
            return a[0] - b[0];
        });
        ArrayList<Integer> points = new ArrayList<>(scored.size());
        for (int[] s : scored) points.add(s[0]);
        return points;
    }

    private List<Move> generateDoubleBlockMoves(PieceColor defender) {
        BoardPro bp = boardPro();
        if (bp == null) return List.of();
        RoadTable roadTable = bp.getRoadTable();

        RoadSet[][] byCount = roadTable.getRoadsByCount();
        RoadSet oppFour = (defender == PieceColor.WHITE) ? byCount[4][0] : byCount[0][4];
        RoadSet oppFive = (defender == PieceColor.WHITE) ? byCount[5][0] : byCount[0][5];

        boolean[] visited = new boolean[361];
        ArrayList<Integer> blocks = new ArrayList<>();
        for (Road road : oppFive) collectEmptyFromRoad(road, blocks, visited);
        for (Road road : oppFour) collectEmptyFromRoad(road, blocks, visited);

        ArrayList<ScoredMove> scored = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                if (System.currentTimeMillis() > dtssDeadlineMs) {
                    dtssTimedOut = true;
                    break;
                }
                Move m = new Move(blocks.get(i), blocks.get(j));
                board.makeMove(m);
                if (bp.countAllThreats(defender) == 0) {
                    int score = evalFromRoadTable(defender, roadTable);
                    scored.add(new ScoredMove(m, score));
                }
                board.undo();
            }
            if (System.currentTimeMillis() > dtssDeadlineMs) {
                dtssTimedOut = true;
                break;
            }
        }

        scored.sort((a, b) -> b.score - a.score);
        ArrayList<Move> result = new ArrayList<>();
        for (ScoredMove m : scored) result.add(m.move);
        return result;
    }

    private void collectEmptyFromRoad(Road road, ArrayList<Integer> out, boolean[] visited) {
        for (int i = 0; i < 6; i++) {
            int pos = road.cellAt(i);
            if (board.get(pos) != PieceColor.EMPTY) continue;
            if (visited[pos]) continue;
            visited[pos] = true;
            out.add(pos);
        }
    }

    private List<Move> generateImmediateBlocks(PieceColor defender, int threatLevel, long deadlineMs) {
        BoardPro bp = boardPro();
        if (bp == null) return List.of();
        RoadTable roadTable = bp.getRoadTable();

        RoadSet[][] byCount = roadTable.getRoadsByCount();
        RoadSet oppFour = (defender == PieceColor.WHITE) ? byCount[4][0] : byCount[0][4];
        RoadSet oppFive = (defender == PieceColor.WHITE) ? byCount[5][0] : byCount[0][5];

        boolean[] visited = new boolean[361];
        ArrayList<Integer> blocks = new ArrayList<>();
        for (Road road : oppFive) collectEmptyFromRoad(road, blocks, visited);
        for (Road road : oppFour) collectEmptyFromRoad(road, blocks, visited);

        if (blocks.isEmpty()) return List.of();

        ArrayList<ScoredMove> scored = new ArrayList<>();
        if (blocks.size() == 1) {
            int p1 = blocks.get(0);
            int added = 0;
            for (int p2 : getCandidates()) {
                if (System.currentTimeMillis() > deadlineMs) break;
                if (p2 == p1) continue;
                Move m = new Move(p1, p2);
                board.makeMove(m);
                boolean solved = bp.countAllThreats(defender) == 0;
                int counter = bp.countAllThreats(defender.opposite());
                int score = solved ? counter * 1_000_000 + evalFromRoadTable(defender, roadTable) : -INF;
                board.undo();
                if (solved) scored.add(new ScoredMove(m, score));
                if (++added >= 8) break;
            }
        } else if (threatLevel == 1) {
            ArrayList<Integer> singleBlocks = new ArrayList<>();
            for (int p : blocks) {
                if (System.currentTimeMillis() > deadlineMs) break;
                if (board.get(p) != PieceColor.EMPTY) continue;
                roadTable.applyStone(p, defender);
                boolean solved = oppFour.isEmpty() && oppFive.isEmpty();
                roadTable.revertStone(p, defender);
                if (solved) singleBlocks.add(p);
            }

            int blockLimit = Math.min(singleBlocks.size(), 6);
            for (int i = 0; i < blockLimit; i++) {
                int p1 = singleBlocks.get(i);
                int added = 0;
                for (int p2 : getCandidates()) {
                    if (System.currentTimeMillis() > deadlineMs) break;
                    if (p2 == p1) continue;
                    Move m = new Move(p1, p2);
                    board.makeMove(m);
                    boolean solved = bp.countAllThreats(defender) == 0;
                    int counter = bp.countAllThreats(defender.opposite());
                    int score = solved ? counter * 1_000_000 + evalFromRoadTable(defender, roadTable) : -INF;
                    board.undo();
                    if (solved) scored.add(new ScoredMove(m, score));
                    if (++added >= 6) break;
                }
            }
        } else {
            for (int i = 0; i < blocks.size(); i++) {
                for (int j = i + 1; j < blocks.size(); j++) {
                    if (System.currentTimeMillis() > deadlineMs) break;
                    Move m = new Move(blocks.get(i), blocks.get(j));
                    board.makeMove(m);
                    boolean solved = bp.countAllThreats(defender) == 0;
                    int counter = bp.countAllThreats(defender.opposite());
                    int score = solved ? counter * 1_000_000 + evalFromRoadTable(defender, roadTable) : -INF;
                    board.undo();
                    if (solved) scored.add(new ScoredMove(m, score));
                }
                if (System.currentTimeMillis() > deadlineMs) break;
            }
        }

        scored.sort((a, b) -> b.score - a.score);
        ArrayList<Move> result = new ArrayList<>();
        int limit = Math.min(scored.size(), 20);
        for (int i = 0; i < limit; i++) result.add(scored.get(i).move);
        return result;
    }

    private Move findBestImmediateBlock(PieceColor defender, long deadlineMs) {
        BoardPro bp = boardPro();
        if (bp == null) return null;

        RoadTable roadTable = bp.getRoadTable();
        RoadSet[][] byCount = roadTable.getRoadsByCount();
        RoadSet oppFour = (defender == PieceColor.WHITE) ? byCount[4][0] : byCount[0][4];
        RoadSet oppFive = (defender == PieceColor.WHITE) ? byCount[5][0] : byCount[0][5];

        boolean[] visited = new boolean[361];
        ArrayList<Integer> blocks = new ArrayList<>();
        for (Road road : oppFive) collectEmptyFromRoad(road, blocks, visited);
        for (Road road : oppFour) collectEmptyFromRoad(road, blocks, visited);

        if (blocks.isEmpty()) return null;
        if (blocks.size() == 1) {
            int p1 = blocks.get(0);
            Move bestMove = null;
            int bestScore = -INF;
            int tried = 0;
            for (int p2 : findPotentialSpots(defender)) {
                if (p2 == p1) continue;
                Move m = new Move(p1, p2);
                board.makeMove(m);
                boolean solved = bp.countAllThreats(defender) == 0;
                int counter = bp.countAllThreats(defender.opposite());
                int score = solved ? counter * 1_000_000 + evalFromRoadTable(defender, roadTable) : -INF;
                board.undo();
                if (solved && score > bestScore) {
                    bestScore = score;
                    bestMove = m;
                }
                if (++tried >= 10) break;
            }
            return bestMove != null ? bestMove : new Move(p1, getBest(p1, defender));
        }

        int threatLevel = bp.countAllThreats(defender);
        if (threatLevel == 1) {
            Move best = findBestSingleThreatDefense(defender, blocks, roadTable, oppFour, oppFive, deadlineMs);
            if (best != null) return best;
        }

        Move bestMove = null;
        int bestScore = -INF;

        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                if (System.currentTimeMillis() > deadlineMs) break;
                Move m = new Move(blocks.get(i), blocks.get(j));
                board.makeMove(m);
                boolean solved = bp.countAllThreats(defender) == 0;
                int counter = bp.countAllThreats(defender.opposite());
                int score = solved ? counter * 1_000_000 + evalFromRoadTable(defender, roadTable) : -INF;
                board.undo();
                if (solved && score > bestScore) {
                    bestScore = score;
                    bestMove = m;
                }
            }
            if (System.currentTimeMillis() > deadlineMs) break;
        }

        return bestMove;
    }

    private Move findBestSingleThreatDefense(
            PieceColor defender,
            ArrayList<Integer> blocks,
            RoadTable roadTable,
            RoadSet oppFour,
            RoadSet oppFive,
            long deadlineMs
    ) {
        BoardPro bp = boardPro();
        if (bp == null) return null;

        ArrayList<Integer> singleBlocks = new ArrayList<>();
        for (int p : blocks) {
            if (System.currentTimeMillis() > deadlineMs) break;
            if (board.get(p) != PieceColor.EMPTY) continue;
            roadTable.applyStone(p, defender);
            boolean solved = oppFour.isEmpty() && oppFive.isEmpty();
            roadTable.revertStone(p, defender);
            if (solved) singleBlocks.add(p);
        }
        if (singleBlocks.isEmpty()) return null;

        List<Integer> seconds = findPotentialSpots(defender);
        int secondsLimit = Math.min(seconds.size(), 18);

        Move bestMove = null;
        int bestScore = -INF;
        for (int p1 : singleBlocks) {
            int tried = 0;
            for (int i = 0; i < secondsLimit; i++) {
                if (System.currentTimeMillis() > deadlineMs) break;
                int p2 = seconds.get(i);
                if (p2 == p1) continue;
                if (board.get(p2) != PieceColor.EMPTY) continue;
                Move m = new Move(p1, p2);
                board.makeMove(m);
                boolean stillSolved = bp.countAllThreats(defender) == 0;
                int counter = bp.countAllThreats(defender.opposite());
                int score = stillSolved ? counter * 1_000_000 + evalFromRoadTable(defender, roadTable) : -INF;
                board.undo();
                if (stillSolved && score > bestScore) {
                    bestScore = score;
                    bestMove = m;
                }
                if (++tried >= 10) break;
            }
        }
        return bestMove;
    }

    private Move findSafeMoveAgainstDtss(Move preferred, PieceColor me, PieceColor opp, long deadlineMs) {
        if (System.currentTimeMillis() > deadlineMs) return null;
        if (preferred == null) return null;
        BoardPro bp = boardPro();
        if (bp == null) return null;

        // Start by checking the preferred move, then try a few strong alternatives.
        ArrayList<Move> candidates = new ArrayList<>(genMovesRoot(me));
        candidates.sort((a, b) -> a.equals(preferred) ? -1 : b.equals(preferred) ? 1 : 0);

        int checked = 0;
        for (Move m : candidates) {
            if (System.currentTimeMillis() > deadlineMs) break;
            if (checked++ > 12) break;

            board.makeMove(m);
            long perCheckDeadline = Math.min(deadlineMs, System.currentTimeMillis() + 250);
            Move oppWin = findDtssWinningMove(opp, 7, perCheckDeadline);
            boolean timedOut = dtssTimedOut;
            board.undo();

            // If we can't finish the opponent check in time, be conservative and treat as unsafe.
            if (timedOut) continue;
            if (oppWin == null) return m;
        }
        return null;
    }

    // Collect potential points: empty cells from 2/3-roads.
    private List<Integer> findPotentialSpots(PieceColor color) {
        BoardPro bp = boardPro();
        if (bp != null) {
            return findPotentialSpotsFromRoadTable(color, bp.getRoadTable());
        }
        return findPotentialSpotsByScan(color);
    }

    private List<Integer> findPotentialSpotsFromRoadTable(PieceColor color, RoadTable roadTable) {
        Map<Integer, Integer> spots = new HashMap<>();
        RoadSet[][] byCount = roadTable.getRoadsByCount();
        RoadSet two = (color == PieceColor.BLACK) ? byCount[2][0] : byCount[0][2];
        RoadSet three = (color == PieceColor.BLACK) ? byCount[3][0] : byCount[0][3];

        addPotentialEmptySpotsFromRoadSet(spots, two, 1);
        addPotentialEmptySpotsFromRoadSet(spots, three, 10);

        if (spots.isEmpty()) {
            for (int pos : getCandidates()) {
                spots.put(pos, 1);
            }
        }

        List<Integer> result = new ArrayList<>(spots.keySet());
        result.sort((a, b) -> spots.get(b) - spots.get(a));
        return result;
    }

    private void addPotentialEmptySpotsFromRoadSet(Map<Integer, Integer> spots, RoadSet roads, int weight) {
        for (Road road : roads) {
            for (int i = 0; i < 6; i++) {
                int pos = road.cellAt(i);
                if (board.get(pos) == PieceColor.EMPTY) {
                    spots.merge(pos, weight, Integer::sum);
                }
            }
        }
    }

    private List<Integer> findPotentialSpotsByScan(PieceColor color) {
        Map<Integer, Integer> spots = new HashMap<>();
        PieceColor opp = color.opposite();

        for (int r = 0; r < 19; r++) {
            for (int c = 0; c < 19; c++) {
                for (int[] d : DIRS) {
                    int cnt = 0, emptyCnt = 0;
                    int[] empties = new int[4];
                    boolean valid = true;
                    for (int i = 0; i < 6 && valid; i++) {
                        int nr = r + d[0] * i, nc = c + d[1] * i;
                        if (nr < 0 || nr >= 19 || nc < 0 || nc >= 19) { valid = false; break; }
                        PieceColor p = board.get(nr * 19 + nc);
                        if (p == opp) valid = false;
                        else if (p == color) cnt++;
                        else if (emptyCnt < 4) empties[emptyCnt++] = nr * 19 + nc;
                        else emptyCnt++;
                    }
                    // 2/3-road segments
                    if (valid && (cnt == 2 || cnt == 3) && emptyCnt <= 4) {
                        int weight = (cnt == 3) ? 10 : 1;
                        for (int i = 0; i < emptyCnt; i++) {
                            spots.merge(empties[i], weight, Integer::sum);
                        }
                    }
                }
            }
        }

        List<Integer> result = new ArrayList<>(spots.keySet());
        result.sort((a, b) -> spots.get(b) - spots.get(a));
        return result;
    }

    // Find best block move.
    private Move findBestBlock(PieceColor opp, Move oppDt, int depth) {
        PieceColor me = opp.opposite();
        int p1 = oppDt.index1();
        int p2 = oppDt.index2();

        List<Integer> blockPoints = new ArrayList<>();
        blockPoints.add(p1);
        blockPoints.add(p2);

        // Add high-value points.
        for (int pos : getCandidates()) {
            if (evalSpotFor(pos, opp) > 500) {
                blockPoints.add(pos);
            }
        }

        int best1 = p1, best2 = p2;
        int bestScore = -INF;
        int m = Math.min(blockPoints.size(), 10);

        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                int a = blockPoints.get(i);
                int b = blockPoints.get(j);
                Move block = new Move(a, b);
                board.makeMove(block);

                Move stillWin = findDoubleThreat(opp, depth);
                int score = evalSpot(a, me) + evalSpot(b, me);
                if (stillWin == null) score += 100000;

                board.undo();

                if (score > bestScore) {
                    bestScore = score;
                    best1 = a;
                    best2 = b;
                }
            }
        }

        if (bestScore > 0) {
            return new Move(best1, best2);
        }
        return null;
    }

    // Defend against opponent double threats.
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
                    score += POW[myC + 1]; // attack score
                }
            }
        }
        score += 18 - Math.abs(r - 9) - Math.abs(c - 9);
        return score;
    }

    // Iterative deepening.
    private Move iterativeDeepening(PieceColor me) {
        List<Move> moves = genMovesRoot(me);
        if (moves.isEmpty()) return new Move(180, 181);

        Move best = moves.get(0);

        for (int depth = 2; depth <= 10; depth += 2) {
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

            // Resort
            final Move fb = best;
            moves.sort((a, b) -> a.equals(fb) ? -1 : b.equals(fb) ? 1 : 0);
        }
        return best;
    }

    private List<Move> genMovesRoot(PieceColor me) {
        BoardPro bp = boardPro();
        if (bp == null) return genMoves(me);

        RoadTable roadTable = bp.getRoadTable();
        ArrayList<int[]> points = new ArrayList<>();
        for (int pos : getCandidates()) {
            roadTable.applyStone(pos, me);
            int score = evalFromRoadTable(me, roadTable);
            roadTable.revertStone(pos, me);
            points.add(new int[]{pos, score});
        }
        points.sort((a, b) -> b[1] - a[1]);

        int top = Math.min(points.size(), 24);
        if (top < 2) return genMoves(me);

        ArrayList<ScoredMove> scored = new ArrayList<>();
        for (int i = 0; i < top; i++) {
            for (int j = i + 1; j < top; j++) {
                int a = points.get(i)[0];
                int b = points.get(j)[0];
                roadTable.applyStone(a, me);
                roadTable.applyStone(b, me);
                int score = evalFromRoadTable(me, roadTable);
                roadTable.revertStone(b, me);
                roadTable.revertStone(a, me);
                scored.add(new ScoredMove(new Move(a, b), score));
            }
        }

        scored.sort((a, b) -> b.score - a.score);

        // Light tactical bias: prefer moves that immediately create higher threat pressure.
        int limit = Math.min(scored.size(), 35);
        ArrayList<ScoredMove> tuned = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Move m = scored.get(i).move;
            int base = scored.get(i).score;
            board.makeMove(m);
            int threatLevel = bp.countAllThreats(me.opposite());
            board.undo();
            tuned.add(new ScoredMove(m, base + threatLevel * 1_000_000));
        }

        tuned.sort((a, b) -> b.score - a.score);
        ArrayList<Move> moves = new ArrayList<>(tuned.size());
        for (ScoredMove m : tuned) moves.add(m.move);
        return moves.isEmpty() ? genMoves(me) : moves;
    }

    private int negamax(int depth, int alpha, int beta) {
        PieceColor me = board.whoseMove();
        PieceColor opp = me.opposite();

        // Terminal: previous move already ended the game.
        if (board.gameOver()) return -INF + (20 - depth);

        // Win in one for the side to move.
        if (hasWinInOne(me)) return INF - (20 - depth);

        BoardPro bp = boardPro();
        int threatLevel = 0;
        if (bp != null) {
            threatLevel = bp.countAllThreats(me);
            if (threatLevel >= 3) return -INF + (20 - depth);
        }

        if (depth <= 0) return eval(me);

        // Transposition table.
        int[] cached = tt.get(hash);
        if (cached != null && cached[0] >= depth) {
            if (cached[2] == 0) return cached[1];
            if (cached[2] == 1 && cached[1] >= beta) return cached[1];
            if (cached[2] == -1 && cached[1] <= alpha) return cached[1];
        }

        // Must defend against immediate 4/5-road threats.
        if (bp != null) {
            if (threatLevel > 0) {
                List<Move> blocks = generateImmediateBlocks(me, threatLevel, System.currentTimeMillis() + 25);
                if (blocks.isEmpty()) return -INF + (20 - depth);

                int bestScore = -INF;
                int origAlpha = alpha;
                for (Move m : blocks) {
                    makeMove(m);
                    int score = -negamax(depth - 1, -beta, -alpha);
                    undoMove(m);
                    bestScore = Math.max(bestScore, score);
                    alpha = Math.max(alpha, score);
                    if (alpha >= beta) break;
                }
                int flag = (bestScore <= origAlpha) ? -1 : (bestScore >= beta) ? 1 : 0;
                tt.put(hash, new int[]{depth, bestScore, flag});
                return bestScore;
            }
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
        BoardPro bp = boardPro();
        if (bp != null) {
            return genMovesFromRoadTable(me, 16, 35);
        }
        return genMovesHeuristic(me);
    }

    private List<Move> genMovesFromRoadTable(PieceColor me, int topPoints, int moveLimit) {
        BoardPro bp = boardPro();
        if (bp == null) return List.of();
        RoadTable roadTable = bp.getRoadTable();

        ArrayList<int[]> points = new ArrayList<>();
        for (int pos : getCandidates()) {
            roadTable.applyStone(pos, me);
            int score = evalFromRoadTable(me, roadTable);
            roadTable.revertStone(pos, me);
            points.add(new int[]{pos, score});
        }
        points.sort((a, b) -> b[1] - a[1]);

        int top = Math.min(points.size(), topPoints);
        if (top < 2) return List.of();

        ArrayList<ScoredMove> scoredMoves = new ArrayList<>();
        for (int i = 0; i < top; i++) {
            for (int j = i + 1; j < top; j++) {
                int a = points.get(i)[0];
                int b = points.get(j)[0];
                roadTable.applyStone(a, me);
                roadTable.applyStone(b, me);
                int score = evalFromRoadTable(me, roadTable);
                roadTable.revertStone(b, me);
                roadTable.revertStone(a, me);
                scoredMoves.add(new ScoredMove(new Move(a, b), score));
            }
        }
        scoredMoves.sort((a, b) -> b.score - a.score);

        ArrayList<Move> moves = new ArrayList<>();
        int limit = Math.min(scoredMoves.size(), moveLimit);
        for (int i = 0; i < limit; i++) moves.add(scoredMoves.get(i).move);
        return moves;
    }

    private List<Move> genMovesHeuristic(PieceColor me) {
        PieceColor opp = me.opposite();

        // Prioritize threat points.
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

    private List<Integer> getCandidates() {
        BoardPro bp = boardPro();
        if (bp != null) {
            int[] battle = bp.getBattle();
            ArrayList<Integer> cands = new ArrayList<>();
            for (int pos = 0; pos < 361; pos++) {
                if (battle[pos] > 0 && board.get(pos) == PieceColor.EMPTY) cands.add(pos);
            }
            if (cands.isEmpty()) {
                if (board.get(180) == PieceColor.EMPTY) {
                    cands.add(180);
                } else {
                    for (int pos = 0; pos < 361; pos++) {
                        if (board.get(pos) == PieceColor.EMPTY) {
                            cands.add(pos);
                            break;
                        }
                    }
                }
            }
            return cands;
        }

        boolean[] visited = new boolean[361];
        ArrayList<Integer> cands = new ArrayList<>();
        for (int pos = 0; pos < 361; pos++) {
            if (board.get(pos) != PieceColor.EMPTY) {
                int r = pos / 19;
                int c = pos % 19;
                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        int nr = r + dr;
                        int nc = c + dc;
                        if (nr < 0 || nr >= 19 || nc < 0 || nc >= 19) continue;
                        int np = nr * 19 + nc;
                        if (board.get(np) != PieceColor.EMPTY) continue;
                        if (visited[np]) continue;
                        visited[np] = true;
                        cands.add(np);
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
        BoardPro bp = boardPro();
        if (bp != null) {
            return evalFromRoadTable(me, bp.getRoadTable());
        }

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

    private int evalFromRoadTable(PieceColor me, RoadTable roadTable) {
        RoadSet[][] byCount = roadTable.getRoadsByCount();

        long myScore = 0;
        long oppScore = 0;
        if (me == PieceColor.BLACK) {
            for (int i = 1; i <= 5; i++) {
                myScore += (long) byCount[i][0].size() * ROAD_SCORE[i];
                oppScore += (long) byCount[0][i].size() * ROAD_SCORE_DEF[i];
            }
        } else {
            for (int i = 1; i <= 5; i++) {
                myScore += (long) byCount[0][i].size() * ROAD_SCORE[i];
                oppScore += (long) byCount[i][0].size() * ROAD_SCORE_DEF[i];
            }
        }
        long score = myScore - oppScore;
        if (score > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (score < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) score;
    }


    private int getBest(int exclude, PieceColor me) {
        int best = -1, bestScore = -1;
        for (int pos : getCandidates()) {
            if (pos != exclude) {
                int s = evalSpot(pos, me);
                if (s > bestScore) { bestScore = s; best = pos; }
            }
        }
        if (best >= 0) return best;
        int fallback = pickAnyEmptyExcept(exclude);
        return fallback >= 0 ? fallback : (exclude + 1) % 361;
    }

    private int getBestFast(int exclude) {
        for (int pos : getCandidates()) {
            if (pos != exclude) return pos;
        }
        int fallback = pickAnyEmptyExcept(exclude);
        return fallback >= 0 ? fallback : (exclude + 1) % 361;
    }

    private void makeMove(Move m) {
        board.makeMove(m);
        updateHash(m);
    }

    private void undoMove(Move m) {
        board.undo();
        PieceColor mover = board.whoseMove();
        int idx = (mover == PieceColor.BLACK) ? 1 : 2;
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

    private void syncHashIfNeeded() {
        if (hashSynced) return;
        hash = computeHashFromBoard();
        hashSynced = true;
    }

    private long computeHashFromBoard() {
        long h = 0;
        for (int pos = 0; pos < 361; pos++) {
            PieceColor c = board.get(pos);
            if (c == PieceColor.EMPTY) continue;
            int idx = (c == PieceColor.BLACK) ? 1 : 2;
            h ^= zobrist[pos][0] ^ zobrist[pos][idx];
        }
        return h;
    }

    private BoardPro boardPro() {
        return (board instanceof BoardPro) ? (BoardPro) board : null;
    }

    @Override
    public String name() { return "G06"; }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new BoardPro();
        hash = 0;
        hashSynced = false;
        tt.clear();
    }
}
