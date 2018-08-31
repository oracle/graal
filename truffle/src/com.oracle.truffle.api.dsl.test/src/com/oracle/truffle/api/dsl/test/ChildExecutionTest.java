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
