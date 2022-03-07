/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.OrNode;
import org.graalvm.compiler.nodes.calc.ShiftNode;
import org.junit.Test;

public class MaskingOptimizationTest extends GraalCompilerTest {

    int shiftAndMask1(int x) {
        return (x << 2) & 3; // reduces to return 0
    }

    @Test
    public void test1() {
        test("shiftAndMask1", 42);
    }

    long shiftAndMask2(long x) {
        return (x << 2) & 3; // reduces to return 0
    }

    @Test
    public void test2() {
        test("shiftAndMask2", 42L);
    }

    int shiftAndMask3(int x, int y) {
        return (y + (x << 2)) & 3; // reduces to return y & 3
    }

    @Test
    public void test3() {
        test("shiftAndMask3", 42, 35);
    }

    long shiftAndMask4(long x, long y) {
        return (y + (x << 2)) & 3; // reduces to return y & 3
    }

    @Test
    public void test4() {
        test("shiftAndMask4", 42L, 35L);
    }

    int shiftAndMask5(int x, int y) {
        return ((y << 2) + (x << 2)) & 3; // reduces to return 0
    }

    @Test
    public void test5() {
        test("shiftAndMask5", 42, 35);
    }

    long shiftAndMask6(long x, long y) {
        return ((y << 2) + (x << 2)) & 3; // reduces to return 0
    }

    @Test
    public void test6() {
        test("shiftAndMask6", 42L, 35L);
    }

    long shiftAndMask7(int x) {
        return ((long) (x << 2)) & 3; // reduces to return 0
    }

    @Test
    public void test7() {
        test("shiftAndMask7", 42);
    }

    long shiftAndMask8(int x, int y) {
        return (y + (x << 2)) & 3; // reduces to y & 3
    }

    @Test
    public void test8() {
        test("shiftAndMask8", 42, 35);
    }

    long shiftAndMask9(int x, int y) {
        return (((long) (y << 2)) + ((long) (x << 2))) & 3; // reduces to return 0
    }

    @Test
    public void test9() {
        test("shiftAndMask9", 42, 35);
    }

    boolean shiftAndMaskCompare1(int x) {
        return ((x << 2) & 3) == 0; // reduces to return true
    }

    @Test
    public void test10() {
        test("shiftAndMaskCompare1", 42);
    }

    boolean shiftAndMaskCompare2(long x) {
        return ((x << 2) & 3) == 0; // reduces to return true
    }

    @Test
    public void test11() {
        test("shiftAndMaskCompare2", 42L);
    }

    boolean shiftAndMaskCompare3(int x, int y) {
        return ((y + (x << 2)) & 3) == 0; // reduces to return y & 3 == 0
    }

    @Test
    public void test12() {
        test("shiftAndMaskCompare3", 42, 35);
    }

    boolean shiftAndMaskCompare4(long x, long y) {
        return ((y + (x << 2)) & 3) == 0; // reduces to return y & 3 == 0
    }

    @Test
    public void test13() {
        test("shiftAndMaskCompare4", 42L, 35L);
    }

    boolean shiftAndMaskCompare5(int x, int y) {
        return (((y << 2) + (x << 2)) & 3) == 0; // reduces to return true
    }

    @Test
    public void test14() {
        test("shiftAndMaskCompare5", 42, 35);
    }

    boolean shiftAndMaskCompare6(long x, long y) {
        return (((y << 2) + (x << 2)) & 3) == 0; // reduces to return true
    }

    @Test
    public void test15() {
        test("shiftAndMaskCompare6", 42L, 35L);
    }

    boolean shiftAndMaskCompare7(int x) {
        return (((long) (x << 2)) & 3) == 0; // reduces to return true
    }

    @Test
    public void test16() {
        test("shiftAndMaskCompare7", 42);
    }

    boolean shiftAndMaskCompare8(int x, int y) {
        return ((y + (x << 2)) & 3) == 0; // reduces to y & 3 == 0
    }

    @Test
    public void test17() {
        test("shiftAndMaskCompare8", 42, 35);
    }

    boolean shiftAndMaskCompare9(int x, int y) {
        return ((((long) (y << 2)) + ((long) (x << 2))) & 3) == 0; // reduces to return true
    }

    @Test
    public void test18() {
        test("shiftAndMaskCompare9", 42, 35);
    }

    int orAndMask1(int x) {
        return (x | 4) & 3; // reduces to return x & 3
    }

    @Test
    public void test19() {
        test("orAndMask1", 42);
    }

    long orAndMask2(long x) {
        return (x | 4L) & 3L; // reduces to return x & 3
    }

    @Test
    public void test20() {
        test("orAndMask2", 42L);
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        super.checkMidTierGraph(graph);
        String methodName = graph.asJavaMethod().getName();
        if (methodName.startsWith("shift") && !graph.getNodes().filter(n -> n instanceof ShiftNode).isEmpty()) {
            throw new AssertionError("Expected shift nodes to be removed by canonicalization");
        } else if (methodName.startsWith("or") && !graph.getNodes().filter(n -> n instanceof OrNode).isEmpty()) {
            throw new AssertionError("Expected or nodes to be removed by canonicalization");
        }
    }
}
