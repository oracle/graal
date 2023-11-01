/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;

import jdk.graal.compiler.api.directives.GraalDirectives;
import org.junit.Test;

import jdk.vm.ci.meta.DeoptimizationReason;

public class SpeculativeGuardMovementTrivialDeoptTest extends GraalCompilerTest {

    static class Node {
        int val;
        Node next;
    }

    private static Node createList(int length) {
        Node head = new Node();
        for (int i = 1; i < length; i++) {
            Node newHead = new Node();
            newHead.val = i;
            newHead.next = head;
            head = newHead;
        }
        return head;
    }

    public static long contradictingAfter(Node head) {
        Node cur = head;
        int i = 0;
        for (; GraalDirectives.injectIterationCount(10000, i < 500); i++) {
            if (GraalDirectives.injectBranchProbability(0.0001, cur == null)) {
                // this loop effectively ensures the guard below is never triggered
                break;
            }
            cur = cur.next;
            GraalDirectives.sideEffect(0);
            if (i == 123) {
                GraalDirectives.sideEffect(1);
            } else {
                GraalDirectives.sideEffect(2);
            }
            if (!(i + 1 < 101)) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            GraalDirectives.sideEffect(3);
        }
        return i;
    }

    @Test
    public void testContradicting01() {
        test(getInitialOptions(), EnumSet.allOf(DeoptimizationReason.class), "contradictingAfter", createList(100));
    }

    public static long contradictingAfterInnerOuter(Node head, int foo) {
        Node cur = head;

        int outerPhi = 0;
        while (outerPhi < foo) {
            int i = outerPhi;
            for (; GraalDirectives.injectIterationCount(10000, i < 500); i++) {
                if (GraalDirectives.injectBranchProbability(0.0001, cur == null)) {
                    // this loop effectively ensures the guard below is never triggered
                    break;
                }
                cur = cur.next;
                GraalDirectives.sideEffect(0);
                if (i == 123) {
                    GraalDirectives.sideEffect(1);
                } else {
                    GraalDirectives.sideEffect(2);
                }
                if (!(i + 1 < 101)) {
                    GraalDirectives.deoptimizeAndInvalidate();
                }
                GraalDirectives.sideEffect(3);
            }
            outerPhi++;
        }
        return outerPhi;
    }

    @Test
    public void testContradicting02() {
        test(getInitialOptions(), EnumSet.allOf(DeoptimizationReason.class), "contradictingAfterInnerOuter", createList(100), 1);
    }

    public static long contradictingAfterInnerOuter2(Node head, int foo, int bar) {
        Node cur = head;
        int outerOuterPhi = 0;
        while (outerOuterPhi < bar) {
            int outerPhi = outerOuterPhi;
            while (outerPhi < foo) {
                int i = outerPhi;
                for (; GraalDirectives.injectIterationCount(10000, i < 500); i++) {
                    if (GraalDirectives.injectBranchProbability(0.0001, cur == null)) {
                        // this loop effectively ensures the guard below is never triggered
                        break;
                    }
                    cur = cur.next;
                    GraalDirectives.sideEffect(0);
                    if (i == 123) {
                        GraalDirectives.sideEffect(1);
                    } else {
                        GraalDirectives.sideEffect(2);
                    }
                    if (!(i + 1 < 101)) {
                        GraalDirectives.deoptimizeAndInvalidate();
                    }
                    GraalDirectives.sideEffect(3);
                }
                outerPhi++;
            }
            outerOuterPhi++;
        }
        return outerOuterPhi;
    }

    @Test
    public void testContradicting03() {
        test(getInitialOptions(), EnumSet.allOf(DeoptimizationReason.class), "contradictingAfterInnerOuter2", createList(100), 1, 1);
    }

}
