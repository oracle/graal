/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.test;

import com.oracle.graal.truffle.GraalTruffleRuntime;
import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.graal.truffle.TruffleCompilerOptions;
import com.oracle.graal.truffle.phases.InstrumentBranchesPhase;
import com.oracle.graal.truffle.test.nodes.AbstractTestNode;
import com.oracle.graal.truffle.test.nodes.RootTestNode;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertNotNull;

public class InstrumentBranchesPhaseTest extends PartialEvaluationTest {

    public static class SimpleIfTestNode extends AbstractTestNode {
        private int constant;

        public SimpleIfTestNode(int constant) {
            this.constant = constant;
        }

        @Override
        public int execute(VirtualFrame frame) {
            if (constant < 0) {
                return -1 * constant;
            } else {
                return 1;
            }
        }
    }

    private static void assertCompiled(OptimizedCallTarget target) {
        assertNotNull(target);
        try {
            ((GraalTruffleRuntime) Truffle.getRuntime()).waitForCompilation(target, 100000);
        } catch (ExecutionException | TimeoutException e) {
            fail("timeout");
        }
        assertTrue(target.isValid());
    }

    @Test
    public void simpleIfTest() {
        FrameDescriptor descriptor = new FrameDescriptor();
        SimpleIfTestNode result = new SimpleIfTestNode(5);
        RootTestNode rootNode = new RootTestNode(descriptor, "simpleIfRoot", result);
        boolean instrumentFlag = TruffleCompilerOptions.TruffleInstrumentBranches.getValue();
        String filterFlag = TruffleCompilerOptions.TruffleInstrumentBranchesFilter.getValue();
        try {
            TruffleCompilerOptions.TruffleInstrumentBranches.setValue(true);
            TruffleCompilerOptions.TruffleInstrumentBranchesFilter.setValue("*.*.execute");
            OptimizedCallTarget target = compileHelper("simpleIfRoot", rootNode, new Object[0]);
            target.compile();
            assertCompiled(target);
            target.call();
        } finally {
            TruffleCompilerOptions.TruffleInstrumentBranches.setValue(instrumentFlag);
            TruffleCompilerOptions.TruffleInstrumentBranchesFilter.setValue(filterFlag);
        }
        Assert.assertEquals(InstrumentBranchesPhase.instrumentation.accessTableToList().get(0), "com.oracle.graal.truffle.test.InstrumentBranchesPhaseTest$SimpleIfTestNode.execute(InstrumentBranchesPhaseTest.java:53) [bci: 4]\n[0] state = ELSE");
    }
}
