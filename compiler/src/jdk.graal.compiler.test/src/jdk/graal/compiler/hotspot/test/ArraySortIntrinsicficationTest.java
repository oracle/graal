/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.hotspot.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

import jdk.graal.compiler.util.EconomicHashMap;
import org.graalvm.collections.Pair;
import org.junit.Test;

import jdk.graal.compiler.api.test.ModuleSupport;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.vm.ci.code.InstalledCode;

public final class ArraySortIntrinsicficationTest extends HotSpotGraalCompilerTest {

    private static final int[] LONG_RUN_LENGTHS = {
                    1, 3, 8, 21, 55, 100, 1_000, 10_000, 100_000};
    private static final int[] PARALLELISMS = {
                    0, 87, 64 * (3 << 1)};

    @Test
    public void testIntSort() throws ClassNotFoundException, InvocationTargetException, IllegalAccessException {
        Class<?> klass = Class.forName("java.util.DualPivotQuicksort");
        Class<?> sorterKlass = Class.forName("java.util.DualPivotQuicksort$Sorter");

        Random rng = getRandomInstance();

        ModuleSupport.exportAndOpenAllPackagesToUnnamed("java.base");
        Method method = getMethod(klass, "sort", int[].class, int.class, int.class, int.class);
        method.setAccessible(true);

        Map<Pair<Integer, Integer>, int[]> goldensFullArray = new EconomicHashMap<>();
        Map<Pair<Integer, Integer>, int[]> goldensHalfArray = new EconomicHashMap<>();

        int[][] arrays = Arrays.stream(LONG_RUN_LENGTHS).mapToObj(len -> rng.ints(len).toArray()).toArray(int[][]::new);

        for (int[] array : arrays) {
            for (int parallelism : PARALLELISMS) {
                int[] golden = array.clone();
                method.invoke(null, golden, parallelism, 0, array.length);
                goldensFullArray.put(Pair.create(array.length, parallelism), golden);

                golden = array.clone();
                method.invoke(null, golden, parallelism, array.length / 2, array.length);
                goldensHalfArray.put(Pair.create(array.length, parallelism), golden);
            }
        }

        InstalledCode intrinsic = getCode(getResolvedJavaMethod(klass, "sort", sorterKlass, int[].class, int.class, int.class, int.class),
                        null, true, true, GraalCompilerTest.getInitialOptions());

        for (int[] array : arrays) {
            for (int parallelism : PARALLELISMS) {
                int[] golden = goldensFullArray.get(Pair.create(array.length, parallelism));
                int[] actual = array.clone();
                method.invoke(null, actual, parallelism, 0, array.length);
                assertDeepEquals(golden, actual);

                golden = goldensHalfArray.get(Pair.create(array.length, parallelism));
                actual = array.clone();
                method.invoke(null, actual, parallelism, array.length / 2, array.length);
                assertDeepEquals(golden, actual);
            }
        }

        intrinsic.invalidate();
    }
}
