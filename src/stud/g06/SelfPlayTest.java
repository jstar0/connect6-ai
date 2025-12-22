package stud.g06;

import core.game.ui.Configuration;
import core.match.GameEvent;
import core.player.Player;

import java.util.ArrayList;

/**
 * AI自我对弈测试 - 带随机性版本
 * 测试真实的先后手胜率
 */
public class SelfPlayTest {
    public static void main(String[] args) {
        Configuration.GUI = false;

        ArrayList<Player> players = new ArrayList<>();
        players.add(new AIRandom());  // 带随机性的AI
        players.add(new AIRandom());  // 带随机性的AI

        GameEvent event = new GameEvent("G06 自我对弈(随机)", players);
        // 100场自我对弈
        event.carnivalRun(100);
        event.showResults();
    }
}
