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
import com.oracle.graal.runtime.*;

/**
 * The following tests perform object/array equality and assignments in various ways. The selected
 * cases have been the problematic ones while implementing the Compressed Oops support.
 */
public class CompressedOopTest extends GraalCompilerTest {

    private final MetaAccessProvider metaAccess;

    public CompressedOopTest() {
        this.metaAccess = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getProviders().getMetaAccess();
    }

    private HotSpotInstalledCode getInstalledCode(String name) throws Exception {
        final Method method = CompressedOopTest.class.getMethod(name, Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaMethod(method);
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
        installedBenchmarkCode.executeVarargs(source.a, destination.a, source.a);
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
        installedBenchmarkCode.executeVarargs(cas, null, initial);
        Assert.assertEquals(cas.get(), initial);
        installedBenchmarkCode.executeVarargs(cas, initial, replacement);
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
        installedBenchmarkCode.executeVarargs(source1.value, source2.value, result);
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
        installedBenchmarkCode.executeVarargs(source1, source2, result);
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
        String c = (String) installedBenchmarkCode.executeVarargs(a, b);
        Assert.assertTrue(c.equals("Test String"));
    }

    public static String stringCopyTest(Object c1, Object c2) {
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
            if (!installedBenchmarkCode.isValid()) {
                // This can get invalidated due to lack of MDO update
                installedBenchmarkCode = getInstalledCode("queueTest");
            }
            installedBenchmarkCode.executeVarargs(q, objects[j]);
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
    public static void queueTest(Object c1, Object c2) {
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
        Object[] array = (Object[]) installedBenchmarkCode.executeVarargs(list);
        Assert.assertTrue(list.size() == array.length);
        int i = 0;
        for (Object obj : list) {
            Assert.assertTrue(obj == array[i]);
            i++;
        }
    }

    @SuppressWarnings("unchecked")
    public static Object[] unmodListTest(Object c1) {
        List<Object> queue = (ArrayList<Object>) c1;
        Object[] result = Collections.unmodifiableCollection(queue).toArray(new Object[queue.size()]);
        return result;
    }

    @Test
    public void test9() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("unmodListTest");
        List<Object> list = new ArrayList<>();
        Object[] array = (Object[]) installedBenchmarkCode.executeVarargs(list, null, null);
        Assert.assertTrue(list.size() == array.length);
    }

    public void test10() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("constantTest");
        Container c = new Container();
        Assert.assertFalse((boolean) installedBenchmarkCode.executeVarargs(c, null, null));
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
        boolean result = ((Boolean) (installedBenchmarkCode.executeVarargs(s1, s2))).booleanValue();
        Assert.assertTrue(result);
    }

    public static Boolean stringEqualsTest(Object c1, Object c2, @SuppressWarnings("unused") Object c3) {
        return ((String) c1).equals(c2);
    }

    @Test
    public void test12() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("stringConstantEqualsTest");
        String s1 = new String("Test");
        boolean result = ((Boolean) (installedBenchmarkCode.executeVarargs(s1))).booleanValue();
        Assert.assertTrue(result);
    }

    public static Boolean stringConstantEqualsTest(Object c1, @SuppressWarnings("unused") Object c2, @SuppressWarnings("unused") Object c3) {
        return "Test".equals(c1);
    }

    @SuppressWarnings("unchecked")
    public static Object[] unmodListTestByte(Object c1, @SuppressWarnings("unused") Object c2, @SuppressWarnings("unused") Object c3) {
        List<Byte> queue = (ArrayList<Byte>) c1;
        Byte[] result = Collections.unmodifiableCollection(queue).toArray(new Byte[queue.size()]);
        return result;
    }

    @Test
    public void test13() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("unmodListTestByte");
        List<Byte> list = new ArrayList<>();
        Byte[] array = (Byte[]) installedBenchmarkCode.executeVarargs(list, null, null);
        Assert.assertTrue(list.size() == array.length);
    }

    @Test
    public void test14() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("stringBufferTest");
        StringBuffer buffer = new StringBuffer("TestTestTestTestTestTestTest");
        Assert.assertTrue(buffer.length() == 28);
        String a = new String("TestTestTestTestTestTestTest");
        installedBenchmarkCode.executeVarargs(buffer, a.toCharArray());
        Assert.assertTrue(buffer.length() == 56);
        Assert.assertTrue(buffer.toString().equals("TestTestTestTestTestTestTestTestTestTestTestTestTestTest"));
    }

    public static void stringBufferTest(Object c1, Object c2) {
        StringBuffer source = (StringBuffer) c1;
        char[] add = (char[]) c2;
        for (int i = 0; i < add.length; i++) {
            source.append(add[i]);
        }
    }

    @Test
    public void test15() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("stringBufferTestIn");
        installedBenchmarkCode.executeVarargs();
    }

    public static void stringBufferTestIn() {
        StringBuffer buffer = new StringBuffer("TestTestTestTestTestTestTest");
        Assert.assertTrue(buffer.length() == 28);
        String a = new String("TestTestTestTestTestTestTest");
        char[] add = a.toCharArray();
        for (int i = 0; i < add.length; i++) {
            buffer.append(add[i]);
        }
        Assert.assertTrue(buffer.length() == 56);
        Assert.assertTrue(buffer.toString().equals("TestTestTestTestTestTestTestTestTestTestTestTestTestTest"));
    }

    @Test
    public void test16() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("stringBufferArrayCopy");
        installedBenchmarkCode.executeVarargs();
    }

    public static void stringBufferArrayCopy() {
        StringBuffer buffer = new StringBuffer("TestTestTestTestTestTestTest");
        Assert.assertTrue(buffer.length() == 28);
        String a = new String("TestTestTestTestTestTestTest");
        char[] dst = new char[buffer.length() * 2];
        System.arraycopy(buffer.toString().toCharArray(), 0, dst, 0, buffer.length());
        System.arraycopy(a.toCharArray(), 0, dst, buffer.length(), buffer.length());
        Assert.assertTrue(dst.length == 56);
        Assert.assertTrue(new String(dst).equals("TestTestTestTestTestTestTestTestTestTestTestTestTestTest"));
    }

    @Test
    public void test17() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("stringFormat");
        installedBenchmarkCode.executeVarargs();
    }

    @SuppressWarnings("unused")
    public static void stringFormat(Object c1, Object c2, Object c3) {
        String.format("Hello %d", 0);
        String.format("Hello %d", -11);
        String.format("Hello %d", -2147483648);
    }

    @Test
    public void test18() throws Exception {
        HotSpotInstalledCode installedBenchmarkCode = getInstalledCode("stringBuilder");
        StringBuilder b = (StringBuilder) installedBenchmarkCode.executeVarargs();
        Assert.assertTrue(b.capacity() == 16);
        Assert.assertTrue(b.length() == 0);
    }

    @SuppressWarnings("unused")
    public static Object stringBuilder(Object c1, Object c2, Object c3) {
        return new StringBuilder();
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
