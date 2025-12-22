package stud.g07;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

public class AI extends core.player.AI {
    private static final int TIME_LIMIT = 2600;
    private static final double EXPLORATION = 1.0; // 降低探索，增加确定性
    private final Random random = new Random();

    @Override
    public Move findNextMove(Move opponentMove) {
        if (opponentMove != null) this.board.makeMove(opponentMove);

        // 1. 斩杀检测 (Level 0)
        Move killMove = findKillMove(this.board, this._myColor);
        if (killMove != null) {
            this.board.makeMove(killMove);
            return killMove;
        }

        // 2. 深度防御检测 (关键：找出对手所有必胜点)
        List<Integer> mustBlock = getMustBlockSpots(this.board, this._myColor.opposite());
        if (!mustBlock.isEmpty()) {
            // 如果对手威胁点超过 2 个，说明已经无法防住，选前两个必堵的点
            int s1 = mustBlock.get(0);
            int s2 = mustBlock.size() > 1 ? mustBlock.get(1) : findBestHeuristicSpot(this.board, s1);
            Move m = new Move(s1, s2);
            this.board.makeMove(m);
            return m;
        }

        // 3. 进入 MCTS
        Move mctsMove = mctsSearch();
        this.board.makeMove(mctsMove);
        return mctsMove;
    }

    /**
     * 普适性威胁分析：找出对手所有“下一步能成6”的空位
     */
    private List<Integer> getMustBlockSpots(Board b, PieceColor oppColor) {
        Set<Integer> criticalSpots = new LinkedHashSet<>();
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

        for (int r = 0; r < 19; r++) {
            for (int c = 0; c < 19; c++) {
                for (int[] d : directions) {
                    List<Integer> empties = getWindowEmpties(b, r, c, d[0], d[1], oppColor);
                    // 如果该窗口只需补 1-2 子即可成 6
                    if (empties != null && empties.size() <= 2) {
                        criticalSpots.addAll(empties);
                    }
                }
            }
        }
        return new ArrayList<>(criticalSpots);
    }

    private List<Integer> getWindowEmpties(Board b, int r, int c, int dr, int dc, PieceColor color) {
        List<Integer> empties = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < 6; i++) {
            int nr = r + dr * i, nc = c + dc * i;
            if (nr < 0 || nr >= 19 || nc < 0 || nc >= 19) return null;
            PieceColor p = b.get(Move.index((char)(nc + 'A'), (char)(nr + 'A')));
            if (p == color.opposite()) return null; // 被截断
            if (p == color) count++;
            else empties.add(nr * 19 + nc);
        }
        return (count >= 4) ? empties : null; // 4连或5连
    }

    private Move findKillMove(Board b, PieceColor color) {
        List<Integer> winSpots = getMustBlockSpots(b, color);
        if (winSpots.size() >= 2) return new Move(winSpots.get(0), winSpots.get(1));
        if (winSpots.size() == 1) return new Move(winSpots.get(0), findBestHeuristicSpot(b, winSpots.get(0)));
        return null;
    }

    private int findBestHeuristicSpot(Board b, int exclude) {
        List<Integer> spots = getCandidateIndices(b);
        for (int s : spots) if (s != exclude) return s;
        return (exclude + 1) % 361;
    }

    private Move mctsSearch() {
        MCTSNode root = new MCTSNode(new Board(this.board), null, null);
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < TIME_LIMIT) {
            MCTSNode leaf = select(root);
            PieceColor winner = simulate(leaf.board);
            backpropagate(leaf, winner);
        }
        return root.getBestChildMove();
    }

    private MCTSNode select(MCTSNode node) {
        MCTSNode curr = node;
        while (!curr.board.gameOver() && curr.isFullyExpanded()) {
            curr = curr.getBestUCBChild();
            if (curr == null) break;
        }
        return (curr.board.gameOver() || !curr.canExpand()) ? curr : curr.expand();
    }

    private PieceColor simulate(Board b) {
        Board simB = new Board(b);
        int depth = 0;
        while (!simB.gameOver() && depth < 20) {
            Move m = findKillMove(simB, simB.whoseMove()); // 尝试斩杀
            if (m == null) m = findKillMove(simB, simB.whoseMove().opposite()); // 尝试防御
            if (m == null) {
                List<Integer> spots = getCandidateIndices(simB);
                m = new Move(spots.get(0), spots.get(Math.min(spots.size()-1, 1)));
            }
            simB.makeMove(m);
            depth++;
        }
        return simB.gameOver() ? simB.whoseMove().opposite() : PieceColor.EMPTY;
    }

    private void backpropagate(MCTSNode node, PieceColor winner) {
        MCTSNode temp = node;
        while (temp != null) {
            temp.visits++;
            if (winner != PieceColor.EMPTY && temp.parent != null) {
                if (temp.parent.board.whoseMove() == winner) temp.wins += 1.0;
                else temp.wins -= 0.5; // 加大失败惩罚，强化生存意识
            }
            temp = temp.parent;
        }
    }

    private List<Integer> getCandidateIndices(Board b) {
        Map<Integer, Integer> scores = new HashMap<>();
        for (int i = 0; i < 361; i++) {
            if (b.get(i) == PieceColor.EMPTY) {
                scores.put(i, evaluateSpot(b, i));
            }
        }
        List<Integer> list = new ArrayList<>(scores.keySet());
        list.sort((o1, o2) -> scores.get(o2) - scores.get(o1));
        return list;
    }

    private int evaluateSpot(Board b, int index) {
        int r = index / 19, c = index % 19;
        int score = 0;
        int[][] dirs = {{0,1}, {1,0}, {1,1}, {1,-1}};
        for (int[] d : dirs) {
            for (int offset = -5; offset <= 0; offset++) {
                int sr = r + d[0]*offset, sc = c + d[1]*offset;
                int myC = 0, oppC = 0;
                boolean valid = true;
                for (int i = 0; i < 6; i++) {
                    int nr = sr + d[0]*i, nc = sc + d[1]*i;
                    if (nr < 0 || nr >= 19 || nc < 0 || nc >= 19) { valid = false; break; }
                    PieceColor p = b.get(Move.index((char)(nc+'A'), (char)(nr+'A')));
                    if (p == _myColor) myC++;
                    else if (p == _myColor.opposite()) oppC++;
                }
                if (valid) {
                    // 普适性权重：防御（对方的长连）权重略高于进攻
                    if (oppC > 0 && myC == 0) score += Math.pow(10, oppC);
                    if (myC > 0 && oppC == 0) score += Math.pow(9, myC);
                }
            }
        }
        return score;
    }

    private class MCTSNode {
        Board board;
        MCTSNode parent;
        Move moveFromParent;
        List<MCTSNode> children = new ArrayList<>();
        double wins = 0;
        int visits = 0;
        List<Move> unexpandedMoves;

        MCTSNode(Board b, MCTSNode p, Move m) {
            this.board = b; this.parent = p; this.moveFromParent = m;
            this.unexpandedMoves = generateMoves(b);
        }

        private List<Move> generateMoves(Board b) {
            // 这里是防御增强的核心：如果对手有威胁，候选移动必须包含防御点
            List<Integer> must = getMustBlockSpots(b, b.whoseMove().opposite());
            List<Integer> candidates = getCandidateIndices(b);
            List<Move> moves = new ArrayList<>();

            if (!must.isEmpty()) {
                // 如果必须防守，只组合防御点
                if (must.size() >= 2) {
                    moves.add(new Move(must.get(0), must.get(1)));
                } else {
                    int s1 = must.get(0);
                    for (int i = 0; i < Math.min(candidates.size(), 5); i++) {
                        if (candidates.get(i) != s1) moves.add(new Move(s1, candidates.get(i)));
                    }
                }
            } else {
                // 正常搜索
                int n = Math.min(candidates.size(), 8);
                for (int i = 0; i < n; i++) {
                    for (int j = i + 1; j < n; j++) {
                        moves.add(new Move(candidates.get(i), candidates.get(j)));
                    }
                }
            }
            return moves;
        }

        boolean isFullyExpanded() { return unexpandedMoves != null && unexpandedMoves.isEmpty(); }
        boolean canExpand() { return unexpandedMoves != null && !unexpandedMoves.isEmpty(); }
        MCTSNode expand() {
            Move m = unexpandedMoves.remove(0);
            Board next = new Board(this.board);
            next.makeMove(m);
            MCTSNode child = new MCTSNode(next, this, m);
            children.add(child);
            return child;
        }

        MCTSNode getBestUCBChild() {
            MCTSNode best = null; double max = -Double.MAX_VALUE;
            for (MCTSNode c : children) {
                double val = (c.wins / (c.visits + 1e-6)) + EXPLORATION * Math.sqrt(Math.log(visits + 1) / (c.visits + 1e-6));
                if (val > max) { max = val; best = c; }
            }
            return best;
        }

        Move getBestChildMove() {
            MCTSNode best = null; int max = -1;
            for (MCTSNode c : children) {
                if (c.visits > max) { max = c.visits; best = c; }
            }
            return best != null ? best.moveFromParent : null;
        }
    }

    @Override public String name() { return "G07"; }
    @Override public void playGame(Game game) { super.playGame(game); this.board = new Board(); }
}