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

public class OptimizationUtility {

    public static class Options {
        @Option(help = "Minimal self time for a compilation unit to be considered hot globally.")//
        public static final OptionKey<Double> HotCodeMinSelfTime = new OptionKey<>(0.001);

    }

    public static <X> X chooseAdaptiveBudgetFactor(StructuredGraph graph, OptionKey<X> coldOption, OptionKey<X> hotOption) {
        return hotGlobalSelfTime(graph) ? hotOption.getValue(graph.getOptions()) : coldOption.getValue(graph.getOptions());
    }

    public static <X> X chooseAdaptiveBudgetFactor(StructuredGraph graph, X coldValue, OptionKey<X> hotOption) {
        return hotGlobalSelfTime(graph) ? hotOption.getValue(graph.getOptions()) : coldValue;
    }

    public static <X> X chooseAdaptiveBudgetFactor(StructuredGraph graph, X coldValue, X hotValue) {
        return hotGlobalSelfTime(graph) ? hotValue : coldValue;
    }

    /**
     * Determine if the given graph should be considered "hot" for additional optimization purposes.
     * We define "hot" by inspecting its self time with respect to overall execution time. This is a
     * pure heuristical value.
     */
    public static boolean hotGlobalSelfTime(StructuredGraph graph) {
        return graph.globalProfileProvider().getGlobalSelfTimePercent() > Options.HotCodeMinSelfTime.getValue(graph.getOptions());
    }
}
