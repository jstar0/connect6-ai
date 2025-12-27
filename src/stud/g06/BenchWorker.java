package stud.g06;

import core.game.Game;
import core.game.GameResult;
import core.game.Move;
import core.game.ui.Configuration;
import core.player.Player;

import java.lang.reflect.Field;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Single-process match runner without busy-wait (joins the game thread).
 *
 * <p>Designed for multi-process benchmarking: prints one machine-readable RESULT line to stdout.
 */
public final class BenchWorker {
    static final class MatchStats {
        // [0]=G06 as first, [1]=G06 as second; [0]=lose, [1]=draw, [2]=win (same indexing as GameResult#score).
        final int[][] g06Stats = new int[2][3];
        int games;
    }

    private BenchWorker() {}

    public static void main(String[] args) {
        Configuration.GUI = false;

        String opponentClass = args.length >= 1 ? args[0] : "stud.g02.AI";
        int games = args.length >= 2 ? Integer.parseInt(args[1]) : 10;

        MatchStats stats = runInProcess(opponentClass, games, System.out);
        if (stats == null) System.exit(2);

        // Machine-readable line for the orchestrator.
        // NOTE: spaces are separators; values must not contain spaces.
        PrintStream out = System.out;
        out.printf(
                "RESULT opp=%s games=%d g06FirstLose=%d g06FirstDraw=%d g06FirstWin=%d g06SecondLose=%d g06SecondDraw=%d g06SecondWin=%d%n",
                opponentClass,
                stats.games,
                stats.g06Stats[0][0],
                stats.g06Stats[0][1],
                stats.g06Stats[0][2],
                stats.g06Stats[1][0],
                stats.g06Stats[1][1],
                stats.g06Stats[1][2]);
    }

    static MatchStats runInProcess(String opponentClass, int games, PrintStream err) {
        if (games <= 0) games = 1;

        Player g06Template = new stud.g06.AI();
        Player oppTemplate = tryLoadPlayer(opponentClass);
        if (oppTemplate == null) {
            err.println("Cannot load opponent: " + opponentClass);
            return null;
        }

        MatchStats stats = new MatchStats();
        String side =
                System.getProperty("bench.side", "both").trim().toLowerCase(Locale.ROOT);
        boolean dumpOpenings = Boolean.parseBoolean(System.getProperty("bench.dumpOpenings", "false"));
        int dumpMoves = 2;
        try {
            dumpMoves = Integer.parseInt(System.getProperty("bench.dumpMoves", "2"));
        } catch (NumberFormatException ignored) {
            dumpMoves = 2;
        }
        Field movesField = null;
        if (dumpOpenings) {
            try {
                movesField = GameResult.class.getDeclaredField("moves");
                movesField.setAccessible(true);
            } catch (ReflectiveOperationException ignored) {
                movesField = null;
                err.println("bench.dumpOpenings requested but GameResult#moves is not accessible.");
            }
        }

        for (int i = 0; i < games; i++) {
            boolean g06First;
            if ("first".equals(side)) {
                g06First = true;
            } else if ("second".equals(side)) {
                g06First = false;
            } else {
                g06First = (i % 2 == 0);
            }
            Player first = g06First ? clonePlayer(g06Template) : clonePlayer(oppTemplate);
            Player second = g06First ? clonePlayer(oppTemplate) : clonePlayer(g06Template);

            try {
                Game game = new Game(first, second);
                Thread t = game.start();
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                err.println("Interrupted while waiting for game to finish.");
                return null;
            }

            Player g06Player = g06First ? first : second;
            List<GameResult> results = g06Player.gameResults();
            if (results.isEmpty()) {
                err.println("No GameResult recorded for " + g06Player.name());
                return null;
            }
            GameResult last = results.get(results.size() - 1);

            if (dumpOpenings && movesField != null) {
                try {
                    @SuppressWarnings("unchecked")
                    ArrayList<Move> moves = (ArrayList<Move>) movesField.get(last);
                    if (moves != null && !moves.isEmpty()) {
                        StringBuilder seq = new StringBuilder();
                        int limit = Math.min(Math.max(1, dumpMoves), moves.size());
                        for (int k = 0; k < limit; k++) {
                            if (k > 0) seq.append(',');
                            seq.append(moves.get(k));
                        }
                        err.println(
                                "OPENING game="
                                        + i
                                        + " g06First="
                                        + g06First
                                        + " moves="
                                        + seq);
                    }
                } catch (IllegalAccessException ignored) {
                }
            }

            int score = last.score(g06Player.name());
            if (score < 0 || score > 2) {
                err.println("Unexpected score: " + score);
                return null;
            }

            stats.g06Stats[g06First ? 0 : 1][score]++;
            stats.games++;
        }

        return stats;
    }

    private static Player tryLoadPlayer(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return (Player) clazz.getDeclaredConstructor().newInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Player clonePlayer(Player template) {
        try {
            return (Player) template.clone();
        } catch (CloneNotSupportedException e) {
            // The framework expects players to be cloneable; treat as fatal in bench.
            throw new RuntimeException(e);
        }
    }
}
