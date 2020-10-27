/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
            if (argument.contains("=")) {
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
            this.consumers.add(new ArgumentConsumer("-wi", (value, options) -> {
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
        runHarness(contextBuilder);
    }

    @Override
    protected String getLanguageId() {
        return "js";
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
