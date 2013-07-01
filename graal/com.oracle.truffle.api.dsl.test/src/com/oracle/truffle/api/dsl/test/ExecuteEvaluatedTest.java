/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.DoubleEvaluatedNodeFactory;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.EvaluatedNodeFactory;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.UseDoubleEvaluatedNodeFactory;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.UseEvaluatedNodeFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ArgumentNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestArguments;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

public class ExecuteEvaluatedTest {

    @Test
    public void testSingleEvaluated() {
        ArgumentNode arg0 = new ArgumentNode(0);
        CallTarget callTarget = TestHelper.createCallTarget(UseEvaluatedNodeFactory.create(arg0, EvaluatedNodeFactory.create(null)));

        Assert.assertEquals(43, callTarget.call(new TestArguments(42)));
        Assert.assertEquals(1, arg0.getInvocationCount());
    }

    @NodeChild("exp")
    abstract static class EvaluatedNode extends ValueNode {

        @Specialization
        int doExecuteWith(int exp) {
            return exp + 1;
        }

        public abstract Object executeEvaluated(VirtualFrame frame, Object targetValue);

        public abstract int executeIntEvaluated(VirtualFrame frame, Object targetValue) throws UnexpectedResultException;
    }

    @NodeChildren({@NodeChild("exp0"), @NodeChild(value = "exp1", type = EvaluatedNode.class, executeWith = "exp0")})
    abstract static class UseEvaluatedNode extends ValueNode {

        @Specialization
        int call(int exp0, int exp1) {
            Assert.assertEquals(exp0 + 1, exp1);
            return exp1;
        }
    }

    @Test
    public void testDoubleEvaluated() {
        ArgumentNode arg0 = new ArgumentNode(0);
        ArgumentNode arg1 = new ArgumentNode(1);
        CallTarget callTarget = TestHelper.createCallTarget(UseDoubleEvaluatedNodeFactory.create(arg0, arg1, DoubleEvaluatedNodeFactory.create(null, null)));

        Assert.assertEquals(85, callTarget.call(new TestArguments(42, 43)));
        Assert.assertEquals(1, arg0.getInvocationCount());
        Assert.assertEquals(1, arg1.getInvocationCount());
    }

    @NodeChildren({@NodeChild("exp0"), @NodeChild("exp1")})
    abstract static class DoubleEvaluatedNode extends ValueNode {

        @Specialization
        int doExecuteWith(int exp0, int exp1) {
            return exp0 + exp1;
        }

        public abstract Object executeEvaluated(VirtualFrame frame, Object exp0, Object exp1);

        public abstract int executeIntEvaluated(VirtualFrame frame, Object exp0, Object exp1) throws UnexpectedResultException;
    }

    @NodeChildren({@NodeChild("exp0"), @NodeChild("exp1"), @NodeChild(value = "exp2", type = DoubleEvaluatedNode.class, executeWith = {"exp0", "exp1"})})
    abstract static class UseDoubleEvaluatedNode extends ValueNode {

        @Specialization
        int call(int exp0, int exp1, int exp2) {
            Assert.assertEquals(exp0 + exp1, exp2);
            return exp2;
        }
    }

}
