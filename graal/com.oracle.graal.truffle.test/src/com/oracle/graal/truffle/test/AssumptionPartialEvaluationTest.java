/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.*;

import com.oracle.graal.truffle.*;
import com.oracle.graal.truffle.test.nodes.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

public class AssumptionPartialEvaluationTest extends PartialEvaluationTest {
    public static Object constant42() {
        return 42;
    }

    @Test
    public void constantValue() {
        Assumption assumption = Truffle.getRuntime().createAssumption();
        AbstractTestNode result = new ConstantWithAssumptionTestNode(assumption, 42);
        RootTestNode rootNode = new RootTestNode(new FrameDescriptor(), "constantValue", result);
        OptimizedCallTarget callTarget = assertPartialEvalEquals("constant42", rootNode);
        Assert.assertTrue(callTarget.isValid());
        assertDeepEquals(42, callTarget.call());
        Assert.assertTrue(callTarget.isValid());
        try {
            assumption.check();
        } catch (InvalidAssumptionException e) {
            Assert.fail("Assumption must not have been invalidated.");
        }
        assumption.invalidate();
        try {
            assumption.check();
            Assert.fail("Assumption must have been invalidated.");
        } catch (InvalidAssumptionException e) {
        }
        Assert.assertFalse(callTarget.isValid());
        assertDeepEquals(43, callTarget.call());
    }
}
