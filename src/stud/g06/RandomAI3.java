package stud.g06;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

/**
 * 走法3随机棋手：中心13×13区域随机，连续10次不中则扩展到全棋盘
 */
public class RandomAI3 extends core.player.AI {

    private Random rand = new Random();
    // 中心13x13区域：col和row都在[3,15]范围内
    private static final int CENTER_MIN = 3;
    private static final int CENTER_MAX = 15;

    @Override
    public Move findNextMove(Move opponentMove) {
        board.makeMove(opponentMove);

        int pos1 = randomCenterPosition(-1);
        int pos2 = randomCenterPosition(pos1);

        Move move = new Move(pos1, pos2);
        board.makeMove(move);
        return move;
    }

    private int randomCenterPosition(int exclude) {
        // 先在中心13x13区域尝试10次
        for (int i = 0; i < 10; i++) {
            int col = CENTER_MIN + rand.nextInt(CENTER_MAX - CENTER_MIN + 1);
            int row = CENTER_MIN + rand.nextInt(CENTER_MAX - CENTER_MIN + 1);
            int pos = col * 19 + row;
            if (pos != exclude && board.get(pos) == PieceColor.EMPTY) {
                return pos;
            }
        }

        // 10次不中，扩展到全棋盘
        while (true) {
            int pos = rand.nextInt(361);
            if (pos != exclude && board.get(pos) == PieceColor.EMPTY) {
                return pos;
            }
        }
    }

    @Override
    public String name() {
        return "G06-R3";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
    }
}
