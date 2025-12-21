package stud.g03;

import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.Random;

/**
 * 在整个棋盘上掷骰子
 */
public class AI extends core.player.AI {
    private int steps = 0;

    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);
        Random rand = new Random();
        while (true) {
            int index1 = rand.nextInt(361);
            int index2 = rand.nextInt(361);
            if (index1 != index2 && this.board.get(index1) == PieceColor.EMPTY &&
                                    this.board.get(index2) == PieceColor.EMPTY) {
                Move move = new Move(index1, index2);
                this.board.makeMove(move);
                steps++;
                return move;
            }
        }
    }

    public String name() {
        return "G03";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new G03Board("G03Board");
        steps = 0;
    }
}
