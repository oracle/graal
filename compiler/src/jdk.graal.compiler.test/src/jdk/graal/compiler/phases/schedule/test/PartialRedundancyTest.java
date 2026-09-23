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
package jdk.graal.compiler.phases.schedule.test;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.phases.schedule.PartialRedundancySchedulePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;

public class PartialRedundancyTest extends GraalCompilerTest {

    public static int intSideEffect;

    public static int simplestSnippet(int a, int b, int c) {
        int p = 0;
        if (GraalDirectives.injectBranchProbability(0.9, a > b)) {
            intSideEffect = 1;
        } else if (GraalDirectives.injectBranchProbability(0.9, a == b)) {
            if (GraalDirectives.injectBranchProbability(0.1, a < b)) {
                p = a * b;
                intSideEffect = p;
            } else {
                p = a * b;
                intSideEffect = p;
            }
        }
        if (GraalDirectives.injectBranchProbability(0.9, a > b)) {
            intSideEffect = 2;
        } else {
            p = a * b;
            intSideEffect = p;
        }
        intSideEffect = c;
        return p;
    }

    public static int depthBoundedSnippet(int a, int b, int c) {
        int p = 0;
        if (GraalDirectives.injectBranchProbability(0.9, a > b)) {
            intSideEffect = 1;
        } else if (GraalDirectives.injectBranchProbability(0.9, a == b)) {
            if (GraalDirectives.injectBranchProbability(0.1, a < b)) {
                p = a * b;
                intSideEffect = p;
            } else {
                p = a * b;
                intSideEffect = p;
            }
        }
        if (GraalDirectives.injectBranchProbability(0.9, a > b)) {
            intSideEffect = 2;
        } else {
            p = a * b;
            GraalDirectives.blackhole(p);
            switch (intSideEffect) {
                case 0:
                    switch (intSideEffect) {
                        case 0:
                            switch (intSideEffect) {
                                case 0:
                                    if (intSideEffect > a) {
                                        GraalDirectives.blackhole(p);
                                        if (intSideEffect > a) {
                                            GraalDirectives.blackhole(p);
                                            if (intSideEffect > a) {
                                                GraalDirectives.blackhole(p);
                                                if (intSideEffect > a) {
                                                    GraalDirectives.blackhole(p);
                                                    if (intSideEffect > a) {
                                                        GraalDirectives.blackhole(p);
                                                        if (intSideEffect > a) {
                                                            GraalDirectives.blackhole(p);
                                                            if (intSideEffect > a) {
                                                                GraalDirectives.blackhole(p);
                                                                if (intSideEffect > a) {
                                                                    GraalDirectives.blackhole(p);
                                                                    if (intSideEffect > a) {
                                                                        GraalDirectives.blackhole(p);
                                                                        GraalDirectives.blackhole(p);
                                                                        if (intSideEffect > a) {
                                                                            GraalDirectives.blackhole(p);
                                                                            GraalDirectives.blackhole(p);
                                                                            if (intSideEffect > a) {
                                                                                GraalDirectives.blackhole(p);
                                                                                GraalDirectives.blackhole(p);
                                                                                if (intSideEffect > a) {
                                                                                    GraalDirectives.blackhole(p);
                                                                                    GraalDirectives.blackhole(p);
                                                                                    if (intSideEffect > a) {
                                                                                        GraalDirectives.blackhole(p);
                                                                                        GraalDirectives.blackhole(p);
                                                                                        if (intSideEffect > a) {
                                                                                            GraalDirectives.blackhole(p);
                                                                                            GraalDirectives.blackhole(p);
                                                                                            if (intSideEffect > a) {
                                                                                                GraalDirectives.blackhole(p);
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    break;

                                default:
                                    break;
                            }
                            break;

                        default:
                            break;
                    }
                    break;

                default:
                    break;
            }

        }
        intSideEffect = c;
        return p;
    }

    @Test
    public void test0() {
        StructuredGraph graph = parseEager("simplestSnippet", AllowAssumptions.YES);
        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS).apply(graph, getDefaultHighTierContext());
        new PartialRedundancySchedulePhase().apply(graph, getDefaultHighTierContext());
    }

    @Test
    public void test1() {
        StructuredGraph graph = parseEager("depthBoundedSnippet", AllowAssumptions.YES);
        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS).apply(graph, getDefaultHighTierContext());
        new PartialRedundancySchedulePhase().apply(graph, getDefaultHighTierContext());
    }
}
