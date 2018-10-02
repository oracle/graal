/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.runtime.GraalCompilerDirectives;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Assert;
import org.junit.Test;

import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.overrideOptions;

public class MultiTierCompilationTest extends PartialEvaluationTest {
    public static class FirstTierCalleeNode extends AbstractTestNode {
        @Override
        public int execute(VirtualFrame frame) {
            if (!CompilerDirectives.inInterpreter()) {
                throw new RuntimeException("Callee not in interpreter.");
            }
            return 7;
        }
    }

    public static class FirstTierRootNode extends AbstractTestNode {
        @Child private DirectCallNode callNode;

        public FirstTierRootNode() {
            RootCallTarget target = Truffle.getRuntime().createCallTarget(new RootTestNode(new FrameDescriptor(), "callee", new FirstTierCalleeNode()));
            this.callNode = Truffle.getRuntime().createDirectCallNode(target);
        }

        @Override
        public int execute(VirtualFrame frame) {
            if (!GraalCompilerDirectives.inFirstTier() || CompilerDirectives.inInterpreter()) {
                throw new RuntimeException("Callee not compiled in first tier.");
            }
            return (int) callNode.call(new Object[0]);
        }
    }

    public static class SecondTierCalleeNode extends AbstractTestNode {
        @Override
        public int execute(VirtualFrame frame) {
            if (GraalCompilerDirectives.inFirstTier() || CompilerDirectives.inInterpreter()) {
                throw new RuntimeException("Callee not compiled in second tier.");
            }
            return 7;
        }
    }

    public static class SecondTierRootNode extends AbstractTestNode {
        @Child private DirectCallNode callNode;

        public SecondTierRootNode() {
            RootCallTarget target = Truffle.getRuntime().createCallTarget(new RootTestNode(new FrameDescriptor(), "callee", new SecondTierCalleeNode()));
            this.callNode = Truffle.getRuntime().createDirectCallNode(target);
        }

        @Override
        public int execute(VirtualFrame frame) {
            if (GraalCompilerDirectives.inFirstTier() || CompilerDirectives.inInterpreter()) {
                throw new RuntimeException("Callee not compiled in second tier.");
            }
            return (int) callNode.call(new Object[0]);
        }
    }

    @SuppressWarnings("try")
    @Test
    public void testCompilationTiers() {
        FirstTierRootNode firstTierNode = new FirstTierRootNode();
        OptimizedCallTarget firstTierTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(new RootTestNode(new FrameDescriptor(), "firstTierCompilation", firstTierNode));
        SecondTierRootNode secondTierNode = new SecondTierRootNode();
        OptimizedCallTarget secondTierTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(new RootTestNode(new FrameDescriptor(), "secondTierCompilation", secondTierNode));
        try (TruffleCompilerOptions.TruffleOptionsOverrideScope scope = overrideOptions(TruffleCompilerOptions.TruffleBackgroundCompilation, false)) {
            // Assert.assertTrue(firstTierTarget.compile(false));
            // firstTierTarget.call(new Object[0]);
            Assert.assertTrue(secondTierTarget.compile(true));
            secondTierTarget.call(new Object[0]);
        }
    }

    @Test
    public void testCompilation() {
    }
}
