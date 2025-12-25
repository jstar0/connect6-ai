package stud.g06;

import core.board.PieceColor;

import static core.board.PieceColor.BLACK;
import static core.board.PieceColor.WHITE;
import static core.game.Move.SIDE;

public final class Road {
    // Forward deltas in linearized index space (row-major: index = row * SIDE + col)
    // Directions: down, right, down-right, up-right
    static final int[] FORWARD = {SIDE, 1, SIDE + 1, -SIDE + 1};

    private final int startPos;   // 0..360
    private final int dir;        // 0..3
    private final boolean active; // whether this 6-segment is within board

    private int blackNum; // 0..6
    private int whiteNum; // 0..6

    Road(int startPos, int dir, boolean active) {
        this.startPos = startPos;
        this.dir = dir;
        this.active = active;
    }

    boolean isActive() {
        return active;
    }

    int getStartPos() {
        return startPos;
    }

    int getDir() {
        return dir;
    }

    int getBlackNum() {
        return blackNum;
    }

    int getWhiteNum() {
        return whiteNum;
    }

    void addStone(PieceColor stone) {
        if (stone == BLACK) {
            blackNum++;
        } else if (stone == WHITE) {
            whiteNum++;
        }
    }

    void removeStone(PieceColor stone) {
        if (stone == BLACK) {
            blackNum--;
        } else if (stone == WHITE) {
            whiteNum--;
        }
    }

    int cellAt(int offset) {
        return startPos + FORWARD[dir] * offset;
    }
}

