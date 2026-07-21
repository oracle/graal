/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.duplication.phases.simulation;

import static jdk.graal.compiler.options.OptionType.Debug;
import static jdk.graal.compiler.options.OptionType.Expert;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;

public class DuplicationOptions {

    //@formatter:off
    @Option(help = "Specifies the percentage in node cost graph size for the duplication budget (computed relative to the methods code size). " +
                   "The greater the budget the more code duplication can be performed. " +
                   "This can improve performance at the cost of additional code size.", type = Expert)
    public static final OptionKey<Double> DuplicationBudgetFactor = new OptionKey<>(0.25D);

    @Option(help = "See DuplicationBudgetFactor.", type = Debug)
    public static final OptionKey<Double> DuplicationBudgetFactorHotCode = new OptionKey<>(2.5D);

    @Option(help = "Node cost graph size for a graph to be considered 'small'.", type = Debug)
    public static final OptionKey<Integer> SmallGraphSize = new OptionKey<>(2000);

    @Option(help = "See 'DuplicationBudgetFactor': for small graphs.", type = Debug)
    public static final OptionKey<Double> SmallGraphDuplicationBudgetFactor = new OptionKey<>(1D);

    @Option(help = "Percentage in node cost graph size for the late duplication budget. Computed relative to the methods code size.", type = Debug)
    public static final OptionKey<Double> DuplicationBudgetFactorLate = new OptionKey<>(0.5D);

    @Option(help = "Considers the vectorizability of loop during the duplication of a merge inside a loop." +
                   "There are rare cases where duplication can destroy vectorization.", type = Debug)
    public static final OptionKey<Boolean> ConsiderVectorizableLoops = new OptionKey<>(true);

    @Option(help = "Increases the cost of duplicating control flow splits inside loops if they are not foldable." +
                   "The generally tend to complicate control flow and generate worse code in the backend.", type = Debug)
    public static final OptionKey<Boolean> PenalizeComplexLoopControlFlow = new OptionKey<>(true);

    @Option(help = "Maximum simulation-duplication iterations of the duplication optimization per invocation.", type = Debug)
    public static final OptionKey<Integer> MaxSimulationIterations = new OptionKey<>(2);

    @Option(help = "See MaxSimulationIterations.", type = Debug)
    public static final OptionKey<Integer> MaxSimulationIterationsHotCode = new OptionKey<>(4);

    @Option(help = "Maximum node cost graph size for duplication. If a graph is bigger duplication will stop.", type = Debug)
    public static final OptionKey<Integer> MaxGraphSizeNodeCost = new OptionKey<>(100_000);

    @Option(help = "Ignores low frequency branches during duplication.", type = Debug)
    public static final OptionKey<Double> DuplicationMinBranchFrequency = new OptionKey<>(0.66);

    @Option(help = "Ignores low frequency branches during simulation.", type = Debug)
    public static final OptionKey<Boolean> SimulationPruneUnlikelyBranches = new OptionKey<>(true);

    @Option(help = "Cost/Benefit heuristic for EE simulation-based code duplication: reduce cost by a constant factor when comparing with relative benefit.", type = Debug)
    public static final OptionKey<Integer> DuplicationCostReductionFactor = new OptionKey<>(64);

    @Option(help = "See DuplicationCostReductionFactor", type = Debug)
    public static final OptionKey<Integer> DuplicationCostReductionFactorHotCode = new OptionKey<>(256);

    @Option(help = "Performs Duplications as long as there is any sane improvement.", type = Debug)
    public static final OptionKey<Boolean> DuplicateALot = new OptionKey<>(false);

    @Option(help = "Ignores duplications with a bad benefit cost relation.", type = Debug)
    public static final OptionKey<Boolean> IgnoreBadDuplications = new OptionKey<>(true);

    @Option(help = "Tries to reduce duplication code size to the minimal amount of code.", type = Debug)
    public static final OptionKey<Boolean> MinimalRegions = new OptionKey<>(true);

    @Option(help = "Simulation can either only process fixed nodes or schedule the graph and also process floating nodes.", type = Debug)
    public static final OptionKey<Boolean> ScheduledDuplicationSimulation = new OptionKey<>(false);

    @Option(help = "Excludes compilations that MethodFilter.match this string from the duplication optimization.", type = Debug)
    public static final OptionKey<String> ExcludeFunctionFromDuplication = new OptionKey<>(null);

    @Option(help = "Enables (if Count is enabled) graph size tracking during every duplication iteration.", type = Debug)
    public static final OptionKey<Boolean> TrackGraphSizesInDuplication = new OptionKey<>(false);
    //@formatter:on

    public static final int EarlySimulationDepth = 3;

    public static final int LateSimulationDepth = 1;

}
