package stud.g06;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

/**
 * 走法2随机棋手：第一子全棋盘随机，第二子在相邻8格中随机选空位
 */
public class RandomAI2 extends core.player.AI {

    private Random rand = new Random();
    private static final int[][] ADJACENT = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};

    @Override
    public Move findNextMove(Move opponentMove) {
        board.makeMove(opponentMove);

        // 第一子：全棋盘随机
        int pos1 = randomEmptyPosition();
        int col1 = pos1 % 19;
        int row1 = pos1 / 19;

        // 第二子：相邻8格中随机选空位
        List<Integer> adjacent = new ArrayList<>();
        for (int[] d : ADJACENT) {
            int nc = col1 + d[0];
            int nr = row1 + d[1];
            if (nc >= 0 && nc < 19 && nr >= 0 && nr < 19) {
                int npos = nc * 19 + nr;
                if (board.get(npos) == PieceColor.EMPTY && npos != pos1) {
                    adjacent.add(npos);
                }
            }
        }

        int pos2;
        if (!adjacent.isEmpty()) {
            pos2 = adjacent.get(rand.nextInt(adjacent.size()));
        } else {
            // 相邻8格都有子，全棋盘随机
            pos2 = randomEmptyPosition(pos1);
        }

        Move move = new Move(pos1, pos2);
        board.makeMove(move);
        return move;
    }

    private int randomEmptyPosition() {
        while (true) {
            int pos = rand.nextInt(361);
            if (board.get(pos) == PieceColor.EMPTY) {
                return pos;
            }
        }
    }

    private int randomEmptyPosition(int exclude) {
        while (true) {
            int pos = rand.nextInt(361);
            if (pos != exclude && board.get(pos) == PieceColor.EMPTY) {
                return pos;
            }
        }
    }

    @Override
    public String name() {
        return "G06-R2";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
    }
}
