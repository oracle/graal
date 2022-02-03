/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.polybench;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.logging.Handler;

/**
 * This launcher is designed for benchmarking Truffle languages. It repeatedly executes the source
 * file specified by its path, whereas the number of warmup and hot iterations can be specified
 * either explicitly or determined via interop from the evaluated source value by reading or
 * executing members <code>iterations</code> and <code>warmupIterations</code>.
 * <p>
 * The launcher generates a structured output containing information on individual iterations as
 * well as the benchmark summary.
 * <p>
 * The following options are accepted:
 * <ul>
 * <li>--path &lt;script&gt;</li>
 * <li>--class-name &lt;class&gt;</li> The class name of an Espresso benchmark inside the jar
 * specified by <code>path</code> (only relevant when executing Java workloads).
 * <li>--mode &lt;mode&gt;</li> Specified the mode which the benchmark will be running in. Consult
 * the available modes via the help command.
 * <li>--metric [peak-time | compilation-time | partial-evaluation-time | one-shot | none]</li>
 * <li>-w &lt;N&gt;</li> The number of warmup iterations
 * <li>-i &lt;N&gt;</li> The number of hot iterations
 * <li>--eval-source-only</li> Indicates that the source file will be evaluated only, and the
 * benchmark execution will be skipped. It can be useful for parsing time benchmarking or for using
 * the debug auxiliary engine cache. N.B. This option can also be specified for each run in a
 * multi-context benchmarking. See below.
 * </ul>
 * <h3>Multi-context benchmarking</h3>
 * <p>
 * This launcher allows for multi-context benchmarking consisting of several consecutive benchmark
 * <code>runs</code>. Each run is executed using a new context instance, while all contexts can
 * share a single engine. A multi-context benchmarking can be configured using the following
 * options:
 * <ul>
 * <li>--multi-context-runs=&lt;N&gt;</li> The number of runs
 * <li>--shared-engine=[true | false]</li> Indicates whether the contexts will share a single
 * engine. If <code>false</code>, the context of each run will have a separate engine, otherwise all
 * contexts will share a single engine.
 * </ul>
 * <p>
 * It is possible to specify different arguments for each run. The pattern of a run specific
 * argument is:
 * 
 * <pre>
 * &lt;arg_name&gt;.&lt;run&gt;=&lt;value&gt;
 * </pre>
 * <p/>
 * <h3>Examples</h3>
 * <h4>A single run with 5 warmup and 10 hot iterations</h4>
 * 
 * <pre>
 *     polybench --path bench.so -w 5 -i 10
 * </pre>
 * 
 * <h4>Two runs with 5 warmup and 10 hot iterations each (no shared engine)</h4>
 * 
 * <pre>
 *     polybench --path bench.so --multi-context-runs=2 -w 5 -i 10
 * </pre>
 * 
 * <h4>Two runs with 5 warmup and 10 hot iterations each with a shared engine</h4>
 * 
 * <pre>
 *     polybench --path bench.so --multi-context-runs=2 --shared-engine=true -w 5 -i 10
 * </pre>
 * 
 * <h4>Storing the auxiliary engine cache and skipping the benchmark</h4>
 * 
 * <pre>
 *     polybench --path bench.so --eval-source-only=true --experimental-options --engine.CacheStore=test.image --engine.CacheCompile=aot
 * </pre>
 * 
 * <h4>Loading the auxiliary engine cache and running the benchmark</h4>
 * 
 * <pre>
 *     polybench --path bench.so --experimental-options --engine.CacheLoad=test.image -w 0 -i 10
 * </pre>
 * 
 * <h4>Using the debug engine cache. The first run only evaluates the source and stores the debug
 * engine cache, while the second run executes the benchmark with the cache.</h4>
 * 
 * <pre>
 *     polybench --path bench.so --multi-context-runs=2 --eval-source-only.0=true -w 0 -i 10 --experimental-options --engine.DebugCacheCompile.0=aot --engine.DebugCacheStore.0=true --engine.DebugCacheStore.1=false --engine.DebugCacheLoad.1=true
 * </pre>
 * 
 * <h4>Reporting the source evaluation time only for 10 runs</h4>
 * 
 * <pre>
 *     polybench --path bench.so --multi-context-runs=10 --eval-source-only=true
 * </pre>
 */
public final class PolyBenchLauncher extends AbstractLanguageLauncher {

    static class ArgumentConsumer {
        private final String prefix;
        private final BiConsumer<String, Config> action;

        ArgumentConsumer(String prefix, BiConsumer<String, Config> action) {
            this.prefix = prefix;
            this.action = action;
        }

        boolean consume(String argument, Iterator<String> args, Config options) {
            if (!argument.startsWith(prefix)) {
                return false;
            }

            final String value;
            if (prefix.length() > 2 && argument.contains("=")) {
                // Only multi-character flags support the equals syntax.
                String[] argNameValue = argument.split("=", 2);
                if (!argNameValue[0].equals(prefix)) {
                    return false;
                }
                value = argNameValue[1];
            } else {
                if (!argument.equals(prefix)) {
                    return false;
                }
                try {
                    value = args.next();
                } catch (NoSuchElementException e) {
                    throw new IllegalArgumentException("Premature end of arguments for prefix " + prefix + ".");
                }
            }
            action.accept(value, options);
            return true;
        }
    }

    RuntimeException abortLaunch(String message) {
        throw abort(message);
    }

    static class ArgumentParser {
        private final List<ArgumentConsumer> consumers;

        ArgumentParser() {
            this.consumers = new ArrayList<>();
            this.consumers.add(new ArgumentConsumer("--path", (value, config) -> config.path = value));
            this.consumers.add(new ArgumentConsumer("--class-name", (value, config) -> config.className = value));
            this.consumers.add(new ArgumentConsumer("--mode", (value, config) -> config.mode = Config.Mode.parse(value)));
            this.consumers.add(new ArgumentConsumer("--metric", (value, config) -> {
                switch (value) {
                    case "peak-time":
                        config.metric = new PeakTimeMetric();
                        break;
                    case "none":
                        config.metric = new NoMetric();
                        break;
                    case "compilation-time":
                        config.metric = new CompilationTimeMetric(CompilationTimeMetric.MetricType.COMPILATION);
                        break;
                    case "partial-evaluation-time":
                        config.metric = new CompilationTimeMetric(CompilationTimeMetric.MetricType.PARTIAL_EVALUATION);
                        break;
                    case "one-shot":
                        config.metric = new OneShotMetric();
                        config.warmupIterations = 0;
                        config.iterations = 1;
                        break;
                    case "allocated-bytes":
                        config.metric = new AllocatedBytesMetric();
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown metric: " + value);
                }
            }));
            this.consumers.add(new ArgumentConsumer("-w", (value, config) -> config.warmupIterations = Integer.parseInt(value)));
            this.consumers.add(new ArgumentConsumer("-i", (value, config) -> config.iterations = Integer.parseInt(value)));
            this.consumers.add(new ArgumentConsumer("--shared-engine", (value, config) -> config.initMultiEngine().sharedEngine = Boolean.parseBoolean(value)));
            this.consumers.add(new ArgumentConsumer("--eval-source-only", (value, config) -> config.evalSourceOnlyDefault = Boolean.parseBoolean(value)));
            this.consumers.add(new ArgumentConsumer("--multi-context-runs", (value, config) -> config.initMultiEngine().numberOfRuns = Integer.parseInt(value)));
        }

        Config parse(List<String> arguments) {
            Config config = new Config();
            final ListIterator<String> iterator = arguments.listIterator();
            outer: while (iterator.hasNext()) {
                final String argument = iterator.next();
                for (ArgumentConsumer consumer : consumers) {
                    if (consumer.consume(argument, iterator, config)) {
                        continue outer;
                    }
                }
                config.unrecognizedArguments.add(argument);
            }
            return config;
        }
    }

    static class RunOption {
        final int runIndex;
        final String name;
        final String value;

        RunOption(int runIndex, String name, String value) {
            this.runIndex = runIndex;
            this.name = name;
            this.value = value;
        }

        static RunOption parse(String option) {
            String[] nameValue = option.split("=", 2);
            if (nameValue.length < 1) {
                return null;
            }
            String indexedName = nameValue[0];
            int lastDot = indexedName.lastIndexOf('.');
            if (lastDot < 0 || lastDot == indexedName.length() - 1) {
                return null;
            }
            String name = indexedName.substring(0, lastDot);
            String runIndexStr = indexedName.substring(lastDot + 1);
            try {
                int runIndex = Integer.parseInt(runIndexStr);
                return new RunOption(runIndex, name, nameValue.length > 1 ? nameValue[1] : null);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private static final ArgumentParser PARSER = new ArgumentParser();
    private Config config;
    private Optional<Double> contextEvalTime = Optional.empty();

    public PolyBenchLauncher() {
    }

    public static void main(String[] args) {
        new PolyBenchLauncher().launch(args);
    }

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        try {
            this.config = PARSER.parse(arguments);
        } catch (IllegalArgumentException e) {
            throw abort(e.getMessage());
        }

        processMultiContextArguments();

        return this.config.unrecognizedArguments;
    }

    private void processMultiContextArguments() {
        List<String> engineOptions = new ArrayList<>();
        // The storage for the run-specific options
        Map<Integer, List<String>> runOptionsMap = new HashMap<>();
        boolean useExperimental = false;

        Iterator<String> iterator = this.config.unrecognizedArguments.iterator();
        while (iterator.hasNext()) {
            String option = iterator.next();

            // Extract run-specific arguments. The pattern of a run-specific option
            // is: <option_name>.<run>=<option_value>
            RunOption runOption = RunOption.parse(option);
            if (runOption != null) {
                iterator.remove();
                if (Config.isPolybenchRunOption(runOption.name)) {
                    // a run-level PolyBench option
                    Map<String, String> pbRunOptions = config.initMultiEngine().polybenchRunOptionsMap.computeIfAbsent(runOption.runIndex, (i) -> new HashMap<>());
                    pbRunOptions.put(runOption.name, runOption.value);
                } else {
                    // a run-level polyglot option
                    List<String> runOptionsToParse = runOptionsMap.computeIfAbsent(runOption.runIndex, (i) -> new ArrayList<>());
                    runOptionsToParse.add(runOption.name + "=" + runOption.value);
                }
            } else if (isAOT() && "--jvm".equals(option)) {
                /*
                 * We're AOT compiled and we see the "--jvm" option: This means we didn't switch to
                 * JVM mode yet. Just abort, this code is going to be called again later in JVM
                 * mode.
                 */
                return;
            } else {
                // The engine options must be separated and used later when building a context
                if (option.startsWith("--engine.")) {
                    iterator.remove();
                    engineOptions.add(option);
                } else if ("--experimental-options".equals(option)) {
                    useExperimental = true;
                    engineOptions.add(option);
                }
            }
        }

        // Parse engine options and store them to config.engineOptions
        HashMap<String, String> polyglotOptions = new HashMap<>();
        parseUnrecognizedOptions(getLanguageId(config.path), polyglotOptions, engineOptions);
        if (!polyglotOptions.isEmpty()) {
            config.initMultiEngine().engineOptions.putAll(polyglotOptions);
        }
        // Parse run specific options and store them to config.runOptionsMap
        for (Map.Entry<Integer, List<String>> runOptionsEntry : runOptionsMap.entrySet()) {
            Map<String, String> runOptions = config.initMultiEngine().polyglotRunOptionsMap.computeIfAbsent(runOptionsEntry.getKey(), (i) -> new HashMap<>());
            if (!runOptionsEntry.getValue().isEmpty()) {
                if (useExperimental) {
                    // the enabled experimental-options flag must be propagated to runOptions to
                    // enable
                    // parsing of run-level experimental options
                    runOptionsEntry.getValue().add("--experimental-options");
                }
                parseUnrecognizedOptions(getLanguageId(config.path), runOptions, runOptionsEntry.getValue());
            }
        }
    }

    @Override
    protected void validateArguments(Map<String, String> polyglotOptions) {
        if (config.path == null) {
            throw abort("Must specify path to the source file with --path.");
        }
        try {
            config.metric.validateConfig(config, polyglotOptions);
        } catch (IllegalStateException ise) {
            throw abort(ise.getMessage());
        }
    }

    @Override
    protected void launch(Context.Builder contextBuilder) {
        if (config.isSingleEngine()) {
            contextBuilder.option("engine.Compilation", config.compilation());
            runHarness(contextBuilder, config.evalSourceOnlyDefault, 0);
        } else {
            multiEngineLaunch(contextBuilder);
        }
    }

    private void multiEngineLaunch(Context.Builder contextBuilder) {
        config.multiEngine.engineOptions.put("engine.Compilation", config.compilation());

        if (config.multiEngine.sharedEngine) {
            contextBuilder.engine(Engine.newBuilder().allowExperimentalOptions(true).options(config.multiEngine.engineOptions).build());
        } else {
            contextBuilder.options(config.multiEngine.engineOptions);
        }
        contextBuilder.allowAllAccess(true);

        for (int i = 0; i < config.multiEngine.numberOfRuns; i++) {
            Map<String, String> perRunOptions = config.multiEngine.polyglotRunOptionsMap.get(i);
            if (perRunOptions != null) {
                contextBuilder.options(perRunOptions);
            }
            runHarness(contextBuilder, config.isEvalSourceOnly(i), i);
        }
    }

    @Override
    protected String getLanguageId() {
        // TODO: this should reflect the language of the input file
        return "js";
    }

    @Override
    protected String[] getDefaultLanguages() {
        return new String[0];
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        System.out.println();
        System.out.println("Usage: polybench [OPTION]... [FILE]");
        System.out.println("Run a benchmark in an arbitrary language on the PolyBench harness.");
    }

    static String getExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        return path.substring(lastDot + 1);
    }

    String getLanguageId(String path) {
        if (path == null) {
            return null;
        }

        final File file = new File(path);
        if ("jar".equals(getExtension(path))) {
            return "java";
        } else {
            try {
                String language = Source.findLanguage(file);
                if (language == null) {
                    throw abort("Could not determine the language for file " + file);
                }
                return language;
            } catch (IOException e) {
                throw abort("Error while examining source file '" + file + "': " + e.getMessage());
            }
        }
    }

    private EvalResult evalSource(Context context, String path) {
        final File file = new File(path);
        if ("jar".equals(getExtension(path))) {
            // Espresso cannot eval .jar files, instead we load the JAR's main class.
            String className = config.className;
            Value mainKlass;
            if (className != null) {
                mainKlass = context.getBindings("java").getMember(className);
            } else {
                Value helper = context.getBindings("java").getMember("sun.launcher.LauncherHelper");
                Value mainClass = helper.invokeMember("checkAndLoadMain", true, 2 /* LM_JAR */, path);
                mainKlass = mainClass.getMember("static"); // Class -> Klass
            }
            return new EvalResult("java", file.getName(), true, file.length(), mainKlass);
        } else {
            Source source;
            String language;
            try {
                language = getLanguageId(path);
                source = Source.newBuilder(language, file).build();
            } catch (IOException e) {
                throw abort("Error while examining source file '" + file + "': " + e.getMessage());
            }
            long evalSourceStartTime = System.nanoTime();
            Value result = context.eval(source);
            contextEvalTime = Optional.of((System.nanoTime() - evalSourceStartTime) / 1_000_000.0);
            return new EvalResult(language, source.getName(), source.hasBytes(), source.getLength(), result);
        }
    }

    static class EvalResult {
        final String languageId;
        final String sourceName;
        final boolean isBinarySource;
        final long sourceLength;
        final Value value;

        EvalResult(String languageId, String sourceName, boolean isBinarySource, long sourceLength, Value value) {
            this.languageId = languageId;
            this.sourceName = sourceName;
            this.isBinarySource = isBinarySource;
            this.sourceLength = sourceLength;
            this.value = value;
        }
    }

    private void runHarness(Context.Builder contextBuilder, boolean evalSourceOnly, int run) {
        log("::: Starting " + config.path + " :::");
        log(config.toString());
        log("");

        contextBuilder.options(config.metric.getEngineOptions(config));
        Handler handler = config.metric.getLogHandler();
        if (handler != null) {
            contextBuilder.logHandler(handler);
        }

        String extension = getExtension(config.path);
        if (extension != null) {
            switch (extension) {
                // Set Java class path before spawning context.
                case "jar":
                    contextBuilder.option("java.Classpath", config.path);
                    break;
                case "wasm":
                    contextBuilder.option("wasm.Builtins", "wasi_snapshot_preview1");
                    break;
            }
        }

        try (Context context = contextBuilder.build()) {
            log("::: Initializing :::");

            PeakTimeMetric evalSourceMetric = new PeakTimeMetric();
            evalSourceMetric.beforeIteration(false, 0, config);
            EvalResult evalResult = evalSource(context, config.path);
            evalSourceMetric.afterIteration(false, 0, config);

            log("run:        " + run);
            log("language:   " + evalResult.languageId);
            log("type:       " + (evalResult.isBinarySource ? "binary" : "source code"));
            log("length:     " + evalResult.sourceLength + (evalResult.isBinarySource ? " bytes" : " characters"));
            log("evaluation: " + round(evalSourceMetric.reportAfterAll().get()) + " " + evalSourceMetric.unit());
            log("");

            log("::: Bench specific options :::");
            config.parseBenchSpecificDefaults(evalResult.value);
            config.metric.parseBenchSpecificOptions(evalResult.value);
            log(config.toString());

            log("Initialization completed.");
            log("");

            if (evalSourceOnly) {
                log("::: Iterations skipped :::");
            } else {
                log("::: Running warmup :::");
                repeatIterations(context, evalResult.languageId, evalResult.sourceName, evalResult.value, true, config.warmupIterations);
                log("");

                log("::: Running :::");
                config.metric.reset();
                repeatIterations(context, evalResult.languageId, evalResult.sourceName, evalResult.value, false, config.iterations);
                log("");
            }

            // this log message is parsed in mx_vm_benchmark.py, if changed adapt parse rule.
            contextEvalTime.ifPresent(delta -> log("### Truffle Context eval time (ms): " + round(delta)));
            log("");
        } catch (Throwable t) {
            throw abort(t);
        }
    }

    private static void log(String msg) {
        System.out.println(msg);
    }

    private static String round(double v) {
        return String.format("%.2f", v);
    }

    private void repeatIterations(Context context, String languageId, String name, Value evalSource, boolean warmup, int iterations) {
        Workload workload = lookup(context, languageId, evalSource, "run");
        // Enter explicitly to avoid context switches for each iteration.
        context.enter();
        try {
            for (int i = 0; i < iterations; i++) {
                config.metric.beforeIteration(warmup, i, config);

                workload.run();

                config.metric.afterIteration(warmup, i, config);

                final Optional<Double> value = config.metric.reportAfterIteration(config);
                if (value.isPresent()) {
                    log("[" + name + "] iteration " + i + ": " + round(value.get()) + " " + config.metric.unit());
                }
            }

            final Optional<Double> value = config.metric.reportAfterAll();
            if (value.isPresent()) {
                log("------");
                log("[" + name + "] " + (warmup ? "after warmup: " : "after run: ") + round(value.get()) + " " + config.metric.unit());
            }
        } finally {
            context.leave();
        }
    }

    private Workload lookup(Context context, String languageId, Value evalSource, String memberName) {
        Value result;
        // language-specific lookup
        switch (languageId) {
            case "wasm":
                // Special case for WASM: Lookup main module and get 'memberName' from there.
                result = context.getBindings(languageId).getMember("main").getMember(memberName);
                break;
            case "java":
                // Espresso doesn't provide methods as executable values.
                // It can only invoke methods from the declaring class or receiver.
                return Workload.createInvoke(evalSource, "main", ProxyArray.fromArray());
            default:
                // first try the memberName directly
                if (evalSource.hasMember(memberName)) {
                    result = evalSource.getMember(memberName);
                } else {
                    // Fallback for other languages: Look for 'memberName' in global scope.
                    result = context.getBindings(languageId).getMember(memberName);
                }
                break;
        }
        if (result == null) {
            throw abort("Cannot find target '" + memberName + "'. Please check that the specified program is a benchmark.");
        }
        if (!result.canExecute()) {
            throw abort("The member named " + memberName + " is not executable: " + result);
        }
        return Workload.createExecuteVoid(result);
    }
}
