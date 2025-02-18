/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import jdk.graal.compiler.java.BytecodeParserOptions;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.AllocateWithExceptionNode;
import jdk.graal.compiler.options.OptionValues;

/**
 * A simple test class verifying that truffle handling for explicit OOME works.
 */
public class TruffleExplicitOOMETest extends PartialEvaluationTest {

    public static final class Compilables {

        public static Object O;

        final OptimizedCallTarget catchButNoIntrinsic = (OptimizedCallTarget) new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                try {
                    Object allocated = new Object();
                    O = allocated;
                } catch (OutOfMemoryError e) {
                    // swallow
                }
                return null;
            }

            @Override
            public String toString() {
                return "CATCH_NO_INTRINSIC";
            }

        }.getCallTarget();

        final OptimizedCallTarget intrinsic = (OptimizedCallTarget) new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                try {
                    Object allocated = CompilerDirectives.ensureAllocatedHere(new Object());
                    O = allocated;
                } catch (OutOfMemoryError e) {
                    // swallow
                }
                return null;
            }

            @Override
            public String toString() {
                return "INTRINSIC";
            }
        }.getCallTarget();

    }

    @Override
    protected OptionValues getGraalOptions() {
        // See TruffleCompilerOptions#updateValues
        return new OptionValues(super.getGraalOptions(), BytecodeParserOptions.DoNotMoveAllocationsWithOOMEHandlers, false);
    }

    @Test
    public void testNeverSeenExceptionHandlerSkipped() {
        /*
         * We disable truffle AST inlining to not inline the callee
         */
        setupContext(Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).option("compiler.Inlining", "false").build());
        Compilables compilables = new Compilables();

        StructuredGraph graph1 = partialEval(compilables.catchButNoIntrinsic, new Object[0]);
        Assert.assertEquals(0, graph1.getNodes().filter(AllocateWithExceptionNode.class).count());

        StructuredGraph graph2 = partialEval(compilables.intrinsic, new Object[0]);
        Assert.assertEquals(1, graph2.getNodes().filter(AllocateWithExceptionNode.class).count());
    }

}
