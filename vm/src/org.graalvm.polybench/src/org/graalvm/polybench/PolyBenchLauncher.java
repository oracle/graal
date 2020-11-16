/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

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
                value = argument.split("=", 2)[1];
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
            this.consumers.add(new ArgumentConsumer("--path", (value, config) -> {
                config.path = value;
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

    public PolyBenchLauncher() {
    }

    public static void main(String[] args) {
        new PolyBenchLauncher().launch(args);
    }

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        // Add the default arguments.
        polyglotOptions.put("wasm.Builtins", "wasi_snapshot_preview1");

        try {
            this.config = PARSER.parse(arguments);
        } catch (IllegalArgumentException e) {
            throw abort(e.getMessage());
        }
        return this.config.unrecognizedArguments;
    }

    @Override
    protected void launch(Context.Builder contextBuilder) {
        validateArguments();
        contextBuilder.allowAllAccess(true);
        runHarness(contextBuilder);
    }

    @Override
    protected String getLanguageId() {
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

    private void validateArguments() {
        if (config.path == null) {
            throw abort("Must specify path to the source file with --path.");
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

        try (Context context = contextBuilder.build()) {
            log("::: Initializing :::");

            final File file = new File(config.path);
            Source source;
            String language;
            try {
                language = Source.findLanguage(file);
                if (language == null) {
                    throw abort("Could not determine the language for file " + file);
                }
                source = Source.newBuilder(language, file).build();
            } catch (IOException e) {
                throw abort("Error while examining source file '" + file + "': " + e.getMessage());
            }

            context.eval(source);

            log("language: " + source.getLanguage());
            log("type:     " + (source.hasBytes() ? "binary" : "source code"));
            log("length:   " + source.getLength() + (source.hasBytes() ? " bytes" : " characters"));
            log("Initialization completed.");
            log("");

            log("::: Running warmup :::");
            repeatIterations(context, language, source.getName(), true, config.warmupIterations);
            log("");

            log("::: Running :::");
            config.metric.reset();
            repeatIterations(context, language, source.getName(), false, config.iterations);
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

    private void repeatIterations(Context context, String languageId, String name, boolean warmup, int iterations) {
        Value run = lookup(context, languageId, "run");
        // Enter explicitly to avoid context switches for each iteration.
        context.enter();
        try {
            for (int i = 0; i < iterations; i++) {
                config.metric.beforeIteration(warmup, i, config);

                // The executeVoid method is the fastest way to do the transition to guest.
                run.executeVoid();

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
