package stud.g06;

import core.game.ui.Configuration;
import core.match.GameEvent;
import core.player.Player;

import java.util.ArrayList;

/**
 * 洗牌算法 vs 走法2 vs 走法3 对战测试
 * 验证洗牌算法的胜率
 */
public class ShuffleTest {
    public static void main(String[] args) {
        Configuration.GUI = false;

        ArrayList<Player> players = new ArrayList<>();
        players.add(new RandomAI0());  // 洗牌算法
        players.add(new RandomAI2());  // 走法2
        players.add(new RandomAI3());  // 走法3

        GameEvent event = new GameEvent("洗牌 vs 走法2 vs 走法3", players);
        // 每对100场
        event.carnivalRun(100);
        event.showResults();
    }
}
