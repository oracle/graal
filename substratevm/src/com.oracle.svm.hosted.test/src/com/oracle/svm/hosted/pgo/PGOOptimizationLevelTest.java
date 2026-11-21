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
package com.oracle.svm.hosted.pgo;

import java.util.List;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.core.SubstrateOptions;

import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

/**
 * {@code --pgo-instrument} raises the default optimization level to {@code -O3}. The builder sets
 * {@code -O} to its default before it parses the command line, so these tests do the same, then
 * apply the options in command-line order.
 */
public class PGOOptimizationLevelTest {

    private static final List<Consumer<EconomicMap<OptionKey<?>, Object>>> PGO_OPTIONS = List.of(
                    values -> PGOInstrumentationFeature.Options.PGOInstrument.update(values, true));

    private static EconomicMap<OptionKey<?>, Object> builderDefaults() {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        SubstrateOptions.Optimize.update(values, SubstrateOptions.Optimize.getDefaultValue());
        return values;
    }

    @Test
    public void defaultLevelIsRaisedToO3() {
        for (Consumer<EconomicMap<OptionKey<?>, Object>> pgoOption : PGO_OPTIONS) {
            EconomicMap<OptionKey<?>, Object> values = builderDefaults();
            pgoOption.accept(values);
            Assert.assertEquals("3", values.get(SubstrateOptions.Optimize));
            /* -O3's own side effect is applied as for an explicit -O3. */
            Assert.assertEquals(Boolean.TRUE, values.get(SubstrateOptions.ReduceImplicitExceptionStackTraceInformation));
        }
    }

    @Test
    public void levelGivenAfterwardsWins() {
        for (Consumer<EconomicMap<OptionKey<?>, Object>> pgoOption : PGO_OPTIONS) {
            EconomicMap<OptionKey<?>, Object> values = builderDefaults();
            pgoOption.accept(values);
            SubstrateOptions.Optimize.update(values, "2");
            Assert.assertEquals("2", values.get(SubstrateOptions.Optimize));
        }
    }

    @Test
    public void otherLevelGivenBeforeIsKept() {
        for (Consumer<EconomicMap<OptionKey<?>, Object>> pgoOption : PGO_OPTIONS) {
            for (String level : new String[]{"0", "1", "b", "s"}) {
                EconomicMap<OptionKey<?>, Object> values = builderDefaults();
                SubstrateOptions.Optimize.update(values, level);
                pgoOption.accept(values);
                Assert.assertEquals(level, values.get(SubstrateOptions.Optimize));
            }
        }
    }

    @Test
    public void optionsThatEnableNoPGOLeaveTheLevel() {
        EconomicMap<OptionKey<?>, Object> values = builderDefaults();
        PGOInstrumentationFeature.Options.PGOInstrument.update(values, false);
        Assert.assertEquals(SubstrateOptions.Optimize.getDefaultValue(), values.get(SubstrateOptions.Optimize));
    }
}
