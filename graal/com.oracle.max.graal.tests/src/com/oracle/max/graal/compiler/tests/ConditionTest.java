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
package com.oracle.max.graal.compiler.tests;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.nodes.calc.*;


public class ConditionTest {

    @Test
    public void testImplies() {
        Random rand = new Random(13);
        for (Condition c1 : Condition.values()) {
            for (Condition c2 : Condition.values()) {
                boolean implies = c1.implies(c2);
                if (implies && c1 != Condition.OF && c2 != Condition.OF && c1 != Condition.NOF && c2 != Condition.NOF) {
                    for (int i = 0; i < 10000; i++) {
                        CiConstant a = CiConstant.forInt(rand.nextInt());
                        CiConstant b = CiConstant.forInt(i < 100 ? a.asInt() : rand.nextInt());
                        boolean result1 = c1.foldCondition(a, b, null, false);
                        boolean result2 = c2.foldCondition(a, b, null, false);
                        if (result1 && implies) {
                            assertTrue(result2);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testJoin() {
        Random rand = new Random(13);
        for (Condition c1 : Condition.values()) {
            for (Condition c2 : Condition.values()) {
                Condition join = c1.join(c2);
                assertTrue(join == c2.join(c1));
                if (join != null && c1 != Condition.OF && c2 != Condition.OF && c1 != Condition.NOF && c2 != Condition.NOF) {
                    for (int i = 0; i < 10000; i++) {
                        CiConstant a = CiConstant.forInt(rand.nextInt());
                        CiConstant b = CiConstant.forInt(i < 100 ? a.asInt() : rand.nextInt());
                        boolean result1 = c1.foldCondition(a, b, null, false);
                        boolean result2 = c2.foldCondition(a, b, null, false);
                        boolean resultJoin = join.foldCondition(a, b, null, false);
                        if (result1 && result2) {
                            assertTrue(resultJoin);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testMeet() {
        Random rand = new Random(13);
        for (Condition c1 : Condition.values()) {
            for (Condition c2 : Condition.values()) {
                Condition meet = c1.meet(c2);
                assertTrue(meet == c2.meet(c1));
                if (meet != null && c1 != Condition.OF && c2 != Condition.OF && c1 != Condition.NOF && c2 != Condition.NOF) {
                    for (int i = 0; i < 10000; i++) {
                        CiConstant a = CiConstant.forInt(rand.nextInt());
                        CiConstant b = CiConstant.forInt(i < 100 ? a.asInt() : rand.nextInt());
                        boolean result1 = c1.foldCondition(a, b, null, false);
                        boolean result2 = c2.foldCondition(a, b, null, false);
                        boolean resultMeet = meet.foldCondition(a, b, null, false);
                        if (result1 || result2) {
                            assertTrue(resultMeet);
                        }
                    }
                }
            }
        }
    }

}
