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
package org.graalvm.compiler.truffle.test;

import static com.oracle.truffle.api.test.ArrayUtilsTest.toByteArray;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.truffle.compiler.amd64.substitutions.TruffleAMD64InvocationPlugins;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.ArrayUtils;

@RunWith(Parameterized.class)
public class ArrayUtilsIndexOfWithMaskTest extends GraalCompilerTest {

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        new TruffleAMD64InvocationPlugins().registerInvocationPlugins(getProviders(), getBackend().getTarget().arch, invocationPlugins, true);
        super.registerInvocationPlugins(invocationPlugins);
    }

    @Parameters(name = "{index}: haystack \"{0}\" fromIndex {1} maxIndex {2} needle \"{3}\" mask \"{4}\"")
    public static Iterable<Object[]> data() {
        return com.oracle.truffle.api.test.ArrayUtilsIndexOfWithMaskTest.data();
    }

    private final String haystack;
    private final int fromIndex;
    private final int length;
    private final String needle;
    private final String mask;

    public ArrayUtilsIndexOfWithMaskTest(String haystack, int fromIndex, int length, String needle, String mask, @SuppressWarnings("unused") int expectedB, @SuppressWarnings("unused") int expectedC) {
        this.haystack = haystack;
        this.fromIndex = fromIndex;
        this.length = length;
        this.needle = needle;
        this.mask = mask;
    }

    @Test
    public void testByteArray() {
        test("indexOfWithORMaskByteArray", haystack, fromIndex, length, needle, mask);
    }

    @Test
    public void testCharArray() {
        test("indexOfWithORMaskCharArray", haystack, fromIndex, length, needle, mask);
    }

    @Test
    public void testString() {
        test("indexOfWithORMaskString", haystack, fromIndex, length, needle, mask);
    }

    public static int indexOfWithORMaskByteArray(String haystack, int fromIndex, int maxIndex, String needle, String mask) {
        return ArrayUtils.indexOfWithOrMask(toByteArray(haystack), fromIndex, maxIndex, toByteArray(needle), toByteArray(mask));
    }

    public static int indexOfWithORMaskCharArray(String haystack, int fromIndex, int maxIndex, String needle, String mask) {
        return ArrayUtils.indexOfWithOrMask(haystack.toCharArray(), fromIndex, maxIndex, needle.toCharArray(), mask.toCharArray());
    }

    public static int indexOfWithORMaskString(String haystack, int fromIndex, int maxIndex, String needle, String mask) {
        return ArrayUtils.indexOfWithOrMask(haystack, fromIndex, maxIndex, needle, mask);
    }
}
