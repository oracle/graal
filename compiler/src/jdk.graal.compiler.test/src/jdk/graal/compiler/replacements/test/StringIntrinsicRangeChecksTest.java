/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.test.AddExports;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@AddExports({"java.base/java.lang"})
public class StringIntrinsicRangeChecksTest extends GraalCompilerTest {

    // Prepare test arrays
    private static int SIZE = 16;
    private static byte[] byteArray = new byte[SIZE];
    private static char[] charArray = new char[SIZE];

    static final MethodHandle StringUTF16_compressByte;
    static final MethodHandle StringUTF16_compressChar;
    static final MethodHandle StringUTF16_toBytes;
    static final MethodHandle StringUTF16_getChars;

    static final MethodHandle StringLatin1_inflateByte;
    static final MethodHandle StringLatin1_inflateChar;

    static MethodHandle unreflect(Method m) throws IllegalAccessException {
        m.setAccessible(true);
        return MethodHandles.lookup().unreflect(m);
    }

    static {
        try {
            Class<?> stringUTF16 = Class.forName("java.lang.StringUTF16");
            StringUTF16_compressByte = unreflect(stringUTF16.getMethod("compress", byte[].class, int.class, byte[].class, int.class, int.class));
            StringUTF16_compressChar = unreflect(stringUTF16.getMethod("compress", char[].class, int.class, byte[].class, int.class, int.class));
            StringUTF16_toBytes = unreflect(stringUTF16.getMethod("toBytes", char[].class, int.class, int.class));
            StringUTF16_getChars = unreflect(stringUTF16.getMethod("getChars", byte[].class, int.class, int.class, char[].class, int.class));

            Class<?> stringLatin1 = Class.forName("java.lang.StringLatin1");
            StringLatin1_inflateByte = unreflect(stringLatin1.getMethod("inflate", byte[].class, int.class, byte[].class, int.class, int.class));
            StringLatin1_inflateChar = unreflect(stringLatin1.getMethod("inflate", byte[].class, int.class, char[].class, int.class, int.class));

        } catch (NoSuchMethodException | ClassNotFoundException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static long compiledReturns = 0;

    @SuppressWarnings("unused")
    public static byte[] compressByte(byte[] src, int srcOff, int dstSize, int dstOff, int len) throws Throwable {
        byte[] dst = new byte[dstSize];
        int result = (int) StringUTF16_compressByte.invokeExact(src, srcOff, dst, dstOff, len);
        if (GraalDirectives.inCompiledCode()) {
            compiledReturns++;
        }
        return dst;
    }

    @SuppressWarnings("unused")
    public static byte[] compressChar(char[] src, int srcOff, int dstSize, int dstOff, int len) throws Throwable {
        byte[] dst = new byte[dstSize];
        int result = (int) StringUTF16_compressChar.invokeExact(src, srcOff, dst, dstOff, len);
        if (GraalDirectives.inCompiledCode()) {
            compiledReturns++;
        }
        return dst;
    }

    public static byte[] inflateByte(byte[] src, int srcOff, int dstSize, int dstOff, int len) throws Throwable {
        byte[] dst = new byte[dstSize];
        StringLatin1_inflateByte.invokeExact(src, srcOff, dst, dstOff, len);
        if (GraalDirectives.inCompiledCode()) {
            compiledReturns++;
        }
        return dst;
    }

    public static char[] inflateChar(byte[] src, int srcOff, int dstSize, int dstOff, int len) throws Throwable {
        char[] dst = new char[dstSize];
        StringLatin1_inflateChar.invokeExact(src, srcOff, dst, dstOff, len);
        if (GraalDirectives.inCompiledCode()) {
            compiledReturns++;
        }
        return dst;
    }

    public static byte[] toBytes(char[] value, int off, int len) throws Throwable {
        byte[] result = (byte[]) StringUTF16_toBytes.invokeExact(value, off, len);
        if (GraalDirectives.inCompiledCode()) {
            compiledReturns++;
        }
        return result;
    }

    public static char[] getChars(byte[] value, int srcBegin, int srcEnd, int dstSize, int dstBegin) throws Throwable {
        char[] dst = new char[dstSize];
        StringUTF16_getChars.invokeExact(value, srcBegin, srcEnd, dst, dstBegin);
        if (GraalDirectives.inCompiledCode()) {
            compiledReturns++;
        }
        return dst;
    }

    public void check(ResolvedJavaMethod method, boolean shouldThrow, Object... args) throws Exception {
        // Prepare error message
        String message = method.getName() + "(";
        for (int i = 0; i < args.length; ++i) {
            message += args[i];
            message += (i + 1 < args.length) ? ", " : ")";
        }

        long count = compiledReturns;
        Result result = test(method, null, args);
        if (result.exception != null) {
            // Get actual exception
            Throwable t = result.exception;
            if (!shouldThrow) {
                throw new RuntimeException("Unexpected exception thrown for " + message, t);
            }
            if (t instanceof StringIndexOutOfBoundsException ||
                            t instanceof ArrayIndexOutOfBoundsException) {
                // Expected exception. Make sure that the exception was not thrown in
                // UTF16.putChar/getChar
                // because the corresponding intrinsics are unchecked and the Java code should do
                // all the checks.
                StackTraceElement[] stack = t.getStackTrace();
                if (stack.length != 0) {
                    String methodName = stack[0].getMethodName();
                    if (methodName.equals("putChar") || methodName.equals("getChar")) {
                        throw new RuntimeException("Exception thrown in " + methodName + " for " + message, t);
                    }
                }
            }
            return;
        }
        if (shouldThrow) {
            throw new RuntimeException("No exception thrown for " + message);
        } else {
            // No exception should be thrown and no deopt should have occurred.
            Assert.assertEquals("deoptimized for arguments " + method.getName() + " " + Arrays.toString(args), count + 1, compiledReturns);
        }
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        return super.editGraphBuilderConfiguration(conf).withBytecodeExceptionMode(GraphBuilderConfiguration.BytecodeExceptionMode.CheckAll);
    }

    @Test
    public void test() throws Exception {
        // Get intrinsified String API methods
        ResolvedJavaMethod compressByte = getResolvedJavaMethod(StringIntrinsicRangeChecksTest.class, "compressByte", byte[].class, int.class, int.class, int.class, int.class);
        ResolvedJavaMethod compressChar = getResolvedJavaMethod(StringIntrinsicRangeChecksTest.class, "compressChar", char[].class, int.class, int.class, int.class, int.class);
        ResolvedJavaMethod inflateByte = getResolvedJavaMethod(StringIntrinsicRangeChecksTest.class, "inflateByte", byte[].class, int.class, int.class, int.class, int.class);
        ResolvedJavaMethod inflateChar = getResolvedJavaMethod(StringIntrinsicRangeChecksTest.class, "inflateChar", byte[].class, int.class, int.class, int.class, int.class);
        ResolvedJavaMethod toBytes = getResolvedJavaMethod(StringIntrinsicRangeChecksTest.class, "toBytes", char[].class, int.class, int.class);
        ResolvedJavaMethod getChars = getResolvedJavaMethod(StringIntrinsicRangeChecksTest.class, "getChars", byte[].class, int.class, int.class, int.class, int.class);

        // Check different combinations of arguments (source/destination offset and length)
        for (int srcOff = 0; srcOff < SIZE; ++srcOff) {
            for (int dstOff = 0; dstOff < SIZE; ++dstOff) {
                for (int len = 0; len < SIZE; ++len) {
                    // Check for potential overlows in source or destination array
                    boolean srcOverflow = (srcOff + len) > SIZE;
                    boolean srcOverflowB = (2 * srcOff + 2 * len) > SIZE;
                    boolean dstOverflow = (dstOff + len) > SIZE;
                    boolean dstOverflowB = (2 * dstOff + 2 * len) > SIZE;
                    // Check if an exception is thrown and bail out if result is inconsistent with
                    // above assumptions (for example, an exception was not thrown although an
                    // overflow happened).
                    check(compressByte, srcOverflowB || dstOverflow, byteArray, srcOff, SIZE, dstOff, len);
                    check(compressChar, srcOverflow || dstOverflow, charArray, srcOff, SIZE, dstOff, len);
                    check(inflateByte, srcOverflow || dstOverflowB, byteArray, srcOff, SIZE, dstOff, len);
                    check(inflateChar, srcOverflow || dstOverflow, byteArray, srcOff, SIZE, dstOff, len);
                    check(toBytes, srcOverflow, charArray, srcOff, len);

                    int srcEnd = len; // len is actually srcEnd in getChars
                    // getChars only does work if srcOff is below srcEnd. Overflow might occur in
                    // the copy loop if the scaled srcEnd is larger than the source array or if the
                    // amount to be copied is larger than the dest array.
                    boolean getCharsOver = (srcOff < srcEnd) && ((2 * (srcEnd - 1) >= SIZE) || ((dstOff + srcEnd - srcOff) > SIZE));
                    check(getChars, getCharsOver, byteArray, srcOff, srcEnd, SIZE, dstOff);
                }
            }
        }
    }

}
