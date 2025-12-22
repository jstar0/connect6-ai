package stud.g06;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

/**
 * G06组的随机棋手 - V0版本（洗牌算法）
 * 将所有空位打散（洗牌），然后依次取出作为落子位置
 * 优点：时间复杂度O(n)，避免重复随机
 */
public class RandomAI0 extends core.player.AI {

    @Override
    public Move findNextMove(Move opponentMove) {
        board.makeMove(opponentMove);

        // 收集所有空位
        List<Integer> emptyPositions = new ArrayList<>();
        for (int i = 0; i < 361; i++) {
            if (board.get(i) == PieceColor.EMPTY) {
                emptyPositions.add(i);
            }
        }

        // 洗牌（Fisher-Yates算法）
        Collections.shuffle(emptyPositions);

        // 取前两个空位
        int pos1 = emptyPositions.get(0);
        int pos2 = emptyPositions.get(1);

        Move move = new Move(pos1, pos2);
        board.makeMove(move);
        return move;
    }

    @Override
    public String name() {
        return "G06-V0";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
    }
}
