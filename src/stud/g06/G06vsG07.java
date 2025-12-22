package stud.g06;

import core.game.ui.Configuration;
import core.match.GameEvent;
import core.player.Player;

import java.util.ArrayList;

/**
 * G06 vs G07 对战测试
 * G06: TBS + PVS + Alpha-Beta
 * G07: MCTS
 */
public class G06vsG07 {
    public static void main(String[] args) {
        Configuration.GUI = false;

        ArrayList<Player> players = new ArrayList<>();
        players.add(new stud.g06.AI());  // 我们的AI
        players.add(new stud.g07.AI());  // G07的MCTS AI

        GameEvent event = new GameEvent("G06 vs G07", players);
        // 10场对战
        event.carnivalRun(10);
        event.showResults();
    }
}
