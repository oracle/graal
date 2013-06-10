/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.graal.hotspot.test;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.hotspot.meta.*;

/**
 * The following tests perform object/array equality and assignments in various ways. The selected
 * cases have been the problematic ones while implementing the Compressed Oops support.
 */
public class CompressedOopTest extends GraalCompilerTest {

    private final MetaAccessProvider metaAccessProvider;
    Object[] argsToBind;

    public CompressedOopTest() {
        this.metaAccessProvider = Graal.getRequiredCapability(MetaAccessProvider.class);
    }

    @Test
    public void test() throws NoSuchMethodException, SecurityException, InvalidInstalledCodeException {
        final Method fooMethod = CompressedOopTest.class.getMethod("fieldTest", Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod fooJavaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(fooMethod);
        final HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooJavaMethod, parse(fooMethod));
        argsToBind = new Object[]{fooCode};
        final Method benchmarkMethod = CompressedOopTest.class.getMethod("benchmark", HotSpotInstalledCode.class, Object.class, Object.class, Object.class);
        final ResolvedJavaMethod benchmarkJavaMethod = metaAccessProvider.lookupJavaMethod(benchmarkMethod);
        final HotSpotInstalledCode installedBenchmarkCode = (HotSpotInstalledCode) getCode(benchmarkJavaMethod, parse(benchmarkMethod));
        Container c1 = new Container();
        Assert.assertEquals(c1.b, installedBenchmarkCode.executeVarargs(argsToBind[0], c1, c1, c1));
    }

    @Test
    public void test1() throws NoSuchMethodException, SecurityException, InvalidInstalledCodeException {
        final Method fooMethod = CompressedOopTest.class.getMethod("arrayTest", Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod fooJavaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(fooMethod);
        final HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooJavaMethod, parse(fooMethod));
        argsToBind = new Object[]{fooCode};
        final Method benchmarkMethod = CompressedOopTest.class.getMethod("benchmark", HotSpotInstalledCode.class, Object.class, Object.class, Object.class);
        final ResolvedJavaMethod benchmarkJavaMethod = metaAccessProvider.lookupJavaMethod(benchmarkMethod);
        final HotSpotInstalledCode installedBenchmarkCode = (HotSpotInstalledCode) getCode(benchmarkJavaMethod, parse(benchmarkMethod));
        ArrayContainer ac = new ArrayContainer();
        Assert.assertEquals(ac.a[9], installedBenchmarkCode.executeVarargs(argsToBind[0], ac.a, 0, 9));
        Assert.assertEquals(ac.a[8], installedBenchmarkCode.executeVarargs(argsToBind[0], ac.a, 1, 8));
        Assert.assertEquals(ac.a[7], installedBenchmarkCode.executeVarargs(argsToBind[0], ac.a, 2, 7));
        Assert.assertEquals(ac.a[6], installedBenchmarkCode.executeVarargs(argsToBind[0], ac.a, 3, 6));
        Assert.assertEquals(ac.a[5], installedBenchmarkCode.executeVarargs(argsToBind[0], ac.a, 4, 5));
        Assert.assertEquals(ac.a[4], installedBenchmarkCode.executeVarargs(argsToBind[0], ac.a, 5, 4));
        Assert.assertEquals(ac.a[3], installedBenchmarkCode.executeVarargs(argsToBind[0], ac.a, 6, 3));
        Assert.assertEquals(ac.a[2], installedBenchmarkCode.executeVarargs(argsToBind[0], ac.a, 7, 2));
        Assert.assertEquals(ac.a[1], installedBenchmarkCode.executeVarargs(argsToBind[0], ac.a, 8, 1));
        Assert.assertEquals(ac.a[0], installedBenchmarkCode.executeVarargs(argsToBind[0], ac.a, 9, 0));
    }

    @Test
    public void test2() throws NoSuchMethodException, SecurityException, InvalidInstalledCodeException {
        final Method fooMethod = CompressedOopTest.class.getMethod("arrayCopyTest", Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod fooJavaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(fooMethod);
        final HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooJavaMethod, parse(fooMethod));
        argsToBind = new Object[]{fooCode};
        ArrayContainer source = new ArrayContainer();
        ArrayContainer destination = new ArrayContainer();
        Assert.assertEquals(source.a.length, destination.a.length);
        Assert.assertFalse(Arrays.equals(source.a, destination.a));
        fooCode.execute(source.a, destination.a, source.a);
        Assert.assertArrayEquals(source.a, destination.a);
    }

    @Test
    public void test3() throws NoSuchMethodException, SecurityException, InvalidInstalledCodeException {
        final Method fooMethod = CompressedOopTest.class.getMethod("compareAndSwapTest", Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod fooJavaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(fooMethod);
        final HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooJavaMethod, parse(fooMethod));
        argsToBind = new Object[]{fooCode};
        Object initial = new Object();
        Object replacement = new Object();
        AtomicReference<Object> cas = new AtomicReference<>();
        Assert.assertEquals(cas.get(), null);
        fooCode.execute(cas, null, initial);
        Assert.assertEquals(cas.get(), initial);
        fooCode.execute(cas, initial, replacement);
        Assert.assertEquals(cas.get(), replacement);
    }

    @Test
    public void test4() throws NoSuchMethodException, SecurityException, InvalidInstalledCodeException {
        final Method fooMethod = CompressedOopTest.class.getMethod("charArrayCopyTest", Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod fooJavaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(fooMethod);
        final HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooJavaMethod, parse(fooMethod));
        argsToBind = new Object[]{fooCode};
        StringContainer1 source1 = new StringContainer1();
        StringContainer2 source2 = new StringContainer2();
        char[] result = new char[source1.value.length + source2.value.length];
        fooCode.execute(source1.value, source2.value, result);
        Assert.assertArrayEquals(new char[]{'T', 'e', 's', 't', ' ', 'S', 't', 'r', 'i', 'n', 'g'}, result);
    }

    @Test
    public void test5() throws NoSuchMethodException, SecurityException, InvalidInstalledCodeException {
        final Method fooMethod = CompressedOopTest.class.getMethod("charContainerArrayCopyTest", Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod fooJavaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(fooMethod);
        final HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooJavaMethod, parse(fooMethod));
        argsToBind = new Object[]{fooCode};
        StringContainer1 source1 = new StringContainer1();
        StringContainer2 source2 = new StringContainer2();
        char[] result = new char[source1.value.length + source2.value.length];
        fooCode.execute(source1, source2, result);
        Assert.assertArrayEquals(new char[]{'T', 'e', 's', 't', ' ', 'S', 't', 'r', 'i', 'n', 'g'}, result);
    }

    @Test
    public void test6() throws NoSuchMethodException, SecurityException, InvalidInstalledCodeException {
        final Method fooMethod = CompressedOopTest.class.getMethod("stringCopyTest", Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod fooJavaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(fooMethod);
        final HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooJavaMethod, parse(fooMethod));
        argsToBind = new Object[]{fooCode};
        String a = new String("Test ");
        String b = new String("String");
        String c = (String) fooCode.execute(a, b, null);
        Assert.assertTrue(c.equals("Test String"));
    }

    @Test
    public void test7() throws NoSuchMethodException, SecurityException, InvalidInstalledCodeException {
        final Method fooMethod = CompressedOopTest.class.getMethod("queueTest", Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod fooJavaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(fooMethod);
        final HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooJavaMethod, parse(fooMethod));
        argsToBind = new Object[]{fooCode};
        ArrayDeque<Object> q = new ArrayDeque<>();
        Object[] objects = new Object[512];
        for (int i = 0; i < objects.length; i++) {
            objects[i] = new Object();
        }

        int j = 0;
        while (j < objects.length) {
            fooCode.execute(q, objects[j], null);
            j++;
        }

        System.gc();
        Assert.assertTrue(q.size() == objects.length);
        Assert.assertTrue(!q.isEmpty());

        j = 0;
        while (j < objects.length) {
            Assert.assertTrue(objects[j] == q.remove());
            j++;
        }

        Assert.assertTrue(q.size() == 0);
        Assert.assertTrue(q.isEmpty());
    }

    @Test
    public void test8() throws NoSuchMethodException, SecurityException, InvalidInstalledCodeException {
        final Method fooMethod = CompressedOopTest.class.getMethod("unmodListTest", Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod fooJavaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(fooMethod);
        final HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooJavaMethod, parse(fooMethod));
        argsToBind = new Object[]{fooCode};
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < 512; i++) {
            list.add(new Object());
        }

        Object[] array = (Object[]) fooCode.execute(list, null, null);
        Assert.assertTrue(list.size() == array.length);
        int i = 0;
        for (Object obj : list) {
            Assert.assertTrue(obj == array[i]);
            i++;
        }
    }

    @Test
    public void test9() throws NoSuchMethodException, SecurityException, InvalidInstalledCodeException {
        final Method fooMethod = CompressedOopTest.class.getMethod("unmodListTest", Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod fooJavaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(fooMethod);
        final HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooJavaMethod, parse(fooMethod));
        argsToBind = new Object[]{fooCode};
        List<Object> list = new ArrayList<>();
        Object[] array = (Object[]) fooCode.execute(list, null, null);
        Assert.assertTrue(list.size() == array.length);
    }

    public void test10() throws NoSuchMethodException, SecurityException, InvalidInstalledCodeException {
        final Method fooMethod = CompressedOopTest.class.getMethod("constantTest", Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod fooJavaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(fooMethod);
        final HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooJavaMethod, parse(fooMethod));
        argsToBind = new Object[]{fooCode};
        Container c = new Container();
        Assert.assertFalse((boolean) fooCode.execute(c, null, null));
    }

    @Test
    public void test11() throws NoSuchMethodException, SecurityException, InvalidInstalledCodeException {
        final Method fooMethod = CompressedOopTest.class.getMethod("stringEqualsTest", String.class, String.class, Object.class);
        final HotSpotResolvedJavaMethod fooJavaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(fooMethod);
        final HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooJavaMethod, parse(fooMethod));
        argsToBind = new Object[]{fooCode};
        String s1 = new String("Test");
        String s2 = new String("Test");
        boolean result = ((Boolean) (fooCode.execute(s1, s2, null))).booleanValue();
        Assert.assertTrue(result);
    }

    @Test
    public void test12() throws NoSuchMethodException, SecurityException, InvalidInstalledCodeException {
        final Method fooMethod = CompressedOopTest.class.getMethod("stringConstantEqualsTest", String.class, String.class, Object.class);
        final HotSpotResolvedJavaMethod fooJavaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(fooMethod);
        final HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooJavaMethod, parse(fooMethod));
        argsToBind = new Object[]{fooCode};
        String s1 = new String("Test");
        boolean result = ((Boolean) (fooCode.execute(s1, null, null))).booleanValue();
        Assert.assertTrue(result);
    }

    public static Object benchmark(HotSpotInstalledCode code, Object o1, Object o2, Object o3) throws InvalidInstalledCodeException {
        return code.execute(o1, o2, o3);
    }

    public static Object fieldTest(Object c1, @SuppressWarnings("unused") Object c2, @SuppressWarnings("unused") Object c3) {
        ((Container) c1).a = ((Container) c1).b;
        return ((Container) c1).a;
    }

    public static Object arrayTest(Object c1, Object c2, Object c3) {
        Object[] array = (Object[]) c1;
        int initialIndex = ((Integer) c2).intValue();
        int replacingIndex = ((Integer) c3).intValue();
        array[initialIndex] = array[replacingIndex];
        return array[initialIndex];
    }

    public static void arrayCopyTest(Object c1, Object c2, @SuppressWarnings("unused") Object c3) {
        Object[] source = (Object[]) c1;
        Object[] destination = (Object[]) c2;
        System.arraycopy(source, 0, destination, 0, source.length);
    }

    public static String stringCopyTest(Object c1, Object c2, @SuppressWarnings("unused") Object c3) {
        String source = (String) c1;
        String destination = (String) c2;
        return source + destination;
    }

    public static char[] charArrayCopyTest(Object c1, Object c2, Object c3) {
        char[] source1 = (char[]) c1;
        char[] source2 = (char[]) c2;
        char[] result = (char[]) c3;

        for (int i = 0; i < source1.length; i++) {
            result[i] = source1[i];
        }

        for (int i = 0; i < source2.length; i++) {
            result[source1.length + i] = source2[i];
        }
        return result;
    }

    public static char[] charContainerArrayCopyTest(Object c1, Object c2, Object c3) {
        char[] source1 = ((StringContainer1) c1).value;
        char[] source2 = ((StringContainer2) c2).value;
        char[] result = (char[]) c3;

        for (int i = 0; i < source1.length; i++) {
            result[i] = source1[i];
        }

        for (int i = 0; i < source2.length; i++) {
            result[source1.length + i] = source2[i];
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static void compareAndSwapTest(Object c1, Object c2, Object c3) throws ClassCastException {
        AtomicReference<Object> cas = (AtomicReference<Object>) c1;
        cas.compareAndSet(c2, c3);
    }

    @SuppressWarnings("unchecked")
    public static HashMap<Object, Object> hashMapCloneTest(Object c1, @SuppressWarnings("unused") Object c2, @SuppressWarnings("unused") Object c3) {
        HashMap<Object, Object> map = (HashMap<Object, Object>) c1;
        return (HashMap<Object, Object>) map.clone();
    }

    @SuppressWarnings("unchecked")
    public static void hashMapCopyTest(Object c1, Object c2, @SuppressWarnings("unused") Object c3) {
        HashMap<Object, Object> map = (HashMap<Object, Object>) c1;
        HashMap<Object, Object> map1 = (HashMap<Object, Object>) c2;
        map.clear();
        map.putAll(map1);
    }

    @SuppressWarnings("unchecked")
    public static void queueTest(Object c1, Object c2, @SuppressWarnings("unused") Object c3) {
        ArrayDeque<Object> queue = (ArrayDeque<Object>) c1;
        queue.add(c2);
    }

    @SuppressWarnings("unchecked")
    public static Object[] unmodListTest(Object c1, @SuppressWarnings("unused") Object c2, @SuppressWarnings("unused") Object c3) {
        List<Object> queue = (ArrayList<Object>) c1;
        Object[] result = Collections.unmodifiableCollection(queue).toArray(new Object[queue.size()]);
        return result;
    }

    public static Boolean constantTest(Object c1, @SuppressWarnings("unused") Object c2, @SuppressWarnings("unused") Object c3) {
        ConstantContainer container = (ConstantContainer) c1;
        return container.a.equals(container.b);
    }

    public static Boolean stringEqualsTest(String c1, String c2, @SuppressWarnings("unused") Object c3) {
        return c1.equals(c2);
    }

    public static Boolean stringConstantEqualsTest(String c1, @SuppressWarnings("unused") String c2, @SuppressWarnings("unused") Object c3) {
        return "Test".equals(c1);
    }

}

class Container {

    public Object a = new Object();
    public Object b = new Object();
}

class ArrayContainer {

    public Object[] a = new Object[10];

    public ArrayContainer() {
        for (int i = 0; i < 10; i++) {
            a[i] = new Object();
        }
    }
}

class HashMapContainer {

    public HashMap<Object, Object> a = new HashMap<>();

    public HashMapContainer() {
        for (int i = 0; i < 10; i++) {
            a.put(new Object(), new Object());
        }
    }
}

class StringContainer1 {

    public char[] value = new char[5];

    public StringContainer1() {
        value[0] = 'T';
        value[1] = 'e';
        value[2] = 's';
        value[3] = 't';
        value[4] = ' ';

    }
}

class StringContainer2 {

    public char[] value = new char[6];

    public StringContainer2() {
        value[0] = 'S';
        value[1] = 't';
        value[2] = 'r';
        value[3] = 'i';
        value[4] = 'n';
        value[5] = 'g';
    }
}

class ConstantContainer {

    public final Object a = new Object();
    public final Object b = new Object();

    public ConstantContainer() {

    }
}
