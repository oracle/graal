/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.util.EnumSet;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class MoveGuardsLoopTest extends GraalCompilerTest {

    static class A {
        int x;
    }

    public static int multiExitGuardTest(int low, int high, A a) {
        int i = low;
        int res = 0;
        while (true) {
            if (GraalDirectives.injectBranchProbability(0.000001, i >= high)) {
                break;
            }
            if (GraalDirectives.sideEffect(i) > 42) {
                if (GraalDirectives.sideEffect(i) >= 43) {
                    A aNonNull = GraalDirectives.guardingNonNull(a);
                    res = aNonNull.x;
                    GraalDirectives.sideEffect(1);
                    break;
                } else {
                    /*
                     * A lot of unnecessary, optimizable code to trick the byte code parser to not
                     * create the loop exit here and pull it out of the >=43 if
                     */
                    A aNonNull = GraalDirectives.guardingNonNull(a);
                    res += aNonNull.x;
                    try {
                        for (int j = 0; j < 9; j++) {
                            if (j > 11) {
                                throw new Exception();
                            }
                        }
                    } catch (Exception e) {
                        continue;
                    }
                    break;
                }
            }
            i++;
        }
        GraalDirectives.sideEffect();
        return res;
    }

    @Test
    public void test01() {
        try {
            multiExitGuardTest(0, 41, null);
            multiExitGuardTest(0, 100, new A());
        } catch (Exception e) {
            Assert.fail("Must not see an exception " + e);
        }

        try {
            multiExitGuardTest(0, 50, null);
            Assert.fail("Must throw NPE exception");
        } catch (Exception e) {
            // Good
        }

        ResolvedJavaMethod method = getResolvedJavaMethod("multiExitGuardTest");
        executeActualCheckDeopt(new OptionValues(getInitialOptions(), ConditionalEliminationPhase.Options.MoveGuardsUpwards, true), method, EnumSet.of(DeoptimizationReason.NullCheckException), null,
                        0, 41, null);
        resetCache();
        executeActualCheckDeopt(new OptionValues(getInitialOptions(), ConditionalEliminationPhase.Options.MoveGuardsUpwards, false), method, EnumSet.of(DeoptimizationReason.NullCheckException), null,
                        0, 41, null);
    }

    public static int multiExitGuardTest2(int low, A a) {
        int i = low;
        int res = 0;
        while (true) {
            if (GraalDirectives.sideEffect(i) > 42) {
                if (GraalDirectives.sideEffect(i) >= 43) {
                    A aNonNull = GraalDirectives.guardingNonNull(a);
                    res = aNonNull.x;
                    GraalDirectives.sideEffect(1);
                    break;
                } else {
                    A aNonNull = GraalDirectives.guardingNonNull(a);
                    res += aNonNull.x;
                    continue;
                }
            }
            i++;
        }
        GraalDirectives.sideEffect();
        return res;
    }

    @Test
    public void test02() {
        // check that we deopt, in this case we should optimize the guard into and before the loop
        // since it will unconditionally deopt
        ResolvedJavaMethod method = getResolvedJavaMethod("multiExitGuardTest2");
        executeActualCheckDeopt(new OptionValues(getInitialOptions(), ConditionalEliminationPhase.Options.MoveGuardsUpwards, true), method, EnumSet.noneOf(DeoptimizationReason.class), null, 0, null);
        resetCache();
        executeActualCheckDeopt(new OptionValues(getInitialOptions(), ConditionalEliminationPhase.Options.MoveGuardsUpwards, false), method, EnumSet.noneOf(DeoptimizationReason.class), null, 0, null);
        resetCache();
    }

}
