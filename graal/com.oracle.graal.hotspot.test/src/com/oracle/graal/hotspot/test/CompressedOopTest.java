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

    public CompressedOopTest() {
        this.metaAccessProvider = Graal.getRequiredCapability(MetaAccessProvider.class);
    }

    private HotSpotInstalledCode getInstalledCode(String name) throws Exception {
        final Method method = CompressedOopTest.class.getMethod(name, Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) metaAccessProvider.lookupJavaMethod(method);
        final HotSpotInstalledCode installedBenchmarkCode = (HotSpotInstalledCode) getCode(javaMethod, parse(method));
        return installedBenchmarkCode;
    }

    @Test
    public void test() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("fieldTest");
        Container c1 = new Container();
        Assert.assertEquals(c1.b, installedBenchmarkCode.executeVarargs(c1, c1, c1));
    }

    public static Object fieldTest(Object c1, @SuppressWarnings("unused") Object c2, @SuppressWarnings("unused") Object c3) {
        ((Container) c1).a = ((Container) c1).b;
        return ((Container) c1).a;
    }

    @Test
    public void test1() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("arrayTest");
        ArrayContainer ac = new ArrayContainer();
        Assert.assertEquals(ac.a[9], installedBenchmarkCode.executeVarargs(ac.a, 0, 9));
        Assert.assertEquals(ac.a[8], installedBenchmarkCode.executeVarargs(ac.a, 1, 8));
        Assert.assertEquals(ac.a[7], installedBenchmarkCode.executeVarargs(ac.a, 2, 7));
        Assert.assertEquals(ac.a[6], installedBenchmarkCode.executeVarargs(ac.a, 3, 6));
        Assert.assertEquals(ac.a[5], installedBenchmarkCode.executeVarargs(ac.a, 4, 5));
        Assert.assertEquals(ac.a[4], installedBenchmarkCode.executeVarargs(ac.a, 5, 4));
        Assert.assertEquals(ac.a[3], installedBenchmarkCode.executeVarargs(ac.a, 6, 3));
        Assert.assertEquals(ac.a[2], installedBenchmarkCode.executeVarargs(ac.a, 7, 2));
        Assert.assertEquals(ac.a[1], installedBenchmarkCode.executeVarargs(ac.a, 8, 1));
        Assert.assertEquals(ac.a[0], installedBenchmarkCode.executeVarargs(ac.a, 9, 0));
    }

    public static Object arrayTest(Object c1, Object c2, Object c3) {
        Object[] array = (Object[]) c1;
        int initialIndex = ((Integer) c2).intValue();
        int replacingIndex = ((Integer) c3).intValue();
        array[initialIndex] = array[replacingIndex];
        return array[initialIndex];
    }

    @Test
    public void test2() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("arrayCopyTest");
        ArrayContainer source = new ArrayContainer();
        ArrayContainer destination = new ArrayContainer();
        Assert.assertEquals(source.a.length, destination.a.length);
        Assert.assertFalse(Arrays.equals(source.a, destination.a));
        installedBenchmarkCode.execute(source.a, destination.a, source.a);
        Assert.assertArrayEquals(source.a, destination.a);
    }

    public static void arrayCopyTest(Object c1, Object c2, @SuppressWarnings("unused") Object c3) {
        Object[] source = (Object[]) c1;
        Object[] destination = (Object[]) c2;
        System.arraycopy(source, 0, destination, 0, source.length);
    }

    @Test
    public void test3() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("compareAndSwapTest");
        Object initial = new Object();
        Object replacement = new Object();
        AtomicReference<Object> cas = new AtomicReference<>();
        Assert.assertEquals(cas.get(), null);
        installedBenchmarkCode.execute(cas, null, initial);
        Assert.assertEquals(cas.get(), initial);
        installedBenchmarkCode.execute(cas, initial, replacement);
        Assert.assertEquals(cas.get(), replacement);
    }

    @SuppressWarnings("unchecked")
    public static void compareAndSwapTest(Object c1, Object c2, Object c3) throws ClassCastException {
        AtomicReference<Object> cas = (AtomicReference<Object>) c1;
        cas.compareAndSet(c2, c3);
    }

    @Test
    public void test4() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("charArrayCopyTest");
        StringContainer1 source1 = new StringContainer1();
        StringContainer2 source2 = new StringContainer2();
        char[] result = new char[source1.value.length + source2.value.length];
        installedBenchmarkCode.execute(source1.value, source2.value, result);
        Assert.assertArrayEquals(new char[]{'T', 'e', 's', 't', ' ', 'S', 't', 'r', 'i', 'n', 'g'}, result);
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

    @Test
    public void test5() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("charContainerArrayCopyTest");
        StringContainer1 source1 = new StringContainer1();
        StringContainer2 source2 = new StringContainer2();
        char[] result = new char[source1.value.length + source2.value.length];
        installedBenchmarkCode.execute(source1, source2, result);
        Assert.assertArrayEquals(new char[]{'T', 'e', 's', 't', ' ', 'S', 't', 'r', 'i', 'n', 'g'}, result);
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

    @Test
    public void test6() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("stringCopyTest");
        String a = new String("Test ");
        String b = new String("String");
        String c = (String) installedBenchmarkCode.execute(a, b, null);
        Assert.assertTrue(c.equals("Test String"));
    }

    public static String stringCopyTest(Object c1, Object c2, @SuppressWarnings("unused") Object c3) {
        String source = (String) c1;
        String destination = (String) c2;
        return source + destination;
    }

    @Test
    public void test7() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("queueTest");
        ArrayDeque<Object> q = new ArrayDeque<>();
        Object[] objects = new Object[512];
        for (int i = 0; i < objects.length; i++) {
            objects[i] = new Object();
        }
        int j = 0;
        while (j < objects.length) {
            installedBenchmarkCode.execute(q, objects[j], null);
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

    @SuppressWarnings("unchecked")
    public static void queueTest(Object c1, Object c2, @SuppressWarnings("unused") Object c3) {
        ArrayDeque<Object> queue = (ArrayDeque<Object>) c1;
        queue.add(c2);
    }

    @Test
    public void test8() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("unmodListTest");
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < 512; i++) {
            list.add(new Object());
        }
        Object[] array = (Object[]) installedBenchmarkCode.execute(list, null, null);
        Assert.assertTrue(list.size() == array.length);
        int i = 0;
        for (Object obj : list) {
            Assert.assertTrue(obj == array[i]);
            i++;
        }
    }

    @SuppressWarnings("unchecked")
    public static Object[] unmodListTest(Object c1, @SuppressWarnings("unused") Object c2, @SuppressWarnings("unused") Object c3) {
        List<Object> queue = (ArrayList<Object>) c1;
        Object[] result = Collections.unmodifiableCollection(queue).toArray(new Object[queue.size()]);
        return result;
    }

    @Test
    public void test9() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("unmodListTest");
        List<Object> list = new ArrayList<>();
        Object[] array = (Object[]) installedBenchmarkCode.execute(list, null, null);
        Assert.assertTrue(list.size() == array.length);
    }

    public void test10() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("constantTest");
        Container c = new Container();
        Assert.assertFalse((boolean) installedBenchmarkCode.execute(c, null, null));
    }

    public static Boolean constantTest(Object c1, @SuppressWarnings("unused") Object c2, @SuppressWarnings("unused") Object c3) {
        ConstantContainer container = (ConstantContainer) c1;
        return container.a.equals(container.b);
    }

    @Test
    public void test11() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("stringEqualsTest");
        String s1 = new String("Test");
        String s2 = new String("Test");
        boolean result = ((Boolean) (installedBenchmarkCode.execute(s1, s2, null))).booleanValue();
        Assert.assertTrue(result);
    }

    public static Boolean stringEqualsTest(Object c1, Object c2, @SuppressWarnings("unused") Object c3) {
        return ((String) c1).equals(c2);
    }

    @Test
    public void test12() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("stringConstantEqualsTest");
        String s1 = new String("Test");
        boolean result = ((Boolean) (installedBenchmarkCode.execute(s1, null, null))).booleanValue();
        Assert.assertTrue(result);
    }

    public static Boolean stringConstantEqualsTest(Object c1, @SuppressWarnings("unused") Object c2, @SuppressWarnings("unused") Object c3) {
        return "Test".equals(c1);
    }

    static class Container {

        public Object a = new Object();
        public Object b = new Object();
    }

    static class ArrayContainer {

        public Object[] a = new Object[10];

        public ArrayContainer() {
            for (int i = 0; i < 10; i++) {
                a[i] = new Object();
            }
        }
    }

    static class HashMapContainer {

        public HashMap<Object, Object> a = new HashMap<>();

        public HashMapContainer() {
            for (int i = 0; i < 10; i++) {
                a.put(new Object(), new Object());
            }
        }
    }

    static class StringContainer1 {

        public char[] value = new char[5];

        public StringContainer1() {
            value[0] = 'T';
            value[1] = 'e';
            value[2] = 's';
            value[3] = 't';
            value[4] = ' ';

        }
    }

    static class StringContainer2 {

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

    static class ConstantContainer {

        public final Object a = new Object();
        public final Object b = new Object();

        public ConstantContainer() {

        }
    }

}
