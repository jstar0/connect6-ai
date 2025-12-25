package stud.g06;

import core.game.ui.Configuration;
import core.match.GameEvent;
import core.player.Player;

import java.util.ArrayList;

/**
 * Quick head-to-head match runner for G06.
 *
 * <p>Usage:
 * <ul>
 *   <li>No args: try {@code stud.g02.AI} (if present), otherwise fall back to {@code stud.g07.AI}</li>
 *   <li>Args: {@code <opponentClassName> <games>}</li>
 * </ul>
 */
public class G06vsG07 {
    private static Player tryLoadPlayer(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return (Player) clazz.getDeclaredConstructor().newInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void main(String[] args) {
        Configuration.GUI = false;

        String opponentClass = args.length >= 1 ? args[0] : "stud.g02.AI";
        int games = args.length >= 2 ? Integer.parseInt(args[1]) : 10;

        ArrayList<Player> players = new ArrayList<>();
        players.add(new stud.g06.AI());   // current version

        Player opponent = tryLoadPlayer(opponentClass);
        if (opponent == null) {
            opponentClass = "stud.g07.AI";
            opponent = tryLoadPlayer(opponentClass);
        }
        if (opponent == null) {
            System.err.println("Cannot load opponent: " + opponentClass);
            return;
        }
        players.add(opponent);

        GameEvent event = new GameEvent("G06 vs " + opponentClass, players);
        event.carnivalRun(games);
        event.showResults();
    }
}
