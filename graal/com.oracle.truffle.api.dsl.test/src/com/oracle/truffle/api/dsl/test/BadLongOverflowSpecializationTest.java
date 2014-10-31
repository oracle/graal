/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.dsl.test.TestHelper.*;
import static org.junit.Assert.*;

import java.math.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.BadLongOverflowSpecializationTestFactory.AddNodeFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

public class BadLongOverflowSpecializationTest {

    @NodeChildren({@NodeChild("left"), @NodeChild("right")})
    abstract static class BinaryNode extends ValueNode {
    }

    abstract static class AddNode extends BinaryNode {
        @Specialization(rewriteOn = ArithmeticException.class)
        int add(int left, int right) {
            return ExactMath.addExact(left, right);
        }

        @Specialization
        long addIntWithOverflow(int left, int right) {
            return (long) left + (long) right;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        Object add(long left, long right) {
            return ExactMath.addExact(left, right);
        }

        @Specialization
        BigInteger addSlow(long left, long right) {
            return BigInteger.valueOf(left).add(BigInteger.valueOf(right));
        }
    }

    @Test
    @Ignore
    public void testAdd() {
        TestRootNode<AddNode> node = createRoot(AddNodeFactory.getInstance());

        long index = -1;
        int baseStep = 0;
        Object r1 = executeWith(node, index, baseStep);
        assertEquals(-1L, r1);

        long err = 5000;
        long errStep = 2000;
        Object r2 = executeWith(node, err, errStep);

        assertEquals(Long.class, r2.getClass());
        assertEquals(7000L, r2);
    }
}
