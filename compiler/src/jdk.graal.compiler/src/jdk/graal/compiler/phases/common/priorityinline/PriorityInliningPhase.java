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
package jdk.graal.compiler.phases.common.priorityinline;

import static jdk.graal.compiler.core.common.NativeImageSupport.inBuildtimeCode;

import java.util.ServiceLoader;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

/**
 * Priority inlining phase for environments that select policies through {@link ServiceLoader}.
 * Runtime-image users with a closed policy set should derive directly from
 * {@link AbstractPriorityInliningPhase}.
 */
public class PriorityInliningPhase extends AbstractPriorityInliningPhase {
    static {
        if (inBuildtimeCode()) {
            // Services cannot be loaded lazily in native images.
            ServiceLoader.load(PolicyFactory.class);
        }
    }

    /** Options for the public, service-loaded priority-inlining phase. */
    public static class Options {
        //@formatter:off
        @Option(help = "Increases or decreases the time spent exploring inlining opportunities under the assumption that " +
                       "more time results in better peak performance and less time reduces time to reach (a lower) peak " +
                       "performance. The value of the option is clamped between -1 and 1 inclusively. " +
                       "A value less than 0 reduces the exploration time and a value greater than 0 increases exploration time. " +
                       "Note that this option is only a heuristic and should be tuned for any specific application.", type = OptionType.User, stability = OptionStability.STABLE)
        public static final OptionKey<Double> TuneInlinerExploration = new OptionKey<>(0.0);

        @Option(help = "Use priority-based inlining.", type = OptionType.Debug, stability = OptionStability.STABLE)
        public static final OptionKey<Boolean> UsePriorityInlining = new OptionKey<>(true);

        @Option(help = "Max number of precise inlining peeling iterations.", type = OptionType.Debug)
        public static final OptionKey<Integer> MaxPriorityInliningPeelingIterations = new OptionKey<>(10);

        @Option(help = "Controls the likelihood of further exploring subtrees with a lot of code during inlining.", type = OptionType.Debug)
        public static final OptionKey<Double> CutoffCodeSizePenaltyCoefficient = new OptionKey<>(0.00001);

        @Option(help = "Controls the likelihood of exploring subtrees that already have a lot of code during inlining.", type = OptionType.Debug)
        public static final OptionKey<Double> CompilerNodePenaltyCoefficient = new OptionKey<>(0.006);

        @Option(help = "Denotes the call graph size that is considered medium size.", type = OptionType.Debug)
        public static final OptionKey<Integer> TypicalCallGraphSize = new OptionKey<>(100);

        @Option(help = "Reduces the likelihood of exploring call graph subtrees that are large.", type = OptionType.Debug)
        public static final OptionKey<Double> CallGraphSizePenaltyCoefficient = new OptionKey<>(0.01);

        @Option(help = "Reduces the likelihood of exploring call graphs with IR size much larger than the root.", type = OptionType.Debug)
        public static final OptionKey<Double> SmallRootIrPenaltyCoefficient = new OptionKey<>(0.02);

        @Option(help = "Reduces likelihood of spending a lot of time inlining when the IR is already large.", type = OptionType.Debug)
        public static final OptionKey<Double> RootSizePenaltyCoefficient = new OptionKey<>(0.1);

        @Option(help = "At what size of the root IR graph do we start to consider applying a exploration penalty.", type = OptionType.Debug)
        public static final OptionKey<Integer> RootSizePenaltyTypicalGraphSize = new OptionKey<>(3250);

        @Option(help = "Reduces the likelihood of exploring call graphs that have a lot of children below the root.", type = OptionType.Debug)
        public static final OptionKey<Double> LargeChildrenCountPenaltyCoefficient = new OptionKey<>(0.005);

        @Option(help = "Controls the maximum size of the call graph before ceasing inlining.", type = OptionType.Debug)
        public static final OptionKey<Integer> CallGraphSizeLimit = new OptionKey<>(1200);

        @Option(help = "Controls the maximum number of compiler nodes that can appear in the call graph", type = OptionType.Debug)
        public static final OptionKey<Integer> CallGraphCompilerNodeLimit = new OptionKey<>(35000);

        @Option(help = "Controls the maximum number of compiler nodes that can be inlined into the compiled method.", type = OptionType.Debug)
        public static final OptionKey<Integer> InlinedCompilerNodeLimit = new OptionKey<>(20000);

        @Option(help = "The slowness at which the expansion pressure grows with code size; the higher it is, the slower the pressure growth.", type = OptionType.Debug)
        public static final OptionKey<Integer> ExpansionInertiaBaseValue = new TunableOptionKey<>(TuneInlinerExploration, 550, false, 50, 800);

        @Option(help = "The extra slowness at which the expansion pressure grows with the code size, for each extra invoke node.", type = OptionType.Debug)
        public static final OptionKey<Integer> ExpansionInertiaInvokeBonus = new OptionKey<>(14);

        @Option(help = "The max slowness at which the expansion pressure grows with the code size.", type = OptionType.Debug)
        public static final OptionKey<Integer> ExpansionInertiaMax = new OptionKey<>(2000);

        @Option(help = "Specifies the typical graph size at which inlining pressure must start growing. ", type = OptionType.Expert)
        public static final OptionKey<Integer> TypicalGraphSize = new TunableOptionKey<>(TuneInlinerExploration, 3250, true, 2800, 4320);

        @Option(help = "The increase in estimated typical graph size after inlining, per each extra invoke.", type = OptionType.Debug)
        public static final OptionKey<Integer> TypicalGraphSizeInvokeBonus = new OptionKey<>(70);

        @Option(help = "The maximum in estimated inlined typical graph size.", type = OptionType.Debug)
        public static final OptionKey<Integer> TypicalGraphSizeMax = new OptionKey<>(15000);

        @Option(help = "The bonus applied to call nodes that can be fully inlined.", type = OptionType.Debug)
        public static final OptionKey<Double> InlineAllBonus = new OptionKey<>(1.0);

        @Option(help = "The decrease in call graph expansion pressure when there are few call nodes left to explore.", type = OptionType.Debug)
        public static final OptionKey<Double> ExpandAllProximityBonus = new OptionKey<>(6.0);

        @Option(help = "The inertia at which the expand-all proximity bonus decreases with the number of yet unexpanded nodes.", type = OptionType.Debug)
        public static final OptionKey<Double> ExpandAllProximityBonusInertia = new OptionKey<>(2.0);

        @Option(help = "The base target spending used to estimate the inlining threshold; the higher, the likelier it is to inline.", type = OptionType.Debug)
        public static final OptionKey<Integer> BaseTargetSpending = new OptionKey<>(120);

        @Option(help = "The maximum number of dispatches in guarded polymorphic inlining.")
        public static final OptionKey<Integer> MaxPolymorphicDispatches = new OptionKey<>(4);

        @Option(help = "The minimum probability for using a dispatch in guarded polymorphic inlining.")
        public static final OptionKey<Double> MinPolymorphicDispatchProbability = new OptionKey<>(0.1);

        @Option(help = "The coefficient used to compute the inlining threshold; higher value means more aggressive inlining.", type = OptionType.Debug)
        public static final OptionKey<Double> RelativeBenefitInliningCoefficient = new OptionKey<>(0.001);

        @Option(help = "Turn on partial escape analysis during inlining.")
        public static final OptionKey<Boolean> UsePriorityInliningPEA = new OptionKey<>(true);

        @Option(help = "The policy to use, must be empty for automatic resolution.")
        public static final OptionKey<String> PriorityInliningPolicy = new OptionKey<>("");

        @Option(help = "Comma-separated list of analysis policies for exploring the methods in the call graph and for inlining, empty for no policy.")
        public static final OptionKey<String> PriorityInliningTuningPolicy = new OptionKey<>("DomainSpecific");

        @Option(help = "Turn on graph caching.")
        public static final OptionKey<Boolean> UseGraphCache = new OptionKey<>(true);

        @Option(help = "Maximum number of expand/inline rounds during a single run of the phase.")
        public static final OptionKey<Integer> MaxRounds = new OptionKey<>(1000);

        //@formatter:on
    }

    public PriorityInliningPhase(CanonicalizerPhase canonicalizer, OptionValues options) {
        super(canonicalizer, options);
    }

    public PriorityInliningPhase(CanonicalizerPhase canonicalizer, OptionValues options, InliningProvider inliningProvider) {
        super(canonicalizer, options, inliningProvider);
    }

}
