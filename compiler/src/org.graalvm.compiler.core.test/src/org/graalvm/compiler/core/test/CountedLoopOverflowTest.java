/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import java.util.Collections;
import java.util.EnumSet;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.junit.Test;

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Test to verify that overflowing loops are not detected as counted. This is done by using a
 * speculation for the overflow which ensures a subsequent recompile will not detect a loop as
 * counted.
 *
 */
public class CountedLoopOverflowTest extends GraalCompilerTest {
    private final SpeculationLog speculationLog;

    public CountedLoopOverflowTest() {
        speculationLog = getCodeCache().createSpeculationLog();
    }

    public static void snippetDown() {
        int i = Integer.MIN_VALUE + 56;
        while (true) {
            if (i > Integer.MIN_VALUE) {
                GraalDirectives.sideEffect(i);
                i = Math.subtractExact(i, 8);
                continue;
            }
            break;
        }
    }

    public static void snippetDownUnsigned() {
        int i = 56;
        while (true) {
            if (Integer.compareUnsigned(i, 8) >= 0) {
                GraalDirectives.sideEffect(i);
                i = Math.subtractExact(i, 8);
                continue;
            }
            break;
        }
    }

    public static void snippetUp() {
        int i = Integer.MAX_VALUE - 56;
        while (true) {
            if (i < Integer.MAX_VALUE) {
                GraalDirectives.sideEffect(i);
                i = Math.addExact(i, 8);
                continue;
            }
            break;
        }
    }

    @Override
    protected SpeculationLog getSpeculationLog() {
        speculationLog.collectFailedSpeculations();
        return speculationLog;
    }

    @Override
    protected OptimisticOptimizations getOptimisticOptimizations() {
        return OptimisticOptimizations.ALL;
    }

    @Test
    public void testDownOverflow() {
        try {
            snippetDown();
        } catch (Throwable t) {
            fail("Caught exception that should not be thrown %s", t.getMessage());
        }
        ResolvedJavaMethod method = getResolvedJavaMethod("snippetDown");
        // first should deopt with a failed speculation, second not
        executeActualCheckDeopt(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false), method, Collections.emptySet(), null);
        executeActualCheckDeopt(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false), method, EnumSet.allOf(DeoptimizationReason.class), null);
    }

    @Test
    public void testDownOverflowUnsigned() {
        try {
            snippetDownUnsigned();
        } catch (Throwable t) {
            fail("Caught exception that should not be thrown %s", t.getMessage());
        }
        ResolvedJavaMethod method = getResolvedJavaMethod("snippetDownUnsigned");
        // first should deopt with a failed speculation, second not
        executeActualCheckDeopt(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false), method, Collections.emptySet(), null);
        executeActualCheckDeopt(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false), method, EnumSet.allOf(DeoptimizationReason.class), null);
    }

    @Test
    public void testUpOverflow() {
        try {
            snippetUp();
        } catch (Throwable t) {
            fail("Caught exception that should not be thrown %s", t.getMessage());
        }
        ResolvedJavaMethod method = getResolvedJavaMethod("snippetUp");
        // first should deopt with a failed speculation, second not
        executeActualCheckDeopt(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false), method, Collections.emptySet(), null);
        executeActualCheckDeopt(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false), method, EnumSet.allOf(DeoptimizationReason.class), null);
    }
}
