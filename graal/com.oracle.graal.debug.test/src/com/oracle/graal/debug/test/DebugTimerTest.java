/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.debug.test;

import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;

public class DebugTimerTest {

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    }

    @Test
    public void test1() {
        DebugConfig debugConfig = Debug.fixedConfig(0, 0, false, false, true, false, null, null, System.out);
        try (DebugConfigScope dcs = new DebugConfigScope(debugConfig); Debug.Scope s = Debug.scope("DebugTimerTest")) {

            DebugTimer timerA = Debug.timer("TimerA");
            DebugTimer timerB = Debug.timer("TimerB");

            try (TimerCloseable a1 = timerA.start()) {
                sleep(50);
                try (TimerCloseable b1 = timerB.start()) {
                    sleep(50);
                }
            }

            Assert.assertTrue(timerB.getCurrentValue() < timerA.getCurrentValue());
            if (timerA.getFlat() != null && timerB.getFlat() != null) {
                assertTrue(timerB.getFlat().getCurrentValue() < timerA.getFlat().getCurrentValue());
                assertEquals(timerA.getFlat().getCurrentValue(), timerA.getCurrentValue() - timerB.getFlat().getCurrentValue(), 10D);
            }
        }
    }

    @Test
    public void test2() {
        DebugConfig debugConfig = Debug.fixedConfig(0, 0, false, false, true, false, null, null, System.out);
        try (DebugConfigScope dcs = new DebugConfigScope(debugConfig); Debug.Scope s = Debug.scope("DebugTimerTest")) {
            DebugTimer timerC = Debug.timer("TimerC");
            try (TimerCloseable c1 = timerC.start()) {
                sleep(50);
                try (TimerCloseable c2 = timerC.start()) {
                    sleep(50);
                    try (TimerCloseable c3 = timerC.start()) {
                        sleep(50);
                        try (TimerCloseable c4 = timerC.start()) {
                            sleep(50);
                            try (TimerCloseable c5 = timerC.start()) {
                                sleep(50);
                            }
                        }
                    }
                }
            }
            if (timerC.getFlat() != null) {
                assertEquals(timerC.getFlat().getCurrentValue(), timerC.getCurrentValue());
            }
        }
    }

    @Test
    public void test3() {
        DebugConfig debugConfig = Debug.fixedConfig(0, 0, false, false, true, false, null, null, System.out);
        try (DebugConfigScope dcs = new DebugConfigScope(debugConfig); Debug.Scope s = Debug.scope("DebugTimerTest")) {

            DebugTimer timerD = Debug.timer("TimerD");
            DebugTimer timerE = Debug.timer("TimerE");

            try (TimerCloseable d1 = timerD.start()) {
                sleep(50);
                try (TimerCloseable e1 = timerE.start()) {
                    sleep(50);
                    try (TimerCloseable d2 = timerD.start()) {
                        sleep(50);
                        try (TimerCloseable d3 = timerD.start()) {
                            sleep(50);
                        }
                    }
                }
            }

            Assert.assertTrue(timerE.getCurrentValue() < timerD.getCurrentValue());
            if (timerD.getFlat() != null && timerE.getFlat() != null) {
                assertTrue(timerE.getFlat().getCurrentValue() < timerD.getFlat().getCurrentValue());
                assertEquals(timerD.getFlat().getCurrentValue(), timerD.getCurrentValue() - timerE.getFlat().getCurrentValue(), 10D);
            }
        }
    }
}
