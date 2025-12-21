package core.game;

import core.board.Board;
import core.board.PieceColor;
import core.game.timer.TimerFactory;
import core.game.ui.Configuration;
import core.game.ui.GameUI;
import core.game.ui.UiFactory;
import core.game.ui.connect6.BeautyGUI;
import core.player.Player;

import javax.swing.*;
import java.util.ArrayList;

//这个是六子棋的Refree
public class Referee {

    private final Board _board;
    private final Player first;     //先手方，执白棋
    private final Player second;    //后手方，执黑棋

    private String endReason;
    private int steps = 0;
    private ArrayList<Move> moves = new ArrayList<>();

    public Referee(Player first, Player second) {
        //设置先手方执白棋，后手方执黑棋
        first.setColor(PieceColor.WHITE);
        this.first = first;

        second.setColor(PieceColor.BLACK);
        this.second = second;

        //六子棋的Board
        this._board = new Board();
        this._board.clear();

        //为棋手设置Timer
        int timeLimit = Configuration.TIME_LIMIT;
        this.first.setTimer(TimerFactory.getTimer("Console", timeLimit));
        this.second.setTimer(TimerFactory.getTimer("Console", timeLimit));
    }


    public Player whoseMove() {
        return (this._board.whoseMove() == PieceColor.WHITE) ? this.first : this.second;
    }


    public boolean gameOver() {
        return this._board.gameOver();
    }


    public void endingGame(String endReason, Move currMove) {
        this.second.stopTimer();
        this.first.stopTimer();
        this.endReason = endReason;
        recordGame();
    }


    public boolean legalMove(Move move) {
        //如果有GUI，暂停STEP_INTER秒
        if (Configuration.GUI) {
            try {
                Thread.sleep(Configuration.STEP_INTER);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //调用当前棋盘的合法走步判断方法
        if (this._board.legalMove(move)) {
            //裁判记下这个走步
            recordMove(move);
            return true;
        }

        return false;
    }

    /**
     * 记录对弈双方的每一个走步
     */
    public void recordMove(Move move) {
        this._board.makeMove(move);
        this.moves.add(move);
        this.steps++;
    }

    /**
     * 记录当前棋局
     */
    private void recordGame() {

        //因为这是六子棋的裁判，所以是黑棋和白棋。如果是中国象棋，则为黑棋和红棋；
        GameResult result = new GameResult(this.first, this.second, getWinner(), this.steps,
                this.endReason, moves);
        //弹出确认棋局结束窗口
        if (Configuration.GUI){
            JOptionPane.showMessageDialog(null,
                    result,
                    this.first.name() + " vs " + this.second.name(),
                    JOptionPane.INFORMATION_MESSAGE);
        }

        this.first.addGameResult(result);
        this.second.addGameResult(result);
    }

    /**
     * 当前棋局的获胜者
     * @return
     */
    private String getWinner() {
        if ("M".equalsIgnoreCase(this.endReason)) {
            return "NONE";
        }
        return (this._board.whoseMove() == PieceColor.WHITE) ? this.second.name() : this.first.name();
    }

    /**
     * 为game设置ui
     * @param game
     */
    public void setUi(Game game) {
        GameUI ui = UiFactory.getUi(Configuration.GUI_TYPE, this.getGameTitle());
        game.addObserver(ui);
    }
    private String getGameTitle() {
        return this.first.name() + " vs " + this.second.name();
    }

}