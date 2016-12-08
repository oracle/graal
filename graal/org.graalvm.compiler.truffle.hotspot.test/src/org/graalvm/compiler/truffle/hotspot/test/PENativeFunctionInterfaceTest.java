/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.truffle.hotspot.test;

import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

import org.graalvm.compiler.truffle.test.PartialEvaluationTest;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.ConstantTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import com.oracle.nfi.NativeFunctionInterfaceRuntime;
import com.oracle.nfi.api.NativeFunctionHandle;
import com.oracle.nfi.api.NativeFunctionInterface;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

public class PENativeFunctionInterfaceTest extends PartialEvaluationTest {

    @NodeInfo
    static class NativeSqrtNode extends AbstractTestNode {

        private final NativeFunctionHandle sqrt;

        @Child private AbstractTestNode value;

        NativeSqrtNode(NativeFunctionInterface nfi, AbstractTestNode value) {
            this.sqrt = nfi.getFunctionHandle("sqrt", double.class, double.class);
            this.value = value;
        }

        @Override
        public int execute(VirtualFrame frame) {
            double ret = (Double) sqrt.call((double) value.execute(frame));
            return (int) ret;
        }

    }

    @Test
    @Ignore
    public void testSqrt() {
        NativeFunctionInterface nfi = NativeFunctionInterfaceRuntime.getNativeFunctionInterface();
        Assume.assumeTrue("NFI not supported on this platform", nfi != null);

        AbstractTestNode input = new ConstantTestNode(42);
        AbstractTestNode sqrt = new NativeSqrtNode(nfi, input);
        RootTestNode root = new RootTestNode(new FrameDescriptor(), "nativeSqrt", sqrt);

        assertPartialEvalNoInvokes(root);
    }
}
