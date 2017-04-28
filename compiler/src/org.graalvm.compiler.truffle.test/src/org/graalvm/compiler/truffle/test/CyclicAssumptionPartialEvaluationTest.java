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
package org.graalvm.compiler.truffle.test;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.utilities.CyclicAssumption;
import org.graalvm.compiler.truffle.test.nodes.ConstantWithCyclicAssumptionTestNode;

public class CyclicAssumptionPartialEvaluationTest extends PartialEvaluationTest {
    public static Object constant42() {
        return 42;
    }

    @Test
    public void constantValue() {
        CyclicAssumption assumption = new CyclicAssumption("cyclic");
        ConstantWithCyclicAssumptionTestNode assumingNode = new ConstantWithCyclicAssumptionTestNode(assumption, 42);
        RootTestNode rootNode = new RootTestNode(new FrameDescriptor(), "constantValue", assumingNode);
        OptimizedCallTarget callTarget = assertPartialEvalEquals("constant42", rootNode);
        Assert.assertTrue(callTarget.isValid());
        assertDeepEquals(42, callTarget.call());
        Assert.assertTrue(callTarget.isValid());
        assumingNode.invalidate();
        Assert.assertTrue("cyclicly valid again", assumption.getAssumption().isValid());
        Assert.assertFalse(callTarget.isValid());
        assertDeepEquals(43, callTarget.call());
    }
}
