package stud.g06;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.Random;

/**
 * G06会以一定的概率，给出一个非法的走步
 */
public class AI extends core.player.AI {
    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);
        Random rand = new Random();
        while (true) {
            int index1 = rand.nextInt(200);
            int index2 = rand.nextInt(100);
            if (index1 < 200 && index1 > 100){ //一定比例出错
                Move move = new Move(index1, index2);
                this.board.makeMove(move);
                return move;
            }
            if (index1 != index2 && this.board.get(index1) == PieceColor.EMPTY && this.board.get(index2) == PieceColor.EMPTY) {
                Move move = new Move(index1, index2);
                this.board.makeMove(move);
                return move;
            }
        }
    }

    public String name() {
        return "G06";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
    }
}
