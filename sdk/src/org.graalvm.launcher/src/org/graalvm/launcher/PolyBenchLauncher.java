package org.graalvm.launcher;

import org.graalvm.launcher.polybench.Config;
import org.graalvm.launcher.polybench.Metric;
import org.graalvm.launcher.polybench.NoMetric;
import org.graalvm.launcher.polybench.PeakTimeMetric;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PolyBenchLauncher extends LanguageLauncherBase {
    private Source.Builder sourceContent;
    private Config config;
    private Metric metric;

    public PolyBenchLauncher() {
        this.sourceContent = null;
        this.config = new Config();
        this.metric = new NoMetric();
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        Engine engine = getTempEngine();
        printVersion(engine);
    }

    @Override
    protected void printVersion() {
        printVersion(getTempEngine());
        printPolyglotVersions();
    }

    protected void printVersion(Engine engine) {
        String engineImplementationName = engine.getImplementationName();
        if (isAOT()) {
            engineImplementationName += " Native";
        }
        println(String.format("%s polyglot launcher %s", engineImplementationName, engine.getVersion()));
    }

    public static void main(String[] args) {
        PolyBenchLauncher launcher = new PolyBenchLauncher();
        try {
            try {
                launcher.launch(args);
            } catch (AbortException e) {
                throw e;
            } catch (PolyglotException e) {
                launcher.handlePolyglotException(e);
            } catch (Throwable t) {
                throw launcher.abort(t);
            }
        } catch (AbortException e) {
            launcher.handleAbortException(e);
        }
    }

    private static void setInterpreterOnly(Map<String, String> options) {
        options.put("engine.Compilation", "false");
    }

    private List<String> parsePolyBenchLauncherOptions(String[] args, Map<String, String> polyglotOptions) {
        List<String> arguments = Arrays.asList(args);
        List<String> unrecognizedArguments = new ArrayList<>();
        final Iterator<String> iterator = arguments.iterator();
        while (iterator.hasNext()) {
            final String arg = iterator.next();
            String[] parts = arg.split("=", 2);
            if (parts.length == 1) {
                unrecognizedArguments.add(arg);
            }
            switch (parts[0]) {
                case "--path":
                    this.config.path = parts[1];
                    sourceContent = Source.newBuilder(languageId(parts[1]), new File(parts[1]));
                    break;
                case "--execution-mode":
                    switch (parts[1]) {
                        case "interpreter":
                            config.mode = "interpreter";
                            setInterpreterOnly(polyglotOptions);
                            break;
                        default:
                            throw abort("Unknown execution-mode: " + parts[1]);
                    }
                    break;
                case "--metric":
                    switch (parts[1]) {
                        case "peak-time":
                            metric = new PeakTimeMetric();
                            break;
                        default:
                            throw abort("Unknown metric: " + parts[1]);
                    }
                    break;
                case "-wi":
                    config.warmupIterations = Integer.parseInt(parts[1]);
                    break;
                case "-i":
                    config.iterations = Integer.parseInt(parts[1]);
                    break;
                default:
                    unrecognizedArguments.add(arg);
            }
        }
        return unrecognizedArguments;
    }

    private String languageId(String path) {
        final int dotIndex = path.lastIndexOf(".");
        if (dotIndex == -1) {
            throw abort("Cannot detect language from path: " + path);
        }
        String extension = path.substring(dotIndex + 1);
        switch (extension) {
            case "js":
                return "js";
            case "wasm":
                return "wasm";
            case "rb":
                return "ruby";
            case "py":
                return "python";
            default:
                throw abort("Unknown extension: " + extension);
        }
    }

    private void launch(String[] args) {
        final Map<String, String> polyglotOptions = new HashMap<>();
        final List<String> unrecognizedArguments = parsePolyBenchLauncherOptions(args, polyglotOptions);
        parseUnrecognizedOptions(null, polyglotOptions, unrecognizedArguments);
        final Context.Builder contextBuilder = Context.newBuilder().options(polyglotOptions);
        contextBuilder.allowAllAccess(true);

        validateArguments();

        runHarness(contextBuilder);
    }

    private void validateArguments() {
        if (sourceContent == null) {
            throw abort("Must specify path to the source file with --path.");
        }
    }

    private void runHarness(Context.Builder contextBuilder) {
        log("::: Starting " + config.path + " :::");
        log(config.toString());
        log("");

        try (Context context = contextBuilder.build()) {
            try {
                log("::: Parsing :::");
                final Source source = sourceContent.build();
                context.eval(source);
                log("Parsing completed.");
                log("");

                log("::: Running warmup :::");
                repeatIterations(context, source.getLanguage(), source.getName(), true, config.warmupIterations);
                log("");

                log("::: Running :::");
                metric.reset();
                repeatIterations(context, source.getLanguage(), source.getName(), false, config.iterations);
                log("");
            } catch (Throwable t) {
                throw abort(t);
            }
        }
    }

    private static void log(String msg) {
        System.out.println(msg);
    }

    private static String round(double v) {
        return String.format("%.2f", v);
    }

    private void repeatIterations(Context context, String languageId, String name, boolean warmup, int iterations) {
        Value run = lookup(context, languageId, "run");

        for (int i = 0; i < iterations; i++) {
            metric.beforeIteration(warmup, i, config);

            run.execute();

            metric.afterIteration(warmup, i, config);

            final Optional<Double> value = metric.reportAfterIteration(config);
            if (value.isPresent()) {
                log("[" + name + "] iteration " + i + ": " + round(value.get()) + " " + metric.unit());
            }
        }

        final Optional<Double> value = metric.reportAfterAll();
        if (value.isPresent()) {
            log("------");
            log("[" + name + "] " + (warmup ? "after warmup: " : "after run: ") + round(value.get()) + " " + metric.unit());
        }
    }

    private Value lookup(Context context, String languageId, String memberName) {
        Value result;
        switch (languageId) {
            case "wasm":
                result = context.getBindings(languageId).getMember("main").getMember(memberName);
                break;
            default:
                result = context.getBindings(languageId).getMember(memberName);
                break;
        }
        if (result == null) {
            throw abort("Cannot find target '" + memberName + "'. Please check that the specified program is a benchmark.");
        }
        if (!result.canExecute()) {
            throw abort("The member named " + memberName + " is not executable: " + result);
        }
        return result;
    }
}
