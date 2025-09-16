/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.GraalOptions.AlignJumpTableEntry;

import org.junit.Test;

import jdk.graal.compiler.options.OptionValues;

// Regression test for JDK-8220643 (GR-14583)
public class IntegerSwitchTest extends GraalCompilerTest {
    public static int snippetWithoutRange(int arg) {
        return switch (arg) {
            case 0 -> 0;
            case 1 -> -1;
            case 2 -> 2;
            case 3 -> -3;
            case 4 -> 4;
            case 5 -> -5;
            case 6 -> 6;
            case 7 -> -7;
            default -> -2;
        };
    }

    @Test
    public void testSnippetWithoutRange() {
        test("snippetWithoutRange", 2);
        test(new OptionValues(getInitialOptions(), AlignJumpTableEntry, false), "snippetWithoutRange", 4);
    }

    public static int snippetWithRange(int arg) {
        return switch (arg & 0x7) {
            case 0 -> 0;
            case 1 -> -1;
            case 2 -> 2;
            case 3 -> -3;
            case 4 -> 4;
            case 5 -> -5;
            case 6 -> 6;
            case 7 -> -7;
            default -> -2;
        };
    }

    @Test
    public void testSnippetWithRange() {
        test("snippetWithRange", 2);
        test(new OptionValues(getInitialOptions(), AlignJumpTableEntry, false), "snippetWithRange", 4);
    }
}
