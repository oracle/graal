/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
        while (countingNode.count < 100) {
            Thread.sleep(100);
        }

        assumption.invalidate();
        thread.join(5_000);
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
        volatile long count = 0;
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
