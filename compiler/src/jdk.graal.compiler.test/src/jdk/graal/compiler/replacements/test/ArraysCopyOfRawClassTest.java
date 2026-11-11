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

import java.util.Arrays;

import org.junit.Test;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.options.OptionValues;

/**
 * {@link Arrays#copyOf(Object[], int, Class)} takes any class through a raw type. For a class that
 * is not an array class the call fails at run time, and it must not fail in the compiler.
 */
public class ArraysCopyOfRawClassTest extends GraalCompilerTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object[] copyOfNonArrayClass() {
        Object[] source = new Object[3];
        return Arrays.copyOf(source, 2, (Class) String.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object[] copyOfRangeNonArrayClass() {
        Object[] source = new Object[3];
        return Arrays.copyOfRange(source, 1, 2, (Class) String.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object[] copyOfParameter(Object[] source) {
        return Arrays.copyOf(source, 2, (Class) String.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object[] copyOfRangeParameter(Object[] source) {
        return Arrays.copyOfRange(source, 1, 2, (Class) String.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object[] copyOfNullClass(boolean range) {
        Object[] source = new Object[3];
        return range ? Arrays.copyOfRange(source, 1, 2, (Class) null) : Arrays.copyOf(source, 2, (Class) null);
    }

    @Test
    public void testLocalSource() {
        test("copyOfNonArrayClass");
        test("copyOfRangeNonArrayClass");
    }

    @Test
    public void testParameter() {
        test("copyOfParameter", (Object) new Object[3]);
        test("copyOfRangeParameter", (Object) new Object[3]);
    }

    @Test
    public void testNullClass() {
        test("copyOfNullClass", false);
        test("copyOfNullClass", true);
    }

    @Test
    public void testWithoutEscapeAnalysis() {
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.PartialEscapeAnalysis, false);
        test(options, "copyOfNonArrayClass");
        test(options, "copyOfRangeNonArrayClass");
        test(options, "copyOfParameter", (Object) new Object[3]);
        test(options, "copyOfRangeParameter", (Object) new Object[3]);
    }
}
