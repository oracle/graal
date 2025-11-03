/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.polyglot.TracingSourceCacheListener.MAX_SOURCE_NAME_LENGTH;
import static com.oracle.truffle.polyglot.TracingSourceCacheListener.truncateString;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;

import org.graalvm.collections.Pair;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.bytecode.BytecodeDescriptor;
import com.oracle.truffle.api.bytecode.debug.HistogramInstructionTracer;
import com.oracle.truffle.api.bytecode.debug.PrintInstructionTracer;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.polyglot.PolyglotEngineOptions.BytecodeHistogramGrouping;

/**
 * Encapsulates data and logic related to polyglot instruction tracers.
 */
final class PolyglotInstructionTracers {

    private final PolyglotSharingLayer layer;
    private final Pair<List<LanguageInfo>, List<LanguageInfo>> languageFilter;
    private final Pair<List<String>, List<String>> methodFilter;
    private final Function<BytecodeDescriptor<?, ?, ?>, PrintInstructionTracer> tracingFactory;
    private final Function<BytecodeDescriptor<?, ?, ?>, HistogramInstructionTracer> histogramFactory;
    private final ScheduledExecutorService intervalExecutor;

    PolyglotInstructionTracers(PolyglotSharingLayer layer) {
        this.layer = layer;
        OptionValues options = layer.engine.getEngineOptionValues();

        this.languageFilter = parseLanguageFilter(layer.engine, options.get(PolyglotEngineOptions.BytecodeLanguageFilter));
        this.methodFilter = parseStringFilter(options.get(PolyglotEngineOptions.BytecodeMethodFilter));

        if (options.get(PolyglotEngineOptions.TraceBytecode)) {
            TruffleLogger engineLogger = layer.engine.getEngineLogger();
            PrintInstructionTracer.Builder b = PrintInstructionTracer.newBuilder((s) -> {
                if (engineLogger.isLoggable(Level.INFO)) {
                    engineLogger.info("[bc] " + s);
                }
            });

            if (this.languageFilter != null || this.methodFilter != null) {
                b.filter((bytecodeNode) -> filterRootNode(languageFilter, methodFilter, bytecodeNode.getRootNode()));
            }

            this.tracingFactory = (descriptor) -> b.build();
            EngineAccessor.BYTECODE.registerInstructionTracerFactory(layer, tracingFactory);
        } else {
            this.tracingFactory = null;
        }

        List<BytecodeHistogramGrouping> groups = options.get(PolyglotEngineOptions.BytecodeHistogram);
        if (groups != null) {
            HistogramInstructionTracer.Builder b = HistogramInstructionTracer.newBuilder();
            if (this.languageFilter != null || this.methodFilter != null) {
                b.filter((bytecodeNode) -> filterRootNode(languageFilter, methodFilter, bytecodeNode.getRootNode()));
            }
            for (BytecodeHistogramGrouping group : groups) {
                switch (group) {
                    case tier:
                        b.groupBy((node, thread, compilationTier) -> {
                            switch (compilationTier) {
                                case 1:
                                    return TierGroup.TIER_2_COMPILED_FIRST;
                                case 2:
                                    return TierGroup.TIER_3_COMPILED_LAST;
                                default:
                                    switch (node.getTier()) {
                                        case CACHED:
                                            return TierGroup.TIER_1_INTERPRETED_CACHED;
                                        case UNCACHED:
                                            return TierGroup.TIER_0_INTERPRETED_UNCACHED;
                                        default:
                                            throw new AssertionError();
                                    }
                            }
                        });
                        break;
                    case root:
                        b.groupBy((node, thread, compiled) -> new RootNodeGroup(node.getRootNode()));
                        break;
                    case language:
                        b.groupBy((node, thread, compiled) -> node.getRootNode().getLanguageInfo().getId());
                        break;
                    case source:
                        b.groupBy((node, thread, compiled) -> {
                            SourceSection sc = node.getRootNode().getSourceSection();
                            if (sc == null) {
                                return "<no-source>";
                            } else {
                                return new SourceGroup(sc.getSource());
                            }
                        });
                        break;
                    case thread:
                        b.groupBy((node, thread, compiled) -> thread);
                        break;
                    default:
                        throw new AssertionError();
                }
            }

            this.histogramFactory = (descriptor) -> b.build(descriptor);

            Duration histogramInterval = options.get(PolyglotEngineOptions.BytecodeHistogramInterval);
            if (!histogramInterval.isZero()) {
                this.intervalExecutor = Executors.newSingleThreadScheduledExecutor();
                this.intervalExecutor.scheduleAtFixedRate(
                                this::dumpAndReset,
                                histogramInterval.toMillis(),
                                histogramInterval.toMillis(),
                                TimeUnit.MILLISECONDS);
            } else {
                this.intervalExecutor = null;
            }

            EngineAccessor.BYTECODE.registerInstructionTracerFactory(layer, histogramFactory);
        } else {
            this.histogramFactory = null;
            this.intervalExecutor = null;
        }
    }

    void onLayerClose() {
        dumpAndReset();

        if (intervalExecutor != null) {
            intervalExecutor.close();
            try {
                intervalExecutor.awaitTermination(25, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
        }
    }

    private void dumpAndReset() {
        List<HistogramInstructionTracer> engineTracers = EngineAccessor.BYTECODE.getEngineInstructionTracers(layer, histogramFactory);
        TruffleLogger logger = layer.engine.getEngineLogger();
        for (HistogramInstructionTracer t : engineTracers) {
            logger.info("[bc] " + t.getHistogramAndReset().dump());
        }
    }

    static PolyglotInstructionTracers install(PolyglotSharingLayer layer) {
        OptionValues options = layer.engine.getEngineOptionValues();
        boolean traceBytecode = options.get(PolyglotEngineOptions.TraceBytecode);
        List<BytecodeHistogramGrouping> bytecodeStatistics = options.get(PolyglotEngineOptions.BytecodeHistogram);
        if (!traceBytecode && bytecodeStatistics == null) {
            // fast-path: not enabled
            return null;
        }
        return new PolyglotInstructionTracers(layer);
    }

    private static boolean filterRootNode(Pair<List<LanguageInfo>, List<LanguageInfo>> languageFilter, Pair<List<String>, List<String>> methodFilter, RootNode rootNode) {
        if (languageFilter != null) {
            if (!applyLanguageFilter(rootNode, languageFilter)) {
                return false;
            }
        }
        if (methodFilter != null) {
            if (!applyMethodFilter(rootNode.getQualifiedName(), methodFilter)) {
                return false;
            }
        }
        return true;
    }

    private static Pair<List<LanguageInfo>, List<LanguageInfo>> parseLanguageFilter(PolyglotEngineImpl engine, String filter) {
        Pair<List<String>, List<String>> f = parseStringFilter(filter);
        if (f == null) {
            return null;
        }
        if (f.getLeft().isEmpty() && f.getRight().isEmpty()) {
            return null;
        }
        return Pair.create(resolveLanguages(engine, f.getLeft()), resolveLanguages(engine, f.getRight()));
    }

    private static List<LanguageInfo> resolveLanguages(PolyglotEngineImpl engine, List<String> f) {
        // we deliberately keep null values.
        return f.stream().map((s) -> engine.idToInternalLanguageInfo.get(s)).toList();
    }

    private static Pair<List<String>, List<String>> parseStringFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return null;
        }
        List<String> includesList = new ArrayList<>();
        List<String> excludesList = new ArrayList<>();
        String[] items = filter.split(",");
        for (String item : items) {
            String trimmedItem = item.trim();
            if (trimmedItem.startsWith("~")) {
                excludesList.add(trimmedItem.substring(1));
            } else {
                includesList.add(trimmedItem);
            }
        }
        return Pair.create(includesList, excludesList);
    }

    private static boolean applyLanguageFilter(RootNode rootNode, Pair<List<LanguageInfo>, List<LanguageInfo>> filter) {
        TruffleLanguage<?> truffleLanguage = EngineAccessor.NODES.getLanguage(rootNode);
        LanguageInfo language;
        if (truffleLanguage != null) {
            PolyglotLanguageInstance li = (PolyglotLanguageInstance) EngineAccessor.LANGUAGE.getPolyglotLanguageInstance(truffleLanguage);
            language = li.language.info;
        } else {
            language = null;
        }
        List<LanguageInfo> includes = filter.getLeft();
        // Empty include list means match-all unless excluded below.
        boolean included = includes.isEmpty();
        if (language != null) {
            for (int i = 0; !included && i < includes.size(); i++) {
                if (includes.get(i) != null && language.equals(includes.get(i))) {
                    included = true;
                }
            }
        }
        if (!included) {
            return false;
        }
        if (language != null) {
            List<LanguageInfo> excludes = filter.getRight();
            for (LanguageInfo exclude : excludes) {
                if (language.equals(exclude)) {
                    return false;
                }
            }
        }
        return included;
    }

    private static boolean applyMethodFilter(String input, Pair<List<String>, List<String>> filter) {
        if (filter == null) {
            return true;
        }
        List<String> includes = filter.getLeft();
        // Empty include list means match-all unless excluded below.
        boolean included = includes.isEmpty();
        if (input != null) {
            for (int i = 0; i < includes.size(); i++) {
                if (input.contains(includes.get(i))) {
                    included = true;
                    break;
                }
            }
        }
        if (!included) {
            return false;
        }
        if (input != null) {
            for (String exclude : filter.getRight()) {
                if (input.contains(exclude)) {
                    return false;
                }
            }
        }
        return true;
    }

    private record RootNodeGroup(RootNode rootNode) {
        @Override
        public String toString() {
            String name = rootNode.getQualifiedName();
            if (name == null) {
                name = rootNode.toString();
            }
            return "Root: " + name;
        }
    }

    private record SourceGroup(Source source) {
        @Override
        public String toString() {
            return String.format("Source: 0x%08x %s", source.hashCode(), truncateString(source.getName(), MAX_SOURCE_NAME_LENGTH));
        }
    }

    private enum TierGroup {
        TIER_3_COMPILED_LAST,
        TIER_2_COMPILED_FIRST,
        TIER_1_INTERPRETED_CACHED,
        TIER_0_INTERPRETED_UNCACHED;

        @Override
        public String toString() {
            return switch (this) {
                case TIER_0_INTERPRETED_UNCACHED -> "Tier 0: Unprofiled Interpreter";
                case TIER_1_INTERPRETED_CACHED -> "Tier 1: Profiled Interpreter";
                case TIER_2_COMPILED_FIRST -> "Tier 2: First Tier Compiler";
                case TIER_3_COMPILED_LAST -> "Tier 3: Last Tier Compiler";
            };
        }
    }

}
