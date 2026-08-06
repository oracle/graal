/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import static com.oracle.truffle.api.test.ArrayUtilsTest.toByteArray;
import static jdk.graal.compiler.truffle.test.strings.TStringTest.testParameterized;

import java.util.Arrays;

import org.junit.Test;

import com.oracle.truffle.api.ArrayUtils;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.truffle.substitutions.TruffleInvocationPlugins;

public class ArrayUtilsRegionEqualsWithMaskTest extends GraalCompilerTest {

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        TruffleInvocationPlugins.register(getBackend().getTarget().arch, invocationPlugins);
        super.registerInvocationPlugins(invocationPlugins);
    }

    public static Iterable<Object[]> data() {
        return com.oracle.truffle.api.test.ArrayUtilsRegionEqualsWithMaskTest.data().stream().map(args -> Arrays.copyOf(args, args.length - 2)).toList();
    }

    @Test
    public void testByteArray() {
        testParameterized(data(), this::testByteArrayCase);
    }

    @Test
    public void testCharArray() {
        testParameterized(data(), this::testCharArrayCase);
    }

    @Test
    public void testString() {
        testParameterized(data(), this::testStringCase);
    }

    private void testByteArrayCase(Object[] args) {
        test("regionEqualsWithORMaskByteArray", args);
    }

    private void testCharArrayCase(Object[] args) {
        test("regionEqualsWithORMaskCharArray", args);
    }

    private void testStringCase(Object[] args) {
        test("regionEqualsWithORMaskString", args);
    }

    public static boolean regionEqualsWithORMaskByteArray(String a1, int fromIndex1, String a2, int fromIndex2, String mask, int length) {
        return ArrayUtils.regionEqualsWithOrMask(toByteArray(a1), fromIndex1, toByteArray(a2), fromIndex2, length, toByteArray(mask));
    }

    public static boolean regionEqualsWithORMaskCharArray(String a1, int fromIndex1, String a2, int fromIndex2, String mask, int length) {
        return ArrayUtils.regionEqualsWithOrMask(a1.toCharArray(), fromIndex1, a2.toCharArray(), fromIndex2, length, mask == null ? null : mask.toCharArray());
    }

    public static boolean regionEqualsWithORMaskString(String a1, int fromIndex1, String a2, int fromIndex2, String mask, int length) {
        return ArrayUtils.regionEqualsWithOrMask(a1, fromIndex1, a2, fromIndex2, length, mask);
    }
}
