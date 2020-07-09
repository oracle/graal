/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.api;

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;

public class PointstoOptions {

    @Option(help = "Enable hybrid context for static methods, i.e. uses invocation site as context for static methods.")//
    public static final OptionKey<Boolean> HybridStaticContext = new OptionKey<>(false);

    @Option(help = "A context sensitive heap means that each heap allocated object is modeled by using at least the allocation site.")//
    public static final OptionKey<Boolean> AllocationSiteSensitiveHeap = new OptionKey<>(false);

    @Option(help = "The minimum length of the context used to model a heap object in addition to the allocation site; used only when ContextSensitiveHeap is enabled.")//
    public static final OptionKey<Integer> MinHeapContextDepth = new OptionKey<>(0);

    @Option(help = "The maximum length of the context used to model a heap object in addition to the allocation site; used only when ContextSensitiveHeap is enabled.")//
    public static final OptionKey<Integer> MaxHeapContextDepth = new OptionKey<>(0);

    @Option(help = "The maximum number of contexts to record for a heap object.  It only affects the analysis when the max and min calling context depth are different.")//
    public static final OptionKey<Integer> MaxHeapContextWidth = new OptionKey<>(0);

    @Option(help = "The minimum length of the methods context chains.")//
    public static final OptionKey<Integer> MinCallingContextDepth = new OptionKey<>(0);

    @Option(help = "The maximum length of the methods context chains.")//
    public static final OptionKey<Integer> MaxCallingContextDepth = new OptionKey<>(0);

    @Option(help = "The maximum number of contexts to record for a method. It only affects the analysis when the max and min calling context depth are different.")//
    public static final OptionKey<Integer> MaxCallingContextWidth = new OptionKey<>(0);

    @Option(help = "Enable a limit for the number of objects recorded for each type of a type state before disabling heap sensitivity for that type. The analysis must be heap sensitive.")//
    public static final OptionKey<Boolean> LimitObjectArrayLength = new OptionKey<>(false);

    @Option(help = "The maximum number of objects recorded for each type of a type state before disabling heap sensitivity for that type. The analysis must be heap sensitive. It has a minimum value of 1.")//
    public static final OptionKey<Integer> MaxObjectSetSize = new OptionKey<>(100);

    @Option(help = "The maximum number of constant objects recorded for each type before merging the constants into one unique constant object per type. The analysis must be heap sensitive. It has a minimum value of 1.")//
    public static final OptionKey<Integer> MaxConstantObjectsPerType = new OptionKey<>(100);

    @Option(help = "Track the progress of the static analysis.")//
    public static final OptionKey<Boolean> ProfileAnalysisOperations = new OptionKey<>(false);

    @Option(help = "Track the creation of constant objects.")//
    public static final OptionKey<Boolean> ProfileConstantObjects = new OptionKey<>(false);

    @Option(help = "Print types used for Java synchronization.")//
    public static final OptionKey<Boolean> PrintSynchronizedAnalysis = new OptionKey<>(false);

    @Option(help = "Analysis: Detect methods that return one of their parameters and hardwire the parameter straight to the return.")//
    public static final OptionKey<Boolean> DivertParameterReturningMethod = new OptionKey<>(true);

    @Option(help = "Enable extended asserts which slow down analysis.")//
    public static final OptionKey<Boolean> ExtendedAsserts = new OptionKey<>(false);

    @Option(help = "Track the callers for methods and accessing methods for fields.")//
    public static final OptionKey<Boolean> TrackAccessChain = new OptionKey<>(false);

    @Option(help = "Track the input for type flows.")//
    public static final OptionKey<Boolean> TrackInputFlows = new OptionKey<>(false);

    @Option(help = "The maximum size of type and method profiles returned by the static analysis. -1 indicates no limitation.")//
    public static final OptionKey<Integer> AnalysisSizeCutoff = new OptionKey<>(8);

    @Option(help = "The maximum number of types recorded in a type flow. -1 indicates no limitation.")//
    public static final OptionKey<Integer> TypeFlowSaturationCutoff = new OptionKey<>(20);

    @Option(help = "Enable the type flow saturation analysis performance optimization.")//
    public static final OptionKey<Boolean> RemoveSaturatedTypeFlows = new OptionKey<Boolean>(true) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            /* Removing saturated type flows needs array type flows aliasing. */
            AliasArrayTypeFlows.update(values, newValue);
        }
    };

    @Option(help = "Model all array type flows using a unique elements type flow abstraction.")//
    public static final OptionKey<Boolean> AliasArrayTypeFlows = new OptionKey<Boolean>(true) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            /* Aliasing array type flows implies relaxation of type flow constraints. */
            RelaxTypeFlowStateConstraints.update(values, newValue);
        }
    };

    @Option(help = "Allow a type flow state to contain types not compatible with its declared type.")//
    public static final OptionKey<Boolean> RelaxTypeFlowStateConstraints = new OptionKey<>(true);

    @Option(help = "Report unresolved elements as errors.")//
    public static final OptionKey<Boolean> UnresolvedIsError = new OptionKey<>(true);

    @Option(help = "Report analysis statistics.")//
    public static final OptionKey<Boolean> PrintPointsToStatistics = new OptionKey<>(false);

    @Option(help = "Path to the contents of the Inspect web server.")//
    public static final OptionKey<String> InspectServerContentPath = new OptionKey<>("inspect");

    @Option(help = "Object scanning in parallel")//
    public static final OptionKey<Boolean> ScanObjectsParallel = new OptionKey<>(true);

    @Option(help = "Scan all objects reachable from roots for analysis. By default false.")//
    public static final OptionKey<Boolean> ExhaustiveHeapScan = new OptionKey<>(false);

    /**
     * Controls the static analysis context sensitivity. Available values:
     * <p/>
     * insens - context insensitive analysis,
     * <p/>
     * allocsens - allocation site sensitive heap, i.e. heap allocated objects are modeled using the
     * allocation site, but the analysis is context insensitive
     * <p/>
     * 1obj - 1 object sensitive analysis with a context insensitive heap (however allocation site
     * sensitive heap),
     * <p/>
     * 2obj1h - 2 object sensitive with a 1 context sensitive heap
     */
    @Option(help = "Controls the static analysis context sensitivity. Available values: insens (context insensitive analysis), allocsens (context insensitive analysis, context insensitive heap, allocation site sensitive heap), " +
                    "_1obj (1 object sensitive analysis with a context insensitive heap), _2obj1h (2 object sensitive with a 1 context sensitive heap)")//
    public static final OptionKey<String> AnalysisContextSensitivity = new OptionKey<String>("insens") {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
            switch (newValue.toLowerCase()) {
                case "insens":
                    AllocationSiteSensitiveHeap.update(values, false);
                    MinHeapContextDepth.update(values, 0);
                    MaxHeapContextDepth.update(values, 0);
                    MinCallingContextDepth.update(values, 0);
                    MaxCallingContextDepth.update(values, 0);
                    break;

                case "allocsens":
                    AllocationSiteSensitiveHeap.update(values, true);
                    MinHeapContextDepth.update(values, 0);
                    MaxHeapContextDepth.update(values, 0);
                    MinCallingContextDepth.update(values, 0);
                    MaxCallingContextDepth.update(values, 0);
                    break;

                case "_1obj":
                    AllocationSiteSensitiveHeap.update(values, true);
                    MinHeapContextDepth.update(values, 0);
                    MaxHeapContextDepth.update(values, 0);
                    MinCallingContextDepth.update(values, 1);
                    MaxCallingContextDepth.update(values, 1);
                    break;

                case "_2obj1h":
                    AllocationSiteSensitiveHeap.update(values, true);
                    MinHeapContextDepth.update(values, 1);
                    MaxHeapContextDepth.update(values, 1);
                    MinCallingContextDepth.update(values, 2);
                    MaxCallingContextDepth.update(values, 2);
                    break;

                case "_3obj2h":
                    AllocationSiteSensitiveHeap.update(values, true);
                    MinHeapContextDepth.update(values, 2);
                    MaxHeapContextDepth.update(values, 2);
                    MinCallingContextDepth.update(values, 3);
                    MaxCallingContextDepth.update(values, 3);
                    break;

                case "_4obj3h":
                    AllocationSiteSensitiveHeap.update(values, true);
                    MinHeapContextDepth.update(values, 3);
                    MaxHeapContextDepth.update(values, 3);
                    MinCallingContextDepth.update(values, 4);
                    MaxCallingContextDepth.update(values, 4);
                    break;

                default:
                    throw shouldNotReachHere("Unknown context sensitivity setting:" + newValue);
            }
        }
    };

    public enum ContextSensitivity {
        insens("insens"),
        allocsens("allocsens"),
        _1obj("_1obj"),
        _2obj1h("_2obj1h"),
        _3obj2h("_3obj2h"),
        _4obj3h("_4obj3h");

        private String value;

        ContextSensitivity(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
