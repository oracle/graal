/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.api.test.ArrayUtilsTest.toCharArray;
import static jdk.graal.compiler.truffle.test.strings.TStringTest.testParameterized;

import org.junit.Test;

import com.oracle.truffle.api.ArrayUtils;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.truffle.substitutions.TruffleInvocationPlugins;

import java.util.Arrays;

public class ArrayUtilsIndexOfWithMaskTest extends GraalCompilerTest {

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        TruffleInvocationPlugins.register(getBackend().getTarget().arch, invocationPlugins);
        super.registerInvocationPlugins(invocationPlugins);
    }

    public static Iterable<Object[]> data() {
        return com.oracle.truffle.api.test.ArrayUtilsIndexOfWithMaskTest.data().stream().map(args -> Arrays.copyOf(args, args.length - 2)).toList();
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
        test("indexOfWithORMaskByteArray", args);
    }

    private void testCharArrayCase(Object[] args) {
        test("indexOfWithORMaskCharArray", args);
    }

    private void testStringCase(Object[] args) {
        test("indexOfWithORMaskString", args);
    }

    public static int indexOfWithORMaskByteArray(String haystack, int fromIndex, int maxIndex, String needle, String mask) {
        return ArrayUtils.indexOfWithOrMask(toByteArray(haystack), fromIndex, maxIndex, toByteArray(needle), toByteArray(mask));
    }

    public static int indexOfWithORMaskCharArray(String haystack, int fromIndex, int maxIndex, String needle, String mask) {
        return ArrayUtils.indexOfWithOrMask(haystack.toCharArray(), fromIndex, maxIndex, needle.toCharArray(), toCharArray(mask));
    }

    public static int indexOfWithORMaskString(String haystack, int fromIndex, int maxIndex, String needle, String mask) {
        return ArrayUtils.indexOfWithOrMask(haystack, fromIndex, maxIndex, needle, mask);
    }
}
