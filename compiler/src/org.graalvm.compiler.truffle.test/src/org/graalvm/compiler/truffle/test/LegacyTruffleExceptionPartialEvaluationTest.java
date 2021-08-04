/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.truffle.test.TruffleExceptionPartialEvaluationTest.createCallerChain;

import org.graalvm.compiler.truffle.test.TruffleExceptionPartialEvaluationTest.NodeFactory;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import org.junit.Test;

public class LegacyTruffleExceptionPartialEvaluationTest extends PartialEvaluationTest {

    public static Object constant42() {
        return 42;
    }

    @Test
    public void testTruffleException() {
        NodeFactory nodeFactory = new NodeFactoryImpl();
        assertPartialEvalEquals(LegacyTruffleExceptionPartialEvaluationTest::constant42, createCallerChain(0, 0, nodeFactory));
        assertPartialEvalEquals(LegacyTruffleExceptionPartialEvaluationTest::constant42, createCallerChain(3, 0, nodeFactory));
        assertPartialEvalEquals(LegacyTruffleExceptionPartialEvaluationTest::constant42, createCallerChain(0, 3, nodeFactory));
        assertPartialEvalEquals(LegacyTruffleExceptionPartialEvaluationTest::constant42, createCallerChain(4, 4, nodeFactory));
    }

    @SuppressWarnings("deprecation")
    private static final class TestTruffleException extends RuntimeException implements com.oracle.truffle.api.TruffleException {

        private static final long serialVersionUID = -6105288741119318027L;

        private final int stackTraceElementLimit;
        private final Node location;
        private final boolean property;

        TestTruffleException(int stackTraceElementLimit, Node location, boolean property) {
            this.stackTraceElementLimit = stackTraceElementLimit;
            this.location = location;
            this.property = property;
        }

        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }

        @Override
        public Node getLocation() {
            return location;
        }

        @Override
        public int getStackTraceElementLimit() {
            return stackTraceElementLimit;
        }
    }

    private static final class NodeFactoryImpl implements NodeFactory {

        @Override
        public AbstractTestNode createThrowNode(int stackTraceElementLimit, boolean property) {
            return new ThrowTruffleExceptionTestNode(stackTraceElementLimit, property);
        }

        @Override
        public AbstractTestNode createCatchNode(AbstractTestNode child) {
            return new CatchTruffleExceptionTestNode(child);
        }
    }

    private static class CatchTruffleExceptionTestNode extends AbstractTestNode {

        @Child private AbstractTestNode child;

        CatchTruffleExceptionTestNode(AbstractTestNode child) {
            this.child = child;
        }

        @Override
        public int execute(VirtualFrame frame) {
            try {
                return child.execute(frame);
            } catch (TestTruffleException e) {
                if (e.property) {
                    return 42;
                }
                throw e;
            }
        }
    }

    private static class ThrowTruffleExceptionTestNode extends AbstractTestNode {

        private final int limit;
        private final boolean property;

        ThrowTruffleExceptionTestNode(int limit, boolean property) {
            this.limit = limit;
            this.property = property;
        }

        @Override
        public int execute(VirtualFrame frame) {
            throw new TestTruffleException(limit, this, property);
        }
    }
}
