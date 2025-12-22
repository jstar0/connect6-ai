package stud.g06;

import core.game.Game;
import core.game.GameResult;
import core.game.ui.Configuration;
import core.match.GameEvent;
import core.match.Match;
import core.player.Player;

import java.util.ArrayList;

/**
 * 走法2 vs 走法3 对战测试
 * 1000场（先后手各500场）
 */
public class RandomTest {
    public static void main(String[] args) {
        Configuration.GUI = false;

        ArrayList<Player> players = new ArrayList<>();
        players.add(new RandomAI2());
        players.add(new RandomAI3());

        GameEvent event = new GameEvent("走法2 vs 走法3", players);
        // 1000场，先后手各500场
        event.carnivalRun(1000);
        event.showResults();
    }
}
