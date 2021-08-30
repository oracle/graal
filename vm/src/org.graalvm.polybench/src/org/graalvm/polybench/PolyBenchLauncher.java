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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Handler;

/**
 * This launcher allows for multi-context benchmarking, which is enabled by the
 * <code>--multi-context-runs</code> option. The value of this option determines the number of
 * benchmark runs and can be <code>0</code> or greater. When zero runs is specified, a new context
 * is created and closed right after the source file is parsed without running the benchmark,
 * though. (N.B. This is especially useful for storing auxiliary engine cache containing ASTs
 * created during the parsing of the source file.) On the other hand, if the number of runs is
 * greater than 1, then a new engine is created and used for each context created for each run. If
 * the number of runs is 1 (the default) then no engine is explicitly created.
 * <p/>
 * It is possible to specify different arguments for each iteration (N.B. currently for the first
 * two iterations only). The pattern of an iteration specific argument is:
 * 
 * <pre>
 * &lt;arg_name&gt;.&lt;iteration&gt;=&lt;value&gt;
 * </pre>
 * <p/>
 * In order to facilitate debugging of benchmarks using auxiliary engine cache, the
 * <code>--use-debug-cache</code> option can be used. Using this option should be accompanied by
 * <code>--multi-context-runs=2</code>. In the first iteration the source is parsed and the
 * in-memory auxiliary engine cache is stored without running the benchmark, while the second
 * iteration will load the in-memory cache and run the benchmark.
 */
public final class PolyBenchLauncher extends AbstractLanguageLauncher {
    static class ArgumentConsumer {
        private final String prefix;
        private final BiConsumer<String, Config> action;
        private final Consumer<Config> flagAction;

        ArgumentConsumer(String prefix, BiConsumer<String, Config> action) {
            this.prefix = prefix;
            this.action = action;
            this.flagAction = null;
        }

        ArgumentConsumer(String prefix, Consumer<Config> flagAction) {
            this.prefix = prefix;
            this.action = null;
            this.flagAction = flagAction;
        }

        boolean consume(String argument, Iterator<String> args, Config options) {
            if (!argument.startsWith(prefix)) {
                return false;
            }

            final String value;
            if (prefix.length() > 2 && argument.contains("=")) {
                // Only multi-character flags support the equals syntax.
                value = argument.split("=", 2)[1];
            } else {
                if (!argument.equals(prefix)) {
                    return false;
                }
                if (flagAction != null) {
                    flagAction.accept(options);
                    return true;
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
            this.consumers.add(new ArgumentConsumer("--path", (value, config) -> {
                config.path = value;
            }));
            this.consumers.add(new ArgumentConsumer("--class-name", (value, config) -> {
                config.className = value;
            }));
            this.consumers.add(new ArgumentConsumer("--mode", (value, config) -> {
                config.mode = Config.Mode.parse(value);
            }));
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
                    default:
                        throw new IllegalArgumentException("Unknown metric: " + value);
                }
            }));
            this.consumers.add(new ArgumentConsumer("-w", (value, config) -> {
                config.warmupIterations = Integer.parseInt(value);
            }));
            this.consumers.add(new ArgumentConsumer("-i", (value, config) -> {
                config.iterations = Integer.parseInt(value);
            }));
            this.consumers.add(new ArgumentConsumer("--use-debug-cache", (config) -> {
                config.useDebugCache = true;
            }));
            this.consumers.add(new ArgumentConsumer("--multi-context-runs", (value, config) -> {
                config.runCount = Integer.parseInt(value);
            }));
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

        if (this.config.runCount > 1) {
            processMultiContextArguments();
        }
        return this.config.unrecognizedArguments;
    }

    private void processMultiContextArguments() {
        List<String> engineArgs = new ArrayList<>();
        // The storage for the iteration-specific arguments
        List<List<String>> perIterationArgs = new ArrayList<>();
        // Iteration-specific arguments can be specified for the two first iterations only atm.
        perIterationArgs.add(new ArrayList<>()); // iteration 0 args
        perIterationArgs.add(new ArrayList<>()); // iteration 1 args

        Iterator<String> iterator = this.config.unrecognizedArguments.iterator();
        while (iterator.hasNext()) {
            String option = iterator.next();

            // Extract iteration-specific arguments. The pattern of an iteration specific arguments
            // is: <arg_name>.<iteration>=<arg_value>
            if (option.contains("=")) {
                String[] nameValue = option.split("=", 2);
                String indexedArgName = nameValue[0];
                if (indexedArgName.length() >= 3) {
                    String argName = indexedArgName.substring(0, indexedArgName.length() - 2);
                    String argValue = nameValue[1];
                    if (indexedArgName.endsWith(".0")) {
                        iterator.remove();
                        perIterationArgs.get(0).add(argName + "=" + argValue);
                        continue;
                    } else if (indexedArgName.endsWith(".1")) {
                        iterator.remove();
                        perIterationArgs.get(1).add(argName + "=" + argValue);
                        continue;
                    }
                }
            }

            // The engine options must be separated and used later when building a context
            if (option.startsWith("--engine.")) {
                iterator.remove();
                engineArgs.add(option);
            } else if ("--experimental-options".equals(option)) {
                engineArgs.add(option);
                perIterationArgs.get(0).add(option);
                perIterationArgs.get(1).add(option);
            }
        }

        parseUnrecognizedOptions(getLanguageId(config.path), config.multiContextEngineOptions, engineArgs);
        parseUnrecognizedOptions(getLanguageId(config.path), config.perIterationContextOptions.get(0), perIterationArgs.get(0));
        parseUnrecognizedOptions(getLanguageId(config.path), config.perIterationContextOptions.get(1), perIterationArgs.get(1));
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
        contextBuilder.allowAllAccess(true);

        contextBuilder.options(config.multiContextEngineOptions);
        if (!config.useDebugCache && config.runCount > 1) {
            contextBuilder.engine(Engine.newBuilder().allowExperimentalOptions(true).options(config.multiContextEngineOptions).build());
        }

        if (config.runCount == 0) {
            // Create the context and close it right afterwards to trigger the compilation of AOT
            // roots
            try (Context context = contextBuilder.build()) {
                context.eval(Source.newBuilder(getLanguageId(config.path), Paths.get(config.path).toFile()).build());
                return;
            } catch (IOException e) {
                throw abort(String.format("Error loading file '%s' (%s)", config.path, e.getMessage()));
            }
        }

        for (int i = 0; i < config.runCount; i++) {
            Map<String, String> perIterationOptions = config.perIterationContextOptions.get(i);
            if (perIterationOptions != null) {
                contextBuilder.options(perIterationOptions);
            }
            if (config.useDebugCache) {
                // The debug engine cache facilitation
                if (i == 0) {
                    contextBuilder.option("engine.DebugCacheCompile", "aot").//
                                    option("engine.DebugCacheStore", "true").//
                                    option("engine.MultiTier", "false").//
                                    option("engine.CompileAOTOnCreate", "false");
                    try (Context context = contextBuilder.build()) {
                        context.eval(Source.newBuilder(getLanguageId(config.path), Paths.get(config.path).toFile()).build());
                    } catch (IOException e) {
                        throw abort(String.format("Error loading file '%s' (%s)", config.path, e.getMessage()));
                    }
                } else {
                    contextBuilder.option("engine.DebugCacheStore", "false").//
                                    option("engine.DebugCacheLoad", "true");
                    runHarness(contextBuilder);
                }
            } else {
                runHarness(contextBuilder);
            }
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
            Value mainKlass = null;
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

    private void runHarness(Context.Builder contextBuilder) {
        log("::: Starting " + config.path + " :::");
        log(config.toString());
        log("");

        switch (config.mode) {
            case interpreter:
                contextBuilder.option("engine.Compilation", "false");
                break;
            case standard:
                contextBuilder.option("engine.Compilation", "true");
                break;
            default:
                throw new AssertionError("Unknown execution-mode: " + config.mode);
        }
        contextBuilder.options(config.metric.getEngineOptions(config));
        Handler handler = config.metric.getLogHandler();
        if (handler != null) {
            contextBuilder.logHandler(handler);
        }

        switch (getExtension(config.path)) {
            // Set Java class path before spawning context.
            case "jar":
                contextBuilder.option("java.Classpath", config.path);
                break;
            case "wasm":
                contextBuilder.option("wasm.Builtins", "wasi_snapshot_preview1");
                break;
        }

        try (Context context = contextBuilder.build()) {
            log("::: Initializing :::");

            EvalResult evalResult = evalSource(context, config.path);

            log("language: " + evalResult.languageId);
            log("type:     " + (evalResult.isBinarySource ? "binary" : "source code"));
            log("length:   " + evalResult.sourceLength + (evalResult.isBinarySource ? " bytes" : " characters"));
            log("");

            log("::: Bench specific options :::");
            config.parseBenchSpecificDefaults(evalResult.value);
            config.metric.parseBenchSpecificOptions(evalResult.value);
            log(config.toString());

            log("Initialization completed.");
            log("");

            log("::: Running warmup :::");
            repeatIterations(context, evalResult.languageId, evalResult.sourceName, evalResult.value, true, config.warmupIterations);
            log("");

            log("::: Running :::");
            config.metric.reset();
            repeatIterations(context, evalResult.languageId, evalResult.sourceName, evalResult.value, false, config.iterations);
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
