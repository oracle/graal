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
package com.oracle.svm.core.imagelayer;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.BundleMember;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;

public class LayeredImageOptions {
    public static final String LAYER_OPTION_PREFIX = "-H:Layer"; // "--layer"
    public static final String LAYER_CREATE_OPTION = LAYER_OPTION_PREFIX + "Create"; // "-create"

    // @APIOption(name = LAYER_CREATE_OPTION) // use when non-experimental
    @Option(help = "Build a Native Image layer. See NativeImageLayers.md for more info.")//
    public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> LayerCreate = new HostedOptionKey<>(AccumulatingLocatableMultiOptionValue.Strings.build());

    // public static final String LAYER_USE_OPTION = LAYER_OPTION_PREFIX + "-use";
    // @APIOption(name = LAYER_USE_OPTION) // use when non-experimental
    @Option(help = "Build an image based on a Native Image layer.")//
    @BundleMember(role = BundleMember.Role.Input) //
    public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Paths> LayerUse = new HostedOptionKey<>(AccumulatingLocatableMultiOptionValue.Paths.build());

    @Option(help = "Mark singleton as application layer only")//
    public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> ApplicationLayerOnlySingletons = new HostedOptionKey<>(AccumulatingLocatableMultiOptionValue.Strings.build());

    @Option(help = "Register class as being initialized in the app layer.")//
    public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> ApplicationLayerInitializedClasses = new HostedOptionKey<>(
                    AccumulatingLocatableMultiOptionValue.Strings.build());
    @Option(help = "Persist and reload all graphs across layers. If false, graphs defined in the base layer can be reparsed by the current layer and inlined before analysis, " +
                    "but will not be inlined after analysis has completed via our other inlining infrastructure")//
    public static final HostedOptionKey<Boolean> UseSharedLayerGraphs = new HostedOptionKey<>(true) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            if (!newValue) {
                UseSharedLayerStrengthenedGraphs.update(values, false);
            }
        }
    };

    @Option(help = "Persist and reload strengthened graphs across layers. If false, inlining after analysis will be disabled")//
    public static final HostedOptionKey<Boolean> UseSharedLayerStrengthenedGraphs = new HostedOptionKey<>(false) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            if (newValue) {
                UserError.guarantee(UseSharedLayerStrengthenedGraphs.getValueOrDefault(values),
                                "UseSharedLayerStrengthenedGraph is a subset of UseSharedLayerGraphs, so the former cannot be enabled alone.");
            } else {
                SubstrateOptions.NeverInline.update(values, "SubstrateStringConcatHelper.simpleConcat");
            }
        }
    };

    public static class LayeredImageDiagnosticOptions {
        @Option(help = "Log discrepancies between layered open world type information. This is an experimental option which will be removed.")//
        public static final HostedOptionKey<Boolean> LogLayeredDispatchTableDiscrepancies = new HostedOptionKey<>(false);

        @Option(help = "Throw an error when there are discrepancies between layered open world type information. This is an experimental option which will be removed.")//
        public static final HostedOptionKey<Boolean> AbortOnLayeredDispatchTableDiscrepancies = new HostedOptionKey<>(false);

        @Option(help = "Log unique names which do not match across layers. This is an experimental option which will be removed.") //
        public static final HostedOptionKey<Boolean> LogUniqueNameInconsistencies = new HostedOptionKey<>(false);

        @Option(help = "Enables logging of failed hash code injection", type = OptionType.Debug) //
        public static final HostedOptionKey<Boolean> LogHashCodeInjectionFailure = new HostedOptionKey<>(false);

        @Option(help = "Enables logging on various loading failures", type = OptionType.Debug) //
        public static final HostedOptionKey<Boolean> LogLoadingFailures = new HostedOptionKey<>(false);

        @Option(help = "Throws an exception on potential type conflict during heap persisting if enabled", type = OptionType.Debug) //
        public static final HostedOptionKey<Boolean> AbortOnNameConflict = new HostedOptionKey<>(false);

        @Option(help = "Logs potential type conflict during heap persisting if enabled", type = OptionType.Debug) //
        public static final HostedOptionKey<Boolean> LogOnNameConflict = new HostedOptionKey<>(false);

        @Option(help = "Enables logging of layered archiving")//
        public static final HostedOptionKey<Boolean> LogLayeredArchiving = new HostedOptionKey<>(false);

        @Option(help = "Perform strict checking of options used for layered image build.")//
        public static final HostedOptionKey<Boolean> LayerOptionVerification = new HostedOptionKey<>(true);

        @Option(help = "Provide verbose output of difference in builder options between layers.")//
        public static final HostedOptionKey<Boolean> LayerOptionVerificationVerbose = new HostedOptionKey<>(false);

        @Option(help = "Emit a warning instead of an error when a runtime option is set within a shared layer.")//
        public static final HostedOptionKey<Boolean> WarnOnSharedLayerSetRuntimeOptions = new HostedOptionKey<>(false);
    }
}
