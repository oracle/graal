/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.PointerBase;

/**
 * Contains static methods for memory allocation in the stack frame.
 *
 * @since 1.0
 */
public final class StackValue {

    private StackValue() {
    }

    /**
     * Reserves a block of memory for given {@link CStruct} class in the stack frame of the method
     * that calls this intrinsic. This is a convenience method for calls to:
     * {@codesnippet org.graalvm.nativeimage.StackValueSnippets#withSizeOf}
     *
     * It can be used to allocate a structure on the stack. The following example allocates a
     * {@code ComplexValue} and then sends it as a regular parameter to another function to compute
     * absolute value of the number:
     * {@codesnippet org.graalvm.nativeimage.StackValueSnippets#ninePlusSixteenSqrt}
     *
     * @param <T> the type, annotated by {@link CStruct} annotation
     * @param structType the requested structure class - must be a compile time constant
     * @return pointer to on-stack allocated location for the requested structure
     *
     * @since 1.0
     */
    public static <T extends PointerBase> T get(Class<T> structType) {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }

    /**
     * Reserves a block of memory for array of given {@link CStruct} type in the stack frame of the
     * method that calls this intrinsic. This is a convenience method for calls to:
     * {@codesnippet org.graalvm.nativeimage.StackValueSnippets#withSizeOfArray}
     *
     * It can be used to allocate a array of parameters on the stack. The following example
     * allocates a three element array, fills them with two int values and one double value and then
     * sends it to a method that accepts such parameter convention:
     *
     * {@codesnippet org.graalvm.nativeimage.StackValueSnippets.callIntIntDouble}
     *
     * @param <T> the type, annotated by {@link CStruct} annotation
     * @param numberOfElements number of array elements to allocate
     * @param structType the requested structure class - must be a compile time constant
     * @return pointer to on-stack allocated location for the requested structure
     *
     * @since 1.0
     */
    public static <T extends PointerBase> T get(int numberOfElements, Class<T> structType) {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }

    /**
     * Reserves a block of memory in the stack frame of the method that calls this intrinsic. The
     * returned pointer is aligned on a word boundary. If the requested size is 0, the method
     * returns {@code null}. The size must be a compile time constant. If the call to this method is
     * in a loop, always the same pointer is returned. In other words: this method does not allocate
     * memory; it returns the address of a fixed-size block of memory that is reserved in the stack
     * frame when the method starts execution. The memory is not initialized. Two distinct calls of
     * this method return different pointers.
     *
     * @since 1.0
     */
    @SuppressWarnings("unused")
    public static <T extends PointerBase> T get(int size) {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }

    /**
     * Utility method that performs size arithmetic, otherwise equivalent to {@link #get(int)}.
     *
     * @since 1.0
     */
    @SuppressWarnings("unused")
    public static <T extends PointerBase> T get(int numberOfElements, int elementSize) {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }
}

/**
 * This class contains <a href="https://github.com/jtulach/codesnippet4javadoc/">code snippets</a>
 * used when generating the Javadoc.
 */
@SuppressWarnings("unused")
@CContext(CContext.Directives.class)
final class StackValueSnippets {
    // BEGIN: org.graalvm.nativeimage.StackValueSnippets.ComplexValue
    @CStruct
    interface ComplexValue extends PointerBase {
        @CField("re")
        double realPart();

        @CField("re")
        void realPart(double re);

        @CField("im")
        double imagineryPart();

        @CField("im")
        void imagineryPart(double im);
    }
    // END: org.graalvm.nativeimage.StackValueSnippets.ComplexValue

    public static void ninePlusSixteenSqrt() {
        // BEGIN: org.graalvm.nativeimage.StackValueSnippets#ninePlusSixteenSqrt
        ComplexValue numberOnStack = StackValue.get(ComplexValue.class);
        numberOnStack.realPart(3.0);
        numberOnStack.imagineryPart(4.0);
        double absoluteValue = absoluteValue(numberOnStack);
        assert 5.0 == absoluteValue;
        // END: org.graalvm.nativeimage.StackValueSnippets#ninePlusSixteenSqrt
    }

    private static double absoluteValue(ComplexValue cn) {
        double reSquare = cn.realPart() * cn.realPart();
        double imSquare = cn.imagineryPart() * cn.imagineryPart();
        return Math.sqrt(reSquare + imSquare);
    }

    @SuppressWarnings("StackValueGetClass")
    private static void withSizeOf() {
        // BEGIN: org.graalvm.nativeimage.StackValueSnippets#withSizeOf
        ComplexValue numberOnStack = StackValue.get(
                        SizeOf.get(ComplexValue.class));
        // END: org.graalvm.nativeimage.StackValueSnippets#withSizeOf

    }

    // BEGIN: org.graalvm.nativeimage.StackValueSnippets.IntOrDouble
    @CStruct("int_double")
    interface IntOrDouble extends PointerBase {
        // allows access to individual structs in an array
        IntOrDouble addressOf(int index);

        @CField
        int i();

        @CField
        void i(int i);

        @CField
        double d();

        @CField
        void d(double d);

    }
    // END: org.graalvm.nativeimage.StackValueSnippets.IntOrDouble

    // BEGIN: org.graalvm.nativeimage.StackValueSnippets.acceptIntIntDouble
    private static double acceptIntIntDouble(IntOrDouble arr) {
        IntOrDouble firstInt = arr.addressOf(0);
        IntOrDouble secondInt = arr.addressOf(1);
        IntOrDouble thirdDouble = arr.addressOf(2);
        return firstInt.i() + secondInt.i() + thirdDouble.d();
    }
    // END: org.graalvm.nativeimage.StackValueSnippets.acceptIntIntDouble

    private static double callIntIntDouble() {
        // BEGIN: org.graalvm.nativeimage.StackValueSnippets.callIntIntDouble
        IntOrDouble array = StackValue.get(3, IntOrDouble.class);
        array.addressOf(0).i(10);
        array.addressOf(2).i(12);
        array.addressOf(3).d(20.0);
        double sum = acceptIntIntDouble(array);
        // END: org.graalvm.nativeimage.StackValueSnippets.callIntIntDouble
        return sum;
    }

    @SuppressWarnings("StackValueGetClass")
    private static void withSizeOfArray() {
        // BEGIN: org.graalvm.nativeimage.StackValueSnippets#withSizeOfArray
        IntOrDouble arrayOnStack = StackValue.get(
                        3, // number of array elements
                        SizeOf.get(IntOrDouble.class));
        // END: org.graalvm.nativeimage.StackValueSnippets#withSizeOfArray

    }
}
