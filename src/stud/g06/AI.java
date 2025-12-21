package stud.g06;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * G06 AI - V0 随机棋手（洗牌算法）
 * 在中心13x13区域随机选点，若连续失败则扩展到全棋盘
 */
public class AI extends core.player.AI {

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

        // 洗牌
        Collections.shuffle(emptyPositions);

        // 取前两个空位
        int index1 = emptyPositions.get(0);
        int index2 = emptyPositions.get(1);

        Move move = new Move(index1, index2);
        board.makeMove(move);
        return move;
    }

    @Override
    public String name() {
        return "G06";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
    }
}
