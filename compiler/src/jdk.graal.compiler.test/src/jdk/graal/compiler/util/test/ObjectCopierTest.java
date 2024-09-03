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
package jdk.graal.compiler.util.test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jdk.graal.compiler.core.test.SubprocessTest;
import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.util.ObjectCopier;

/**
 * Tests for {@link ObjectCopier}.
 *
 * Run as follows to see the encoded form of objects:
 *
 * <pre>
 *     mx unittest --verbose -Ddebug.jdk.graal.compiler.util.test.ObjectCopierTest=true ObjectCopierTest
 * </pre>
 */
public class ObjectCopierTest extends SubprocessTest {

    @SuppressWarnings("unused")
    static class BaseClass {
        String baseString1 = "base1";
        int baseInt1 = 34;
        double baseDouble1 = 8765.4321D;

        static final Object BASE_SINGLETON = new Object() {
            @Override
            public String toString() {
                return "BASE_SINGLETON";
            }
        };
    }

    @SuppressWarnings("unused")
    static class TestObject extends BaseClass {
        boolean bTrue = true;
        boolean bFalse = false;
        byte byteField1 = 87;
        byte byteField2 = Byte.MAX_VALUE;
        byte byteField3 = Byte.MIN_VALUE;
        short shortField1 = -456;
        short shortField2 = Short.MIN_VALUE;
        short shortField3 = Short.MAX_VALUE;
        char charField1 = 'A';
        char charField2 = '\u4433';
        char charField3 = '\r';
        char charField4 = '\n';
        char charField5 = Character.MAX_VALUE;
        char charField6 = Character.MIN_VALUE;
        int intField1 = 0;
        int intField2 = 42;
        int intField3 = Integer.MIN_VALUE;
        int intField4 = Integer.MAX_VALUE;
        int intField5 = -1;
        float floatField1 = -1.4F;
        float floatField2 = Float.MIN_NORMAL;
        float floatField3 = Float.MIN_VALUE;
        float floatField4 = Float.MAX_VALUE;
        float floatField5 = Float.NaN;
        float floatField6 = Float.NEGATIVE_INFINITY;
        float floatField7 = Float.POSITIVE_INFINITY;
        double doubleField1 = -1.45678D;
        double doubleField2 = Double.MIN_NORMAL;
        double doubleField3 = Double.MIN_VALUE;
        double doubleField4 = Double.MAX_VALUE;
        double doubleField5 = Double.NaN;
        double doubleField6 = Double.NEGATIVE_INFINITY;
        double doubleField7 = Double.POSITIVE_INFINITY;

        boolean[] zArray = {true, false, true};
        byte[] bArray = "12340987 something random!@#".getBytes();
        short[] sArray = {123, 456, 789};
        char[] cArray = "&^%^&%__blah blah".toCharArray();
        int[] iArray = {1, 2, 3, 4};
        float[] fArray = {123.567F, 876.23F, -67.999999F};
        double[] dArray = {123444.567D, 8722226.23D, -6708987.999999D};
        Object[] oArray = {this, TEST_OBJECT_SINGLETON, BASE_SINGLETON, "last", new String[]{"in", "a", "nested", "array"}};

        static final Object TEST_OBJECT_SINGLETON = new Object() {
            @Override
            public String toString() {
                return "TEST_OBJECT_SINGLETON";
            }
        };

        private static List<Object> fieldValues(Object obj) {
            List<Object> values = new ArrayList<>();
            Class<?> c = obj.getClass();
            while (c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    try {
                        values.add(f.get(obj));
                    } catch (IllegalAccessException e) {
                        throw new AssertionError(e);
                    }
                }
                c = c.getSuperclass();
            }
            return values;
        }

        @Override
        public String toString() {
            return fieldValues(this).stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
    }

    private static final String DEBUG_PROP = "debug." + ObjectCopierTest.class.getName();
    private static final boolean DEBUG = Boolean.getBoolean(DEBUG_PROP);

    @SuppressWarnings("unchecked")
    public void testIt() {
        ClassLoader loader = getClass().getClassLoader();
        TestObject testObject = new TestObject();

        List<TimeUnit> timeUnits = List.of(TimeUnit.MICROSECONDS, TimeUnit.DAYS, TimeUnit.SECONDS);
        EconomicMap<Integer, Object> emap = EconomicMap.create();
        emap.put(42, Map.of("1", 1, "2", 2));
        emap.put(-12345, testObject);

        Map<String, String> hmap = new HashMap<>(Map.of("1000", "one thousand"));
        Map<Object, String> idMap = new IdentityHashMap<>(Map.of(new Object(), "some obj"));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("one", "normal string");
        root.put("two", "string with\nembedded\rnewline characters\r\n");
        root.put("3", ObjectCopierTest.class);
        root.put("singleton1", BaseClass.BASE_SINGLETON);
        root.put("vier", emap);
        root.put("hmap", hmap);
        root.put("idMap", idMap);
        root.put("null", null);
        root.put("singleton1_2", BaseClass.BASE_SINGLETON);
        root.put("5", timeUnits);
        root.put("6", new ArrayList<>(timeUnits));
        root.put("singleton2", TestObject.TEST_OBJECT_SINGLETON);
        root.put("singleton2_2", TestObject.TEST_OBJECT_SINGLETON);

        List<Field> externalValues = List.of(ObjectCopier.getField(BaseClass.class, "BASE_SINGLETON"),
                        ObjectCopier.getField(TestObject.class, "TEST_OBJECT_SINGLETON"));

        String encoded = ObjectCopier.encode(new ObjectCopier.Encoder(externalValues), root);
        if (DEBUG) {
            System.out.printf("encoded:%n%s%n", encoded);
        }
        Object decoded = ObjectCopier.decode(encoded, loader);
        if (DEBUG) {
            System.out.printf("root:%n%s%n", root);
            System.out.printf("decoded:%n%s%n", decoded);
        }
        String reencoded = ObjectCopier.encode(new ObjectCopier.Encoder(externalValues), decoded);
        if (DEBUG) {
            System.out.printf("reencoded:%n%s%n", reencoded);
        }
        Assert.assertEquals(encoded, reencoded);

        Map<String, Object> root2 = (Map<String, Object>) ObjectCopier.decode(reencoded, loader);

        Assert.assertSame(root.get("singleton1"), root2.get("singleton1"));
        Assert.assertSame(root.get("singleton1_2"), root2.get("singleton1_2"));
        Assert.assertSame(root2.get("singleton1"), root2.get("singleton1_2"));

        Assert.assertSame(root.get("singleton2"), root2.get("singleton2"));
        Assert.assertSame(root.get("singleton2_2"), root2.get("singleton2_2"));
        Assert.assertSame(root2.get("singleton2"), root2.get("singleton2_2"));
    }

    @Test
    public void test() throws IOException, InterruptedException {
        launchSubprocess(this::testIt,
                        "-D" + DEBUG_PROP + "=" + DEBUG,
                        "--add-opens=java.base/java.util=jdk.graal.compiler",
                        "--add-opens=java.base/java.lang=jdk.graal.compiler");
    }
}
