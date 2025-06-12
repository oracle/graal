/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.util;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;

/**
 * Utility class for managing and applying additional optimizer benefits to "super hot" code
 * regions.
 *
 * This class provides methods to identify, evaluate, and process compilation units that demonstrate
 * exceptional runtime significance based on global profiling data. Its functions enable the
 * optimizer to selectively apply advanced strategies and enhancements to code regions determined to
 * be of highest execution criticality, maximizing performance gains where they matter most.
 */
public class GlobalProfilesOptimizationUtility {

    public static class Options {
        @Option(help = "Minimal self time for a compilation unit to be considered hot globally.")//
        public static final OptionKey<Double> HotCodeMinSelfTime = new OptionKey<>(0.001);

    }

    /**
     * Selects and returns the appropriate option value for the specified compilation unit based on
     * its optimization significance. If the provided {@link StructuredGraph} is determined to merit
     * prioritization for optimization - such as exhibiting high runtime significance or profiling
     * "hotness" - the {@code hotOption} value is returned. Otherwise, the {@code coldOption} value
     * is chosen. This selection allows the optimizer to allocate additional resources beyond
     * regular hotness to a compilation unit.
     */
    public static <X> X selectOptionBySignificance(StructuredGraph graph, OptionKey<X> coldOption, OptionKey<X> hotOption) {
        return shouldPrioritizeForOptimization(graph) ? hotOption.getValue(graph.getOptions()) : coldOption.getValue(graph.getOptions());
    }

    public static <X> X selectOptionBySignificance(StructuredGraph graph, X coldValue, OptionKey<X> hotOption) {
        return shouldPrioritizeForOptimization(graph) ? hotOption.getValue(graph.getOptions()) : coldValue;
    }

    public static <X> X selectOptionBySignificance(StructuredGraph graph, X coldValue, X hotValue) {
        return shouldPrioritizeForOptimization(graph) ? hotValue : coldValue;
    }

    /**
     * Determines if the specified graph merits prioritization for optimization. This method
     * evaluates the compilation unit's significance-based on heuristics such as self time relative
     * to overall execution - to decide whether it should receive additional optimizer benefits.
     * This assessment is heuristic and intended to guide the allocation of extra optimization
     * resources.
     */
    public static boolean shouldPrioritizeForOptimization(StructuredGraph graph) {
        final double globalSelfTimePercent = graph.globalProfileProvider().getGlobalSelfTimePercent();
        if (globalSelfTimePercent == StructuredGraph.GlobalProfileProvider.GLOBAL_PROFILE_PROVIDER_DISABLED) {
            // We are in a mode where there is no self time data available. In JIT all compilation
            // units are hot but none is extra hot.
            return false;
        }
        return globalSelfTimePercent > Options.HotCodeMinSelfTime.getValue(graph.getOptions());
    }
}
