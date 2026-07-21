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
package jdk.graal.compiler.duplication.test;

import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.duplication.phases.PullThroughPhiPhase;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationPhase;
import jdk.graal.compiler.duplication.phases.simulation.FixedDuplicationSimulationConfig;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;

public class DuplicationPhiCreationTest extends GraalCompilerTest {

    public static Object field;

    static final class A {
        int a;
    }

    // parameter used to indicate jmp target
    public static void sideEffect(@SuppressWarnings("unused") int i) {
        field = null;
    }

    public static int hardPattern(int a, int b, int c, int d, int e) {
        A pe = new A();
        if (e == 0) {
            pe.a = 1;
            field = 1;
        } else {
            pe.a = 2;
            field = 2;
        }

        int phi1 = 0;
        int phi2 = 0;
        int phi3 = 0;
        int phi4 = 0;
        int phi5 = 0;
        b0: {
            b1: {
                if (a == 0) {
                    phi1 = a;
                    pe.a = a + b + c;
                    if (b == 0) {
                        phi2 = b;
                        if (c == 0) {
                            phi3 = c;
                            // merge with other branch by breaking b1
                            sideEffect(1);
                            break b1;
                        }
                    }
                }
                if (a > 0) {
                    pe.a = d;
                    phi4 = 4;
                    // we want to merge with sideEffect(1) thus we break b1
                    sideEffect(2);
                    break b1;
                } else {
                    phi5 = e;
                    pe.a = c;
                    // we do not want to merge with sideeffect(1) or (sideffect(2) thus we break b0
                    break b0;
                }
            }
            // do sth
            int res = a * b * c + pe.a;
            sideEffect(3);
            // we repeat everything
            b0_0: {
                b1_1: {
                    if (a == 0) {
                        if (pe.a == 0) {
                            if (c == 0) {
                                // merge with other branch by breaking b1
                                pe.a = a + res;
                                sideEffect(1);
                                break b0;
                            }
                        }
                    }
                    pe.a = res;
                    if (pe.a - a > 0) {
                        res += b;
                        // we want to merge with sideEffect(1) thus we break b1
                        if (res > b) {
                            // escape the object
                            field = pe;
                        }
                        sideEffect(2);
                        break b1_1;
                    } else {
                        // we do not want to merge with sideeffect(1) or (sideffect(2) thus we break
                        // b0
                        break b0_0;
                    }
                }
                pe.a = res;
                // do sth
                sideEffect(3);
            }
        }

        // we merge everything
        return pe.a * phi1 * phi2 * phi3 * phi4 * phi5;
    }

    public static int breakTest(int p) {
        A a = new A();
        if (field == null) {
            if (p == 0) {
                a.a = 1;
                field = 1;
            } else {
                a.a = 2;
                field = p;
            }
        }

        int s = a.a * p;

        // what follows is the usage that penetrates generatePhis
        int res = 0;
        block_b3: {
            block_b2: {
                block_b1: {
                    if (p > 10) {
                        if (p * 2 > 10) {
                            res = 11;
                            break block_b1;
                        } else {
                            res = 1;
                            break block_b2;
                        }
                    } else {
                        if (p * 2 > 10) {
                            res = 4 * a.a;
                            break block_b1;
                        } else {
                            field = a;
                            res = 12;
                            break block_b2;
                        }
                    }
                }// end of b1
                field = a.a * res;
                break block_b3;
            }// end of b2
            field = res * s;
        }// end of b3
        field = p * a.a;
        return res * s;
    }

    public static int breakTestSimple(int p) {
        A a = new A();
        if (p == 0) {
            a.a = 1;
            field = 1;
        } else {
            a.a = 2;
            field = p;
        }

        int s = a.a * p;

        // what follows is the usage that penetrates generatePhis
        int res = 0;
        block_b3: {
            block_b2: {
                block_b1: {
                    if (p * 2 > 10) {
                        res = 11;
                        break block_b1;
                    } else {
                        res = 1;
                        break block_b2;
                    }
                }// end of b1
                field = a.a * res;
                break block_b3;
            }// end of b2
            field = res * s;
        }// end of b3
        field = p * a.a;
        return res * s;
    }

    public static int castFromSideEffect() {
        return ((Integer) field).intValue();
    }

    public static int intSideEffect;

    public static int testCachingOfPhiTrees(int a, int b, int p) {
        int phi = 0;
        if (a == 0) {
            phi = a + castFromSideEffect();
            field = phi;
        } else {
            phi = b + castFromSideEffect();
            field = phi;
        }
        if (b == 0) {
            if (a > 0) {
                field = a;
            } else {
                field = b;
            }
            intSideEffect = phi;
            intSideEffect = phi;
        } else {
            if (a > 0) {
                field = a;
            } else {
                field = b;
            }
            intSideEffect = phi;
            intSideEffect = phi;
        }

        // what follows is the usage that penetrates generatePhis
        int res = 0;
        block_b3: {
            block_b2: {
                block_b1: {
                    if (p * 2 > 10) {
                        res = 11;
                        break block_b1;
                    } else {
                        res = 1;
                        break block_b2;
                    }
                }// end of b1
                intSideEffect = phi;
                break block_b3;
            }// end of b2
            intSideEffect = phi;
        }// end of b3
        intSideEffect = phi;
        return res;
    }

    @Test
    public void test0() {
        testGraph("breakTestSimple", true);
    }

    @Test
    public void test1() {
        testGraph("hardPattern", true);
    }

    @Test
    public void test2() {
        testGraph("breakTest", true);
    }

    @Test
    public void test3() {
        testGraph("testCachingOfPhiTrees", false);
    }

    private void testGraph(String snippet, boolean prePhases) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        new DisableOverflownCountedLoopsPhase().apply(graph);
        CanonicalizerPhase c = createCanonicalizerPhase();
        if (prePhases) {
            new PartialEscapePhase(true, createCanonicalizerPhase(), graph.getOptions()).apply(graph, getDefaultHighTierContext());
            new PullThroughPhiPhase(c).apply(graph, getDefaultHighTierContext());
        }
        StructuredGraph other = (StructuredGraph) graph.copy(graph.getDebug());
        new DuplicationPhase(FixedDuplicationSimulationConfig.defaultForDepth(DuplicationOptions.EarlySimulationDepth), true, true,
                        DuplicationPhase.FACTORS_INCLUDING_PEA, c,
                        other.getOptions()).apply(other,
                                        getDefaultHighTierContext());
    }
}
