/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.ICONST_5;
import static org.objectweb.asm.Opcodes.ICONST_M1;
import static org.objectweb.asm.Opcodes.IFLT;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.IRETURN;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.test.ExportingClassLoader;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SwitchFoldingTest extends GraalCompilerTest {

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
        debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
        createCanonicalizerPhase().apply(graph, getProviders());
        StructuredGraph referenceGraph = parseEager(ref, StructuredGraph.AllowAssumptions.YES);
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

    private static final String NAME = "D";
    private static final byte[] clazz = makeClass();

    public static class MyClassLoader extends ExportingClassLoader {
        @Override
        protected Class<?> findClass(String className) throws ClassNotFoundException {
            return defineClass(NAME.replace('/', '.'), clazz, 0, clazz.length);
        }
    }

    private static byte[] makeClass() {
        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;
        String jvmName = NAME.replace('.', '/');
        cw.visit(49, ACC_SUPER | ACC_PUBLIC, jvmName, null, "java/lang/Object", new String[]{});

        // Checkstyle: stop AvoidNestedBlocks
        {
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
            Label outMerge = new Label();
            Label inMerge = new Label();
            Label commonTarget = new Label();
            Label def = new Label();

            int[] keys = new int[10];
            Label[] labels = new Label[10];

            Label[] inMerges = new Label[3];
            Label[] outMerges = new Label[2];
            Label[] simple = new Label[2];

            for (int i = 0; i < inMerges.length; i++) {
                inMerges[i] = new Label();
            }
            for (int i = 0; i < simple.length; i++) {
                simple[i] = new Label();
            }
            for (int i = 0; i < outMerges.length; i++) {
                outMerges[i] = new Label();
            }
            for (int i = 0; i < keys.length; i++) {
                keys[i] = i;
            }
            int in = 0;
            int out = 0;
            int s = 0;
            labels[0] = commonTarget;
            labels[1] = simple[s++];
            labels[2] = simple[s++];
            labels[3] = inMerges[in++];
            labels[4] = commonTarget;
            labels[5] = outMerges[out++];
            labels[6] = inMerges[in++];
            labels[7] = outMerges[out++];
            labels[8] = commonTarget;
            labels[9] = inMerges[in++];

            mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "m", "(ZI)I", null, null);
            mv.visitCode();
            mv.visitIntInsn(ILOAD, 0);
            mv.visitJumpInsn(IFLT, outMerge);

            mv.visitIntInsn(ILOAD, 1);
            mv.visitLookupSwitchInsn(def, keys, labels);

            for (int i = 0; i < inMerges.length; i++) {
                mv.visitLabel(inMerges[i]);
                mv.visitJumpInsn(GOTO, inMerge);
            }
            for (int i = 0; i < outMerges.length; i++) {
                mv.visitLabel(outMerges[i]);
                mv.visitJumpInsn(GOTO, outMerge);
            }
            for (int i = 0; i < simple.length; i++) {
                mv.visitLabel(simple[i]);
                mv.visitInsn(ICONST_0 + i);
                mv.visitInsn(IRETURN);
            }

            mv.visitLabel(def);
            mv.visitInsn(ICONST_1);
            mv.visitInsn(IRETURN);

            mv.visitLabel(inMerge);
            mv.visitInsn(ICONST_5);
            mv.visitInsn(IRETURN);

            mv.visitLabel(commonTarget);
            mv.visitJumpInsn(GOTO, inMerge);

            mv.visitLabel(outMerge);
            mv.visitMethodInsn(INVOKESTATIC, GraalDirectives.class.getName().replace('.', '/'), "deoptimize", "()V", false);
            mv.visitInsn(ICONST_M1);
            mv.visitInsn(IRETURN);

            mv.visitMaxs(3, 2);
            mv.visitEnd();
        }
        // Checkstyle: resume AvoidNestedBlocks

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Makes sure the merge common successor optimization for integer switches works correctly and
     * does not forget a branch, even when a branch merges with something out of the switch.
     */
    @Test
    public void compileTest() {
        try {
            MyClassLoader loader = new MyClassLoader();
            Class<?> c = loader.findClass(NAME);
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
}
