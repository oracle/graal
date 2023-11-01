/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/*
 * If you update the code here, please also update truffle/docs/HostOptimization.md.
 */
public class HostInliningBytecodeInterpreterExampleTest extends TruffleCompilerImplTest {

    @Test
    @SuppressWarnings("try")
    public void test() throws Throwable {
        // call the method to initialize classes
        interpreterSwitch();

        // ensure truffle is initialized
        getTruffleCompiler();

        ResolvedJavaMethod method = getResolvedJavaMethod(BytecodeNode.class, "execute");
        OptionValues options = HostInliningTest.createHostInliningOptions(30000, -1);

        StructuredGraph graph = parseForCompile(method, options);
        try (DebugContext.Scope ds = graph.getDebug().scope("Testing", method, graph)) {
            super.createSuites(options).getHighTier().apply(graph, getDefaultHighTierContext());
            for (InvokeNode invoke : graph.getNodes().filter(InvokeNode.class)) {
                ResolvedJavaMethod invokedMethod = invoke.getTargetMethod();
                String name = invokedMethod.getName();
                Assert.assertTrue(name, name.equals("traceTransferToInterpreter") || name.equals("truffleBoundary") || name.equals("protectedByInIntepreter") || name.equals("recursive") ||
                                name.equals("execute") || name.equals("shouldNotReachHere") || name.equals("dominatedByTransferToInterpreter") || name.equals("inliningCutoff"));
            }
        }
    }

    static final int BYTECODES = 8;

    @Override
    protected BasePhase<HighTierContext> createInliningPhase(CanonicalizerPhase canonicalizer) {
        return null;
    }

    @BytecodeInterpreterSwitch
    static void interpreterSwitch() {
        byte[] ops = new byte[BYTECODES];
        for (int i = 0; i < 7; i++) {
            ops[i] = (byte) i;
        }
        new BytecodeNode(ops).execute();
    }

    @Override
    protected InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
    }

    public static class BytecodeNode extends Node {

        @CompilationFinal(dimensions = 1) final byte[] ops;
        @Children final BaseNode[] polymorphic = new BaseNode[]{new SubNode1(), new SubNode2()};
        @Child SubNode1 monomorphic = new SubNode1();

        BytecodeNode(byte[] ops) {
            this.ops = ops;
        }

        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public void execute() {
            int bci = 0;
            while (bci < ops.length) {
                switch (ops[bci++]) {
                    case 0:
                        // regular operation
                        add(21, 21);
                        break;
                    case 1:
                        // complex operation in @TruffleBoundary annotated method
                        truffleBoundary();
                        break;
                    case 2:
                        // complex operation protected behind inIntepreter
                        if (CompilerDirectives.inInterpreter()) {
                            protectedByInIntepreter();
                        }
                        break;
                    case 3:
                        // complex operation dominated by transferToInterpreter
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        dominatedByTransferToInterpreter();
                        break;
                    case 4:
                        // first level of recursion is inlined
                        recursive(5);
                        break;
                    case 5:
                        // can be inlined is still monomorphic (with profile)
                        monomorphic.execute();
                        break;
                    case 6:
                        for (int y = 0; y < polymorphic.length; y++) {
                            // can no longer be inlined (no longer monomorphic)
                            polymorphic[y].execute();
                        }
                        break;
                    case 7:
                        inliningCutoff();
                        break;
                    default:
                        // propagates transferToInterpeter from within the call
                        throw CompilerDirectives.shouldNotReachHere();
                }
            }
        }

        private static int add(int a, int b) {
            return a + b;
        }

        private void protectedByInIntepreter() {
        }

        private void dominatedByTransferToInterpreter() {
        }

        @HostCompilerDirectives.InliningCutoff
        private void inliningCutoff() {
        }

        private void recursive(int i) {
            if (i == 0) {
                return;
            }
            recursive(i - 1);
        }

        @TruffleBoundary
        private void truffleBoundary() {
        }

        abstract static class BaseNode extends Node {

            abstract int execute();

        }

        static class SubNode1 extends BaseNode {
            @Override
            int execute() {
                return 42;
            }
        }

        static class SubNode2 extends BaseNode {
            @Override
            int execute() {
                return 42;
            }
        }

    }

}
