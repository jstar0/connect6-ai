package stud.g06;

import core.board.Board;
import core.board.PieceColor;
import core.game.Move;

import java.util.ArrayList;
import java.util.Arrays;

public final class BoardPro extends Board {
    private final RoadTable roadTable = new RoadTable();
    private final int[] battle = new int[361];

    public BoardPro() {
        super();
        updateBattleForMove(180);
    }

    public BoardPro(BoardPro src) {
        super(src);
        getMoveList().addAll(src.getMoveList());

        roadTable.reset();
        Arrays.fill(battle, 0);
        for (int pos = 0; pos < 361; pos++) {
            PieceColor c = get(pos);
            if (c == PieceColor.EMPTY) continue;
            if (pos != 180) roadTable.applyStone(pos, c);
            updateBattleForMove(pos);
        }
    }

    public RoadTable getRoadTable() {
        return roadTable;
    }

    int[] getBattle() {
        return battle;
    }

    /**
     * Returns how many stones are minimally required (1/2/3) to eliminate all opponent 4/5-roads
     * against {@code threatenedColor}. 0 means there is no immediate 4/5-road threat.
     *
     * <p>This is intentionally aligned with the classic Connect6 DTSS threat counting.
     */
    public int countAllThreats(PieceColor threatenedColor) {
        RoadSet[][] byCount = roadTable.getRoadsByCount();
        RoadSet opponentFour = (threatenedColor == PieceColor.WHITE) ? byCount[4][0] : byCount[0][4];
        RoadSet opponentFive = (threatenedColor == PieceColor.WHITE) ? byCount[5][0] : byCount[0][5];

        if (opponentFour.isEmpty() && opponentFive.isEmpty()) return 0;

        RoadSet probeSet = !opponentFour.isEmpty() ? opponentFour : opponentFive;
        Road probeRoad = probeSet.iterator().next();
        for (int i = 0; i < 6; i++) {
            int pos = probeRoad.cellAt(i);
            if (get(pos) != PieceColor.EMPTY) continue;
            roadTable.applyStone(pos, threatenedColor);
            int t = opponentFour.size() + opponentFive.size();
            roadTable.revertStone(pos, threatenedColor);
            if (t == 0) return 1;
        }

        boolean[] visited = new boolean[361];
        ArrayList<Integer> blocks = new ArrayList<>();

        for (Road road : opponentFive) {
            collectEmptyCells(road, blocks, visited);
        }
        for (Road road : opponentFour) {
            collectEmptyCells(road, blocks, visited);
        }

        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                int p1 = blocks.get(i);
                int p2 = blocks.get(j);
                roadTable.applyStone(p1, threatenedColor);
                roadTable.applyStone(p2, threatenedColor);
                boolean solved = opponentFour.isEmpty() && opponentFive.isEmpty();
                roadTable.revertStone(p2, threatenedColor);
                roadTable.revertStone(p1, threatenedColor);
                if (solved) return 2;
            }
        }

        return 3;
    }

    private void collectEmptyCells(Road road, ArrayList<Integer> out, boolean[] visited) {
        for (int i = 0; i < 6; i++) {
            int pos = road.cellAt(i);
            if (get(pos) != PieceColor.EMPTY) continue;
            if (visited[pos]) continue;
            visited[pos] = true;
            out.add(pos);
        }
    }

    @Override
    public void makeMove(Move mov) {
        PieceColor mover = whoseMove();
        super.makeMove(mov);
        roadTable.applyMove(mov, mover);
        updateBattleForMove(mov.index1());
        updateBattleForMove(mov.index2());
    }

    @Override
    public void undo() {
        if (getMoveList().isEmpty()) return;
        Move last = getMoveList().get(getMoveList().size() - 1);
        super.undo();
        // After undo(), whoseMove() is restored to the player who made the undone move.
        roadTable.revertMove(last, whoseMove());
        updateBattleForUndo(last.index1());
        updateBattleForUndo(last.index2());
    }

    private void updateBattleForMove(int pos) {
        int r = pos / 19;
        int c = pos % 19;
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                int nr = r + dr;
                int nc = c + dc;
                if (nr < 0 || nr >= 19 || nc < 0 || nc >= 19) continue;
                battle[nr * 19 + nc]++;
            }
        }
    }

    private void updateBattleForUndo(int pos) {
        int r = pos / 19;
        int c = pos % 19;
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                int nr = r + dr;
                int nc = c + dc;
                if (nr < 0 || nr >= 19 || nc < 0 || nc >= 19) continue;
                battle[nr * 19 + nc]--;
            }
        }
    }
}
