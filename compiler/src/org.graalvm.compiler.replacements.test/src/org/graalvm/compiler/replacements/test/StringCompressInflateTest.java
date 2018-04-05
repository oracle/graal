/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.replacements.StringSubstitutions;
import org.graalvm.compiler.replacements.amd64.AMD64StringLatin1InflateNode;
import org.graalvm.compiler.replacements.amd64.AMD64StringUTF16CompressNode;
import org.graalvm.compiler.test.AddExports;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests {@link StringSubstitutions}.
 */
@AddExports({"java.base/java.lang"})

public final class StringCompressInflateTest extends MethodSubstitutionTest {

    final static int N = 1000;

    @Test
    public void testStringLatin1Inflate() throws ClassNotFoundException
    {
        if (Java8OrEarlier)
            return;   // StringLatin1.inflate introduced in Java 9.
        if (!(getTarget().arch instanceof AMD64))
            return;   // Test case is (currently) AMD64 only.

        Class<?> java_class = Class.forName("java.lang.StringLatin1");
        Class<?> test_class = AMD64StringLatin1InflateNode.class;

        TestMethods tms = new TestMethods("testInflate", java_class,
                                              "inflate", byte[].class, int.class,
                                                         char[].class, int.class, int.class);

        tms.testSubstitution(test_class);

        for (int i = 0; i < N; i++)
        {
            byte[] src = fillLatinBytes(new byte[i2sz(i)]);
            char[] dst = new char[i2sz(i)];

            // Invoke void StringLatin1.inflate(byte[], 0, char[], 0, length)
            Object nil = tms.invokeJava(src, 0, dst, 0, i2sz(i));

            assert nil == null;
            String str = new String(src);

            // Invoke char[] testInflate(String)
            char[] inflate1 = (char[]) tms.invokeTest(str);

            assertDeepEquals(dst, inflate1);

            // Invoke char[] testInflate(String) through code handle.
            char[] inflate2 = (char[]) tms.invokeCode(str);

            assertDeepEquals(dst, inflate2);
        }
    }
    
    @Test
    public void testStringUTF16Compress() throws ClassNotFoundException
    {
        if (Java8OrEarlier)
            return;   // StringUTF16.compress introduced in Java 9.
        if (!(getTarget().arch instanceof AMD64))
            return;   // Test case is (currently) AMD64 only.

        Class<?> java_class = Class.forName("java.lang.StringUTF16");
        Class<?> test_class = AMD64StringUTF16CompressNode.class;

        TestMethods tms = new TestMethods("testCompress", java_class,
                                              "compress", char[].class, int.class,
                                                          byte[].class, int.class, int.class);
        tms.testSubstitution(test_class);

        for (int i = 0; i < N; i++)
        {
            char[] src = fillLatinChars(new char[i2sz(i)]);
            byte[] dst = new byte[i2sz(i)];

            // Invoke int StringUTF16.compress(char[], 0, byte[], 0, length)
            Object len = tms.invokeJava(src, 0, dst, 0, i2sz(i));

            assert (int)len == i2sz(i);
             
            // Invoke String testCompress(char[])
            String str1 = (String) tms.invokeTest(src);

            assertDeepEquals(dst, str1.getBytes());

            // Invoke String testCompress(char[]) through code handle.
            String str2 = (String) tms.invokeCode(src);

            assertDeepEquals(dst, str2.getBytes());
        }
    }

    private class TestMethods
    {
        TestMethods(String test_mname, Class<?> java_class, String java_mname, Class<?>... params)
        {
            java_method = getResolvedJavaMethod(java_class, java_mname, params);
            test_method = getResolvedJavaMethod(test_mname);
            test_graph  = testGraph(test_mname);

            assert java_method != null;
            assert test_method != null;

            // Force the test method to be compiled.
            test_code = getCode(test_method);

            //HotSpotCodeCacheProvider ccp = (HotSpotCodeCacheProvider) getCodeCache();
            //System.out.println(ccp.disassemble(getCode(java_method)));
            //System.out.println(ccp.disassemble(test_code));

            assert test_code != null;
        }

        StructuredGraph replacementGraph()
        {
            return getReplacements().getSubstitution(java_method, -1, false, null);
        }
        StructuredGraph testMethodGraph() { return test_graph; }

        void testSubstitution(Class<?> intrinsic_class)
        {
            // Check if the resulting graph contains the expected node.
            if (replacementGraph() == null) {
                assertInGraph(testMethodGraph(), intrinsic_class);
            }
        }

        Object invokeJava(Object... args)
        {
            return invokeSafe(java_method, null, args);
        }

        Object invokeTest(Object... args)
        {
            return invokeSafe(test_method, null, args);
        }

        Object invokeCode(Object... args)
        {
            return executeVarargsSafe(test_code, args);
        }

        // Private data section:
        private ResolvedJavaMethod java_method;
        private ResolvedJavaMethod test_method;
        private StructuredGraph    test_graph;
        private InstalledCode      test_code;
    }

    private static byte[] fillLatinBytes(byte[] v)
    {
        for (int ch = 32, i = 0; i < v.length; i++)
        {
            v[i] = (byte) ch;
            ch = ch == 126 ? 32 : ch + 1; //160 : (ch == 255 ? 32 : ch + 1);
        }
        return v;
    }

    private static char[] fillLatinChars(char[] v)
    {
        for (int ch = 32, i = 0; i < v.length; i++)
        {
            v[i] = (char) ch;
            ch = ch == 126 ? 32 : ch + 1; //160 : (ch == 255 ? 32 : ch + 1);
        }
        return v;
    }

    private static int i2sz(int i) { return i * 3; }
    
    @SuppressWarnings("all")
    public static String testCompress(char[] a) {
        return new String(a);
    }
/*
    public int testCompress2(char[] a, byte[] b) {
        assert a.length == b.length;

        java_method = getResolvedJavaMethod(java_class, java_mname, params);

        this.invokeSafe(javaMethod, receiver, args)
        return StringUTF16.compress(a, 0, b, 0, a.length);
    }
*/
    @SuppressWarnings("all")
    public static char[] testInflate(String a) {
        return a.toCharArray();
    }

}
