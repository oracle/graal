/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.cai;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.shared.option.HostedOptionKey;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;

public class CAICompileQueueOptions {

    @Option(help = "Use PGO sampling data to do context aware inlining")//
    public static final HostedOptionKey<Boolean> ContextAwareInlining = new HostedOptionKey<>(false) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            if (newValue) {
                SubstrateOptions.AOTPriorityInline.update(values, true);
            }
        }
    };

    @Option(help = "Perform more aggressive optimization on compilation units deemed to be hot.", type = OptionType.Debug) //
    public static final HostedOptionKey<Boolean> CAIAggressivelyOptimizeHot = new HostedOptionKey<>(true);

    @Option(help = "Apply sampling based profiles to candidates for inlining before doing the inlining.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> CAIApplyProfilesWhileExpanding = new HostedOptionKey<>(true);

    @Option(help = "Apply a bonus to the priority of hot cutoff nodes during exploration. Set to 0 to disable.", type = OptionType.Debug)//
    public static final HostedOptionKey<Integer> CAIHotBonusWhileExpanding = new HostedOptionKey<>(50);

    @Option(help = "Apply a bonus to the priority of hot nodes during inlining. Set to 0 to disable.", type = OptionType.Debug)//
    public static final HostedOptionKey<Integer> CAIHotBonusWhileInlining = new HostedOptionKey<>(0);

    @Option(help = "Determine if the optimizer should run aggressive optimizations on cold code (rarely executed code during the PGO run).")//
    public static final HostedOptionKey<Boolean> CAIAggressiveColdCodeOptimizations = new HostedOptionKey<>(false);

    @Option(help = "Determines if loop optimizations should be applied to cold code.")//
    public static final HostedOptionKey<Boolean> CAIOptimizeLoopsInColdCode = new HostedOptionKey<>(false);

    @Option(help = "Determines if partial escape analysis should be applied to cold code.")//
    public static final HostedOptionKey<Boolean> CAIPartialEscapeAnalyzeColdCode = new HostedOptionKey<>(false);

    @Option(help = "Maximum number of invocations of a non-sampled method beyond which that method will be considered cold. " +
                    "Will result in less optimizations performed on such compilation units.", deprecated = true, deprecationMessage = "please use '-H:CAIRegularCompilationCoverage=' to fine tune the regular-cold method classification")//
    public static final HostedOptionKey<Integer> CAIColdCodeMaxInvocations = new HostedOptionKey<>(3);

    @Option(help = "Control the portion of the method combined count (call count + conditional profiles) distribution that is used to classify regular and cold methods. " +
                    "The distribution is split using this value: smaller values will make more methods cold which will decrease the overall size, but also affect the performance.")//
    public static final HostedOptionKey<Double> CAIRegularCompilationCoverage = new HostedOptionKey<>(0.999, (optionKey) -> {
        if (optionKey.hasBeenSet() && CAIColdCodeMaxInvocations.hasBeenSet()) {
            UserError.invalidOptionValue(optionKey, String.valueOf(optionKey.getValue()),
                            "-H:%s and -H:%s cannot be used together since they represent different strategies for determining cold methods. Please only use one of the two options".formatted(
                                            optionKey.getName(), CAIColdCodeMaxInvocations.getName()));
        }
    });

    @Option(help = "Print the cold methods (only works if -H:CAIRegularCompilationCoverage is used).")//
    public static final HostedOptionKey<Boolean> CAIPrintColdMethods = new HostedOptionKey<>(false, (optionKey) -> {
        if (optionKey.hasBeenSet() && optionKey.getValue() && (!CAIRegularCompilationCoverage.hasBeenSet() && CAIColdCodeMaxInvocations.hasBeenSet())) {
            UserError.invalidOptionValue(optionKey, optionKey.getValue(),
                            "-H:+%s can only be used when -H:%s is used for determining cold methods. Please use the specified classification option".formatted(optionKey.getName(),
                                            CAIRegularCompilationCoverage.getName()));
        }
    });

    @Option(help = "Control which cold methods will be printed (when -H:+CAIPrintColdMethods is used).")//
    public static final HostedOptionKey<String> CAIPrintColdMethodsFilter = new HostedOptionKey<>(null, (optionKey) -> {
        if (optionKey.hasBeenSet() && !CAIPrintColdMethods.hasBeenSet()) {
            UserError.invalidOptionValue(optionKey, String.valueOf(optionKey.getValue()),
                            "-H:%s can only be used when printing cold methods (using the -H:+%s option). Please enable cold method printing first".formatted(optionKey.getName(),
                                            CAIPrintColdMethods.getName()));
        }
    });

    @Option(help = "Whether to print the hot compilation units.")//
    public static final HostedOptionKey<Boolean> CAIPrintCallees = new HostedOptionKey<>(false);

    @Option(help = "The minimal percentage of the total time that has to be spent in a method invoked from a calling-context to consider them as hot.")//
    public static final HostedOptionKey<Double> CAIHotContextsRatio = new HostedOptionKey<>(0.05);

    @Option(help = "SmallRootIrPenaltyCoefficient value for the hot compilations.")//
    public static final HostedOptionKey<Double> HotCompilationSmallRootIrPenaltyCoefficient = new HostedOptionKey<>(0.0);

    @Option(help = "LargeChildrenCountPenaltyCoefficient value for the hot compilations.")//
    public static final HostedOptionKey<Double> HotCompilationLargeChildrenCountPenaltyCoefficient = new HostedOptionKey<>(0.0);

    @Option(help = "CompilerNodePenaltyCoefficient value for the hot compilations.")//
    public static final HostedOptionKey<Double> HotCompilationCompilerNodePenaltyCoefficient = new HostedOptionKey<>(0.00);

    @Option(help = "CutoffCodeSizePenaltyCoefficient value for the hot compilations.")//
    public static final HostedOptionKey<Double> HotCompilationCutoffCodeSizePenaltyCoefficient = new HostedOptionKey<>(0.0);

    @Option(help = "RelativeBenefitInliningCoefficient value for the hot compilations.")//
    public static final HostedOptionKey<Double> HotCompilationRelativeBenefitInliningCoefficient = new HostedOptionKey<>(0.0002);

    @Option(help = "MinPolymorphicDispatchProbability value for the hot compilations.")//
    public static final HostedOptionKey<Double> HotCompilationMinPolymorphicDispatchProbability = new HostedOptionKey<>(0.09);

    @Option(help = "TypicalGraphSize value for the hot compilations.")//
    public static final HostedOptionKey<Integer> HotCompilationTypicalGraphSize = new HostedOptionKey<>(4320);

    @Option(help = "TypicalGraphSizeInvokeBonus value for the hot compilations.")//
    public static final HostedOptionKey<Integer> HotCompilationTypicalGraphSizeInvokeBonus = new HostedOptionKey<>(20);

    @Option(help = "ExpansionInertiaBaseValue value for the hot compilations.")//
    public static final HostedOptionKey<Integer> HotCompilationExpansionInertiaBaseValue = new HostedOptionKey<>(550);

    @Option(help = "BaseTargetSpending value for the hot compilations.")//
    public static final HostedOptionKey<Integer> HotCompilationBaseTargetSpending = new HostedOptionKey<>(300);

    @Option(help = "MaxPolymorphicDispatches value for the hot compilations.")//
    public static final HostedOptionKey<Integer> HotCompilationMaxPolymorphicDispatches = new HostedOptionKey<>(3);

    @Option(help = "Print a summary of the distribution of hot/regular/cold compilation units.")//
    public static final HostedOptionKey<Boolean> CAIPrintTemperatureSummary = new HostedOptionKey<>(false);

    @Option(help = "For each compilation unit print whether its compiled as hot/regular/cold.")//
    public static final HostedOptionKey<Boolean> CAIPrintTemperatureDetails = new HostedOptionKey<>(false);
}
