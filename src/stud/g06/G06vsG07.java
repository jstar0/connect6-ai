package stud.g06;

/**
 * Backwards-compatible match runner entrypoint for G06.
 *
 * <p>This class delegates to {@link Bench}, which:
 * <ul>
 *   <li>avoids GameEvent's busy-wait (no CPU spin while waiting for games),</li>
 *   <li>supports optional multi-process benchmarking for faster local evaluation.</li>
 * </ul>
 */
public class G06vsG07 {
    public static void main(String[] args) {
        Bench.main(args);
    }
}
