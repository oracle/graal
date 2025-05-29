/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import jdk.graal.compiler.options.EnumOptionKey;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;

public enum SpectrePHTMitigations {
    None,
    AllTargets,
    GuardTargets,
    NonDeoptGuardTargets;

    public static class Options {
        // @formatter:off

        @Option(help = "Stop speculative execution on all branch targets with execution barrier instructions.", stability = OptionStability.STABLE)
        public static final OptionKey<Boolean> SpeculativeExecutionBarriers = new OptionKey<>(false) {

            @Override
            public Boolean getValue(OptionValues values) {
                // Do not use getValue to avoid an infinite recursion
                if (values.getMap().get(SpectrePHTBarriers) == AllTargets) {
                    return true;
                }
                return super.getValue(values);
            }

            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                if (values.containsKey(SpectrePHTBarriers)) {
                    Object otherValue = values.get(SpectrePHTBarriers);
                    if (newValue && otherValue != AllTargets || (!newValue && otherValue == AllTargets)) {
                        throw new IllegalArgumentException("SpectrePHTBarriers can be set to 'AllTargets' if and only if SpeculativeExecutionBarriers is enabled or unspecified.");
                    }
                }
            }
        };

        @Option(help = """
                       Selects a strategy to mitigate speculative bounds check bypass (also known as Spectre-PHT or Spectre V1).
                       The accepted values are:
                                         None - No mitigations are used in JIT compiled code.
                                   AllTargets - Speculative execution on all branch targets is
                                                stopped using speculative execution barrier instructions.
                                                This option is equivalent to setting SpeculativeExecutionBarriers to true.
                                 GuardTargets - Branch targets relevant to Java memory safety are instrumented with barrier instructions.
                                                This option has a lower performance impact than AllTargets.
                         NonDeoptGuardTargets - Same as GuardTargets, except that branches which deoptimize are not protected because they cannot be
                                                executed repeatedly and are thus less likely to be successfully exploited in an attack.


                       Note that all modes except "None" also instrument branch target blocks containing UNSAFE memory accesses with barrier instructions.""", type = OptionType.Expert)
        public static final EnumOptionKey<SpectrePHTMitigations> SpectrePHTBarriers = new EnumOptionKey<>(None) {
            private boolean isSpeculativeExecutionBarriersEnabled(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
                Object value = values.get(SpeculativeExecutionBarriers);
                return value != null && (boolean) value;
            }

            @Override
            public SpectrePHTMitigations getValue(OptionValues values) {
                if (isSpeculativeExecutionBarriersEnabled(values.getMap())) {
                    return AllTargets;
                }
                return super.getValue(values);
            }

            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, SpectrePHTMitigations oldValue, SpectrePHTMitigations newValue) {
                if (values.containsKey(SpeculativeExecutionBarriers)) {
                    boolean otherIsEnabled = isSpeculativeExecutionBarriersEnabled(values);
                    if (otherIsEnabled && newValue != AllTargets || (!otherIsEnabled && newValue == AllTargets)) {
                        throw new IllegalArgumentException("SpectrePHTBarriers can be set to 'AllTargets' if and only if SpeculativeExecutionBarriers is enabled or unspecified.");
                    }
                }
            }
        };

        @Option(help = "Masks indices to scope access to allocation size after bounds check.", type = OptionType.User, stability = OptionStability.STABLE)
        public static final OptionKey<Boolean> SpectrePHTIndexMasking = new OptionKey<>(false);
        // @formatter:on
    }
}
