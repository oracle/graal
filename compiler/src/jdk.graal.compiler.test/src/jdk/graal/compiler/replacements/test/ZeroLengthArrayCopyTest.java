/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;

/**
 * A zero-length {@link System#arraycopy} still checks its positions. Virtualizing the copy into a
 * non-escaping destination must not drop the check of the source position.
 */
public class ZeroLengthArrayCopyTest extends GraalCompilerTest {

    public static int intPastEnd(int[] source) {
        int[] destination = new int[4];
        System.arraycopy(source, 5, destination, 0, 0);
        return destination[1];
    }

    public static int longPastEnd(long[] source) {
        long[] destination = new long[4];
        System.arraycopy(source, 3, destination, 0, 0);
        return destination.length;
    }

    public static int objectPastEnd(Object[] source) {
        Object[] destination = new Object[4];
        System.arraycopy(source, 9, destination, 2, 0);
        return destination.length;
    }

    public static int atEnd(int[] source) {
        int[] destination = new int[4];
        System.arraycopy(source, 2, destination, 0, 0);
        return destination.length;
    }

    public static int negativePosition(int[] source) {
        int[] destination = new int[4];
        System.arraycopy(source, -1, destination, 0, 0);
        return destination.length;
    }

    public static int atStart(int[] source) {
        int[] destination = new int[4];
        System.arraycopy(source, 0, destination, 0, 0);
        return destination.length;
    }

    @Test
    public void testPastEnd() {
        test("intPastEnd", new int[2]);
        test("longPastEnd", new long[2]);
        test("objectPastEnd", (Object) new Object[2]);
    }

    @Test
    public void testInBounds() {
        test("intPastEnd", new int[8]);
        test("longPastEnd", new long[8]);
        test("objectPastEnd", (Object) new Object[16]);
        test("atEnd", new int[2]);
        test("atStart", new int[0]);
    }

    @Test
    public void testNegativePosition() {
        test("negativePosition", new int[2]);
    }

    @Test
    public void testNullSource() {
        test("atStart", (Object) null);
    }
}
