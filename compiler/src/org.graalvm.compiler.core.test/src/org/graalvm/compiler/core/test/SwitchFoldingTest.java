/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.junit.Test;

public class SwitchFoldingTest extends GraalCompilerTest {

    private static final String REFERENCE_SNIPPET = "referenceSnippet";

    public static int referenceSnippet(int a) {
        switch (a) {
            case 0:
                return 10;
            case 1:
                return 5;
            case 2:
                return 3;
            case 3:
                return 11;
            case 4:
                return 14;
            case 5:
                return 2;
            case 6:
                return 1;
            case 7:
                return 0;
            case 8:
                return 7;
            default:
                return 6;
        }
    }

    public static int test1Snippet(int a) {
        if (a == 0) {
            return 10;
        } else if (a == 1) {
            return 5;
        } else if (a == 2) {
            return 3;
        } else if (a == 3) {
            return 11;
        } else if (a == 4) {
            return 14;
        } else if (a == 5) {
            return 2;
        } else if (a == 6) {
            return 1;
        } else if (a == 7) {
            return 0;
        } else if (a == 8) {
            return 7;
        } else {
            return 6;
        }
    }

    @Test
    public void test1() {
        test("test1Snippet");
    }

    public static int test2Snippet(int a) {
        switch (a) {
            case 0:
                return 10;
            case 1:
                return 5;
            case 2:
                return 3;
            case 3:
                return 11;
            case 4:
                return 14;
            default:
                switch (a) {
                    case 5:
                        return 2;
                    case 6:
                        return 1;
                    case 7:
                        return 0;
                    case 8:
                        return 7;
                    default:
                        return 6;
                }
        }
    }

    @Test
    public void test2() {
        test("test2Snippet");
    }

    public static int test3Snippet(int a) {
        switch (a) {
            case 0:
                return 10;
            default:
                switch (a) {
                    case 1:
                        return 5;
                    default:
                        switch (a) {
                            case 2:
                                return 3;
                            default:
                                switch (a) {
                                    case 3:
                                        return 11;
                                    default:
                                        switch (a) {
                                            case 4:
                                                return 14;
                                            default:
                                                switch (a) {
                                                    case 5:
                                                        return 2;
                                                    default:
                                                        switch (a) {
                                                            case 6:
                                                                return 1;
                                                            default:
                                                                switch (a) {
                                                                    case 7:
                                                                        return 0;
                                                                    default:
                                                                        switch (a) {
                                                                            case 8:
                                                                                return 7;
                                                                            default:
                                                                                return 6;
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

    @Test
    public void test3() {
        test("test3Snippet");
    }

    public static int test4Snippet(int a) {
        switch (a) {
            case 0:
                return 10;
            case 1:
                return 5;
            case 2:
                return 3;
            case 3:
                return 11;
            case 4:
                return 14;
            case 5:
                return 2;
            case 6:
                return 1;
            default:
                if (a == 7) {
                    return 0;
                } else if (a == 8) {
                    return 7;
                } else {
                    return 6;
                }
        }
    }

    @Test
    public void test4() {
        test("test4Snippet");
    }

    private void test(String snippet) {
        StructuredGraph graph = parseEager(snippet, StructuredGraph.AllowAssumptions.YES);
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
        new CanonicalizerPhase().apply(graph, getProviders());
        StructuredGraph referenceGraph = parseEager(REFERENCE_SNIPPET, StructuredGraph.AllowAssumptions.YES);
        assertEquals(referenceGraph, graph);
    }
}
