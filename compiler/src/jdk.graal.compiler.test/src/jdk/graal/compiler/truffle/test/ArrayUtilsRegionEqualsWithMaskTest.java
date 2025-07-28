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

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.truffle.substitutions.TruffleInvocationPlugins;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.ArrayUtils;

@RunWith(Parameterized.class)
public class ArrayUtilsRegionEqualsWithMaskTest extends GraalCompilerTest {

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        TruffleInvocationPlugins.register(getBackend().getTarget().arch, invocationPlugins);
        super.registerInvocationPlugins(invocationPlugins);
    }

    @Parameters(name = "{index}: fromIndex1 {1} fromIndex2 {3} length {5} mask {4}")
    public static Iterable<Object[]> data() {
        return com.oracle.truffle.api.test.ArrayUtilsRegionEqualsWithMaskTest.data();
    }

    private final String a1;
    private final int fromIndex1;
    private final String a2;
    private final int fromIndex2;
    private final int length;
    private final String mask;

    public ArrayUtilsRegionEqualsWithMaskTest(String a1, int fromIndex1, String a2, int fromIndex2, String mask, int length, @SuppressWarnings("unused") boolean expectedByte,
                    @SuppressWarnings("unused") boolean expectedChar) {
        this.a1 = a1;
        this.fromIndex1 = fromIndex1;
        this.a2 = a2;
        this.fromIndex2 = fromIndex2;
        this.length = length;
        this.mask = mask;
    }

    @Test
    public void testByteArray() {
        test("regionEqualsWithORMaskByteArray", a1, fromIndex1, a2, fromIndex2, length, mask);
    }

    @Test
    public void testCharArray() {
        test("regionEqualsWithORMaskCharArray", a1, fromIndex1, a2, fromIndex2, length, mask);
    }

    @Test
    public void testString() {
        test("regionEqualsWithORMaskString", a1, fromIndex1, a2, fromIndex2, length, mask);
    }

    public static boolean regionEqualsWithORMaskByteArray(String a1, int fromIndex1, String a2, int fromIndex2, int length, String mask) {
        return ArrayUtils.regionEqualsWithOrMask(toByteArray(a1), fromIndex1, toByteArray(a2), fromIndex2, length, toByteArray(mask));
    }

    public static boolean regionEqualsWithORMaskCharArray(String a1, int fromIndex1, String a2, int fromIndex2, int length, String mask) {
        return ArrayUtils.regionEqualsWithOrMask(a1.toCharArray(), fromIndex1, a2.toCharArray(), fromIndex2, length, mask == null ? null : mask.toCharArray());
    }

    public static boolean regionEqualsWithORMaskString(String a1, int fromIndex1, String a2, int fromIndex2, int length, String mask) {
        return ArrayUtils.regionEqualsWithOrMask(a1, fromIndex1, a2, fromIndex2, length, mask);
    }
}
