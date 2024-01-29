/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class ChildExecutionTest {

    public static class ChildExecutionChildNode extends Node {

        public Object execute(VirtualFrame frame) {
            assert frame != null;
            return null;
        }
    }

    @ExpectError("Child execution method: Object ChildExecutionChildNode::execute(VirtualFrame) called from method: Object TestNode::execute() requires a frame parameter.")
    @NodeChild(value = "foo", type = ChildExecutionChildNode.class)
    public abstract static class TestNode extends Node {

        public abstract void execute(VirtualFrame frame);

        public abstract Object executeObject(VirtualFrame frame);

        public abstract Object execute();

        @Specialization
        protected Object doIt(Object a) {
            return a;
        }
    }

    public static class ChildExecutionChildNode2 extends Node {

        public String executeString(VirtualFrame frame) {
            assert frame != null;
            return null;
        }
    }

    @ExpectError("Child execution method: String ChildExecutionChildNode2::executeString(VirtualFrame) called from method: boolean TestNode2::executeBool(boolean) requires a frame parameter.")
    @NodeChildren({
                    @NodeChild(value = "first", type = ChildExecutionChildNode2.class),
                    @NodeChild(value = "second", type = ChildExecutionChildNode2.class)
    })
    public abstract static class TestNode2 extends Node {

        public abstract Object execute(VirtualFrame frame);

        public abstract Object execute(Object arg1, Object arg2);

        public boolean executeBool(boolean arg) throws UnexpectedResultException {
            final Object res = execute(arg, arg);
            if (res instanceof Boolean) {
                return (Boolean) res;
            } else {
                throw new UnexpectedResultException(res);
            }
        }

        @Specialization
        protected boolean doIt(String a, String b) {
            return a.isEmpty() && b.isEmpty();
        }
    }

    @NodeChildren({
                    @NodeChild(value = "first", type = ChildExecutionChildNode2.class),
                    @NodeChild(value = "second", type = ChildExecutionChildNode2.class)
    })
    public abstract static class TestNode3 extends Node {

        public abstract Object execute(VirtualFrame frame);

        public abstract Object execute(Object arg1, Object arg2);

        public final boolean executeBool(boolean arg) throws UnexpectedResultException {
            final Object res = execute(arg, arg);
            if (res instanceof Boolean) {
                return (Boolean) res;
            } else {
                throw new UnexpectedResultException(res);
            }
        }

        @Specialization
        protected boolean doIt(String a, String b) {
            return a.isEmpty() && b.isEmpty();
        }
    }
}
