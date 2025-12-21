package stud.g02;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;
import core.game.timer.StopwatchCPU;

import java.util.Random;

/**
 * 每当轮到下棋时，该棋手会先发一会儿呆
 */
public class AI extends core.player.AI {
    private int steps = 0;

    @Override
    public Move findNextMove(Move opponentMove) {

        this.board.makeMove(opponentMove);

        //每次下棋时，先发一会儿呆 :)
        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Random rand = new Random();
        while (true) {
            int index1 = rand.nextInt(100);
            int index2 = rand.nextInt(100);

            if (index1 != index2 && this.board.get(index1) == PieceColor.EMPTY && this.board.get(index2) == PieceColor.EMPTY) {
                Move move = new Move(index1, index2);
                this.board.makeMove(move);
                steps++;
                return move;
            }
        }
    }

    @Override
    public String name() {
        return "G02";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
        steps = 0;
    }
}
