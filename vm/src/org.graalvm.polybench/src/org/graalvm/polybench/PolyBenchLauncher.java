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

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class PolyBenchLauncher extends AbstractLanguageLauncher {
    class ArgumentConsumer {
        private final String prefix;
        private final BiConsumer<String, Map<String, String>> action;

        ArgumentConsumer(String prefix, BiConsumer<String, Map<String, String>> action) {
            this.prefix = prefix;
            this.action = action;
        }

        boolean consume(String argument, Iterator<String> remaining, Map<String, String> options) {
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
                value = remaining.next();
            }

            action.accept(value, options);
            return true;
        }
    }

    class ArgumentParser {
        private final List<ArgumentConsumer> consumers;

        ArgumentParser() {
            this.consumers = new ArrayList<>();
            this.consumers.add(new ArgumentConsumer("--path", (value, options) -> {
                config.path = value;
                final File file = new File(value);
                try {
                    sourceContent = Source.newBuilder(Source.findLanguage(file), file);
                } catch (IOException e) {
                    throw abort("Error while examining source file '" + file + "': " + e.getMessage());
                }
            }));
            this.consumers.add(new ArgumentConsumer("--mode", (value, options) -> {
                switch (value) {
                    case "interpreter":
                        config.mode = "interpreter";
                        setInterpreterOnly(options);
                        break;
                    case "default":
                        config.mode = "default";
                        setDefault(options);
                        break;
                    default:
                        throw abort("Unknown execution-mode: " + value);
                }
            }));
            this.consumers.add(new ArgumentConsumer("--metric", (value, options) -> {
                switch (value) {
                    case "peak-time":
                        config.metric = new PeakTimeMetric();
                        break;
                    default:
                        throw abort("Unknown metric: " + value);
                }
            }));
            this.consumers.add(new ArgumentConsumer("-w", (value, options) -> {
                config.warmupIterations = Integer.parseInt(value);
            }));
            this.consumers.add(new ArgumentConsumer("-i", (value, options) -> {
                config.iterations = Integer.parseInt(value);
            }));
        }

        List<String> parse(List<String> arguments, Map<String, String> polyglotOptions) {
            try {
                List<String> unrecognizedArguments = new ArrayList<>();
                final Iterator<String> iterator = arguments.iterator();
                outer: while (iterator.hasNext()) {
                    final String argument = iterator.next();
                    for (ArgumentConsumer consumer : consumers) {
                        if (consumer.consume(argument, iterator, polyglotOptions)) {
                            continue outer;
                        }
                    }
                    unrecognizedArguments.add(argument);
                }
                return unrecognizedArguments;
            } catch (NoSuchElementException e) {
                throw abort("Premature end of arguments.");
            }
        }
    }

    private Source.Builder sourceContent;
    private Config config;

    public PolyBenchLauncher() {
        this.sourceContent = null;
        this.config = new Config();
    }

    public static void main(String[] args) {
        PolyBenchLauncher launcher = new PolyBenchLauncher();
        launcher.launch(args);
    }

    private static void setInterpreterOnly(Map<String, String> options) {
        options.put("engine.Compilation", "false");
    }

    private static void setDefault(Map<String, String> options) {
        options.put("engine.Compilation", "true");
    }

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        final ArgumentParser parser = new ArgumentParser();
        return parser.parse(arguments, polyglotOptions);
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
                log("language: " + source.getLanguage());
                log("type:     " + (source.hasBytes() ? "binary" : "source code"));
                log("length:   " + source.getLength() + (source.hasBytes() ? " bytes" : " characters"));
                log("Parsing completed.");
                log("");

                log("::: Running warmup :::");
                repeatIterations(context, source.getLanguage(), source.getName(), true, config.warmupIterations);
                log("");

                log("::: Running :::");
                config.metric.reset();
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
            config.metric.beforeIteration(warmup, i, config);

            run.execute();

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
