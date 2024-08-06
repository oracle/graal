/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import org.junit.Test;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class ConditionTest {

    private static final Stamp INT_STAMP = StampFactory.forKind(JavaKind.Int);

    @Test
    public void testImplies() {
        Random rand = GraalCompilerTest.getRandomInstance();
        for (Condition c1 : Condition.values()) {
            for (Condition c2 : Condition.values()) {
                boolean implies = c1.implies(c2);
                if (implies) {
                    for (int i = 0; i < 1000; i++) {
                        JavaConstant a = JavaConstant.forInt(rand.nextInt());
                        JavaConstant b = JavaConstant.forInt(i < 100 ? a.asInt() : rand.nextInt());
                        boolean result1 = c1.foldCondition(INT_STAMP, a, b, null, false).toBoolean();
                        boolean result2 = c2.foldCondition(INT_STAMP, a, b, null, false).toBoolean();
                        if (result1) {
                            assertTrue(result2);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testJoin() {
        Random rand = GraalCompilerTest.getRandomInstance();
        for (Condition c1 : Condition.values()) {
            for (Condition c2 : Condition.values()) {
                Condition join = c1.join(c2);
                assertEquals(join, c2.join(c1));
                if (join != null) {
                    for (int i = 0; i < 1000; i++) {
                        JavaConstant a = JavaConstant.forInt(rand.nextInt());
                        JavaConstant b = JavaConstant.forInt(i < 100 ? a.asInt() : rand.nextInt());
                        boolean result1 = c1.foldCondition(INT_STAMP, a, b, null, false).toBoolean();
                        boolean result2 = c2.foldCondition(INT_STAMP, a, b, null, false).toBoolean();
                        boolean resultJoin = join.foldCondition(INT_STAMP, a, b, null, false).toBoolean();
                        if (result1 && result2) {
                            assertTrue(resultJoin);
                        } else {
                            assertFalse(resultJoin);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testMeet() {
        Random rand = GraalCompilerTest.getRandomInstance();
        for (Condition c1 : Condition.values()) {
            for (Condition c2 : Condition.values()) {
                Condition meet = c1.meet(c2);
                assertEquals(meet, c2.meet(c1));
                if (meet != null) {
                    for (int i = 0; i < 1000; i++) {
                        JavaConstant a = JavaConstant.forInt(rand.nextInt());
                        JavaConstant b = JavaConstant.forInt(i < 100 ? a.asInt() : rand.nextInt());
                        boolean result1 = c1.foldCondition(INT_STAMP, a, b, null, false).toBoolean();
                        boolean result2 = c2.foldCondition(INT_STAMP, a, b, null, false).toBoolean();
                        boolean resultMeet = meet.foldCondition(INT_STAMP, a, b, null, false).toBoolean();
                        if (result1 || result2) {
                            assertTrue(resultMeet);
                        } else {
                            assertFalse(resultMeet);
                        }
                    }
                }
            }
        }
    }

    static int[] intBoundaryValues = new int[]{-1, 0, 1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};

    @Test
    public void testTrueIsDisjoint() {
        for (Condition c1 : Condition.values()) {
            for (Condition c2 : Condition.values()) {
                if (c1.trueIsDisjoint(c2)) {
                    for (int v1 : intBoundaryValues) {
                        for (int v2 : intBoundaryValues) {
                            JavaConstant a = JavaConstant.forInt(v1);
                            JavaConstant b = JavaConstant.forInt(v2);
                            boolean result1 = c1.foldCondition(INT_STAMP, a, b, null, false).toBoolean();
                            boolean result2 = c2.foldCondition(INT_STAMP, a, b, null, false).toBoolean();
                            // If these conditions are disjoint then both conditions can't evaluate
                            // to true for the same inputs.
                            assertFalse(String.format("%s %s %s (%s) is not disjoint from %s %s %s (%s)", a, c1, b, result1, a, c2, b, result2), result1 && result2);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testCheckUtilityMethod() {
        // this utility method might otherwise only be used for debugging
        assertTrue(Condition.EQ.check(1, 1));
        assertFalse(Condition.EQ.check(0, 1));
        assertFalse(Condition.EQ.check(1, 0));

        assertFalse(Condition.NE.check(1, 1));
        assertTrue(Condition.NE.check(0, 1));
        assertTrue(Condition.NE.check(1, 0));

        assertFalse(Condition.LT.check(1, 1));
        assertTrue(Condition.LT.check(0, 1));
        assertFalse(Condition.LT.check(1, 0));

        assertTrue(Condition.LE.check(1, 1));
        assertTrue(Condition.LE.check(0, 1));
        assertFalse(Condition.LE.check(1, 0));

        assertFalse(Condition.GT.check(1, 1));
        assertFalse(Condition.GT.check(0, 1));
        assertTrue(Condition.GT.check(1, 0));

        assertTrue(Condition.GE.check(1, 1));
        assertFalse(Condition.GE.check(0, 1));
        assertTrue(Condition.GE.check(1, 0));

        assertFalse(Condition.BT.check(1, 1));
        assertTrue(Condition.BT.check(0, 1));
        assertFalse(Condition.BT.check(1, 0));

        assertTrue(Condition.BE.check(1, 1));
        assertTrue(Condition.BE.check(0, 1));
        assertFalse(Condition.BE.check(1, 0));

        assertFalse(Condition.AT.check(1, 1));
        assertFalse(Condition.AT.check(0, 1));
        assertTrue(Condition.AT.check(1, 0));

        assertTrue(Condition.AE.check(1, 1));
        assertFalse(Condition.AE.check(0, 1));
        assertTrue(Condition.AE.check(1, 0));
    }

}
