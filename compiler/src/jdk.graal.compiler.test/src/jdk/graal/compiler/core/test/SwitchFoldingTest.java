/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.MTD_void;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SwitchFoldingTest extends GraalCompilerTest implements CustomizedBytecodePattern {

    private static final String REFERENCE_SNIPPET = "referenceSnippet";
    private static final String REFERENCE_SNIPPET_2 = "reference2Snippet";
    private static final String REFERENCE_SNIPPET_3 = "reference3Snippet";
    private static final String REFERENCE_SNIPPET_4 = "reference4Snippet";
    private static final String REFERENCE_SNIPPET_5 = "reference5Snippet";

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

    public static int reference2Snippet(int a) {
        switch (a) {
            case 0:
                return 4;
            case 1:
            case 2:
                return 1;
            case 3:
                return 6;
            default:
                return 7;
        }
    }

    public static int reference3Snippet(int a) {
        switch (a) {
            case 0:
                return 4;
            case 1:
            case 2:
            case 4:
                return 6;
            case 6:
            case 7:
            default:
                return 7;
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
        test1("test1Snippet");
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
        test1("test2Snippet");
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
        test1("test3Snippet");
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
        test1("test4Snippet");
    }

    public static int test5Snippet(int a) {
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
                                                                if (a == 7) {
                                                                    return 0;
                                                                } else if (a == 8) {
                                                                    return 7;
                                                                } else {
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

    @Test
    public void test5() {
        test1("test5Snippet");
    }

    public static int test6Snippet(int a) {
        if (a == 0) {
            return 10;
        } else {
            switch (a) {
                case 1:
                    return 5;
                default:
                    if (a == 2) {
                        return 3;
                    } else if (a == 3) {
                        return 11;
                    } else {
                        switch (a) {
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

            }
        }
    }

    @Test
    public void test6() {
        test1("test6Snippet");
    }

    public static int test7Snippet(int a) {
        if (a == 0) {
            return 4;
        } else {
            switch (a) {
                case 1:
                case 2:
                    return 1;
                case 3:
                    return 6;
                default:
                    return 7;
            }
        }
    }

    @Test
    public void test7() {
        test2("test7Snippet");
    }

    public static int test8Snippet(int a) {
        switch (a) {
            case 0:
                return 4;
            case 1:
            case 2:
            case 7:
            default:
                switch (a) {
                    case 2:
                    case 6:
                    default:
                        switch (a) {
                            case 1:
                            case 2:
                            case 4:
                                return 6;
                            default:
                                return 7;
                        }
                }
        }
    }

    @Test
    public void test8() {
        test3("test8Snippet");
    }

    public static int reference4Snippet(int a) {
        switch (a) {
            case 0:
                return 4;
            case 1:
            case 2:
            case 4:
                return 6;
            case 6:
                return 7;
            case 7:
                return 7;
            default:
                return 7;
        }
    }

    public static int test9Snippet(int a) {
        switch (a) {
            case 0:
                return 4;
            case 1:
            case 2:
            case 4:
                return 6;
            case 6:
            case 7:
            default:
                if (a == 6) {
                    return 7;
                } else if (a == 7) {
                    return 7;
                } else {
                    return 7;
                }
        }
    }

    @Test
    public void test9() {
        test4("test9Snippet");
    }

    public static int reference5Snippet(int a) {
        switch (a) {
            case 0:
                return 4;
            case 1:
            case 2:
                GraalDirectives.deoptimize();
                return 1;
            case 3:
                return 6;
            default:
                return 7;
        }
    }

    public static int test10Snippet(int a) {
        if (a == 0) {
            return 4;
        } else {
            if (a == 1 || a == 2) {
                GraalDirectives.deoptimize();
                return 1;
            } else {
                switch (a) {
                    case 3:
                        return 6;
                    default:
                        return 7;
                }
            }
        }
    }

    @Test
    public void test10() {
        test5("test10Snippet");
    }

    private void test1(String snippet) {
        test(snippet, REFERENCE_SNIPPET);
    }

    private void test2(String snippet) {
        test(snippet, REFERENCE_SNIPPET_2);
    }

    private void test3(String snippet) {
        test(snippet, REFERENCE_SNIPPET_3);
    }

    private void test4(String snippet) {
        test(snippet, REFERENCE_SNIPPET_4);
    }

    private void test5(String snippet) {
        test(snippet, REFERENCE_SNIPPET_5);
    }

    private void test(String snippet, String ref) {
        StructuredGraph graph = parseEager(snippet, StructuredGraph.AllowAssumptions.YES);
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph: Before folding");
        createCanonicalizerPhase().apply(graph, getProviders());
        debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph: After folding");
        StructuredGraph referenceGraph = parseEager(ref, StructuredGraph.AllowAssumptions.YES);
        debug.dump(DebugContext.BASIC_LEVEL, referenceGraph, "Reference Graph");
        compareGraphs(referenceGraph, graph);
    }

    private static boolean compareGraphs(StructuredGraph g1, StructuredGraph g2) {
        NodeIterable<IntegerSwitchNode> switches1 = g1.getNodes().filter(IntegerSwitchNode.class);
        NodeIterable<IntegerSwitchNode> switches2 = g2.getNodes().filter(IntegerSwitchNode.class);
        assertTrue(switches1.count() == switches2.count() && switches1.count() == 1);
        assertTrue(g1.getNodes().filter(BeginNode.class).count() == g2.getNodes().filter(BeginNode.class).count());
        assertTrue(g1.getNodes().filter(IfNode.class).count() == g2.getNodes().filter(IfNode.class).count());
        IntegerSwitchNode s1 = switches1.first();
        IntegerSwitchNode s2 = switches2.first();
        assertTrue(s1.keyCount() == s2.keyCount());

        for (int i = 0; i < s1.keyCount(); i++) {
            JavaConstant key = s1.keyAt(i);
            int j = 0;
            for (; j < s2.keyCount(); j++) {
                if (s2.keyAt(j).equals(key)) {
                    break;
                }
            }
            assertTrue(j < s2.keyCount());
            FixedNode b1 = s1.keySuccessor(i).next();
            FixedNode b2 = s2.keySuccessor(i).next();
            assertTrue(b1.getClass() == b2.getClass());
            if (b1 instanceof ReturnNode) {
                ReturnNode r1 = (ReturnNode) b1;
                ReturnNode r2 = (ReturnNode) b2;
                assertTrue(r1.result().getClass() == r2.result().getClass());
                if (r1.result() instanceof ConstantNode) {
                    if (r1.result().isJavaConstant() && r2.result().isJavaConstant()) {
                        assertTrue(r1.result().asJavaConstant().equals(r2.result().asJavaConstant()));
                    }
                }
            }
        }

        return true;
    }

    @Override
    public byte[] generateClass(String className) {
        /*-
         * public static int m(boolean, int) {
         *     ILOAD_0
         *     IFLT L1
         *     ILOAD_1
         *     LOOKUPSWITCH
         *       [1, 2] ->
         *         return some int
         *       [5, 7] ->
         *         GOTO L1
         *       [3, 6, 9] ->
         *         GOTO L2 (Different blocks)
         *       [0, 4, 8] ->
         *         GOTO L2 (Same block)
         *     L1:
         *       deopt
         *       return 0
         *     L2:
         *       return 5
         * }
         *
         * Optimization should coalesce branches [5, 7] and  [3, 6, 9]
         */
        // @formatter:off
        return ClassFile.of().build(ClassDesc.of(className), classBuilder -> classBuilder
                        .withMethodBody("m", MethodTypeDesc.of(CD_int, CD_boolean, CD_int), ACC_STATIC | ACC_PUBLIC, b -> {
                            Label outMerge = b.newLabel();
                            Label inMerge = b.newLabel();
                            Label commonTarget = b.newLabel();
                            Label def = b.newLabel();

                            Label[] inMerges = new Label[3];
                            Label[] outMerges = new Label[2];
                            Label[] simple = new Label[2];

                            for (int i = 0; i < inMerges.length; i++) {
                                inMerges[i] = b.newLabel();
                            }
                            for (int i = 0; i < simple.length; i++) {
                                simple[i] = b.newLabel();
                            }
                            for (int i = 0; i < outMerges.length; i++) {
                                outMerges[i] = b.newLabel();
                            }

                            int in = 0;
                            int out = 0;
                            int s = 0;

                            List<SwitchCase> cases = new ArrayList<>();
                            cases.add(SwitchCase.of(0, commonTarget));
                            cases.add(SwitchCase.of(1, simple[s++]));
                            cases.add(SwitchCase.of(2, simple[s++]));
                            cases.add(SwitchCase.of(3, inMerges[in++]));
                            cases.add(SwitchCase.of(4, commonTarget));
                            cases.add(SwitchCase.of(5, outMerges[out++]));
                            cases.add(SwitchCase.of(6, inMerges[in++]));
                            cases.add(SwitchCase.of(7, outMerges[out++]));
                            cases.add(SwitchCase.of(8, commonTarget));
                            cases.add(SwitchCase.of(9, inMerges[in++]));

                            b
                                            .iload(0)
                                            .iflt(outMerge)
                                            .iload(1)
                                            .lookupswitch(def, cases);

                            for (int i = 0; i < inMerges.length; i++) {
                                b.labelBinding(inMerges[i]).goto_(inMerge);
                            }

                            for (int i = 0; i < outMerges.length; i++) {
                                b.labelBinding(outMerges[i]).goto_(outMerge);
                            }
                            for (int i = 0; i < simple.length; i++) {
                                b.labelBinding(simple[i]).loadConstant(i).ireturn();
                            }

                            b.labelBinding(def)
                                            .iconst_1()
                                            .ireturn()
                                            .labelBinding(inMerge)
                                            .iconst_5()
                                            .ireturn()
                                            .labelBinding(commonTarget)
                                            .goto_(inMerge)
                                            .labelBinding(outMerge)
                                            .invokestatic(cd(GraalDirectives.class), "deoptimize", MTD_void)
                                            .iconst_m1()
                                            .ireturn();
                        }));
        // @formatter:on
    }

    /**
     * Makes sure the merge common successor optimization for integer switches works correctly and
     * does not forget a branch, even when a branch merges with something out of the switch.
     */
    @Test
    public void compileTest() {
        try {
            Class<?> c = getClass("D");
            ResolvedJavaMethod method = getResolvedJavaMethod(c, "m");
            StructuredGraph graph = parse(builder(method, StructuredGraph.AllowAssumptions.NO), getEagerGraphBuilderSuite());
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "Graph");
            IntegerSwitchNode s1 = graph.getNodes().filter(IntegerSwitchNode.class).first();
            int s1SuccCount = s1.successors().count();
            int s1KeyCount = s1.keyCount();
            createCanonicalizerPhase().apply(graph, getProviders());
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "Graph");
            IntegerSwitchNode s2 = graph.getNodes().filter(IntegerSwitchNode.class).first();
            // make sure canonicalization did not break the switch.
            assertTrue(s1KeyCount == s2.keyCount());
            assertTrue(s1SuccCount > s2.successors().count());
        } catch (Exception e) {
            Assert.fail();
        }
    }

    // Make switch big enough to trigger spawning hash array of the economic map
    public static int guardSnippet(int k) {
        if (k == 0) {
            return 3;
        } else if (k == 1) {
            return 4;
        } else if (k == 2) {
            return 2;
        } else if (k == 3) {
            return 1;
        } else if (k == 4) {
            return 7;
        } else if (k == 5) {
            return 6;
        } else if (k == 6) {
            return 5;
        } else if (k == 7) {
            return 8;
        } else if (k == 8) {
            return 11;
        } else if (k == 9) {
            return 10;
        } else if (k == 10) {
            return 9;
        } else if (k == 11) {
            return 12;
        } else {
            if (k == 0) {
                // ConvertDeoptToGuard will transform that into a fixed guard
                GraalDirectives.deoptimizeAndInvalidate();
                return 0;
            }
        }
        return -1;
    }

    public static int guardSnippetReference(int k) {
        switch (k) {
            case 0:
                return 3;
            case 1:
                return 4;
            case 2:
                return 2;
            case 3:
                return 1;
            case 4:
                return 7;
            case 5:
                return 6;
            case 6:
                return 5;
            case 7:
                return 8;
            case 8:
                return 11;
            case 9:
                return 10;
            case 10:
                return 9;
            case 11:
                return 12;
            default:
                return -1;
        }
    }

    /**
     * Ensures that a duplicate key for a fixed guard gets removed by the switch folding.
     */
    @Test
    public void testTrivialGuard() {
        StructuredGraph graph = parseEager("guardSnippet", StructuredGraph.AllowAssumptions.YES);
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph: Before folding");
        new ConvertDeoptimizeToGuardPhase(createCanonicalizerPhase()).apply(graph, getProviders());
        debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph: After folding");
        StructuredGraph referenceGraph = parseEager("guardSnippetReference", StructuredGraph.AllowAssumptions.YES);
        debug.dump(DebugContext.BASIC_LEVEL, referenceGraph, "Reference Graph");
        compareGraphs(referenceGraph, graph);
    }
}
