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

import java.io.UnsupportedEncodingException;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;


/**
 * Test intrinsic/node substitutions for (innate) methods StringLatin1.inflate
 * and StringUTF16.compress provided by {@link AMD64StringLatin1Substitutions}
 * and {@link AMD64StringUTF16Substitutions}.
 */
@AddExports({"java.base/java.lang"})
public final class StringCompressInflateTest extends MethodSubstitutionTest {

    final static int N = 1000;

    @Test
    public void testStringLatin1Inflate() throws ClassNotFoundException,
                                                 UnsupportedEncodingException
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

            // Perform a sanity check:
            for (int j = 0; j < i; j++)
            {
                assert (dst[j] & 0xff00) == 0;
                assert (32 <= dst[j] && dst[j] <= 126) ||
                      (160 <= dst[j] && dst[j] <= 255);
                assert ((byte)dst[j] == src[j]);
            }

            String str = new String(src, 0, src.length, "ISO8859_1");

            for (int j = 0; j < src.length; j++)
            {
                assert ((char)src[j] & 0xff) == str.charAt(j);
            }

            // Invoke char[] testInflate(String)
            char[] inflate1 = (char[]) tms.invokeTest(str);

            // Another sanity check:
            for (int j = 0; j < i; j++)
            {
                assert (inflate1[j] & 0xff00) == 0;
                assert (32 <= inflate1[j] && inflate1[j] <= 126) ||
                      (160 <= inflate1[j] && inflate1[j] <= 255);
            }

            assertDeepEquals(dst, inflate1);

            // Invoke char[] testInflate(String) through code handle.
            char[] inflate2 = (char[]) tms.invokeCode(str);

            assertDeepEquals(dst, inflate2);
        }
    }

    @Test
    public void testStringUTF16Compress() throws ClassNotFoundException,
                                                 UnsupportedEncodingException
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

            assertDeepEquals(dst, str1.getBytes("ISO8859_1"));

            // Invoke String testCompress(char[]) through code handle.
            String str2 = (String) tms.invokeCode(src);

            assertDeepEquals(dst, str2.getBytes("ISO8859_1"));
        }
    }

    @SuppressWarnings("all")
    public static String testCompress(char[] a) {
        return new String(a);
    }

    @SuppressWarnings("all")
    public static char[] testInflate(String a) {
        return a.toCharArray();
    }

    private class TestMethods
    {
        TestMethods(String test_mname, Class<?> java_class, String java_mname, Class<?>... params)
        {
            java_method = getResolvedJavaMethod(java_class, java_mname, params);
            test_method = getResolvedJavaMethod(test_mname);
            test_graph  = testGraph(test_mname, java_mname);

            assert java_method != null;
            assert test_method != null;

            // Force the test method to be compiled.
            test_code = getCode(test_method);

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
            v[i] = (byte) (ch & 0xff);
            ch = ch == 126 ? 160 : (ch == 255 ? 32 : ch + 1);
        }
        return v;
    }

    private static char[] fillLatinChars(char[] v)
    {
        for (int ch = 32, i = 0; i < v.length; i++)
        {
            v[i] = (char) (ch & 0xff);
            ch = ch == 126 ? 160 : (ch == 255 ? 32 : ch + 1);
        }
        return v;
    }

    private static int i2sz(int i) { return i * 3; }

}
