package stud.g06;

import core.game.ui.Configuration;
import core.player.Player;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fast benchmark runner for local tuning.
 *
 * <p>Key features:
 * <ul>
 *   <li>No busy-wait: games are awaited via {@code Thread.join()} inside {@link BenchWorker}.</li>
 *   <li>Multi-process: split games across JVM processes to speed up local evaluation.</li>
 * </ul>
 *
 * <p>Usage (compatible with the old runner defaults):
 * <pre>
 *   java -cp lib/aiFramework.jar:out stud.g06.Bench [opponentClass] [games] [procs] [g06Threads]
 * </pre>
 *
 * <p>Notes:
 * <ul>
 *   <li>{@code games} follows the original convention (Match gameNumbers). With 2 players this equals total games.</li>
 *   <li>When {@code procs > 1}, this runner will spawn child JVMs and pass {@code -Dg06.threads=g06Threads}.</li>
 *   <li>Keep {@code procs * g06Threads <= CPU cores} to avoid timeouts caused by oversubscription.</li>
 * </ul>
 */
public final class Bench {
    private Bench() {}

    public static void main(String[] args) {
        Configuration.GUI = false;

        String opponentClass = args.length >= 1 ? args[0] : "stud.g02.AI";
        int games = args.length >= 2 ? parseInt(args[1], 10) : 10;
        int procs = args.length >= 3 ? parseInt(args[2], 1) : 1;

        int available = Runtime.getRuntime().availableProcessors();
        int defaultThreads = Math.max(1, Math.min(4, available));
        int g06Threads =
                args.length >= 4 ? parseInt(args[3], defaultThreads) : defaultThreads;

        // Backwards-compatible behavior: if the requested opponent cannot be loaded,
        // fall back to g07 (when available).
        if (!canLoadPlayer(opponentClass)) {
            String fallback = "stud.g07.AI";
            if (!opponentClass.equals(fallback) && canLoadPlayer(fallback)) {
                System.err.println("Cannot load opponent: " + opponentClass + " (fallback to " + fallback + ")");
                opponentClass = fallback;
            } else {
                System.err.println("Cannot load opponent: " + opponentClass);
                System.exit(2);
            }
        }

        procs = Math.max(1, procs);
        games = Math.max(1, games);
        if (procs > games) procs = games;

        String g06Name = new stud.g06.AI().name();
        String oppName = resolvePlayerName(opponentClass);

        if (procs == 1) {
            BenchWorker.MatchStats stats = BenchWorker.runInProcess(opponentClass, games, System.err);
            if (stats == null) System.exit(2);
            printSummary(g06Name, oppName, stats, 1, 0);
            return;
        }

        // Multi-process mode: distribute games across workers.
        int autoThreads = Math.max(1, available / procs);
        if (args.length < 4) {
            g06Threads = Math.min(autoThreads, 16);
        }

        List<Integer> gamesPerProc = splitGames(games, procs);
        BenchWorker.MatchStats total = new BenchWorker.MatchStats();

        String javaBin = javaBin();
        String classPath = System.getProperty("java.class.path");

        ArrayList<Process> processes = new ArrayList<>();
        ArrayList<String> outputs = new ArrayList<>();

        try {
            for (int i = 0; i < gamesPerProc.size(); i++) {
                int g = gamesPerProc.get(i);
                ArrayList<String> cmd = new ArrayList<>();
                cmd.add(javaBin);
                cmd.add("-Djava.awt.headless=true");
                cmd.add("-Dg06.threads=" + g06Threads);
                cmd.add("-cp");
                cmd.add(classPath);
                cmd.add("stud.g06.BenchWorker");
                cmd.add(opponentClass);
                cmd.add(Integer.toString(g));

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                processes.add(pb.start());
            }

            // Collect outputs (each worker prints one RESULT line).
            for (Process p : processes) {
                String out = readAllStdout(p);
                outputs.add(out);
                int code = p.waitFor();
                if (code != 0) {
                    System.err.println("Worker process failed (exit " + code + ").");
                    System.err.println(out);
                    System.exit(code);
                }
            }
        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }

        for (String out : outputs) {
            String line = firstResultLine(out);
            if (line == null) {
                System.err.println("Missing RESULT line in worker output:");
                System.err.println(out);
                System.exit(2);
            }
            Map<String, String> kv = parseKv(line);
            total.games += parseInt(kv.get("games"), 0);
            total.g06Stats[0][0] += parseInt(kv.get("g06FirstLose"), 0);
            total.g06Stats[0][1] += parseInt(kv.get("g06FirstDraw"), 0);
            total.g06Stats[0][2] += parseInt(kv.get("g06FirstWin"), 0);
            total.g06Stats[1][0] += parseInt(kv.get("g06SecondLose"), 0);
            total.g06Stats[1][1] += parseInt(kv.get("g06SecondDraw"), 0);
            total.g06Stats[1][2] += parseInt(kv.get("g06SecondWin"), 0);
        }

        printSummary(g06Name, oppName, total, procs, g06Threads);
    }

    private static String resolvePlayerName(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Object obj = clazz.getDeclaredConstructor().newInstance();
            if (obj instanceof Player) {
                return ((Player) obj).name();
            }
        } catch (Throwable ignored) {
        }
        return className;
    }

    private static boolean canLoadPlayer(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Object obj = clazz.getDeclaredConstructor().newInstance();
            return (obj instanceof Player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void printSummary(
            String g06Name, String oppName, BenchWorker.MatchStats stats, int procs, int g06Threads) {
        int firstWin = stats.g06Stats[0][2];
        int firstLose = stats.g06Stats[0][0];
        int firstDraw = stats.g06Stats[0][1];
        int secondWin = stats.g06Stats[1][2];
        int secondLose = stats.g06Stats[1][0];
        int secondDraw = stats.g06Stats[1][1];

        int firstScore = firstWin * 2 + firstDraw;
        int secondScore = secondWin * 2 + secondDraw;
        int totalScore = firstScore + secondScore;

        System.out.println("=========================================================================");
        if (procs > 1) {
            System.out.println(
                    "\t(Bench) procs="
                            + procs
                            + " g06Threads="
                            + g06Threads
                            + " games="
                            + stats.games);
        }
        System.out.println(
                "\tGame Statistics (" + g06Name + " vs " + oppName + "): " + format(stats.games));
        System.out.println(
                "\t\t先手：\twin: "
                        + format(firstWin)
                        + ", lose: "
                        + format(firstLose)
                        + ", draw: "
                        + format(firstDraw)
                        + ", 得分: "
                        + format(firstScore));
        System.out.println(
                "\t\t后手：\twin: "
                        + format(secondWin)
                        + ", lose: "
                        + format(secondLose)
                        + ", draw: "
                        + format(secondDraw)
                        + ", 得分: "
                        + format(secondScore));
        System.out.println(
                "\t\t合计：\twin: "
                        + format(firstWin + secondWin)
                        + ", lose: "
                        + format(firstLose + secondLose)
                        + ", draw: "
                        + format(firstDraw + secondDraw)
                        + ", 得分: "
                        + format(totalScore));
    }

    private static List<Integer> splitGames(int total, int parts) {
        int base = total / parts;
        int rem = total % parts;
        ArrayList<Integer> res = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            res.add(base + (i < rem ? 1 : 0));
        }
        return res;
    }

    private static String javaBin() {
        String home = System.getProperty("java.home");
        File bin = new File(home, "bin");
        File java = new File(bin, "java");
        return java.getAbsolutePath();
    }

    private static String readAllStdout(Process p) throws Exception {
        try (BufferedReader br =
                new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private static String firstResultLine(String out) {
        if (out == null) return null;
        for (String line : out.split("\\R")) {
            if (line.startsWith("RESULT ")) return line;
        }
        return null;
    }

    private static Map<String, String> parseKv(String resultLine) {
        HashMap<String, String> m = new HashMap<>();
        for (String token : resultLine.split("\\s+")) {
            int idx = token.indexOf('=');
            if (idx <= 0) continue;
            m.put(token.substring(0, idx), token.substring(idx + 1));
        }
        return m;
    }

    private static String format(int v) {
        return String.format("%5d", v);
    }

    private static int parseInt(String s, int fallback) {
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
