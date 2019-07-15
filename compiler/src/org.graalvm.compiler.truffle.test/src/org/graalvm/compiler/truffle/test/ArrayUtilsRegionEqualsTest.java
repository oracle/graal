/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.truffle.compiler.amd64.substitutions.TruffleAMD64InvocationPlugins;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.ArrayUtils;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class ArrayUtilsRegionEqualsTest extends GraalCompilerTest {

    private static Map<ResolvedJavaMethod, InstalledCode> cache;

    @BeforeClass
    public static void setupCache() {
        cache = new ConcurrentHashMap<>();
    }

    @AfterClass
    public static void tearDownCache() {
        cache = null;
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        new TruffleAMD64InvocationPlugins().registerInvocationPlugins(getProviders(), getBackend().getTarget().arch, invocationPlugins, true);
        super.registerInvocationPlugins(invocationPlugins);
    }

    @Parameters(name = "{index}: haystack {0} fromIndex {1} maxIndex {2} needle {3}")
    public static Iterable<Object[]> data() {
        return com.oracle.truffle.api.test.ArrayUtilsRegionEqualsTest.data();
    }

    private final String a1;
    private final int fromIndex1;
    private final String a2;
    private final int fromIndex2;
    private final int length;

    public ArrayUtilsRegionEqualsTest(String a1, int fromIndex1, String a2, int fromIndex2, int length, @SuppressWarnings("unused") int length2, @SuppressWarnings("unused") boolean expected) {
        super(cache);
        this.a1 = a1;
        this.fromIndex1 = fromIndex1;
        this.a2 = a2;
        this.fromIndex2 = fromIndex2;
        this.length = length;
    }

    @Test
    public void testString() {
        test("regionEqualsString", a1, fromIndex1, a2, fromIndex2, length);
    }

    @Test
    public void testCharArray() {
        test("regionEqualsCharArray", a1.toCharArray(), fromIndex1, a2.toCharArray(), fromIndex2, length);
    }

    @Test
    public void testByteArray() {
        test("regionEqualsByteArray", toByteArray(a1), fromIndex1, toByteArray(a2), fromIndex2, length);
    }

    public static boolean regionEqualsString(String a1, int fromIndex1, String a2, int fromIndex2, int length) {
        return ArrayUtils.regionEquals(a1, fromIndex1, a2, fromIndex2, length);
    }

    public static boolean regionEqualsCharArray(char[] a1, int fromIndex1, char[] a2, int fromIndex2, int length) {
        return ArrayUtils.regionEquals(a1, fromIndex1, a2, fromIndex2, length);
    }

    public static boolean regionEqualsByteArray(byte[] a1, int fromIndex1, byte[] a2, int fromIndex2, int length) {
        return ArrayUtils.regionEquals(a1, fromIndex1, a2, fromIndex2, length);
    }
}
