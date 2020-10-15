import org.dacapo.harness.Callback;
import org.dacapo.harness.CommandLineArgs;
import org.dacapo.harness.TestHarness;

/**
 * For every iteration, prints the total elapsed wall time since the start of the run.
 */
public final class WallTimeCallback extends Callback {

    private long startMillis = Long.MIN_VALUE;
    private long currentIterationMillis = Long.MIN_VALUE;

    public WallTimeCallback(CommandLineArgs args) {
        super(args);
    }

    @Override
    public void start(String benchmark) {
        if (startMillis == Long.MIN_VALUE) {
            startMillis = System.currentTimeMillis();
        }
        super.start(benchmark);
    }

    @Override
    public void stop() {
        super.stop();
        currentIterationMillis = System.currentTimeMillis();
    }

    @Override
    public void complete(String benchmark, boolean valid) {
        super.complete(benchmark, valid);
        long sinceStartMillis = currentIterationMillis - startMillis;
        System.err.print("===== DaCapo " + TestHarness.getBuildVersion() + " " + benchmark);
        System.err.print(" walltime " + (this.iterations + 1) + " : ");
        System.err.print(sinceStartMillis + " msec ");
        System.err.println("=====");
        System.err.flush();
    }
}
