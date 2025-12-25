package stud.g06;

import core.board.PieceColor;
import core.game.Move;

import java.util.ArrayList;
import java.util.List;

import static core.board.PieceColor.BLACK;
import static core.board.PieceColor.WHITE;
import static core.game.Move.SIDE;

public final class RoadTable {
    private final Road[][] roadsByStartDir = new Road[SIDE * SIDE][4];
    private final RoadSet[][] roadsByCount = new RoadSet[7][7];

    @SuppressWarnings("unchecked")
    private final List<Road>[] posToRoads = new List[SIDE * SIDE];

    public RoadTable() {
        for (int i = 0; i < roadsByCount.length; i++) {
            for (int j = 0; j < roadsByCount[0].length; j++) {
                roadsByCount[i][j] = new RoadSet();
            }
        }
        for (int pos = 0; pos < posToRoads.length; pos++) {
            posToRoads[pos] = new ArrayList<>(24);
        }
        reset();
    }

    public RoadSet[][] getRoadsByCount() {
        return roadsByCount;
    }

    public List<Road> getRoadsThroughPos(int pos) {
        if (!Move.validSquare(pos)) return List.of();
        return posToRoads[pos];
    }

    public void reset() {
        for (int b = 0; b <= 6; b++) {
            for (int w = 0; w <= 6; w++) {
                roadsByCount[b][w].clear();
            }
        }
        for (int pos = 0; pos < posToRoads.length; pos++) {
            posToRoads[pos].clear();
        }

        for (int row = 0; row < SIDE; row++) {
            for (int col = 0; col < SIDE; col++) {
                int startPos = row * SIDE + col;
                for (int dir = 0; dir < 4; dir++) {
                    int endPos = startPos + Road.FORWARD[dir] * 5;
                    boolean active = Move.validSquare(endPos) && isSameLine(startPos, endPos, dir);
                    Road road = new Road(startPos, dir, active);
                    roadsByStartDir[startPos][dir] = road;
                    if (!active) continue;

                    roadsByCount[0][0].add(road);
                    for (int i = 0; i < 6; i++) {
                        posToRoads[road.cellAt(i)].add(road);
                    }
                }
            }
        }

        // The framework board always starts with a BLACK stone at the center (J,J => index 180).
        applyStone(180, BLACK);
    }

    public void applyMove(Move move, PieceColor color) {
        if (move == null) return;
        applyStone(move.index1(), color);
        if (Move.validSquare(move.index2())) {
            applyStone(move.index2(), color);
        }
    }

    public void revertMove(Move move, PieceColor color) {
        if (move == null) return;
        revertStone(move.index1(), color);
        if (Move.validSquare(move.index2())) {
            revertStone(move.index2(), color);
        }
    }

    void applyStone(int pos, PieceColor color) {
        if (!Move.validSquare(pos)) return;
        if (color != BLACK && color != WHITE) return;
        for (Road road : posToRoads[pos]) {
            moveRoad(road, color, +1);
        }
    }

    void revertStone(int pos, PieceColor color) {
        if (!Move.validSquare(pos)) return;
        if (color != BLACK && color != WHITE) return;
        for (Road road : posToRoads[pos]) {
            moveRoad(road, color, -1);
        }
    }

    private void moveRoad(Road road, PieceColor color, int delta) {
        roadsByCount[road.getBlackNum()][road.getWhiteNum()].remove(road);
        if (color == BLACK) {
            if (delta > 0) road.addStone(BLACK);
            else road.removeStone(BLACK);
        } else {
            if (delta > 0) road.addStone(WHITE);
            else road.removeStone(WHITE);
        }
        roadsByCount[road.getBlackNum()][road.getWhiteNum()].add(road);
    }

    private boolean isSameLine(int startPos, int endPos, int dir) {
        int startRow = startPos / SIDE;
        int startCol = startPos % SIDE;
        int endRow = endPos / SIDE;
        int endCol = endPos % SIDE;

        switch (dir) {
            case 0: // down
                return startCol == endCol;
            case 1: // right
                return startRow == endRow;
            case 2: // down-right
                return (endRow - startRow) == (endCol - startCol);
            case 3: // up-right
                return (startRow - endRow) == (endCol - startCol);
            default:
                return false;
        }
    }
}
