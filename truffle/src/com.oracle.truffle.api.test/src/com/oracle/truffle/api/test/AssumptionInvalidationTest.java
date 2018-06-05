/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Test that Assumption invalidation is propagated to other threads.
 */
public class AssumptionInvalidationTest {

    @Test
    public void test() throws InterruptedException {
        TruffleRuntime runtime = Truffle.getRuntime();
        Assumption assumption = runtime.createAssumption("propagated assumption invalidation");
        CountingNode countingNode = new CountingNode(assumption);
        TestRootNode countingRootNode = new TestRootNode(countingNode);
        final CallTarget countingTarget = runtime.createCallTarget(countingRootNode);

        Thread thread = new Thread(new Runnable() {
            public void run() {
                countingTarget.call();
            }
        });
        thread.start();

        // Give a chance for CountingNode.execute to OSR compile
        Thread.sleep(100);

        assumption.invalidate();
        thread.join(100);
        assertEquals("Thread ought to be notified of invalidation in reasonable time.", false, thread.isAlive());
    }

    static class TestRootNode extends RootNode {
        @Child private ValueNode child;

        TestRootNode(ValueNode child) {
            super(null);
            this.child = child;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return child.execute(frame);
        }
    }

    abstract static class ValueNode extends Node {
        abstract long execute(VirtualFrame frame);
    }

    static class CountingNode extends ValueNode {
        long count = 0;
        final Assumption assumption;

        CountingNode(Assumption assumption) {
            this.assumption = assumption;
        }

        @Override
        long execute(VirtualFrame frame) {
            while (assumption.isValid()) {
                count++;
            }
            return count;
        }
    }
}
