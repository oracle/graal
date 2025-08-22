/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.hotspot.test.HotSpotGraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.test.AddExports;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AddExports({"java.base/java.lang", "java.base/sun.nio.cs"})
public class EncodeArrayTest extends HotSpotGraalCompilerTest {

    protected final String[] testData = new String[]{
                    "A", "\uFF21", "AB", "A", "a", "Ab", "AA", "\uFF21",
                    "A\uFF21", "ABC", "AB", "ABcD", "ABCD\uFF21\uFF21", "ABCD\uFF21", "ABCDEFG\uFF21", "ABCD",
                    "ABCDEFGH\uFF21\uFF21", "\uFF22", "\uFF21\uFF22", "\uFF21A",
                    "\uFF21\uFF21",
                    "\u043c\u0430\u043c\u0430\u0020\u043c\u044b\u043b\u0430\u0020\u0440\u0430\u043c\u0443\u002c\u0020\u0440\u0430\u043c\u0430\u0020\u0441\u044a\u0435\u043b\u0430\u0020\u043c\u0430\u043c\u0443",
                    "crazy dog jumps over laszy fox",
                    "some-string\0xff",
                    "XMM-XMM-YMM-YMM-ZMM-ZMM-ZMM-ZMM-",
                    "XMM-XMM+YMM-YMM-ZMM-ZMM-ZMM-ZMM-",
                    "XMM-XMM-YMM-YMM+ZMM-ZMM-ZMM-ZMM-",
                    "XMM-XMM-YMM-YMM-ZMM-ZMM-ZMM-ZMM+",
                    "XMM-XMM-XMM-XMM-YMM-YMM-YMM-YMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-",
                    "XMM-XMM-XMM-XMM+YMM-YMM-YMM-YMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-",
                    "XMM-XMM-XMM-XMM-YMM-YMM-YMM-YMM+ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-",
                    "XMM-XMM-XMM-XMM-YMM-YMM-YMM-YMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM+",
                    "",
                    "\u0000" + "0".repeat(15) + "1".repeat(15) + "\u00ffsome-string",
    };

    private static Result executeCompiledMethod(InstalledCode compiledMethod, Object... args) {
        try {
            return new Result(compiledMethod.executeVarargs(args), null);
        } catch (Throwable e) {
            return new Result(null, e);
        }
    }

    @Test
    public void testStringCodingISO() throws ClassNotFoundException {
        Class<?> klass = Class.forName("java.lang.StringCoding");
        ResolvedJavaMethod method = getResolvedJavaMethod(klass, "encodeISOArray0");
        StructuredGraph graph = getIntrinsicGraph(method, CompilationIdentifier.INVALID_COMPILATION_ID, getDebugContext(), StructuredGraph.AllowAssumptions.YES, null);
        InstalledCode compiledMethod = getCode(method, graph);

        // Caller of the tested method should guarantee the indexes are within the range -- there is
        // no need for boundary-value testing.
        for (String input : testData) {
            char[] value = input.toCharArray();
            int len = value.length;
            byte[] sa = new byte[len << 1];
            UNSAFE.copyMemory(value, UNSAFE.arrayBaseOffset(char[].class), sa, UNSAFE.arrayBaseOffset(byte[].class), sa.length);
            byte[] daExpected = new byte[len];
            byte[] daActual = new byte[len];
            int sp = 0;
            int dp = 0;
            while (sp < value.length) {
                Result expected = executeExpected(method, null, sa, sp, daExpected, dp, len);
                Result actual = executeCompiledMethod(compiledMethod, sa, sp, daActual, dp, len);
                assertEquals(expected, actual);
                assertDeepEquals(daExpected, daActual);
                int ret = (int) actual.returnValue;
                sp += ret;
                dp += ret;
                while (sp < value.length && value[sp++] > 0xff) {
                    dp++;
                }
            }
        }
    }

    @Test
    public void testStringCodingAscii() throws ClassNotFoundException {
        Class<?> klass = Class.forName("java.lang.StringCoding");
        ResolvedJavaMethod method = getResolvedJavaMethod(klass, "encodeAsciiArray0");
        StructuredGraph graph = getIntrinsicGraph(method, CompilationIdentifier.INVALID_COMPILATION_ID, getDebugContext(), StructuredGraph.AllowAssumptions.YES, null);
        InstalledCode compiledMethod = getCode(method, graph);

        // Caller of the tested method should guarantee the indexes are within the range --
        // there is no need for boundary-value testing.
        for (String inputOrig : testData) {
            for (String input : new String[]{inputOrig, "_".repeat(31) + inputOrig, "_".repeat(63) + inputOrig}) {
                char[] value = input.toCharArray();
                int len = value.length;
                byte[] daExpected = new byte[len];
                byte[] daActual = new byte[len];
                int sp = 0;
                int dp = 0;
                while (sp < value.length) {
                    Result expected = executeExpected(method, null, value, sp, daExpected, dp, len - sp);
                    Result actual = executeCompiledMethod(compiledMethod, value, sp, daActual, dp, len - sp);
                    assertEquals(expected, actual);
                    assertDeepEquals(daExpected, daActual);
                    int ret = (int) actual.returnValue;
                    sp += ret;
                    dp += ret;
                    while (sp < value.length && value[sp++] > 0x7f) {
                        dp++;
                    }
                }
            }
        }
    }

    @Test
    public void testISOEncoding() throws ClassNotFoundException {
        Class<?> klass = Class.forName("sun.nio.cs.ISO_8859_1$Encoder");
        ResolvedJavaMethod method = getResolvedJavaMethod(klass, "encodeISOArray0");
        StructuredGraph graph = getIntrinsicGraph(method, CompilationIdentifier.INVALID_COMPILATION_ID, getDebugContext(), StructuredGraph.AllowAssumptions.YES, null);
        InstalledCode compiledMethod = getCode(method, graph);

        // Caller of the tested method should guarantee the indexes are within the range -- there is
        // no need for boundary-value testing. See ISO_8859_1$Encoder.encodeISOArrayCheck
        for (String input : testData) {
            char[] sa = input.toCharArray();
            int len = sa.length;
            byte[] daExpected = new byte[len];
            byte[] daActual = new byte[len];
            int sp = 0;
            int dp = 0;
            while (sp < sa.length) {
                Result expected = executeExpected(method, null, sa, sp, daExpected, dp, len);
                Result actual = executeCompiledMethod(compiledMethod, sa, sp, daActual, dp, len);
                assertEquals(expected, actual);
                assertDeepEquals(daExpected, daActual);
                int ret = (int) actual.returnValue;
                sp += ret;
                dp += ret;
                while (sp < sa.length && sa[sp++] > 0xff) {
                    dp++;
                }
            }
        }
    }
}
