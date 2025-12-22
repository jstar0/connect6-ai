package stud.g06;

import core.game.ui.Configuration;
import core.match.GameEvent;
import core.player.Player;

import java.util.ArrayList;

/**
 * 正式AI vs 随机AI 对战测试
 */
public class FinalTest {
    public static void main(String[] args) {
        Configuration.GUI = false;

        ArrayList<Player> players = new ArrayList<>();
        players.add(new AI());        // 正式AI
        players.add(new AIRandom()); // 随机版AI

        GameEvent event = new GameEvent("正式AI vs 随机AI", players);
        event.carnivalRun(50);
        event.showResults();
    }
}
