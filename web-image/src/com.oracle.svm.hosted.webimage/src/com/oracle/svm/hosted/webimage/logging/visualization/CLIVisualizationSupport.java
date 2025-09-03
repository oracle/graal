/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.logging.visualization;

import static com.oracle.svm.hosted.webimage.WebImageGenerator.COMPILE_QUEUE_SCOPE_NAME;
import static com.oracle.svm.hosted.webimage.WebImageGenerator.UNIVERSE_BUILD_SCOPE_NAME;
import static com.oracle.svm.hosted.webimage.WebImageGenerator.WebImageTotalTime;
import static com.oracle.svm.hosted.webimage.codegen.WebImageCodeGen.CODE_GEN_SCOPE_NAME;
import static com.oracle.svm.hosted.webimage.codegen.WebImageCodeGen.WebImageEmitTimer;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.CONSTANTS_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.CONSTANT_SIZE_CLASSES;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.ENTIRE_IMAGE_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.EXTRA_DEFINITIONS_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.INITIAL_DEFINITIONS_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.NO_CF_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.RECONSTRUCTED_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.STATIC_FIELDS_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.TOTAL_METHOD_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.TYPE_DECLARATIONS_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.ANALYSIS_METHODS;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.ANALYSIS_TYPES;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.COMPILED_METHODS;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.COMPILED_TYPES;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.EMITTED_METHODS;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.EMITTED_TYPES;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.HOSTED_METHODS;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.HOSTED_TYPES;

import java.io.PrintStream;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.graalvm.collections.EconomicSet;

import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.webimage.codegen.WebImageJSCodeGen;
import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.options.WebImageOptions.CompilerBackend;
import com.oracle.svm.hosted.webimage.util.metrics.ImageMetricsCollector;

import jdk.graal.compiler.debug.MetricKey;
import jdk.graal.compiler.options.OptionValues;

public class CLIVisualizationSupport extends VisualizationSupport {
    private static final HashMap<String, Color> SIZE_COLORS = new HashMap<>();
    private static final HashMap<String, Color> TYPE_COLORS = new HashMap<>();
    private static final HashMap<String, Color> METHOD_COLORS = new HashMap<>();

    static {
        SIZE_COLORS.put(INITIAL_DEFINITIONS_SIZE.getName(), Color.WHITE);
        SIZE_COLORS.put(EXTRA_DEFINITIONS_SIZE.getName(), Color.BLACK_BRIGHT);
        SIZE_COLORS.put(STATIC_FIELDS_SIZE.getName(), Color.GREEN);
        SIZE_COLORS.put(TYPE_DECLARATIONS_SIZE.getName(), Color.GREEN_BRIGHT);
        SIZE_COLORS.put(RECONSTRUCTED_SIZE.getName(), Color.BLUE);
        SIZE_COLORS.put(NO_CF_SIZE.getName(), Color.BLUE_BRIGHT);
        SIZE_COLORS.put(CONSTANTS_SIZE.getName(), Color.YELLOW_BRIGHT);

        TYPE_COLORS.put(ANALYSIS_TYPES.getName(), new Color(250, 160, 100));
        TYPE_COLORS.put(HOSTED_TYPES.getName(), Color.GREEN_BRIGHT);
        TYPE_COLORS.put(COMPILED_TYPES.getName(), Color.BLUE);
        TYPE_COLORS.put(EMITTED_TYPES.getName(), Color.YELLOW_BRIGHT);

        METHOD_COLORS.put(ANALYSIS_METHODS.getName(), new Color(250, 160, 100));
        METHOD_COLORS.put(HOSTED_METHODS.getName(), Color.GREEN_BRIGHT);
        METHOD_COLORS.put(COMPILED_METHODS.getName(), Color.BLUE);
        METHOD_COLORS.put(EMITTED_METHODS.getName(), Color.YELLOW_BRIGHT);
    }

    public static Map<String, Number> toMap() {
        TimerCollection timerCollection = TimerCollection.singleton();
        LinkedHashMap<String, Number> map = new LinkedHashMap<>();
        map.put(TimerCollection.Registry.SETUP.name, timerCollection.get(TimerCollection.Registry.SETUP).getTotalTime());
        map.put(TimerCollection.Registry.ANALYSIS.name, timerCollection.get(TimerCollection.Registry.ANALYSIS).getTotalTime());
        map.put(TimerCollection.Registry.UNIVERSE.name, timerCollection.get(TimerCollection.Registry.UNIVERSE).getTotalTime());
        map.put(TimerCollection.Registry.COMPILE_TOTAL.name, timerCollection.get(TimerCollection.Registry.COMPILE_TOTAL).getTotalTime());
        map.put(WebImageEmitTimer, timerCollection.get(WebImageEmitTimer).getTotalTime());
        if (WebImageOptions.ClosureCompiler.getValue()) {
            map.put(WebImageJSCodeGen.ClosureTimer, timerCollection.get(WebImageJSCodeGen.ClosureTimer).getTotalTime());
        }
        return map;
    }

    @Override
    public void visualize(PrintStream printStream) {
        PrintStreamDrawKit kit = new PrintStreamDrawKit(printStream);
        CompilerBackend backend = WebImageOptions.getBackend();

        kit.println();

        LinkedHashMap<String, Widget> contents = new LinkedHashMap<>();

        visualizeInfo(contents);

        visualizeStepTiming(contents);

        visualizeTypeCounts(contents);

        visualizeMethodCounts(contents);

        if (backend == CompilerBackend.JS) {
            visualizeObjectSizes(contents);
        }

        EconomicSet<MetricKey> breakdownKeys = EconomicSet.create();
        breakdownKeys.addAll(Arrays.asList(ImageMetricsCollector.SAVED_SIZE_BREAKDOWN_KEYS));
        /*
         * The total method size is not part of the breakdown (because it is already represented by
         * the size of the reconstructed and no-control-flow methods.
         */
        breakdownKeys.remove(TOTAL_METHOD_SIZE);

        if (backend == CompilerBackend.WASM) {
            // Many breakdown metrics do not apply in the WASM backend.
            breakdownKeys.clear();
            breakdownKeys.add(INITIAL_DEFINITIONS_SIZE);
            breakdownKeys.add(EXTRA_DEFINITIONS_SIZE);
            breakdownKeys.add(ENTIRE_IMAGE_SIZE);
        }

        MetricKey[] breakdownKeysArray = breakdownKeys.toArray(new MetricKey[breakdownKeys.size()]);

        visualizeBreakdown(LoggerContext.getQualifiedScopeName(CODE_GEN_SCOPE_NAME, ImageMetricsCollector.PRE_CLOSURE_SCOPE_NAME), "pre-closure size breakdown",
                        contents, breakdownKeysArray);

        if (backend == CompilerBackend.JS && WebImageOptions.ClosureCompiler.getValue()) {
            visualizeBreakdown(LoggerContext.getQualifiedScopeName(CODE_GEN_SCOPE_NAME, ImageMetricsCollector.CLOSURE_SCOPE_NAME), "post-closure size breakdown",
                            contents, breakdownKeysArray);
        }
        PanelGroupWidget panel = new PanelGroupWidget("Web Image Build Statistics", contents);
        panel.visualize(kit);

        kit.println();
    }

    private static void visualizeInfo(LinkedHashMap<String, Widget> contents) {
        SummaryWidget summary = new SummaryWidget(createInfo());
        contents.put("summary", summary);
    }

    private static LinkedHashMap<String, String> createInfo() {
        LinkedHashMap<String, String> info = new LinkedHashMap<>();
        OptionValues options = HostedOptionValues.singleton();
        info.put("Image name", SubstrateOptions.Name.getValue(options));
        info.put("Target VM", WebImageOptions.getTargetVM());
        info.put("Backend", WebImageOptions.getBackend().name());
        info.put("Host JDK", System.getProperty("java.version"));

        StringBuilder optionSummary = new StringBuilder();
        optionSummary.append("<naming=" + WebImageOptions.NamingConvention.getValue(options).toString().toLowerCase(Locale.ROOT) + "> ");
        if (WebImageOptions.UsePEA.getValue(options)) {
            optionSummary.append("<pea> ");
        }
        if (WebImageOptions.ClosureCompiler.getValue()) {
            optionSummary.append("<closure> ");
        }
        if (WebImageOptions.AutoRunVM.getValue(options)) {
            optionSummary.append("<autorun> ");
        }
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            optionSummary.append("<little-endian> ");
        } else {
            optionSummary.append("<big-endian> ");
        }
        info.put("Settings summary", optionSummary.toString());

        return info;
    }

    private static void visualizeStepTiming(LinkedHashMap<String, Widget> contents) {
        BarPlotWidget buildTimes = new BarPlotWidget(toMap(), Optional.of(TimerCollection.singleton().get(WebImageTotalTime).getTotalTime()), "ms", label -> Color.BLUE_BRIGHT);
        contents.put("build timing", buildTimes);
    }

    private static void visualizeObjectSizes(LinkedHashMap<String, Widget> contents) {
        // This histogram displays the count of objects per size-class.
        // A size-class i contains all the objects that consume the size between 2^{i-1} and 2^i.
        Map<String, Number> savedCounters = LoggerContext.currentContext().getSavedCounters(LoggerContext.getQualifiedScopeName(CODE_GEN_SCOPE_NAME),
                        CONSTANT_SIZE_CLASSES.toArray(new MetricKey[0]));
        Map<Number, Number> sizeClasses = new HashMap<>();
        for (MetricKey sizeClassKey : CONSTANT_SIZE_CLASSES) {
            String name = sizeClassKey.getName();
            int sizeClass = Integer.parseInt(name.substring(name.lastIndexOf("-") + 1));
            long count = savedCounters.get(name).longValue();
            sizeClasses.put(1 << sizeClass, count);
        }
        LogHistogramWidget objectSizeHistogram = new LogHistogramWidget(sizeClasses, "B", Color.BLUE_BRIGHT, 12);
        contents.put("pre-closure object-size", objectSizeHistogram);
    }

    private static void visualizeTypeCounts(LinkedHashMap<String, Widget> contents) {
        Map<String, Number> counters = new LinkedHashMap<>();
        counters.putAll(LoggerContext.currentContext().getSavedCounters(UNIVERSE_BUILD_SCOPE_NAME, ANALYSIS_TYPES, HOSTED_TYPES));
        counters.putAll(LoggerContext.currentContext().getSavedCounters(COMPILE_QUEUE_SCOPE_NAME, COMPILED_TYPES));
        counters.putAll(LoggerContext.currentContext().getSavedCounters(CODE_GEN_SCOPE_NAME, EMITTED_TYPES));
        BarPlotWidget typeCounts = new BarPlotWidget(counters, Optional.empty(), "", TYPE_COLORS::get);
        contents.put("reachable types", typeCounts);
    }

    private static void visualizeMethodCounts(LinkedHashMap<String, Widget> contents) {
        Map<String, Number> counters = new LinkedHashMap<>();
        counters.putAll(LoggerContext.currentContext().getSavedCounters(UNIVERSE_BUILD_SCOPE_NAME, ANALYSIS_METHODS, HOSTED_METHODS));
        counters.putAll(LoggerContext.currentContext().getSavedCounters(COMPILE_QUEUE_SCOPE_NAME, COMPILED_METHODS));
        counters.putAll(LoggerContext.currentContext().getSavedCounters(CODE_GEN_SCOPE_NAME, EMITTED_METHODS));
        BarPlotWidget typeCounts = new BarPlotWidget(counters, Optional.empty(), "", METHOD_COLORS::get);
        contents.put("reachable methods", typeCounts);
    }

    private static void visualizeBreakdown(String scopeName, String title, LinkedHashMap<String, Widget> contents, MetricKey[] breakdownKeys) {
        LoggerContext context = LoggerContext.currentContext();
        Map<String, Number> savedCounters = new LinkedHashMap<>(context.getSavedCounters(scopeName, breakdownKeys));
        Number totalSize = savedCounters.get(ENTIRE_IMAGE_SIZE.getName());
        savedCounters.remove(ENTIRE_IMAGE_SIZE.getName());
        StackedBarWidget sizeBreakdown = new StackedBarWidget(savedCounters, totalSize, "kB", 1000, SIZE_COLORS);
        contents.put(title, sizeBreakdown);
    }
}
