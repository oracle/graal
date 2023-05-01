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

import org.junit.Test;

/**
 * Test (Zero|Sign)Extend Nodes are minimized. The constraints are validated in
 * {@code OptimizeExtendsPhase::validateOptimization}.
 */

public class OptimizeExtendsTest extends GraalCompilerTest {

    long testSnippet1(byte[] value, int sumKind) {
        long result = 0;
        byte op = value[0];
        switch (sumKind) {
            case 1:
                result += Long.sum(op, result);
                break;
            case 2:
                result += Integer.sum(op, (int) result);
                break;
            case 3:
                result += Character.toLowerCase(op);
                break;
            case 4:
                result += op;
                break;
        }
        return result;
    }

    long testSnippet2(char[] value, int sumKind) {
        long result = 0;
        char op = value[0];
        switch (sumKind) {
            case 1:
                result += Long.sum(op, result);
                break;
            case 2:
                result += Integer.sum(op, (int) result);
                break;
            case 3:
                result += Character.toLowerCase(op);
                break;
            case 4:
                result += (short) op;
                break;
            case 5:
                result += (byte) op;
                break;
        }
        return result;
    }

    long testSnippet3(short[] value, int sumKind) {
        long result = 0;
        short op = value[0];
        switch (sumKind) {
            case 1:
                result += Long.sum(op, result);
                break;
            case 2:
                result += Integer.sum(op, (int) result);
                break;
            case 3:
                result += Character.toLowerCase(op);
                break;
            case 4:
                result += op;
                break;
            case 5:
                result += (byte) op;
                break;
        }
        return result;
    }

    long testSnippet4(int[] value, int sumKind) {
        long result = 0;
        int op = value[0];
        switch (sumKind) {
            case 1:
                result += Long.sum(op, result);
                break;
            case 2:
                result += Integer.sum(op, (int) result);
                break;
            case 3:
                result += Character.toLowerCase(op);
                break;
            case 4:
                result += (short) op;
                break;
            case 5:
                result += (byte) op;
                break;
        }
        return result;
    }

    @Test
    public void test() {
        test("testSnippet1", new byte[]{1}, 2);
        test("testSnippet2", new char[]{1}, 2);
        test("testSnippet3", new short[]{1}, 2);
        test("testSnippet4", new int[]{1}, 2);
    }
}
